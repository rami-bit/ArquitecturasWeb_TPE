"""Arnés de integración de punta a punta para TicketYa (PDF-E2-TESTING).

Uso: python postman/integration-harness.py [--down]

Comprueba o levanta el stack aislado, espera a que todos los servicios estén
`healthy`, obtiene tokens del IdP local y ejecuta la colección Postman por el
Gateway. Termina con código distinto de cero si falla cualquier comprobación.

Si el puerto del Gateway ya responde, el arnés reutiliza ese stack y no detiene
ni elimina nada. Si no responde, levanta su propio proyecto Compose y con
`--down` detiene únicamente los contenedores que creó (sin `-v`).
"""

import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parent.parent

# La colección usa estos `sub` en sus comprobaciones de ownership y aislamiento.
FIXED_SUBS = {
    "organizer": "e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b",
    "client": "e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b",
    "numberedBuyer": "9f44c33f-35eb-4764-becd-02f6e2414701",
    "admin": "a1b2c3d4-1111-2222-3333-444455556666",
    "accessStaff": "d160e54c-5f88-49a1-9e9a-2c901ac70a01",
}
ROLE_CLAIMS = {
    "organizer": "ORGANIZER", "client": "CLIENT", "numberedBuyer": "CLIENT",
    "admin": "ADMIN", "accessStaff": "ACCESS_STAFF",
}
GATEWAY = os.environ.get("BASE_URL", "http://localhost:8080").rstrip("/")
IDP = os.environ.get("IDP_TOKEN_URL", "http://localhost:19998/token")
COMPOSE_PROJECT = "ticketya_e2e_harness"
COLLECTION = ROOT / "postman" / "ticketya.postman_collection.json"
IDP_JAVA = ROOT / "tools" / "local-test-idp" / "LocalTestIdp.java"
IDP_KEY = ROOT / ".local" / "idp-key.pem"


def run(command, check=True, capture=False):
    result = subprocess.run(command, cwd=ROOT, text=True, capture_output=capture)
    if check and result.returncode != 0:
        raise RuntimeError(f"Comando falló ({result.returncode}): {' '.join(command)}")
    return result


def healthy(url, timeout=2):
    try:
        with urllib.request.urlopen(url, timeout=timeout) as response:
            return response.status == 200
    except (urllib.error.URLError, OSError):
        return False


def compose(project, *args, check=True, capture=False):
    env = dict(os.environ)
    if args and args[0] == "up":
        env["JWT_ISSUER_URI"] = "http://host.docker.internal:19998"
        env["JWT_JWK_SET_URI"] = "http://host.docker.internal:19998/.well-known/jwks.json"
    command = ["docker", "compose", "-p", project, *args]
    result = subprocess.run(command, cwd=ROOT, env=env, text=True,
                            capture_output=capture)
    if check and result.returncode != 0:
        raise RuntimeError(f"Compose falló ({result.returncode}): {' '.join(command)}\n"
                           f"{result.stdout}\n{result.stderr}")
    return result


def detect_running_project():
    result = subprocess.run(
        ["docker", "ps", "--filter", "publish=8080", "--format",
         '{{.Label "com.docker.compose.project"}}'],
        cwd=ROOT, text=True, capture_output=True)
    projects = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    return projects[0] if projects else None


def wait_for_stack(project, timeout_seconds=180):
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        output = compose(project, "ps", "--format", "{{.Name}} {{.Status}}",
                         check=False, capture=True).stdout.strip()
        lines = [line for line in output.splitlines() if line.strip()]
        if lines and all("healthy" in line for line in lines):
            return lines
        time.sleep(3)
    raise RuntimeError(f"El stack {project} no quedó healthy en {timeout_seconds}s:\n{output}")


def ensure_idp():
    jwks = "http://localhost:19998/.well-known/jwks.json"
    if healthy(jwks):
        return None
    IDP_KEY.parent.mkdir(parents=True, exist_ok=True)
    log = open(ROOT / ".local" / "idp-harness.log", "w", encoding="utf-8")
    process = subprocess.Popen([
        "java", str(IDP_JAVA), "--port", "19998",
        "--issuer", "http://host.docker.internal:19998",
        "--key-file", str(IDP_KEY),
    ], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    for _ in range(30):
        if healthy(jwks):
            return process
        time.sleep(1)
    process.terminate()
    raise RuntimeError("El IdP de prueba no inició; revisa .local/idp-harness.log")


def mint(role):
    query = urllib.parse.urlencode({"sub": FIXED_SUBS[role], "role": ROLE_CLAIMS[role]})
    with urllib.request.urlopen(f"{IDP}?{query}", timeout=15) as response:
        token = json.load(response).get("access_token")
    if not token:
        raise RuntimeError(f"El IdP no devolvió access_token para {role}")
    return token


def run_newman():
    tokens = {role: mint(role) for role in
              ("organizer", "client", "numberedBuyer", "admin", "accessStaff")}
    import shutil
    npx = shutil.which("npx.cmd") or shutil.which("npx") or "npx"
    command = [npx, "--yes", "newman", "run", str(COLLECTION),
               "--env-var", f"baseUrl={GATEWAY}"]
    for role, token in tokens.items():
        command += ["--env-var", f"{role}Token={token}"]
    command += ["--reporters", "cli", "--reporter-cli-no-console"]
    result = subprocess.run(command, cwd=ROOT, text=True,
                            capture_output=True, encoding="utf-8", errors="replace")
    if result.returncode != 0:
        raise RuntimeError(f"Newman falló ({result.returncode}):\n{result.stdout}\n{result.stderr}")
    return result.stdout


def metrics(output):
    def value(label):
        match = re.search(rf"│\s*{label}\s*│\s*(\d+)\s*│\s*(\d+)\s*│", output)
        return (int(match.group(1)), int(match.group(2))) if match else (0, 0)

    return {
        "requests": value("requests"),
        "assertions": value("assertions"),
        "test_scripts": value("test-scripts"),
    }


def main():
    started_by_us = False
    project = COMPOSE_PROJECT
    idp_process = None
    try:
        if healthy(f"{GATEWAY}/actuator/health"):
            project = detect_running_project()
            print(f"Reutilizando stack existente en {GATEWAY}"
                  + (f" (proyecto {project})" if project else ""))
        else:
            print(f"Levantando stack aislado {project}")
            idp_process = ensure_idp()
            compose(project, "up", "--build", "-d", "--wait")
            started_by_us = True

        if project:
            services = wait_for_stack(project)
        else:
            services = [f"gateway {GATEWAY}"]
        print(f"Salud: {len(services)} servicios/verificaciones OK")

        output = run_newman()
        result = metrics(output)
        print(f"Newman: {result['requests'][0]} requests, "
              f"{result['assertions'][0]} aserciones, "
              f"{result['assertions'][1]} fallos")
        print(f"SUMMARY: servicios={len(services)} requests={result['requests'][0]} "
              f"assertions={result['assertions'][0]} failed={result['assertions'][1]} RESULT=PASS")
    except Exception as error:
        print(f"SUMMARY: RESULT=FAIL motivo={error}")
        return 1
    finally:
        if idp_process is not None:
            idp_process.terminate()
        if started_by_us and "--down" in sys.argv:
            compose(project, "down", check=False)
            print(f"Stack {project} detenido (volúmenes conservados)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
