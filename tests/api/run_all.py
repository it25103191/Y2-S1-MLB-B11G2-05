"""
Runs every API suite in order and prints a combined result.

The suites assume a freshly seeded database and change its data, so run them against a
throwaway database (see api_client.py). Exit code is non-zero if any check fails.

    python tests/api/run_all.py
"""
import os
import re
import subprocess
import sys

SUITES = [
    ("Module 1  Trip booking", "m1_booking.py"),
    ("Module 2  Vehicles, guides, assignment", "m2_assignment.py"),
    ("Module 3  Customer relations", "m3_relations.py"),
    ("Module 4  Parks & permits", "m4_permits.py"),
    ("Module 5  Payments & refunds", "m5_finance.py"),
    ("Journey   End-to-end walkthrough", "journey.py"),
    ("CRUD      All six members", "crud_all_members.py"),
    ("Currency  LKR / USD", "currency.py"),
    ("Accounts  Staff accounts", "staff_accounts.py"),
    ("Accounts  My account", "my_account.py"),
    ("Timeline  Booking history", "booking_timeline.py"),
]

HERE = os.path.dirname(os.path.abspath(__file__))
TALLY = re.compile(r"(\d+)/(\d+) .*passed")

total_passed = total_checks = 0
failed_suites = []

for title, script in SUITES:
    proc = subprocess.run([sys.executable, os.path.join(HERE, script)], cwd=HERE,
                          capture_output=True, text=True, encoding="utf-8", errors="replace")
    output = proc.stdout + proc.stderr
    match = None
    for line in output.splitlines():
        found = TALLY.search(line)
        if found:
            match = found
    if match:
        passed, checks = int(match.group(1)), int(match.group(2))
    else:
        passed, checks = 0, 1  # crashed before reporting

    total_passed += passed
    total_checks += checks
    ok = proc.returncode == 0 and passed == checks
    print("%-4s %-42s %3d/%-3d" % ("OK" if ok else "FAIL", title, passed, checks))
    if not ok:
        failed_suites.append(script)
        for line in output.splitlines():
            if "FAIL" in line or "Error" in line or "Traceback" in line:
                print("       " + line.strip())

print("\n%d/%d checks passed across %d suites" % (total_passed, total_checks, len(SUITES)))
sys.exit(1 if failed_suites else 0)
