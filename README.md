# Ceylon Trails — Wild-Life Safari Trip Management System

A full-stack wildlife safari operator platform for Sri Lanka: a Spring Boot REST API backed by
Microsoft SQL Server, and a React single-page front end. It covers the whole operating cycle —
a public website and catalogue, bookings, crew and fleet assignment, customer relations, park
permits, payments and refunds in rupees or dollars, and an analytics dashboard.

Anyone can browse the site; an account is only needed at the moment a traveller reserves seats.

---

## Contents

- [Quick start](#quick-start)
- [Demo accounts](#demo-accounts)
- [Architecture](#architecture)
- [The public site](#the-public-site)
- [Rupees and dollars](#rupees-and-dollars)
- [Modules](#modules)
- [CRUD by team member](#crud-by-team-member)
- [Business rules worth knowing](#business-rules-worth-knowing)
- [API reference](#api-reference)
- [Configuration](#configuration)
- [Environment notes](#environment-notes-read-if-something-will-not-start)
- [Verification](#verification)
- [Assumptions and decisions](#assumptions-and-decisions)
- [Known limitations](#known-limitations)

---

## Quick start

**Prerequisites**

| Requirement | Version | Notes |
|---|---|---|
| JDK | 17 | Spring Boot 3.3 does not support JDK 24; see [Environment notes](#environment-notes-read-if-something-will-not-start) |
| Maven | 3.9+ | |
| Node.js | 18+ | Built and tested on Node 24 |
| SQL Server | 2019+ | Express edition is fine |

**1. Create the database** (one-off)

```bash
sqlcmd -S "localhost,1433" -E -C -Q "IF DB_ID('ceylon_trails') IS NULL CREATE DATABASE ceylon_trails;"
```

**2. Start the API** — `http://localhost:8080`

```bash
run-backend.cmd
```

If your SQL Server uses a login such as `sa` instead of Windows authentication, run
`run-backend-sql.cmd` instead: it asks for the password, creates the database if it is missing and
starts the API. Nothing is stored, so no password ends up in the repository.

**3. Start the UI** — `http://localhost:5173`

```bash
run-frontend.cmd
```

Open <http://localhost:5173> and sign in with any account below. Hibernate creates the schema on
first run and the seeder fills it with a realistic demo dataset, so every screen has data
immediately.

> To start over from scratch, drop the database and restart the API — it will recreate and reseed.

**Upgrading an existing database.** Starting a newer version against a database created by an
older one is safe. Existing rows are left alone. Hibernate adds any new tables and columns, and
`SchemaPatchRunner` widens enum CHECK constraints that Hibernate cannot alter itself (for example,
it adds `VOIDED` to `payments.status`). Modules added later, such as reply templates and KPI
targets, seed their own demo rows the first time their table is empty.

---

## Demo accounts

Every seeded account uses the password **`Password123!`**. The login screen does not list them
by default, because showing credentials on a sign-in page is a security risk. For a rehearsal, put
`VITE_SHOW_DEMO_ACCOUNTS=true` in `frontend/.env.local` and restart the front end to get one-click
chips for each role.

| Role | Name | Email | What they see |
|---|---|---|---|
| Operations Manager | Kamal Perera | `kamal@ceylontrails.lk` | Everything: analytics, bookings, catalogue, crew, permits, complaints |
| Customer Relations Officer | Sachini Silva | `sachini@ceylontrails.lk` | Analytics, bookings, complaints, customer profiles, notifications |
| Safari Vehicle Coordinator | Saman Kumara | `saman@ceylontrails.lk` | Analytics, bookings, assignments, schedule, vehicles, guides, permits |
| Finance & Reservations Executive | Nuwan Jayasinghe | `nuwan@ceylontrails.lk` | Analytics, bookings, catalogue, parks, permits, customers, payments, refunds |
| Finance Accounts Officer | Ishara Dias | `ishara@ceylontrails.lk` | Analytics, bookings, customers, notifications, payments, refunds |
| Customer | Sarah Smith | `sarah@example.com` | Browse, book, pay, My trips, Support (prices in USD) |
| Customer | John Brown | `john@example.com` | as above |
| Customer | Amal Bandara | `amal@example.com` | as above; lives in Sri Lanka, so sees prices in LKR |

New sign-ups through the registration screen are always created as `CUSTOMER`. These accounts are
created only when the database is empty; a database seeded by an earlier version keeps the accounts
it was created with.

---

## Architecture

```
T:\Safari Trip Management System\
├── backend/                       Spring Boot 3.3 · Java 17 · Maven
│   ├── lib/                       mssql-jdbc_auth DLL (Windows integrated auth)
│   ├── .mvn/jvm.config            TLS trust-store fix (see Environment notes)
│   └── src/main/java/com/safari/tms/
│       ├── config/                Security, CORS, properties, data seeder, schema patch
│       ├── domain/                17 JPA entities + 18 enums
│       ├── dto/                   Request/response records, grouped per module
│       ├── exception/             ApiException + global handler
│       ├── repo/                  17 Spring Data repositories
│       ├── security/              JWT issue/verify, filter, role constants
│       ├── service/               Business logic
│       └── controller/            18 REST controllers
├── frontend/                      React 18 · Vite · React Router · Axios · Recharts
│   └── src/
│       ├── api/                   Axios client + typed endpoint wrappers
│       ├── components/            Design system, DataTable, Timeline, charts helpers
│       ├── config/nav.js          Role-scoped sidebar definition
│       ├── context/               Auth, currency and toast providers
│       ├── hooks/useApi.js        Loading / error / reload with race protection
│       ├── layouts/               Public site shell and staff console shell
│       ├── pages/                 Public, customer, staff and shared screens
│       ├── routes/                Route guards
│       └── styles/                Design tokens, shells, page and site styles
├── design-samples/                The design mockups; wild-lanka.html is the chosen direction
├── tests/api/                     Python API test suites (run against a throwaway database)
├── run-backend.cmd
├── run-frontend.cmd
└── README.md
```

**Request flow:** React → Axios (attaches `Authorization: Bearer …`) → `JwtAuthenticationFilter`
→ `@PreAuthorize` on the controller → service (business rules, `@Transactional`) → Spring Data
JPA → SQL Server.

**Identity** always comes from the JWT subject via `AuthService.requireCurrentUser()`. No endpoint
trusts a user id supplied in a request body.

---

## The public site

The front end is a public website first and a booking system second, in the style of
[safari.com](https://www.safari.com): Newsreader for display type with one italic word per
headline, Hanken Grotesk for text, micro capitals for labels, square buttons, and a palette named
after island materials (bone, ivory, sand, canopy, jungle, cinnamon). Motion is slow and
deliberate — photo crossfades with a slow zoom, headlines that rise out of a mask, content that
fades up as it scrolls into view — and switches off for visitors who ask for reduced motion.

| Route | Who | What it is |
|---|---|---|
| `/` | everyone | Home: hero "film", island map of the national parks, trip styles, a two-monsoon season calendar, featured itineraries |
| `/safaris` | everyone | Every departure, filterable by park, length, budget (in the visitor's currency) and trip style |
| `/safaris/:id` | everyone | The itinerary with a booking panel: live seat count, price, and **Reserve** |
| `/my-trips`, `/my-trips/:id`, `/my-trips/:id/pay` | customers | Trips, a step-by-step trip timeline, and payment |
| `/my-support`, `/invoices/:id` | customers | Support cases and invoices |
| `/login`, `/register` | everyone | Sign in and create an account |
| `/staff/*` | staff | The operations console |

**Sign in at the moment of booking.** A guest can set a date and travellers and press
*Reserve*; a dialog offers "New here" or "I have an account" without leaving the page, and the
reservation completes as soon as they are signed in. Links from the previous version
(`/browse`, `/packages/:id`, `/my-bookings/...`) redirect to their new addresses.

The mockups the direction was chosen from live in `design-samples/` (`wild-lanka.html` is the
chosen one). Editorial content — photography, the map, the season calendar — is in
`frontend/src/config/lanka.js`; the brand name lives once in `frontend/src/brand.js`.

---

## Rupees and dollars

Every visitor can see prices in **LKR** or **USD** from the switch in the header.

- **Who sees what.** Guests start in rupees if their device is on Sri Lankan time, otherwise
  dollars, and the choice is remembered on the device. Registration asks *"Where do you live?"*
  — Sri Lanka (LKR) or elsewhere (USD) — and the choice is saved on the account (`PATCH
  /api/auth/me`), so it follows the customer to any device.
- **One ledger.** Bookings, payments and refunds are stored in USD. LKR is converted at a single
  exchange rate that the finance team maintains from the Payments screen (`PUT
  /api/currency/LKR`). The rate is public (`GET /api/currency`) so prices show before sign-in,
  and a rate outside 50–1,000 is rejected as a typo.
- **Paying in rupees.** A payment records the USD ledger amount, the currency it was charged in,
  the charged amount and the rate used. The page sends the rupee figure it showed the customer;
  if finance changed the rate in the meantime, the payment is refused with a request to refresh
  rather than charging a different figure. Finance can also record LKR cash or transfers at the
  desk, and receipts, messages and the payments ledger show the rupee amount.

---

## Modules

### Authentication & roles
Email/password login with BCrypt hashing and an HS256 JWT (8-hour expiry by default). The SPA
stores the token, rehydrates the session on refresh via `/api/auth/me`, and clears it on 401 or
logout. Six roles drive both server-side `@PreAuthorize` rules and the front-end route guards and
sidebar. A customer landing on a staff URL is redirected to their own home rather than shown an
error.

**Staff accounts** (Operations Manager only, *Team → Staff Accounts*). The manager adds employees
with a role and a temporary password (handed over in person, never e-mailed), edits their name,
phone and role, and deactivates or reactivates accounts. A deactivated account cannot sign in and
any open session stops working on its next request, but the person's name stays on everything
they handled. Deactivation is refused while they still own open customer cases. Removing an
account outright works only if it was never used: the delete relies on the database's foreign
keys, so any booking, payment, permit or case note that refers to the person blocks it and the API
suggests deactivating instead. Managers cannot change their own role or deactivate or remove
themselves, so the team always keeps an Operations Manager.

**My account** (everyone, from the account menu or the name card in the staff sidebar). Edit your
name and phone (customers also pick their currency) and change your password, which needs the
current one. Customers can also **close their account**, confirmed with their password. It is
refused while they have upcoming trips or a refund on its way. Closing replaces the name, email and
phone, disables sign-in and deletes the e-mails sent to them, while bookings, payments and cases
stay for the accounts, labelled "Closed account".

### Module 1 — Trip booking & reservations
Full CRUD on safari packages (staff only); each package belongs to a park from Module 4. Booking creation performs an **atomic capacity
check**: the package row is locked with `PESSIMISTIC_WRITE` for the transaction, so two concurrent
bookings for the same departure cannot jointly exceed `maxGroupSize`. Rejections return HTTP 409
with a structured payload (`seatsTaken`, `seatsRemaining`, `requested`). Cancelling releases the
seats immediately.

A pending booking can be **edited** (trip date, travellers, special requests). The edit re-runs
the same locked capacity check, leaving out the booking's own seats, and re-prices the booking. It
can also be **deleted** while nothing is attached to it. Packages can be deleted only if no
booking has ever used them; otherwise the API asks you to deactivate the package instead.

Every booking has a **timeline** (customer booking page and the staff booking popup): booked,
changed, confirmed by payment, reopened by a voided payment, completed or cancelled, each with who
did it and when. It is written by `BookingHistoryRecorder`, a third observer on the booking events,
so neither `BookingService` nor the other observers changed. Customers see "You" and "Ceylon Trails
team"; staff see names and roles. Bookings made before the timeline existed show a "Booked" entry
worked out from the booking itself. Deleting an unpaid booking deletes its timeline through
`ON DELETE CASCADE`.

*Screens:* package browsing with search/park/price/duration filters, package detail with live
availability meter and a three-step booking flow, My Bookings with status tabs, edit (with a live
re-quote), delete, and cancellation with the refund you would receive, plus staff package, park
and booking management.

### Module 2 — Vehicle, guide & resource assignment
Full CRUD on vehicles and guides. Assignments span the whole trip (`assignmentDate` → `endDate`,
persisted so overlap detection runs in SQL). Creating one is refused if the guide or vehicle is
already committed to an overlapping trip, is off-duty/off-road, or the vehicle is too small — the
409 response lists every clash. A **manual override** commits anyway but requires a reason, which
is stored on the assignment and badged in the UI. Auto-suggest ranks all guides and vehicles by
availability and workload and recommends the least-loaded free guide plus the smallest vehicle
that fits.

*Screens:* operations queue of confirmed bookings awaiting crew, assignment dialog with
availability lists and inline conflict panel, a status dropdown on each assignment (Scheduled →
In Progress → Completed / Cancelled), vehicle and guide management, and a Gantt-style schedule
across guides or vehicles.

### Module 3 — Customer relationship & communication
Complaints have a full lifecycle (Open → In Progress → Resolved/Unresolved), an owner, a priority,
optional booking link, and an escalation path that moves the case to another department and raises
its priority. Every message, status change and escalation is written to a communication log
rendered as a timeline. Email/SMS are simulated by persisting a `notification_logs` record.

The author of a message can edit or delete it. System entries (status changes, escalations) and
the customer's opening message are locked, so the audit trail cannot be rewritten. A customer can
withdraw their own case while it is still open and no staff member has replied. Relations staff
can delete spam or duplicate cases.

**Reply templates** are canned responses that relations staff manage themselves. Each can be
tied to a complaint category and use the placeholders `{{customerName}}`, `{{caseReference}}` and
`{{bookingReference}}`. These are filled in from the case when the template is inserted. The API
rejects unknown placeholders, counts how often each template is used, and lets you hide a template
without deleting it.

*Screens:* relations dashboard with status/priority filters, case detail with timeline, template
picker and management controls, the reply templates page (with a live preview), the customer 360
profile (bookings + cases + communication + notifications), the notification log, and a
customer-facing support page where customers raise, reply to and withdraw cases.

### Module 4 — Park & permit management
Permits link a booking to a park, carry a fee derived from the park's per-person entry fee, and
have Pending / Approved / Expired / Rejected states plus renewals. The service computes risk
server-side so every screen agrees:

- **Expiring soon** — approved and lapsing within the configurable warning window (default 30 days)
- **At risk** — expired, rejected, still pending close to departure, *expiring before the trip
  ends*, or *covering fewer travellers than the booking has*

A pending permit can be edited (expiry date, travellers covered, notes), and its fee is
recalculated. Pending, rejected and expired permits can be deleted. An approved, still-valid
permit cannot, because it is a live authority to enter the park.

*Screens:* park directory CRUD, permits dashboard with at-risk and "needs a permit" queues, and a
permit panel embedded in staff booking detail, customer booking detail and assignment detail.

### Module 5 — Payment, billing & refunds
A simulated gateway with realistic success / decline / timeout paths. Outcomes are weighted by
configuration, and specific card suffixes are deterministic so every path can be demonstrated:

| Card ends with | Outcome |
|---|---|
| `4242` | Always succeeds |
| `0002` | Always declines |
| `0003` | Always times out |
| anything else | Weighted random (default 80 / 15 / 5) |

Online payments are **card only**. Cash and bank transfers happen outside the website, so finance
staff **record them as offline payments** with a reference and notes. Partial payments are
supported — any amount up to the outstanding balance is accepted, and the booking only flips to
`CONFIRMED` when the balance reaches zero. Declines and timeouts are recorded but move no money.

Payments are never edited in place. A mistaken payment is **voided** with a mandatory reason: the
money comes off the booking, a confirmed booking drops back to pending, and the voided row stays
for audit. Only failed attempts (declined or timed out) can be deleted outright.

Refunds can also be **raised by finance** for a cancelled booking. While a refund is still
requested, its amount and reason can be adjusted (the policy figure is kept alongside), or it can
be withdrawn.

*Screens:* payment page with a card-style mock form and quick deposit buttons, finance dashboard
with status filter, overdue highlighting, offline recording, void and delete actions, printable
invoice/receipt, and a refund processing screen showing the policy calculation, adjustments and
withdrawal.

### Module 6 — Reporting & analytics
Read-only aggregation endpoint returning chart-ready shapes (`{label, value, secondary}`), never
raw entities. It powers the staff landing page: four KPI cards, a bookings-over-time bar chart
(departures vs cancellations), a popular-packages donut, a revenue-trend line chart, a
bookings-by-park bar chart, and guide/vehicle utilisation meters. The date-range filter re-queries
and re-renders without a page reload.

**KPI targets** give the dashboard something to measure against. Management and finance set a
monthly target for any of five metrics: bookings, revenue, travellers, cancellation rate, and
average booking value. Each target is scored against the same numbers the dashboard shows and
labelled *Achieved*, *Missed*, *In progress* (current month) or *Upcoming*. Cancellation rate is
the one metric where lower is better. The dashboard adds a "Performance against target" card,
target progress on the KPI cards, and dashed target lines on the bookings and revenue charts.

*Screens:* staff dashboard and the KPI Targets page (status cards, filters, create / edit /
delete). All staff can view targets; only the Ops Manager and the two finance roles can change
them.

---

## CRUD by team member

Each of the six team members owns one module, and every module offers full create / read /
update / delete. Where a hard delete would destroy history, "delete" is **guarded**: the API
refuses with an error that names what is blocking it, and points to the non-destructive alternative
(deactivate, cancel, void, hide).

| Member | Entity | Create | Read | Update | Delete |
|---|---|---|---|---|---|
| **1 — Trip booking & reservation** | Package | ✓ | ✓ | ✓ · activate/deactivate | only if never booked |
| | Booking | ✓ (customer) | ✓ | edit while pending · status · cancel | pending, with nothing paid or attached |
| **2 — Vehicle, guide & assignment** | Vehicle · Guide | ✓ | ✓ | ✓ · status | only with no upcoming trips |
| | Assignment | ✓ (conflict-checked, override) | ✓ · schedule | ✓ · status dropdown | ✓ |
| **3 — Customer relations & communication** | Complaint | ✓ (customer) | ✓ · timeline | status · priority · owner · escalate | customer withdraws while unanswered; staff delete spam |
| | Note / message | ✓ | ✓ | author only, not system entries | author only, not system entries |
| | Reply template | ✓ | ✓ | ✓ · hide/show | ✓ |
| **4 — Park & permit** | Park | ✓ | ✓ | ✓ | only if no package uses it |
| | Permit | ✓ | ✓ · dashboard | edit while pending · approve · reject · renew | pending, rejected or expired |
| **5 — Payment, billing & refund** | Payment | card online · offline by finance | ✓ · invoice | void with reason | failed attempts only |
| | Refund | auto on cancel · raised by finance | ✓ · quote | adjust while requested · approve · reject · process | withdraw while requested |
| **6 — Reporting & analytics** | KPI target | ✓ | ✓ · on dashboard | ✓ | ✓ |
| **Shared — accounts** | Staff account | ✓ (Ops Manager) | ✓ | name · phone · role · deactivate/reactivate | only if never used |
| | Own account | sign-up | ✓ | name · phone · currency · password | customer closes it (anonymised) |

---

## Business rules worth knowing

**Cancellation-window refund policy** (`RefundService`)

| When the booking is cancelled | Refund |
|---|---|
| More than 7 days before departure | 100% of everything paid |
| 2–7 days before departure | 50% |
| Inside 48 hours, or after departure | 0% |

Cancelling a booking that has money against it raises a refund request automatically. Finance
reviews it, may override the amount (never above what was actually paid), approves, then processes
the payout — which reduces the amount held against the booking and marks the underlying payments
`REFUNDED`.

**Payment due date** — 7 days before departure, or 2 days from booking if that is sooner (never
after the trip date). Bookings past their due date with a balance are flagged overdue.

**Booking status** — `PENDING` on creation, `CONFIRMED` automatically on full payment,
`COMPLETED` when staff mark the trip done, `CANCELLED` by either side. A cancelled booking cannot
be reopened, and confirming a booking with nothing paid is refused.

**Editing a booking** — only while `PENDING`. After a crew assignment or a pending/approved
permit exists, the date and group size are frozen, because both were issued for that departure.
Special requests can still change. The new total may not fall below what has already been paid.

**Guarded deletes** — a refused delete returns an error (usually 409) that names what is in the way:

| Record | Can be deleted when | Otherwise |
|---|---|---|
| Park | no package uses it | deactivate its packages first |
| Package | no booking has ever used it | deactivate it |
| Booking | pending, nothing paid, and no payments, assignment, permits, complaints or refunds | cancel it |
| Vehicle / guide | no upcoming assignments | reassign those trips, or set it off-road / off-duty |
| Complaint | *customer:* still open and no staff reply · *staff:* any time (spam, duplicates) | resolve it |
| Message on a case | you wrote it, and it is not a system entry or the opening message | — |
| Permit | pending, rejected or expired | an approved, valid permit stays |
| Payment | declined or timed out (no money moved) | void it with a reason |
| Refund | still requested (withdraw) | reject it |

**Voiding a payment** is refused while a refund on that booking is requested or approved, or if
it would take the amount paid below zero.

**KPI targets** — one target per metric per month. Cancellation rate is a percentage (0–100).
Bookings and travellers must be whole numbers. Months more than five years from today are
rejected. Over a multi-month date range, the dashboard only compares the months that have a
target. Counts and revenue are summed across those months, and the rate and average value are
averaged.

---

## API reference

All routes are under `/api`. Everything requires a bearer token except `/auth/login`,
`/auth/register`, `/health`, and read-only catalogue browsing.

<details>
<summary><b>Auth, catalogue and bookings</b></summary>

| Method | Path | Access |
|---|---|---|
| POST | `/auth/register` · `/auth/login` | public |
| GET · PATCH | `/auth/me` (PATCH saves only the fields sent: `fullName`, `phone`, `preferredCurrency`) | authenticated |
| POST | `/auth/me/password` | authenticated |
| DELETE | `/auth/me` (body `{password}`; closes the account) | Customer |
| GET · POST | `/staff-accounts` | Ops Manager |
| PUT · DELETE | `/staff-accounts/{id}` (delete only if never used) | Ops Manager |
| PATCH | `/staff-accounts/{id}/status` (`{active}`) | Ops Manager |
| GET | `/currency` | public |
| PUT | `/currency/{code}` | Finance roles |
| GET | `/parks` · `/packages` · `/packages/{id}` | public |
| GET | `/packages/{id}/availability?date=` · `/packages/{id}/quote?date=&participants=[&excludeBookingId=]` | public |
| POST/PUT/DELETE | `/parks` · `/parks/{id}` | Ops Manager, Reservations Exec |
| POST/PUT/DELETE | `/packages` · `/packages/{id}` · `PATCH /packages/{id}/active` | Ops Manager, Reservations Exec |
| GET | `/bookings` (all for staff, own for customers) · `/bookings/mine` · `/bookings/{id}` | authenticated |
| GET | `/bookings/awaiting-assignment` | Ops Manager, Vehicle Coordinator |
| GET | `/bookings/{id}/permits` | owner or staff |
| GET | `/bookings/{id}/history` | owner or staff |
| POST | `/bookings` | Customer |
| PUT/DELETE | `/bookings/{id}` (pending only) | owner or staff |
| POST | `/bookings/{id}/cancel` | owner or staff |
| PATCH | `/bookings/{id}/status` | any staff |

</details>

<details>
<summary><b>Fleet, guides and assignments</b></summary>

| Method | Path | Access |
|---|---|---|
| GET | `/vehicles` · `/guides` | any staff |
| POST/PUT/DELETE | `/vehicles` · `/guides` · `PATCH …/status` | Ops Manager, Vehicle Coordinator |
| GET | `/assignments` · `/assignments/{id}` · `/assignments/schedule?from=&to=` | any staff |
| GET | `/assignments/suggest/{bookingId}` | Ops Manager, Vehicle Coordinator |
| POST/PUT/DELETE | `/assignments` · `/assignments/{id}` · `PATCH …/status` | Ops Manager, Vehicle Coordinator |

</details>

<details>
<summary><b>Relations, permits, finance and reports</b></summary>

| Method | Path | Access |
|---|---|---|
| GET | `/complaints` · `/complaints/mine` · `/complaints/{id}` · `/complaints/{id}/timeline` | owner or staff |
| POST | `/complaints` | Customer |
| POST | `/complaints/{id}/notes` (optional `templateId`) | owner or staff |
| PUT/DELETE | `/complaints/{id}/notes/{noteId}` | the note's author |
| DELETE | `/complaints/{id}` | owning customer (unanswered, open) · Relations, Ops Manager |
| PATCH | `/complaints/{id}` · POST `/complaints/{id}/escalate` | Relations, Ops Manager |
| GET/POST/PUT/DELETE | `/reply-templates` · `/reply-templates/{id}` | Relations, Ops Manager |
| GET | `/customers` · `/customers/{id}` · `/staff-users` · `/notifications` | any staff |
| GET | `/permits` · `/permits/dashboard` · `/permits/expiring?days=` · `/permits/awaiting-request` | any staff |
| POST | `/permits` · `/permits/{id}/approve` · `/reject` · `/renew` | Ops Manager, Coordinator, Reservations Exec |
| PUT/DELETE | `/permits/{id}` | Ops Manager, Coordinator, Reservations Exec |
| GET | `/payments` · `/payments/summary` | any staff |
| GET | `/payments/booking/{id}` · `/payments/invoice/{id}` | owner or staff |
| POST | `/payments` (card only) | owner or staff |
| POST | `/payments/offline` · `/payments/{id}/void` · DELETE `/payments/{id}` | Finance roles |
| GET | `/refunds` · `/refunds/quote/{bookingId}` | owner or staff |
| POST | `/refunds` (cancelled booking) | owner or staff |
| PUT/DELETE | `/refunds/{id}` (requested only) | Finance roles |
| POST | `/refunds/{id}/approve` · `/reject` · `/process` | Finance roles |
| GET | `/reports/dashboard?from=&to=` | any staff |
| GET | `/kpi-targets` · `/kpi-targets/{id}` | any staff |
| POST/PUT/DELETE | `/kpi-targets` · `/kpi-targets/{id}` | Ops Manager, both Finance roles |

</details>

**Error shape** — every failure returns the same JSON:

```json
{
  "timestamp": "2026-08-24T09:12:33Z",
  "status": 409,
  "message": "Guide Nimal Perera is already committed to BK-2610-868X4 from 2026-10-16 to 2026-10-20.",
  "path": "/api/assignments",
  "details": { "clashes": [ … ], "canOverride": true }
}
```

Validation failures add a `fieldErrors` map, which the UI binds directly to the offending inputs.

---

## Configuration

Everything is overridable by environment variable — see `backend/src/main/resources/application.yml`.

| Variable | Default | Purpose |
|---|---|---|
| `SAFARI_DB_URL` | `jdbc:sqlserver://localhost:1433;databaseName=ceylon_trails;…` | JDBC URL |
| `SAFARI_DB_USER` / `SAFARI_DB_PASSWORD` | empty | Set these (and drop `integratedSecurity`) to use SQL authentication |
| `SAFARI_JWT_SECRET` | dev value | **Change in production.** Minimum 32 characters |
| `SAFARI_JWT_EXP_MINUTES` | `480` | Token lifetime |
| `SAFARI_CORS_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Allowed origins |
| `SAFARI_SEED_ENABLED` | `true` | The core demo data seeds only when `users` is empty; reply templates, KPI targets and the LKR rate (300) each seed when their own table is empty, and an older database gets the Sri Lankan parks and packages added alongside what it has |
| `SAFARI_PERMIT_WARNING_DAYS` | `30` | "Expiring soon" window |
| `SAFARI_PAY_SUCCESS_RATE` / `_DECLINE_RATE` / `_TIMEOUT_RATE` | `80` / `15` / `5` | Gateway outcome weights |
| `SAFARI_PAY_FORCE_OUTCOME` | empty | `SUCCESS`, `DECLINED` or `TIMEOUT` pins every attempt — useful for tests |

The front end reads `VITE_API_BASE` (default `http://localhost:8080/api`) and
`VITE_SHOW_DEMO_ACCOUNTS` (default off; `true` lists the demo accounts on the login screen).

---

## Environment notes (read if something will not start)

These are specific to the machine this was built on and are the two things most likely to trip you
up elsewhere.

**1. Java 17 is required.** Spring Boot 3.3 supports Java 17–22. This machine had only JDK 24, on
which Hibernate/ByteBuddy fails, so a Temurin JDK 17 was installed to
`%USERPROFILE%\devtools\jdk-17.0.20+8` along with Maven 3.9.9 at
`%USERPROFILE%\devtools\apache-maven-3.9.9`. `run-backend.cmd` points at both; edit it or set
`JAVA_HOME` if yours live elsewhere.

**2. TLS interception breaks Maven downloads.** AVG antivirus on this machine re-signs HTTPS
traffic. Its root certificate is in the Windows store but not in Java's, so Maven fails with
`PKIX path building failed`. Fixed by `backend/.mvn/jvm.config`:

```
-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT
```

That makes the Maven JVM trust the Windows root store. It is harmless on machines without
interception — **do not delete the file** unless you know you do not need it.

**3. Database authentication.** The app connects with **Windows integrated authentication** using
`backend/lib/mssql-jdbc_auth-13.2.0.x64.dll`, loaded via `java.library.path` (configured in
`pom.xml`). No SQL login was created and no server security setting was changed. To use SQL
authentication instead, set `SAFARI_DB_USER` / `SAFARI_DB_PASSWORD` and remove
`integratedSecurity=true` from `SAFARI_DB_URL`.

**4. Spaces in the project path.** The path contains spaces, so the `jvmArguments` in `pom.xml`
are quoted. Keep the quotes if you edit them.

---

## Verification

The app was verified end to end against a freshly seeded **throwaway** database.

**API test suites** — `tests/api/`, 354 checks, all passing:

| Suite | Checks | Covers |
|---|---|---|
| `m1_booking.py` | 23 | Auth, role guards, catalogue filters, availability, booking, capacity guard, cancellation |
| `m2_assignment.py` | 26 | Vehicle/guide CRUD, uniqueness, auto-suggest, conflict detection, override, schedule |
| `m3_relations.py` | 27 | Complaint lifecycle, timeline, escalation, ownership rules, customer 360, notifications |
| `m4_permits.py` | 27 | Park CRUD, permit request/approve/renew, expiry + at-risk flagging, role limits |
| `m5_finance.py` | 34 | Gateway success/decline/timeout, partial payments, auto-confirm, invoice, every refund tier |
| `journey.py` | 27 | Register → book → pay → crew → permit → support → complete → reporting, plus both conflict guards |
| `crud_all_members.py` | 92 | Every create/update/delete added for all six members, including each guarded-delete refusal |
| `currency.py` | 28 | Public rates, customer preference, finance-only rate changes, LKR card and cash payments, stale-rate refusal, receipts |
| `staff_accounts.py` | 29 | Manager-only access, create with validation, role change taking effect at once, deactivate (session ends, sign-in refused, open-case guard), reactivate, guarded remove |
| `my_account.py` | 24 | Edit details, field-level errors, password change, closing an account (upcoming-trip and refund guards, anonymised records, email freed) |
| `booking_timeline.py` | 17 | Booked, changed, confirmed by payment, reopened by a void, completed, cancelled; names per viewer; access; older bookings; cascade on delete |

The suites create, change and delete data, so **never point them at a database you care about**.
Create a scratch database, start the API against it, and run them:

```bash
sqlcmd -S "localhost,1433" -E -C -Q "CREATE DATABASE ceylon_trails_test;"
```

```bash
set SAFARI_DB_URL=jdbc:sqlserver://localhost:1433;databaseName=ceylon_trails_test;encrypt=true;trustServerCertificate=true;integratedSecurity=true
```

```bash
run-backend.cmd
```

Then, in a second terminal:

```bash
python tests/api/run_all.py
```

`run_all.py` runs the suites in order and exits non-zero if any check fails. The suites expect
fresh seed data, so drop and recreate the scratch database before each full run.

**Refund policy boundaries** were checked at 30, 9, 8, 7, 4, 2 and 1 days before departure —
all seven tiers return the documented percentage.

**Browser verification** — every screen was exercised in a real browser: login and role redirect,
catalogue filters, the booking flow with live pricing, the assignment dialog (including a
deliberately induced double-booking showing both clashes and the override path), the schedule grid
rendering the overlap, complaint timelines, the permits dashboard, the payment form through
decline → success → auto-confirm, the invoice, refunds, and the analytics dashboard with its date
filter re-querying live. The newer CRUD was also walked through in the UI for every member:

- booking edit and delete
- the assignment status dropdown
- reply templates and note edit/delete
- permit edit and delete
- offline payments, void, and deleting a failed attempt
- refund raise, adjust and withdraw
- KPI targets and the dashboard target lines

**Upgrade path** — the current version was started against a database created by the first
release. The payments CHECK constraint was widened, and the two new tables were created and
seeded. Every existing row count and booking was unchanged.

**All six roles** were signed in and confirmed to land on an appropriate working dashboard with a
correctly scoped sidebar.

Both builds are clean: `mvn compile` and `npm run build` produce no errors.

---

## Assumptions and decisions

Where the brief left room, these calls were made:

1. **USD is the ledger currency; LKR is shown and charged at a maintained rate** (see
   [Rupees and dollars](#rupees-and-dollars)). Rupees display without cents.
2. **Package photography** is a URL in the package's `imageUrl`; the seeded Sri Lankan packages
   use Unsplash photographs. A package without a URL, such as one from the earlier African
   catalogue, shows a branded placeholder instead.
3. **Browsing is public; reserving needs an account.** Customer pages that hold personal data
   (trips, payments, support, invoices) and the whole staff console still require sign-in.
4. **Self-registration always creates a `CUSTOMER`.** Staff accounts are created by the
   Operations Manager on the Staff Accounts page; the seeded staff are only a starting point.
5. **Permit validity** defaults to the trip end date plus 7 days when no expiry is given. A permit
   may not expire before its trip starts.
6. **Assignment duration** equals the package duration, so a trip occupies its guide and vehicle
   for the whole journey, not just day one.
7. **"Bookings over time"** buckets by *departure* month (what operations plans around), while
   **revenue** buckets by *payment* month (what finance recognises).
8. **Notifications are recorded, not sent.** `NotificationService` writes a row and logs it; no
   SMTP or SMS provider is wired in, as the brief allowed.
9. **Report aggregation happens in the service layer** over repository results rather than in SQL
   `GROUP BY`. At this data volume it is simpler and easier to verify; a much larger dataset would
   want the grouping pushed into the database.
10. **`ddl-auto: update`** lets Hibernate manage the schema. `SchemaPatchRunner` covers the one
    change Hibernate will not make: widening an enum CHECK constraint. A production deployment
    should use Flyway or Liquibase migrations instead.
11. **Money records are append-only.** Payments are voided rather than edited, and refunds are
    adjusted only before approval, so the ledger always explains how a balance was reached.
12. **Online payment is card only.** Cash and bank transfer are recorded by finance staff as
    offline payments, since the website cannot take them.

---

## Known limitations

Stated plainly rather than shipped quietly:

- **No unit tests.** The committed tests are black-box API suites (`tests/api`) that need a
  running backend and a scratch database. There is no JUnit or Vitest suite, and nothing runs in CI.
- **Schema is Hibernate-generated.** No versioned migrations. `SchemaPatchRunner` handles the
  known enum-constraint case, but other changes, such as renaming or dropping columns, are not
  managed.
- **The payment gateway is simulated.** No real provider, no PCI handling. Only the last four card
  digits are ever stored, and no CVC or full PAN is persisted — but this is a mock, not a payment
  system.
- **Refunds do not move real money**; processing adjusts the booking ledger only.
- **Permit expiry is evaluated on read,** not by a scheduled job. A permit whose date has passed is
  reported as `EXPIRED` when queried; the stored row still says `APPROVED`. A nightly job would
  reconcile this.
- **No pagination.** All list endpoints return full collections, and tables sort/filter
  client-side. This is fine for demo volumes but would need server-side paging at scale.
- **Two currencies, one manual rate.** The LKR rate is typed in by finance; there is no live
  exchange-rate feed, and no tax/VAT handling on invoices.
- **Photographs and fonts load from the internet** (Unsplash and Google Fonts). Offline, the
  site falls back to branded placeholders and system fonts. For a production launch, licence
  or commission photography and self-host the fonts.
- **Reporting is not cached**, so each dashboard load recomputes aggregates.
