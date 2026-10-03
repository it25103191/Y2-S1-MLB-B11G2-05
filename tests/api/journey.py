"""
Definition-of-done walkthrough: register -> browse -> book -> pay -> My Bookings -> crew ->
permit -> support case -> complete -> reporting. Also deliberately trips both conflict guards.
"""
import time
from datetime import date, timedelta

from api_client import call, check as step, login, msg, summary

print("CUSTOMER JOURNEY")
email = "journey%d@example.com" % int(time.time())
st, reg = call("POST", "/auth/register", {"fullName": "Journey Tester", "email": email,
                                          "password": "JourneyPass1!", "phone": "+44 7700 900999"})
step("1. Register a new customer", st == 201 and reg["user"]["role"] == "CUSTOMER", email)
CUST = reg["token"]

st, pkgs = call("GET", "/packages")
step("2. Browse packages", st == 200 and len(pkgs) >= 6, "%d packages" % len(pkgs))
st, filtered = call("GET", "/packages?search=wilpattu&maxPrice=400")
step("   Filter by park + price", st == 200 and all(float(p["pricePerPerson"]) <= 400 for p in filtered), "")

pick = [p for p in pkgs if p["name"] == "Wilpattu Villus & Sloth Bears"][0]
trip_date = (date.today() + timedelta(days=60)).isoformat()
st, quote = call("GET", "/packages/%d/quote?date=%s&participants=2" % (pick["id"], trip_date))
step("3. Live availability + price quote", st == 200 and quote["available"], quote.get("message"))

st, booking = call("POST", "/bookings", {"packageId": pick["id"], "tripDate": trip_date, "participants": 2,
                                         "specialRequests": "Window seats please"}, token=CUST)
step("4. Create booking", st == 201 and booking["status"] == "PENDING", booking.get("bookingReference"))
BID = booking["id"]

total = float(booking["totalPrice"])
st, dep = call("POST", "/payments", {"bookingId": BID, "amount": round(total * 0.3, 2), "method": "CREDIT_CARD",
                                     "cardNumber": "4242 4242 4242 4242", "cardHolderName": "Journey Tester",
                                     "cardExpiry": "11/29", "cardCvc": "123"}, token=CUST)
step("5. Pay a 30% deposit", st == 200 and dep["accepted"] and dep["partial"], dep.get("message"))
st, bal = call("POST", "/payments", {"bookingId": BID, "amount": float(dep["remainingBalance"]),
                                     "cardNumber": "4242424242424242"}, token=CUST)
step("   Pay the balance -> auto-confirm", bal["accepted"] and bal["bookingNowConfirmed"], "")

st, mine = call("GET", "/bookings/mine", token=CUST)
row = [b for b in mine if b["id"] == BID]
step("6. Shows in My Bookings, paid in full", len(row) == 1 and row[0]["fullyPaid"], "")
st, inv = call("GET", "/payments/invoice/%d" % BID, token=CUST)
step("   Invoice/receipt available", st == 200 and inv["fullyPaid"], inv.get("invoiceNumber"))

print("STAFF FULFILMENT")
OPS = login("kamal@ceylontrails.lk")
st, awaiting = call("GET", "/bookings/awaiting-assignment", token=OPS)
step("7. Appears in the operations queue", any(b["id"] == BID for b in awaiting), "")
st, sug = call("GET", "/assignments/suggest/%d" % BID, token=OPS)
step("   Auto-suggest offers a free pairing", sug["recommendedGuideId"] and sug["recommendedVehicleId"], "")
st, asg = call("POST", "/assignments", {"bookingId": BID, "guideId": sug["recommendedGuideId"],
                                        "vehicleId": sug["recommendedVehicleId"], "notes": "Journey"}, token=OPS)
step("8. Assign guide + vehicle", st == 201, "%s / %s" % (asg.get("guideName"), asg.get("vehicleRegistration")))

st, permit = call("POST", "/permits", {"bookingId": BID, "notes": "Journey"}, token=OPS)
step("9. Raise a park permit", st == 201 and permit["status"] == "PENDING", permit.get("permitNumber"))
st, approved = call("POST", "/permits/%d/approve" % permit["id"], {}, token=OPS)
step("   Approve the permit", approved["status"] == "APPROVED", "")
st, bperm = call("GET", "/bookings/%d/permits" % BID, token=CUST)
step("   Customer can see their permit status", st == 200 and len(bperm) == 1, "")

st, case = call("POST", "/complaints", {"subject": "Can we add a vegetarian meal?",
                                        "description": "One traveller is vegetarian.", "category": "BOOKING",
                                        "bookingId": BID}, token=CUST)
step("10. Customer raises a case", st == 201, case.get("reference"))
REL = login("sachini@ceylontrails.lk")
st, _ = call("POST", "/complaints/%d/notes" % case["id"], {"message": "Noted, the kitchen has been told.",
                                                          "type": "EMAIL", "direction": "OUTBOUND",
                                                          "notifyCustomer": True}, token=REL)
step("    Relations replies and notifies", st == 201, "")
st, resolved = call("PATCH", "/complaints/%d" % case["id"], {"status": "RESOLVED",
                                                             "resolutionNotes": "Dietary requirement recorded."},
                    token=REL)
step("    Case resolved", resolved["status"] == "RESOLVED", "")

st, done = call("PATCH", "/bookings/%d/status" % BID, {"status": "COMPLETED"}, token=OPS)
step("11. Mark the trip completed", done["status"] == "COMPLETED", "")

frm = (date.today() - timedelta(days=30)).isoformat()
to = (date.today() + timedelta(days=120)).isoformat()
st, rep = call("GET", "/reports/dashboard?from=%s&to=%s" % (frm, to), token=OPS)
step("12. Reflected in reporting", st == 200 and any(p["label"] == pick["name"] for p in rep["popularPackages"]), "")
util = [g for g in rep["guideUtilisation"] if g["label"] == asg["guideName"]]
step("    Guide utilisation picked it up", util and util[0]["daysDeployed"] > 0, "")

print("CONFLICT GUARDS (deliberately trying to break them)")
st, avail = call("GET", "/packages/%d/availability?date=%s" % (pick["id"], trip_date))
seats_left = avail["seatsRemaining"]
st, boom = call("POST", "/bookings", {"packageId": pick["id"], "tripDate": trip_date,
                                      "participants": seats_left + 1}, token=CUST)
step("M1: overbooking by one seat is blocked", st == 409, msg(boom))
st, exact = call("GET", "/packages/%d/availability?date=%s" % (pick["id"], trip_date))
step("    Rejected attempt consumed no seats", exact["seatsRemaining"] == seats_left, "")

overlap = (date.fromisoformat(trip_date) + timedelta(days=1)).isoformat()
st, second = call("POST", "/bookings", {"packageId": pick["id"], "tripDate": overlap, "participants": 1}, token=CUST)
st, clash = call("POST", "/assignments", {"bookingId": second["id"], "guideId": asg["guideId"],
                                          "vehicleId": asg["vehicleId"]}, token=OPS)
step("M2: double-booking guide+vehicle is blocked", st == 409, msg(clash))
step("    Conflict payload names both clashes", len((clash.get("details") or {}).get("clashes", [])) == 2, "")
st, no_reason = call("POST", "/assignments", {"bookingId": second["id"], "guideId": asg["guideId"],
                                              "vehicleId": asg["vehicleId"], "override": True}, token=OPS)
step("    Override without a reason is blocked", st == 400, msg(no_reason))
st, forced = call("POST", "/assignments", {"bookingId": second["id"], "guideId": asg["guideId"],
                                           "vehicleId": asg["vehicleId"], "override": True,
                                           "overrideReason": "Client insisted on the same guide"}, token=OPS)
step("    Override with a reason is allowed and audited", st == 201 and forced["manualOverride"], "")

call("DELETE", "/assignments/%d" % forced["id"], token=OPS)
call("POST", "/bookings/%d/cancel" % second["id"], {"reason": "Journey cleanup"}, token=CUST)

summary("journey steps")
