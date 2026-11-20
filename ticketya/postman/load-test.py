"""Prueba de carga del flujo de compra por el API Gateway (PDF-E2-TESTING).

Uso: python postman/load-test.py
Requiere un stack saludable y el IdP de prueba local en http://localhost:19998.
No instala dependencias: usa sólo la biblioteca estándar de Python 3.

El perfil por omisión recorre 10, 20 y 40 compradores concurrentes. Cada
comprador tiene un `sub` y una `Idempotency-Key` propios, y el evento se crea
en cada corrida para no mezclar datos con la colección funcional.
"""

import json
import os
import statistics
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
from time import perf_counter


BASE_URL = os.environ.get("BASE_URL", "http://localhost:8080").rstrip("/")
IDP_TOKEN_URL = os.environ.get("IDP_TOKEN_URL", "http://localhost:19998/token")
STAGES = [int(value) for value in os.environ.get("LOAD_STAGES", "10,20,40").split(",")]
REPORT = Path(__file__).resolve().parent.parent / "docs" / "load-test-report.md"


def request(method, path, body=None, headers=None, timeout=45):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        BASE_URL + path,
        data=data,
        method=method,
        headers={"Content-Type": "application/json", **(headers or {})},
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        try:
            payload = json.load(error)
        except (ValueError, json.JSONDecodeError):
            payload = {}
        return error.code, payload


def expect(method, path, status, body=None, headers=None):
    actual, payload = request(method, path, body, headers)
    assert actual == status, f"{method} {path}: esperado {status}, obtenido {actual}: {payload}"
    return payload


def mint_token(subject, role):
    query = urllib.parse.urlencode({"sub": subject, "role": role})
    try:
        with urllib.request.urlopen(f"{IDP_TOKEN_URL}?{query}", timeout=15) as response:
            token = json.load(response).get("access_token")
    except (urllib.error.URLError, urllib.error.HTTPError) as error:
        raise AssertionError(f"No se pudo emitir un token {role} en {IDP_TOKEN_URL}: {error}") from error
    assert token, "El IdP no devolvió access_token"
    return {"Authorization": f"Bearer {token}"}


def percentile(values, fraction):
    if not values:
        return 0.0
    ordered = sorted(values)
    position = min(len(ordered) - 1, int(round(fraction * (len(ordered) - 1))))
    return ordered[position]


def create_scenario(run_id):
    organizer = mint_token(str(uuid.uuid4()), "ORGANIZER")
    admin = mint_token(str(uuid.uuid4()), "ADMIN")
    venue = expect("POST", "/api/venues", 201, {
        "name": f"Load {run_id}",
        "address": "Calle de prueba 1",
        "city": "Buenos Aires",
        "latitude": -34.6037,
        "longitude": -58.3816,
        "sectors": [{"name": "Campo", "type": "GENERAL_ADMISSION", "capacity": 10000}],
    }, admin)
    sector_id = venue["sectors"][0]["id"]
    event = expect("POST", "/api/management/events", 201, {
        "name": f"Load {run_id}",
        "description": "Prueba de carga de la saga de compra",
        "eventType": "MUSIC",
        "startsAt": "2030-06-01T20:00:00-03:00",
        "venueId": venue["id"],
        "participants": ["Load Test"],
        "offerings": [],
    }, organizer)
    event_id = event["id"]
    expect("PUT", f"/api/management/events/{event_id}/offerings", 200,
           {"offerings": [{"sectorId": sector_id, "price": "10.00"}]}, organizer)
    expect("POST", f"/api/management/events/{event_id}/publish", 200, headers=organizer)
    availability = expect("GET", f"/api/inventory/events/{event_id}/availability", 200)
    general = next(sector for sector in availability["sectors"]
                   if sector["type"] == "GENERAL_ADMISSION")
    body = {
        "eventId": event_id,
        "items": [{"sectorId": general["sectorId"], "quantity": 1}],
        "payment": {"method": "CARD", "token": "tok_approved"},
    }
    return body


def run_stage(users, body, run_id):
    buyers = [(str(uuid.uuid4()), mint_token(str(uuid.uuid4()), "CLIENT")) for _ in range(users)]

    def buy(index):
        subject, headers = buyers[index]
        started = perf_counter()
        status, _ = request("POST", "/api/orders", body, {
            **headers,
            "Idempotency-Key": f"load-{run_id}-{index}-{uuid.uuid4().hex[:6]}",
        })
        return status, (perf_counter() - started) * 1000

    started = perf_counter()
    with ThreadPoolExecutor(max_workers=users) as pool:
        results = list(pool.map(buy, range(users)))
    elapsed = perf_counter() - started
    latencies = [latency for _, latency in results]
    errors = [status for status, _ in results if status != 201]
    return {
        "users": users,
        "requests": len(results),
        "successful": len(results) - len(errors),
        "errors": len(errors),
        "error_statuses": sorted({str(status) for status in errors}),
        "duration_seconds": round(elapsed, 2),
        "throughput_rps": round(len(results) / elapsed, 2) if elapsed else 0,
        "p50_ms": round(percentile(latencies, 0.50), 1),
        "p95_ms": round(percentile(latencies, 0.95), 1),
        "p99_ms": round(percentile(latencies, 0.99), 1),
        "max_ms": round(max(latencies), 1) if latencies else 0,
    }


def git_commit():
    try:
        return subprocess.check_output(["git", "rev-parse", "--short", "HEAD"],
                                       text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        return "desconocido"


def write_report(stages, run_id):
    lines = [
        "# Informe de prueba de carga - flujo de compra",
        "",
        f"- **Fecha:** {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M UTC')}",
        f"- **Commit:** `{git_commit()}`",
        f"- **Objetivo:** medir `POST /api/orders` por el Gateway con compradores concurrentes (PDF-E2-TESTING).",
        f"- **Entorno:** stack `ticketya_f10_verification` (17 contenedores), Java 19, PostgreSQL 16 y MongoDB 7.",
        f"- **Perfil:** {', '.join(str(stage['users']) for stage in stages)} compradores concurrentes por etapa.",
        f"- **Datos:** evento de carga `{run_id}` creado en cada corrida, sector de admisión general con cupo 10000.",
        "- **Reproducción:** `python postman/load-test.py` contra un stack saludable y el IdP local en `:19998`.",
        "",
        "| Etapa (usuarios) | Requests | OK | Errores | Duración (s) | RPS | p50 (ms) | p95 (ms) | p99 (ms) | Máx (ms) |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for stage in stages:
        lines.append(
            f"| {stage['users']} | {stage['requests']} | {stage['successful']} | {stage['errors']} "
            f"| {stage['duration_seconds']} | {stage['throughput_rps']} | {stage['p50_ms']} "
            f"| {stage['p95_ms']} | {stage['p99_ms']} | {stage['max_ms']} |")
    lines += [
        "",
        "## Interpretación",
        "",
        "- Cada comprador usa un `sub` distinto y una clave de idempotencia nueva: no hay competencia por el límite de seis entradas por cliente.",
        "- Se considera éxito un `201 CONFIRMED`; cualquier otro código se cuenta como error. "
        "Códigos observados: "
        + (", ".join(sorted({code for stage in stages for code in stage["error_statuses"]})) or "ninguno") + ".",
        "- La latencia se mide en el cliente desde el envío de la orden hasta la respuesta completa; incluye red, Gateway y la saga completa.",
        "- Esta prueba complementa pero no sustituye la colección Postman ni la prueba de concurrencia sobre un mismo asiento.",
        "- El propósito es didáctico: no se fijan umbrales de rendimiento productivo, se reporta lo medido para el entorno indicado.",
        "",
    ]
    REPORT.write_text("\n".join(lines), encoding="utf-8")


def main():
    run_id = uuid.uuid4().hex[:10]
    body = create_scenario(run_id)
    print(f"Escenario de carga listo: eventId={body['eventId']}")
    stages = []
    for users in STAGES:
        stage = run_stage(users, body, run_id)
        stages.append(stage)
        print(f"  {users} usuarios: {stage['requests']} requests, "
              f"{stage['errors']} errores, p95={stage['p95_ms']} ms, "
              f"RPS={stage['throughput_rps']}")
    write_report(stages, run_id)
    print(f"Informe escrito en {REPORT}")


if __name__ == "__main__":
    main()
