"""
Shared helpers for the API test suites.

These suites create, change and delete data. Point them at a backend running on a throwaway
database, never at one holding real data:

    set SAFARI_DB_URL=jdbc:sqlserver://localhost:1433;databaseName=ceylon_trails_test;encrypt=true;trustServerCertificate=true;integratedSecurity=true
    run-backend.cmd
    python tests/api/run_all.py

SAFARI_API overrides the base URL (default http://localhost:8080/api).
"""
import json
import os
import sys
import urllib.error
import urllib.request

BASE = os.environ.get("SAFARI_API", "http://localhost:8080/api")
PASSWORD = "Password123!"

_results = []


def call(method, path, body=None, token=None):
    """Sends a request and returns (status, parsed JSON body or None)."""
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, {"raw": raw}


def check(label, ok, extra=""):
    """Records and prints one assertion."""
    _results.append(bool(ok))
    print(("  PASS  " if ok else "  FAIL  ") + label + (" :: " + str(extra) if extra != "" else ""))
    return ok


def msg(body):
    """The API's error message, if any."""
    return body.get("message") if isinstance(body, dict) else body


def login(email, password=PASSWORD):
    status, body = call("POST", "/auth/login", {"email": email, "password": password})
    if status != 200:
        sys.exit("Could not sign in as %s (%s). Is the backend running on %s?" % (email, status, BASE))
    return body["token"]


def summary(noun="checks"):
    """Prints the tally and exits non-zero if anything failed."""
    passed = sum(_results)
    print("\n%d/%d %s passed" % (passed, len(_results), noun))
    sys.exit(0 if passed == len(_results) else 1)
