"""Exercise the real image against a disposable MongoDB. Never targets a public service."""
import json
import os
import time
import uuid
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

BASE = os.environ.get("MESSENGER_SMOKE_URL", "http://127.0.0.1:18081")
parsed = urlparse(BASE)
if parsed.scheme != "http" or parsed.hostname not in ("127.0.0.1", "localhost"):
    raise SystemExit("This test only runs against a disposable local container.")
ORIGIN = "https://messenger-ci.onrender.com"


def request(method, path, body=None, token=None, origin=ORIGIN):
    headers = {"Origin": origin}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    try:
        with urlopen(Request(BASE + path, data=data, headers=headers, method=method), timeout=10) as response:
            return response.status, response.read().decode()
    except HTTPError as error:
        return error.code, error.read().decode()


def api(method, path, body=None, token=None, expected=200):
    status, text = request(method, path, body, token)
    if status != expected:
        raise AssertionError(f"{method} {path}: expected {expected}, got {status}; {text[:400]}")
    return json.loads(text) if text else None


def main():
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        try:
            if request("GET", "/login")[0] == 200:
                break
        except (URLError, TimeoutError, ConnectionError):
            pass
        time.sleep(1)
    else:
        raise AssertionError("Container did not become ready within 180 seconds.")
    assert request("GET", "/ws-stomp/info")[0] == 200
    assert request("GET", "/ws-stomp/info", origin="https://not-allowed.invalid")[0] == 403
    assert request("GET", "/api/friends")[0] == 401
    print("PASS: login, exact cloud origin, rejected foreign origin, unauthenticated API", flush=True)

    suffix = uuid.uuid4().hex[:8]
    password = "Disposable_Smoke_42!"
    users = []
    for name in ("alice", "bob"):
        identifier = f"smoke_{name}_{suffix}"
        api("POST", "/api/auth/register", {"id": identifier, "userName": name,
            "password": password, "phoneNumber": "01012345678"}, expected=201)
        login = api("POST", "/api/auth/login", {"id": identifier, "password": password})
        users.append((identifier, login["token"]))
    (alice, at), (bob, bt) = users
    api("POST", "/api/friends", {"friendId": bob}, at, expected=204)
    friends = api("GET", "/api/friends", token=at)
    assert any(friend["userId"] == bob for friend in friends)
    room = api("POST", "/api/rooms/dm", {"peerId": bob}, at)
    message = api("POST", "/api/messages", {"roomId": room["id"], "content": "container persistence smoke"}, at)
    history = api("GET", "/api/messages/" + room["id"], token=bt)
    assert any(item["id"] == message["id"] for item in history)
    print("PASS: real MongoDB registration, login, friend list, DM save and peer history", flush=True)


if __name__ == "__main__":
    main()
