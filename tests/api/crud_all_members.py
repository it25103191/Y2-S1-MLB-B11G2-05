"""Full CRUD for all six members: success paths, guarded deletes and role limits."""
from datetime import date, timedelta

from api_client import call, check, login, msg, summary

OPS = login("kamal@ceylontrails.lk")
REL = login("sachini@ceylontrails.lk")
FLEET = login("saman@ceylontrails.lk")
RES = login("nuwan@ceylontrails.lk")
ACCTS = login("ishara@ceylontrails.lk")
SARAH = login("sarah@example.com")
JOHN = login("john@example.com")

future = lambda d: (date.today() + timedelta(days=d)).isoformat()
_, pkgs = call("GET", "/packages?activeOnly=false")
gathering = [p for p in pkgs if p["name"] == "The Gathering: Minneriya & Kaudulla"][0]      # max 16
dawn = [p for p in pkgs if p["name"] == "Yala Dawn & Dusk"][0]      # max 8

# =========================================================== MEMBER 1
print("\n--- Member 1: Trip Booking & Reservation ---")

# Package: delete only when never booked
st, temp = call("POST", "/packages", {"name": "CRUD Test Package", "description": "temp", "parkId": gathering["parkId"],
                                      "durationDays": 2, "pricePerPerson": 450, "maxGroupSize": 6}, token=OPS)
check("create package", st == 201, st)
st, _ = call("DELETE", "/packages/%d" % temp["id"], token=OPS)
check("delete a never-booked package", st == 204, st)
st, body = call("DELETE", "/packages/%d" % gathering["id"], token=OPS)
check("delete a booked package is refused", st == 409, msg(body))
st, _ = call("DELETE", "/packages/%d" % dawn["id"], token=SARAH)
check("customer cannot delete packages", st == 403, st)

# Booking: update
st, b = call("POST", "/bookings", {"packageId": gathering["id"], "tripDate": future(70), "participants": 2}, token=SARAH)
check("create booking to edit", st == 201, st)
st, q = call("GET", "/packages/%d/quote?date=%s&participants=3&excludeBookingId=%d" % (gathering["id"], future(70), b["id"]))
check("quote can ignore the booking being edited", q["seatsRemaining"] == gathering["maxGroupSize"], q["seatsRemaining"])

st, upd = call("PUT", "/bookings/%d" % b["id"], {"tripDate": future(72), "participants": 4,
                                                  "specialRequests": "Two child seats"}, token=SARAH)
check("edit pending booking (date + size)", st == 200 and upd["participants"] == 4 and upd["tripDate"] == future(72), st)
check("price recalculated on edit",
      abs(float(upd["totalPrice"]) - 4 * float(gathering["pricePerPerson"])) < 0.01, upd["totalPrice"])
st, body = call("PUT", "/bookings/%d" % b["id"], {"tripDate": future(72), "participants": 17}, token=SARAH)
check("edit beyond package max is refused", st == 400, msg(body))

# fill the date so capacity actually blocks
st, filler = call("POST", "/bookings", {"packageId": gathering["id"], "tripDate": future(74), "participants": 14}, token=JOHN)
st, body = call("PUT", "/bookings/%d" % b["id"], {"tripDate": future(74), "participants": 4}, token=SARAH)
check("edit into a nearly-full date is refused (409)", st == 409, msg(body))
st, body = call("PUT", "/bookings/%d" % b["id"], {"tripDate": future(72), "participants": 4}, token=JOHN)
check("another customer cannot edit it", st == 403, st)

# permit lock: date/size frozen, requests still editable
st, permit = call("POST", "/permits", {"bookingId": b["id"]}, token=OPS)
st, body = call("PUT", "/bookings/%d" % b["id"], {"tripDate": future(73), "participants": 4}, token=SARAH)
check("date locked once a permit covers the booking", st == 409, msg(body))
st, body = call("PUT", "/bookings/%d" % b["id"], {"tripDate": future(72), "participants": 4,
                                                   "specialRequests": "Vegetarian lunch"}, token=SARAH)
check("special requests still editable under a permit", st == 200 and body["specialRequests"] == "Vegetarian lunch", st)

# Booking: delete
st, body = call("DELETE", "/bookings/%d" % b["id"], token=SARAH)
check("delete refused while a permit exists", st == 409, msg(body))
call("DELETE", "/permits/%d" % permit["id"], token=OPS)
st, _ = call("DELETE", "/bookings/%d" % b["id"], token=SARAH)
check("delete pending unpaid booking", st == 204, st)
st, _ = call("GET", "/bookings/%d" % b["id"], token=SARAH)
check("deleted booking is gone", st == 404, st)

st, mine = call("GET", "/bookings/mine", token=SARAH)
confirmed = [x for x in mine if x["status"] == "CONFIRMED"][0]
st, body = call("DELETE", "/bookings/%d" % confirmed["id"], token=SARAH)
check("delete a confirmed booking is refused", st == 409, msg(body))
st, body = call("PUT", "/bookings/%d" % confirmed["id"], {"tripDate": future(90), "participants": 1}, token=SARAH)
check("edit a confirmed booking is refused", st == 400, msg(body))
call("DELETE", "/bookings/%d" % filler["id"], token=JOHN)

# =========================================================== MEMBER 2
print("\n--- Member 2: Vehicle, Guide & Assignment ---")
st, asg = call("GET", "/assignments", token=FLEET)
a0 = asg[0]
st, moved = call("PATCH", "/assignments/%d/status" % a0["id"], {"status": "IN_PROGRESS"}, token=FLEET)
check("change assignment status", st == 200 and moved["status"] == "IN_PROGRESS", st)
st, back = call("PATCH", "/assignments/%d/status" % a0["id"], {"status": "SCHEDULED"}, token=FLEET)
check("revert assignment status", st == 200 and back["status"] == "SCHEDULED", st)

# =========================================================== MEMBER 3
print("\n--- Member 3: Customer Relations & Communication ---")

# customer withdraws an untouched case
st, case = call("POST", "/complaints", {"subject": "Question about binoculars",
                                        "description": "Are binoculars provided?", "category": "GENERAL_INQUIRY"}, token=SARAH)
st, _ = call("DELETE", "/complaints/%d" % case["id"], token=SARAH)
check("customer withdraws own untouched case", st == 204, st)

st, case2 = call("POST", "/complaints", {"subject": "Pickup time", "description": "What time is pickup?",
                                         "category": "BOOKING"}, token=SARAH)
st, tl = call("GET", "/complaints/%d/timeline" % case2["id"], token=REL)
check("opening message is locked", tl[0]["locked"] is True, tl[0]["locked"])

st, staffNote = call("POST", "/complaints/%d/notes" % case2["id"], {"message": "Pickup is at 06:00.",
                                                                    "type": "EMAIL", "notifyCustomer": False}, token=REL)
st, body = call("DELETE", "/complaints/%d" % case2["id"], token=SARAH)
check("customer cannot withdraw after staff replied", st == 400 and "already replied" in msg(body), msg(body))

# note editing
st, edited = call("PUT", "/complaints/%d/notes/%d" % (case2["id"], staffNote["id"]),
                  {"message": "Pickup is at 06:15 from the lodge entrance."}, token=REL)
check("author edits own note", st == 200 and edited["editedAt"] is not None, st)
st, body = call("PUT", "/complaints/%d/notes/%d" % (case2["id"], staffNote["id"]), {"message": "hijack"}, token=OPS)
check("non-author cannot edit a note", st == 403, st)
st, body = call("PUT", "/complaints/%d/notes/%d" % (case2["id"], tl[0]["id"]), {"message": "rewrite"}, token=SARAH)
check("opening message cannot be edited", st == 400, msg(body))

st, _ = call("PATCH", "/complaints/%d" % case2["id"], {"priority": "CRITICAL"}, token=REL)
st, tl2 = call("GET", "/complaints/%d/timeline" % case2["id"], token=REL)
status_entry = [e for e in tl2 if e["type"] == "STATUS_CHANGE"][0]
check("status-change entry is locked", status_entry["locked"] is True, status_entry["type"])
st, body = call("DELETE", "/complaints/%d/notes/%d" % (case2["id"], status_entry["id"]), token=REL)
check("audit entries cannot be deleted", st == 400, msg(body))

st, reply = call("POST", "/complaints/%d/notes" % case2["id"], {"message": "Thanks!"}, token=SARAH)
st, _ = call("DELETE", "/complaints/%d/notes/%d" % (case2["id"], reply["id"]), token=SARAH)
check("customer deletes own reply", st == 204, st)

st, _ = call("DELETE", "/complaints/%d" % case2["id"], token=FLEET)
check("unrelated staff cannot delete cases", st == 403, st)
st, _ = call("DELETE", "/complaints/%d" % case2["id"], token=REL)
check("relations officer deletes a case", st == 204, st)
st, _ = call("GET", "/complaints/%d" % case2["id"], token=REL)
check("deleted case is gone", st == 404, st)

# reply templates
st, tpls = call("GET", "/reply-templates", token=REL)
check("list reply templates", st == 200 and len(tpls) >= 6, len(tpls) if st == 200 else st)
st, tpl = call("POST", "/reply-templates", {"title": "Weather delay", "category": "PARK_EXPERIENCE",
                                            "subject": "Weather on {{bookingReference}}",
                                            "body": "Hi {{customerName}}, heavy rain delayed your drive.",
                                            "active": True}, token=REL)
check("create template", st == 201 and tpl["placeholders"] == ["bookingReference", "customerName"], tpl.get("placeholders"))
st, body = call("POST", "/reply-templates", {"title": "weather DELAY", "body": "x"}, token=REL)
check("duplicate template title refused", st == 409, msg(body))
st, body = call("POST", "/reply-templates", {"title": "Bad", "body": "Hi {{firstName}}"}, token=REL)
check("unknown placeholder refused", st == 400, msg(body))
st, upd = call("PUT", "/reply-templates/%d" % tpl["id"], {"title": "Weather delay", "body": "Updated {{caseReference}}",
                                                         "active": False}, token=REL)
check("update template", st == 200 and upd["active"] is False, st)

st, case3 = call("POST", "/complaints", {"subject": "Rain", "description": "It rained.", "category": "PARK_EXPERIENCE"},
                 token=SARAH)
st, _ = call("POST", "/complaints/%d/notes" % case3["id"], {"message": "Sorry about the rain.", "templateId": tpl["id"]},
             token=REL)
st, after = call("GET", "/reply-templates/%d" % tpl["id"], token=REL)
check("posting from a template counts its usage", after["usageCount"] == 1, after["usageCount"])
st, _ = call("GET", "/reply-templates", token=SARAH)
check("customer cannot see templates", st == 403, st)
st, _ = call("POST", "/reply-templates", {"title": "x", "body": "y"}, token=FLEET)
check("unrelated staff cannot create templates", st == 403, st)
st, _ = call("DELETE", "/reply-templates/%d" % tpl["id"], token=REL)
check("delete template", st == 204, st)
call("DELETE", "/complaints/%d" % case3["id"], token=REL)

# =========================================================== MEMBER 4
print("\n--- Member 4: Park & Permit ---")
st, b4 = call("POST", "/bookings", {"packageId": dawn["id"], "tripDate": future(80), "participants": 3}, token=SARAH)
st, p4 = call("POST", "/permits", {"bookingId": b4["id"]}, token=OPS)
st, pe = call("PUT", "/permits/%d" % p4["id"], {"expiryDate": future(90), "coveredParticipants": 2,
                                               "notes": "Awaiting third name"}, token=OPS)
check("edit pending permit", st == 200 and pe["coveredParticipants"] == 2, st)
_, park_list = call("GET", "/parks")
entry_fee = float([pk for pk in park_list if pk["id"] == dawn["parkId"]][0]["entryFeePerPerson"])
check("fee follows travellers covered", float(pe["feeAmount"]) == 2 * entry_fee, pe["feeAmount"])
check("short coverage flags the trip at risk", pe["atRisk"] and "covers 2 of 3" in pe["riskReason"], pe["riskReason"])
st, body = call("PUT", "/permits/%d" % p4["id"], {"expiryDate": future(90), "coveredParticipants": 5}, token=OPS)
check("cannot cover more travellers than booked", st == 400, msg(body))
st, body = call("PUT", "/permits/%d" % p4["id"], {"expiryDate": future(10), "coveredParticipants": 3}, token=OPS)
check("expiry before the trip is refused", st == 400, msg(body))

st, _ = call("POST", "/permits/%d/approve" % p4["id"], {}, token=OPS)
st, body = call("PUT", "/permits/%d" % p4["id"], {"expiryDate": future(95), "coveredParticipants": 3}, token=OPS)
check("approved permit can't be edited (renew instead)", st == 400, msg(body))
st, body = call("DELETE", "/permits/%d" % p4["id"], token=OPS)
check("valid approved permit can't be deleted", st == 409, msg(body))
st, _ = call("POST", "/permits/%d/reject" % p4["id"], {"notes": "No longer needed"}, token=OPS)
st, _ = call("DELETE", "/permits/%d" % p4["id"], token=OPS)
check("rejected permit can be deleted", st == 204, st)
st, _ = call("DELETE", "/permits/%d" % p4["id"], token=ACCTS)
check("accounts officer cannot delete permits", st in (403, 404), st)
call("DELETE", "/bookings/%d" % b4["id"], token=SARAH)

# =========================================================== MEMBER 5
print("\n--- Member 5: Payment, Billing & Refund ---")
st, b5 = call("POST", "/bookings", {"packageId": gathering["id"], "tripDate": future(60), "participants": 2}, token=SARAH)
total = float(b5["totalPrice"])

st, body = call("POST", "/payments", {"bookingId": b5["id"], "amount": 100, "method": "CASH"}, token=SARAH)
check("customer cannot pay cash online (gateway fix)", st == 400, msg(body))
st, body = call("POST", "/payments", {"bookingId": b5["id"], "amount": 100, "method": "CREDIT_CARD"}, token=SARAH)
check("card payment needs a card number", st == 400, msg(body))

st, off = call("POST", "/payments/offline", {"bookingId": b5["id"], "amount": round(total / 2, 2),
                                             "method": "BANK_TRANSFER", "reference": "BANK-7781",
                                             "notes": "Received at Arusha branch"}, token=ACCTS)
check("record offline payment", st == 201 and off["accepted"] and off["payment"]["status"] == "SUCCESS", st)
check("offline payment keeps reference + notes",
      off["payment"]["gatewayReference"] == "BANK-7781" and off["payment"]["notes"] == "Received at Arusha branch", "")
st, body = call("POST", "/payments/offline", {"bookingId": b5["id"], "amount": 10, "method": "CREDIT_CARD"}, token=ACCTS)
check("card method refused on the offline form", st == 400, msg(body))
st, _ = call("POST", "/payments/offline", {"bookingId": b5["id"], "amount": 10, "method": "CASH"}, token=SARAH)
check("customer cannot record offline payments", st == 403, st)

st, rest = call("POST", "/payments", {"bookingId": b5["id"], "amount": float(off["remainingBalance"]),
                                      "cardNumber": "4242424242424242"}, token=SARAH)
check("pay the rest by card -> confirmed", rest["bookingNowConfirmed"] is True, rest["booking"]["status"])

st, voided = call("POST", "/payments/%d/void" % rest["payment"]["id"], {"reason": "Duplicate card charge"}, token=ACCTS)
check("void a successful payment", st == 200 and voided["status"] == "VOIDED" and voided["voidReason"], st)
st, bk = call("GET", "/bookings/%d" % b5["id"], token=ACCTS)
check("void reopens the balance and un-confirms the booking",
      bk["status"] == "PENDING" and abs(float(bk["balanceDue"]) - float(rest["payment"]["amount"])) < 0.01,
      "%s balance %s" % (bk["status"], bk["balanceDue"]))
st, body = call("POST", "/payments/%d/void" % rest["payment"]["id"], {"reason": "again"}, token=ACCTS)
check("cannot void twice", st == 400, msg(body))
st, body = call("POST", "/payments/%d/void" % off["payment"]["id"], {"reason": ""}, token=ACCTS)
check("void needs a reason", st == 400, msg(body))

st, dec = call("POST", "/payments", {"bookingId": b5["id"], "amount": 10, "cardNumber": "4000000000000002"}, token=SARAH)
st, _ = call("DELETE", "/payments/%d" % dec["payment"]["id"], token=ACCTS)
check("delete a declined attempt", st == 204, st)
st, body = call("DELETE", "/payments/%d" % off["payment"]["id"], token=ACCTS)
check("successful payments cannot be deleted", st == 400, msg(body))

# refunds
st, body = call("POST", "/refunds", {"bookingId": b5["id"], "reason": "test"}, token=ACCTS)
check("refund refused for a booking that isn't cancelled", st == 400, msg(body))
st, _ = call("POST", "/bookings/%d/cancel" % b5["id"], {"reason": "Change of plans"}, token=SARAH)
st, refunds = call("GET", "/refunds/booking/%d" % b5["id"], token=ACCTS)
rf = refunds[0]
check("cancellation auto-raises a refund", rf["status"] == "REQUESTED", rf["status"])
st, body = call("POST", "/refunds", {"bookingId": b5["id"]}, token=ACCTS)
check("second refund on the same booking refused", st == 409, msg(body))

st, body = call("POST", "/payments/%d/void" % off["payment"]["id"], {"reason": "test"}, token=ACCTS)
check("void blocked while a refund is in progress", st == 409, msg(body))

half = round(float(rf["calculatedAmount"]) / 2, 2)
st, adj = call("PUT", "/refunds/%d" % rf["id"], {"amount": half, "decisionNotes": "Goodwill split agreed"}, token=ACCTS)
check("adjust a requested refund", st == 200 and float(adj["adjustedAmount"]) == half, st)
st, body = call("PUT", "/refunds/%d" % rf["id"], {"amount": 999999}, token=ACCTS)
check("adjustment cannot exceed what was paid", st == 400, msg(body))
st, _ = call("DELETE", "/refunds/%d" % rf["id"], token=ACCTS)
check("withdraw a requested refund", st == 204, st)

st, again = call("POST", "/refunds", {"bookingId": b5["id"], "reason": "Re-raised after review"}, token=ACCTS)
check("finance can re-raise a refund for a cancelled booking", st == 201, st)
st, adj2 = call("PUT", "/refunds/%d" % again["id"], {"amount": half}, token=ACCTS)
st, appr = call("POST", "/refunds/%d/approve" % again["id"], {}, token=ACCTS)
check("approval defaults to the adjusted amount", float(appr["approvedAmount"]) == half, appr["approvedAmount"])
st, body = call("DELETE", "/refunds/%d" % again["id"], token=ACCTS)
check("approved refund cannot be withdrawn", st == 400, msg(body))
st, _ = call("PUT", "/refunds/%d" % again["id"], {"amount": 1}, token=OPS)
check("operations cannot edit refunds", st == 403, st)

# =========================================================== MEMBER 6
print("\n--- Member 6: Reporting & Analytics (KPI targets) ---")
st, targets = call("GET", "/kpi-targets", token=OPS)
statuses = sorted({t["status"] for t in targets})
check("seeded targets listed with live actuals", st == 200 and len(targets) >= 12, len(targets) if st == 200 else st)
check("seeded targets span every status", set(statuses) == {"ACHIEVED", "MISSED", "IN_PROGRESS", "UPCOMING"}, statuses)

nm = (date.today().replace(day=1) + timedelta(days=100)).strftime("%Y-%m")
st, t = call("POST", "/kpi-targets", {"metric": "REVENUE", "month": nm, "targetValue": 25000,
                                      "notes": "Test target"}, token=OPS)
check("create KPI target", st == 201 and t["status"] == "UPCOMING", st)
st, body = call("POST", "/kpi-targets", {"metric": "REVENUE", "month": nm, "targetValue": 1}, token=OPS)
check("duplicate metric+month refused", st == 409, msg(body))
st, body = call("POST", "/kpi-targets", {"metric": "CANCELLATION_RATE", "month": nm, "targetValue": 150}, token=OPS)
check("cancellation rate over 100% refused", st == 400, msg(body))
st, body = call("POST", "/kpi-targets", {"metric": "BOOKINGS", "month": nm, "targetValue": 2.5}, token=OPS)
check("fractional bookings target refused", st == 400, msg(body))
st, body = call("POST", "/kpi-targets", {"metric": "TRAVELLERS", "month": nm, "targetValue": 0}, token=OPS)
check("zero target refused for count metrics", st == 400, msg(body))

st, t2 = call("PUT", "/kpi-targets/%d" % t["id"], {"metric": "REVENUE", "month": nm, "targetValue": 30000,
                                                  "notes": "Raised"}, token=ACCTS)
check("finance can update a target", st == 200 and float(t2["targetValue"]) == 30000, st)
check("update records who changed it", t2["updatedByName"] == "Ishara Dias", t2["updatedByName"])

st, _ = call("POST", "/kpi-targets", {"metric": "BOOKINGS", "month": nm, "targetValue": 5}, token=FLEET)
check("vehicle coordinator cannot set targets", st == 403, st)
st, _ = call("GET", "/kpi-targets", token=FLEET)
check("vehicle coordinator can read targets", st == 200, st)
st, _ = call("GET", "/kpi-targets", token=SARAH)
check("customers cannot see targets", st == 403, st)

frm = date.today().replace(day=1) - timedelta(days=100)
to = date.today() + timedelta(days=150)
st, dash = call("GET", "/reports/dashboard?from=%s&to=%s" % (frm.isoformat(), to.isoformat()), token=OPS)
rev = [p for p in dash["targetProgress"] if p["metric"] == "REVENUE"]
check("dashboard carries target progress", st == 200 and len(rev) == 1, [p["metric"] for p in dash["targetProgress"]])
check("progress counts only months with targets", rev[0]["monthsWithTarget"] < rev[0]["monthsInRange"],
      "%s of %s months" % (rev[0]["monthsWithTarget"], rev[0]["monthsInRange"]))
check("dashboard carries monthly target lines", any(m["key"] == nm for m in dash["monthlyTargets"]), "")
cr = [p for p in dash["targetProgress"] if p["metric"] == "CANCELLATION_RATE"]
check("cancellation rate reports lower-is-better", cr and cr[0]["higherIsBetter"] is False, "")

st, _ = call("DELETE", "/kpi-targets/%d" % t["id"], token=RES)
check("delete KPI target", st == 204, st)
st, _ = call("GET", "/kpi-targets/%d" % t["id"], token=OPS)
check("deleted target is gone", st == 404, st)

summary()
