#!/usr/bin/env python3
"""Remove the seeded demo platforms, boards and people. Anything you created is untouched."""
import json, os, sys, urllib.request, urllib.error

API      = os.environ.get("API", "http://127.0.0.1:6060")
EMAIL    = os.environ.get("ADMIN_EMAIL", "")
PASSWORD = os.environ.get("ADMIN_PASSWORD", "")

DEMO_BOARDS    = {"LMS", "SP", "GRA", "INF"}
DEMO_PLATFORMS = {"EDU", "RND", "OPS"}
DEMO_PEOPLE    = {"nigar.aliyeva@aztu.edu.az", "elvin.mammadov@aztu.edu.az", "leyla.huseynova@aztu.edu.az"}
DROP_PEOPLE    = os.environ.get("REMOVE_DEMO_USERS", "false").lower() == "true"

def call(method, path, body=None, token=None):
    req = urllib.request.Request(API + path, method=method, headers={"Content-Type": "application/json"})
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw

if not EMAIL or not PASSWORD:
    sys.exit("Set ADMIN_EMAIL and ADMIN_PASSWORD before running.")

status, login = call("POST", "/api/auth/login", {"email": EMAIL, "password": PASSWORD})
if status != 200:
    sys.exit(f"Login failed ({status}): {login}")
token = login["token"]
if login["user"]["role"] != "ADMIN":
    sys.exit("That account is not an administrator.")

# Boards first: a platform cannot be deleted while it still owns boards.
_, boards = call("GET", "/api/boards", None, token)
removed_boards, kept_boards = [], []
for board in boards:
    if board["boardKey"] in DEMO_BOARDS:
        st, err = call("DELETE", f"/api/boards/{board['id']}", None, token)
        (removed_boards if st in (200, 204) else kept_boards).append(
            board["boardKey"] if st in (200, 204) else f"{board['boardKey']}({st} {err})")
    else:
        kept_boards.append(board["boardKey"])

_, platforms = call("GET", "/api/platforms", None, token)
removed_platforms, kept_platforms = [], []
for platform in platforms:
    if platform["code"] in DEMO_PLATFORMS:
        st, err = call("DELETE", f"/api/platforms/{platform['id']}", None, token)
        (removed_platforms if st in (200, 204) else kept_platforms).append(
            platform["code"] if st in (200, 204) else f"{platform['code']}({st} {err})")
    else:
        kept_platforms.append(platform["code"])

removed_people = []
if DROP_PEOPLE:
    _, page = call("GET", "/api/users?size=200", None, token)
    for person in page["content"]:
        if person["email"] in DEMO_PEOPLE:
            st, err = call("DELETE", f"/api/users/{person['id']}", None, token)
            removed_people.append(person["email"] if st in (200, 204) else f"{person['email']} ({st})")

print(f"  removed boards    : {', '.join(removed_boards) or 'none'}")
print(f"  removed platforms : {', '.join(removed_platforms) or 'none'}")
if DROP_PEOPLE:
    print(f"  removed people    : {', '.join(removed_people) or 'none'}")
print(f"  kept boards       : {', '.join(kept_boards) or 'none'}")
print(f"  kept platforms    : {', '.join(kept_platforms) or 'none'}")
