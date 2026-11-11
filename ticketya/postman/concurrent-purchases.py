"""Exercise complete competing purchases through the public Gateway.

Run against a healthy Compose stack: python postman/concurrent-purchases.py
Set BASE_URL to override http://localhost:8080.
"""

import json
import os
import threading
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor


BASE_URL = os.environ.get("BASE_URL", "http://localhost:8080").rstrip("/")


def request(method, path, body=None, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        BASE_URL + path,
        data=data,
        method=method,
        headers={"Content-Type": "application/json", **(headers or {})},
    )
    try:
        with urllib.request.urlopen(req, timeout=45) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def expect(method, path, status, body=None, headers=None):
    actual, payload = request(method, path, body, headers)
    assert actual == status, f"{method} {path}: expected {status}, got {actual}: {payload}"
    return payload


def main():
    run_id = uuid.uuid4().hex[:12]
    organizer = str(uuid.uuid4())
    organizer_headers = {"X-User-Id": organizer, "X-User-Role": "ORGANIZER"}
    venue = expect("POST", "/api/venues", 201, {
        "name": f"Concurrency {run_id}", "address": "Test 1", "city": "Buenos Aires",
        "latitude": -34.6037, "longitude": -58.3816,
        "sectors": [{"name": "Single seat", "type": "NUMBERED", "capacity": 1,
                     "seats": [{"rowLabel": "A", "seatNumber": 1}]}],
    }, {"X-User-Role": "ADMIN"})
    sector_id = venue["sectors"][0]["id"]
    event = expect("POST", "/api/management/events", 201, {
        "name": f"Concurrency {run_id}", "description": "Competing purchases",
        "eventType": "MUSIC", "startsAt": "2030-06-01T20:00:00-03:00",
        "venueId": venue["id"], "participants": ["Concurrency Test"], "offerings": [],
    }, organizer_headers)
    event_id = event["id"]
    expect("PUT", f"/api/management/events/{event_id}/offerings", 200,
           {"offerings": [{"sectorId": sector_id, "price": "25.00"}]}, organizer_headers)
    expect("POST", f"/api/management/events/{event_id}/publish", 200,
           headers=organizer_headers)
    availability = expect("GET", f"/api/inventory/events/{event_id}/availability", 200)
    numbered = next(s for s in availability["sectors"] if s["type"] == "NUMBERED")
    seat_id = numbered["seats"][0]["seatId"]
    body = {"eventId": event_id,
            "items": [{"sectorId": numbered["sectorId"], "seatIds": [seat_id]}],
            "payment": {"method": "CARD", "token": "tok_approved"}}

    buyers = [str(uuid.uuid4()) for _ in range(12)]
    barrier = threading.Barrier(len(buyers))

    def buy(index):
        barrier.wait(timeout=30)
        return request("POST", "/api/orders", body, {
            "X-User-Id": buyers[index],
            "Idempotency-Key": f"concurrent-{run_id}-{index}",
        })

    with ThreadPoolExecutor(max_workers=len(buyers)) as pool:
        results = list(pool.map(buy, range(len(buyers))))

    successful = [payload for status, payload in results if status == 201]
    failures = [(status, payload) for status, payload in results if status != 201]
    assert len(successful) == 1, f"expected one successful purchase; statuses: {[s for s, _ in results]}"
    assert all(status == 409 for status, _ in failures), f"unexpected failures: {failures}"
    winner = successful[0]
    assert winner["status"] == "CONFIRMED", winner
    assert winner["items"][0]["units"][0]["seatId"] == seat_id, winner
    assert len(winner["items"][0]["units"]) == 1, winner

    after = expect("GET", f"/api/inventory/events/{event_id}/availability", 200)
    sold_sector = next(s for s in after["sectors"] if s["sectorId"] == numbered["sectorId"])
    assert sold_sector["sold"] == 1 and sold_sector["reserved"] == 0, sold_sector
    assert sold_sector["seats"][0]["status"] == "SOLD", sold_sector
    print(f"PASS: 12 purchases, one CONFIRMED, 11 conflicts, one SOLD seat; event={event_id}")


if __name__ == "__main__":
    main()
