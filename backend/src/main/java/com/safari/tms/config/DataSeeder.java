package com.safari.tms.config;

import com.safari.tms.domain.*;
import com.safari.tms.domain.enums.*;
import com.safari.tms.repo.*;
import com.safari.tms.service.ReferenceGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    public static final String DEMO_PASSWORD = "Password123!";

    private final AppProperties props;
    private final PasswordEncoder encoder;
    private final ReferenceGenerator refs;

    private final UserRepository users;
    private final ParkRepository parks;
    private final SafariPackageRepository packages;
    private final BookingRepository bookings;
    private final VehicleRepository vehicles;
    private final GuideRepository guides;
    private final AssignmentRepository assignments;
    private final ComplaintRepository complaints;
    private final CommunicationLogRepository communications;
    private final NotificationLogRepository notificationLogs;
    private final PermitRepository permits;
    private final PaymentRepository paymentsRepo;
    private final RefundRepository refundsRepo;

    public DataSeeder(AppProperties props, PasswordEncoder encoder, ReferenceGenerator refs,
                      UserRepository users, ParkRepository parks, SafariPackageRepository packages,
                      BookingRepository bookings, VehicleRepository vehicles, GuideRepository guides,
                      AssignmentRepository assignments, ComplaintRepository complaints,
                      CommunicationLogRepository communications,
                      NotificationLogRepository notificationLogs, PermitRepository permits,
                      PaymentRepository paymentsRepo, RefundRepository refundsRepo) {
        this.props = props;
        this.encoder = encoder;
        this.refs = refs;
        this.users = users;
        this.parks = parks;
        this.packages = packages;
        this.bookings = bookings;
        this.vehicles = vehicles;
        this.guides = guides;
        this.assignments = assignments;
        this.complaints = complaints;
        this.communications = communications;
        this.notificationLogs = notificationLogs;
        this.permits = permits;
        this.paymentsRepo = paymentsRepo;
        this.refundsRepo = refundsRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.getSeed().isEnabled()) {
            log.info("Seeding disabled (safari.seed.enabled=false)");
            return;
        }
        if (users.count() > 0) {
            log.info("Seed skipped - {} user(s) already present", users.count());
            return;
        }

        log.info("Seeding demo dataset...");
        List<User> people = seedUsers();
        List<Park> parkList = seedParks();
        List<SafariPackage> packageList = seedPackages(parkList);
        List<Booking> bookingList = seedBookings(people, packageList);
        List<Vehicle> vehicleList = seedVehicles();
        List<Guide> guideList = seedGuides();
        seedAssignments(bookingList, guideList, vehicleList, people.get(0));
        seedComplaints(people, bookingList);
        seedNotifications(bookingList);
        seedPermits(bookingList, people.get(0));
        seedFinance(bookingList, people.get(4));

        log.info("Seed complete: {} users, {} parks, {} packages, {} bookings, {} vehicles, "
                        + "{} guides, {} assignments, {} complaints, {} notifications, {} permits, "
                        + "{} payments, {} refunds",
                users.count(), parks.count(), packages.count(), bookings.count(),
                vehicles.count(), guides.count(), assignments.count(), complaints.count(),
                notificationLogs.count(), permits.count(),
                paymentsRepo.count(), refundsRepo.count());
    }

    /* --------------------------------------------------------------- Users */

    private List<User> seedUsers() {
        List<User> created = new ArrayList<>();
        created.add(user("Amara Okonkwo", "ops@sundara.test", "+255 712 004 991", Role.OPERATIONS_MANAGER));
        created.add(user("Priya Raman", "relations@sundara.test", "+254 733 118 220", Role.CUSTOMER_RELATIONS_OFFICER));
        created.add(user("Tobias Mwangi", "fleet@sundara.test", "+254 720 553 018", Role.SAFARI_VEHICLE_COORDINATOR));
        created.add(user("Lena Fischer", "reservations@sundara.test", "+27 82 447 6610", Role.FINANCE_RESERVATIONS_EXECUTIVE));
        created.add(user("Daniel Perera", "accounts@sundara.test", "+27 83 220 7745", Role.FINANCE_ACCOUNTS_OFFICER));
        created.add(user("Nadia Hassan", "nadia@example.test", "+44 7700 900321", Role.CUSTOMER));
        created.add(user("Marcus Bell", "marcus@example.test", "+1 415 555 0142", Role.CUSTOMER));
        created.add(user("Sofia Alvarez", "sofia@example.test", "+34 611 22 33 44", Role.CUSTOMER));
        return created;
    }

    private User user(String name, String email, String phone, Role role) {
        return users.save(new User(name, email, encoder.encode(DEMO_PASSWORD), phone, role));
    }

    /* --------------------------------------------------------------- Parks */

    private List<Park> seedParks() {
        return List.of(
                parks.save(new Park("Serengeti National Park", "Northern Tanzania",
                        "Endless short-grass plains that carry the largest terrestrial mammal migration on earth. "
                                + "Best known for big cat densities in the Seronera valley.",
                        new BigDecimal("70.00"), "Tanzania National Parks Authority")),
                parks.save(new Park("Maasai Mara National Reserve", "Narok County, Kenya",
                        "The northern extension of the Serengeti ecosystem, famous for the Mara River crossings "
                                + "between July and October and for resident black rhino.",
                        new BigDecimal("80.00"), "Narok County Wildlife Board")),
                parks.save(new Park("Kruger National Park", "Limpopo & Mpumalanga, South Africa",
                        "One of Africa's largest reserves with an exceptional road network, making it ideal for "
                                + "self-drive style game viewing and night predator drives.",
                        new BigDecimal("45.00"), "South African National Parks")),
                parks.save(new Park("Okavango Delta", "Ngamiland, Botswana",
                        "A vast inland delta where flood waters from the Angolan highlands create seasonal "
                                + "channels best explored by traditional mokoro canoe.",
                        new BigDecimal("95.00"), "Botswana Department of Wildlife")));
    }

    /* ------------------------------------------------------------ Packages */

    private List<SafariPackage> seedPackages(List<Park> parkList) {
        Park serengeti = parkList.get(0);
        Park mara = parkList.get(1);
        Park kruger = parkList.get(2);
        Park okavango = parkList.get(3);

        return List.of(
                packages.save(new SafariPackage("Great Migration Explorer",
                        "Five days tracking the wildebeest herds across the Seronera and western corridor, "
                                + "with two nights in a mobile tented camp that moves with the migration.",
                        serengeti, 5, new BigDecimal("2450.00"), 12,
                        "serengeti-migration", "Mobile tented camp|Sunrise game drives|Resident big cat tracking")),

                packages.save(new SafariPackage("Serengeti Balloon & Bush",
                        "A short break built around a dawn hot-air balloon flight over the plains, followed by "
                                + "a champagne bush breakfast and two afternoon drives.",
                        serengeti, 3, new BigDecimal("1780.00"), 8,
                        "serengeti-balloon", "Hot-air balloon flight|Bush breakfast|Small group")),

                packages.save(new SafariPackage("Mara Big Five Safari",
                        "Four days concentrating on the Mara Triangle with experienced spotters, targeting lion, "
                                + "leopard, elephant, buffalo and the reserve's protected black rhino.",
                        mara, 4, new BigDecimal("1990.00"), 10,
                        "mara-big-five", "Big Five focus|Expert spotters|Mara Triangle")),

                packages.save(new SafariPackage("Mara River Crossing Special",
                        "A six-day seasonal departure timed for the river crossings, with patient full-day vigils "
                                + "at the main crossing points and a Maasai homestead visit.",
                        mara, 6, new BigDecimal("3120.00"), 14,
                        "mara-crossing", "River crossing vigils|Cultural visit|Full-day drives")),

                packages.save(new SafariPackage("Kruger Classic Bush Drive",
                        "Three days working the southern Kruger loop roads from a comfortable rest camp base, "
                                + "an excellent first safari for families.",
                        kruger, 3, new BigDecimal("1290.00"), 16,
                        "kruger-classic", "Family friendly|Rest camp base|Southern loop roads")),

                packages.save(new SafariPackage("Kruger Night Predator Trail",
                        "A two-night specialist departure using permitted after-dark drives and spotlights to "
                                + "find hyena, civet, genet and hunting lion.",
                        kruger, 2, new BigDecimal("940.00"), 10,
                        "kruger-night", "Night drives|Spotlight tracking|Nocturnal specialists")),

                packages.save(new SafariPackage("Okavango Delta Mokoro Journey",
                        "Five days poling the delta channels by mokoro with walking safaris on the palm islands "
                                + "and two nights of fly-camping under canvas.",
                        okavango, 5, new BigDecimal("2680.00"), 8,
                        "okavango-mokoro", "Mokoro channels|Walking safaris|Island fly-camping")));
    }

    /* ------------------------------------------------------------ Bookings */

    private List<Booking> seedBookings(List<User> allUsers, List<SafariPackage> packageList) {
        User nadia = allUsers.get(5);
        User marcus = allUsers.get(6);
        User sofia = allUsers.get(7);
        LocalDate today = LocalDate.now();
        List<Booking> all = new ArrayList<>();

        // Completed trips in the recent past give the reporting dashboard real history.
        all.add(booking(nadia, packageList.get(0), today.minusDays(96), 2, BookingStatus.COMPLETED, true));
        all.add(booking(marcus, packageList.get(4), today.minusDays(74), 4, BookingStatus.COMPLETED, true));
        all.add(booking(sofia, packageList.get(2), today.minusDays(58), 2, BookingStatus.COMPLETED, true));
        all.add(booking(nadia, packageList.get(5), today.minusDays(41), 3, BookingStatus.COMPLETED, true));
        all.add(booking(marcus, packageList.get(6), today.minusDays(24), 2, BookingStatus.COMPLETED, true));

        // A couple of cancellations so the cancellation-rate metric is not zero.
        Booking cancelledOne = booking(sofia, packageList.get(1), today.minusDays(30), 2, BookingStatus.CANCELLED, false);
        cancelledOne.setCancellationReason("Customer rescheduled to next season");
        cancelledOne.setCancelledAt(cancelledOne.getCreatedAt());
        all.add(bookings.save(cancelledOne));

        Booking cancelledTwo = booking(marcus, packageList.get(3), today.plusDays(52), 3, BookingStatus.CANCELLED, false);
        cancelledTwo.setCancellationReason("Flight connection no longer available");
        cancelledTwo.setCancelledAt(cancelledTwo.getCreatedAt());
        all.add(bookings.save(cancelledTwo));

        // Confirmed future departures - these are what operations needs to crew.
        all.add(booking(nadia, packageList.get(2), today.plusDays(9), 2, BookingStatus.CONFIRMED, true));
        all.add(booking(marcus, packageList.get(0), today.plusDays(16), 4, BookingStatus.CONFIRMED, true));
        all.add(booking(sofia, packageList.get(6), today.plusDays(23), 2, BookingStatus.CONFIRMED, true));
        all.add(booking(nadia, packageList.get(4), today.plusDays(31), 5, BookingStatus.CONFIRMED, true));
        all.add(booking(marcus, packageList.get(3), today.plusDays(44), 6, BookingStatus.CONFIRMED, true));

        // Pending departures still awaiting payment.
        all.add(booking(sofia, packageList.get(5), today.plusDays(12), 2, BookingStatus.PENDING, false));
        all.add(booking(nadia, packageList.get(1), today.plusDays(27), 3, BookingStatus.PENDING, false));
        all.add(booking(marcus, packageList.get(2), today.plusDays(38), 2, BookingStatus.PENDING, false));

        return all;
    }

    private Booking booking(User customer, SafariPackage pkg, LocalDate tripDate,
                            int participants, BookingStatus status, boolean paidInFull) {
        Booking b = new Booking();
        b.setBookingReference(refs.booking());
        b.setCustomer(customer);
        b.setSafariPackage(pkg);
        b.setTripDate(tripDate);
        b.setParticipants(participants);
        b.setTotalPrice(pkg.getPricePerPerson().multiply(BigDecimal.valueOf(participants)));
        b.setAmountPaid(paidInFull ? b.getTotalPrice() : BigDecimal.ZERO);
        b.setStatus(status);
        b.setPaymentDueDate(tripDate.minusDays(7));
        b.setCreatedAt(tripDate.minusDays(45).atStartOfDay(ZoneOffset.UTC).toInstant());
        return bookings.save(b);
    }

    /* ------------------------------------------------------------ Vehicles */

    private List<Vehicle> seedVehicles() {
        LocalDate today = LocalDate.now();
        return List.of(
                vehicles.save(new Vehicle("KAJ 442T", "Toyota Land Cruiser 79", "4x4 Game Viewer", 7,
                        VehicleStatus.AVAILABLE, today.minusDays(38), "Pop-top roof, fridge, two spare wheels.")),
                vehicles.save(new Vehicle("KBX 907M", "Toyota Land Cruiser 78", "Extended Safari", 9,
                        VehicleStatus.AVAILABLE, today.minusDays(15), "Long wheelbase, charging points at every seat.")),
                vehicles.save(new Vehicle("TZ 118 SG", "Nissan Patrol Safari", "4x4 Game Viewer", 6,
                        VehicleStatus.AVAILABLE, today.minusDays(61), "Serengeti-based, fitted with long-range tank.")),
                vehicles.save(new Vehicle("GP 55 KRG", "Land Rover Defender 130", "Open Game Viewer", 10,
                        VehicleStatus.MAINTENANCE, today.minusDays(4), "Gearbox rebuild — back on the road in two weeks.")),
                vehicles.save(new Vehicle("BW 776 OKV", "Toyota Hilux Delta Spec", "Delta Transfer", 5,
                        VehicleStatus.AVAILABLE, today.minusDays(22), "Raised air intake for delta water crossings.")));
    }

    /* -------------------------------------------------------------- Guides */

    private List<Guide> seedGuides() {
        return List.of(
                guides.save(new Guide("Joseph Kimani", "joseph.kimani@sundara.test", "+254 722 145 908",
                        "KE-G-4471", "English, Swahili, German", "Big Five tracking", 12, GuideStatus.AVAILABLE)),
                guides.save(new Guide("Grace Mutinda", "grace.mutinda@sundara.test", "+254 711 662 340",
                        "KE-G-5518", "English, Swahili, French", "Ornithology", 8, GuideStatus.AVAILABLE)),
                guides.save(new Guide("Elias Ndlovu", "elias.ndlovu@sundara.test", "+27 82 559 1174",
                        "ZA-G-2209", "English, Afrikaans, Zulu", "Walking safaris", 15, GuideStatus.AVAILABLE)),
                guides.save(new Guide("Thandiwe Moyo", "thandiwe.moyo@sundara.test", "+267 71 448 205",
                        "BW-G-1183", "English, Setswana", "Delta and mokoro", 6, GuideStatus.AVAILABLE)),
                guides.save(new Guide("Peter Massawe", "peter.massawe@sundara.test", "+255 754 220 617",
                        "TZ-G-7702", "English, Swahili, Italian", "Migration ecology", 10, GuideStatus.ON_LEAVE)));
    }

    /* --------------------------------------------------------- Assignments */

    private void seedAssignments(List<Booking> bookingList, List<Guide> guideList,
                                 List<Vehicle> vehicleList, User assignedBy) {
        // Crew two of the confirmed departures; the rest stay in the operations queue on purpose
        // so the "awaiting assignment" dashboard has real work to show.
        List<Booking> confirmed = bookingList.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .toList();

        if (confirmed.size() >= 2) {
            assign(confirmed.get(0), guideList.get(0), vehicleList.get(0), assignedBy,
                    "Client has requested an early start each morning.");
            assign(confirmed.get(1), guideList.get(2), vehicleList.get(1), assignedBy, null);
        }
    }

    /* ---------------------------------------------------------- Complaints */

    private void seedComplaints(List<User> people, List<Booking> bookingList) {
        User relations = people.get(1);
        User opsManager = people.get(0);
        User nadia = people.get(5);
        User marcus = people.get(6);
        User sofia = people.get(7);

        List<Booking> completed = bookingList.stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED).toList();

        // 1. Open, unassigned - shows up as new work on the relations dashboard.
        Complaint open = complaint(sofia, completed.isEmpty() ? null : completed.get(2),
                "Vehicle air conditioning failed on day two",
                "The air conditioning in our vehicle stopped working on the second morning and was "
                        + "never fixed. With midday temperatures over 35C this made the drives very "
                        + "uncomfortable for my parents.",
                ComplaintCategory.VEHICLE, ComplaintPriority.HIGH, ComplaintStatus.OPEN, null, 3);
        note(open, sofia, CommunicationType.IN_APP_NOTE, CommunicationDirection.INBOUND,
                open.getSubject(), open.getDescription(), sofia);

        // 2. In progress, assigned and escalated.
        Complaint inProgress = complaint(marcus, completed.isEmpty() ? null : completed.get(1),
                "Charged twice for the same booking",
                "My card statement shows two identical charges for the Kruger trip. I have attached "
                        + "the statement lines to this case. Please refund the duplicate.",
                ComplaintCategory.PAYMENT, ComplaintPriority.CRITICAL, ComplaintStatus.IN_PROGRESS,
                relations, 9);
        inProgress.setEscalatedToDepartment("Finance");
        inProgress.setEscalatedAt(inProgress.getCreatedAt().plusSeconds(7200));
        complaints.save(inProgress);
        note(inProgress, marcus, CommunicationType.IN_APP_NOTE, CommunicationDirection.INBOUND,
                inProgress.getSubject(), inProgress.getDescription(), marcus);
        note(inProgress, marcus, CommunicationType.EMAIL, CommunicationDirection.OUTBOUND,
                "We are looking into this",
                "Thanks for flagging this Marcus. I can see two authorisations against your booking "
                        + "and have asked our finance team to confirm which one settled.", relations);
        note(inProgress, marcus, CommunicationType.ESCALATION, CommunicationDirection.INTERNAL,
                "Escalated to Finance",
                "Duplicate settlement confirmed by the gateway reference. Finance to raise the refund.",
                relations);

        // 3. Resolved case with a full trail.
        Complaint resolved = complaint(nadia, completed.isEmpty() ? null : completed.get(0),
                "Requested a vegetarian menu but none was provided",
                "I noted a vegetarian requirement when booking but the camp kitchen had not been told.",
                ComplaintCategory.PARK_EXPERIENCE, ComplaintPriority.MEDIUM, ComplaintStatus.RESOLVED,
                relations, 26);
        resolved.setResolutionNotes("Camp catering briefed, and a 10% credit applied to the customer's "
                + "next booking as a goodwill gesture. Customer confirmed they were happy.");
        resolved.setResolvedAt(resolved.getCreatedAt().plusSeconds(60 * 60 * 52));
        complaints.save(resolved);
        note(resolved, nadia, CommunicationType.IN_APP_NOTE, CommunicationDirection.INBOUND,
                resolved.getSubject(), resolved.getDescription(), nadia);
        note(resolved, nadia, CommunicationType.PHONE_CALL, CommunicationDirection.OUTBOUND,
                "Called the customer",
                "Spoke with Nadia for 10 minutes, apologised and offered a goodwill credit.", relations);
        note(resolved, nadia, CommunicationType.STATUS_CHANGE, CommunicationDirection.INTERNAL,
                "Case update", "Status IN_PROGRESS -> RESOLVED. Goodwill credit approved.", opsManager);

        // 4. Unresolved - closed without a fix, useful for reporting.
        Complaint unresolved = complaint(marcus, null,
                "Lost sunglasses in the vehicle",
                "I think I left a pair of sunglasses in the vehicle on the last afternoon drive.",
                ComplaintCategory.OTHER, ComplaintPriority.LOW, ComplaintStatus.UNRESOLVED, relations, 40);
        unresolved.setResolutionNotes("Vehicle searched and lost-property log checked. Nothing found.");
        unresolved.setResolvedAt(unresolved.getCreatedAt().plusSeconds(60 * 60 * 96));
        complaints.save(unresolved);
        note(unresolved, marcus, CommunicationType.IN_APP_NOTE, CommunicationDirection.INBOUND,
                unresolved.getSubject(), unresolved.getDescription(), marcus);
        note(unresolved, marcus, CommunicationType.EMAIL, CommunicationDirection.OUTBOUND,
                "No luck I am afraid",
                "We searched the vehicle and checked lost property but could not find them. Sorry.",
                relations);

        // 5. A general inquiry still open.
        Complaint inquiry = complaint(nadia, null,
                "Do you offer single-supplement-free departures?",
                "I travel alone and would like to know whether any departures waive the single supplement.",
                ComplaintCategory.GENERAL_INQUIRY, ComplaintPriority.LOW, ComplaintStatus.OPEN, null, 1);
        note(inquiry, nadia, CommunicationType.IN_APP_NOTE, CommunicationDirection.INBOUND,
                inquiry.getSubject(), inquiry.getDescription(), nadia);
    }

    /* ------------------------------------------------------- Notifications */

    /**
     * Back-fills the messages the system would have sent for the seeded bookings, so the
     * notification log is representative on a fresh install.
     */
    private void seedNotifications(List<Booking> bookingList) {
        for (Booking b : bookingList) {
            notify(b.getCustomer(), NotificationChannel.EMAIL,
                    "Booking " + b.getBookingReference() + " received",
                    "Thanks " + b.getCustomer().getFullName() + ", we are holding "
                            + b.getParticipants() + " place(s) on '" + b.getSafariPackage().getName()
                            + "' for " + b.getTripDate() + ".",
                    "Booking", b.getId(), b.getCreatedAt());

            if (b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.COMPLETED) {
                notify(b.getCustomer(), NotificationChannel.EMAIL,
                        "Booking " + b.getBookingReference() + " confirmed",
                        "Your payment has been received in full and your departure is confirmed.",
                        "Payment", b.getId(), b.getCreatedAt().plusSeconds(86400));
                notify(b.getCustomer(), NotificationChannel.SMS, null,
                        "Sundara Safari: " + b.getBookingReference() + " confirmed for " + b.getTripDate() + ".",
                        "Booking", b.getId(), b.getCreatedAt().plusSeconds(86400));
            }

            if (b.getStatus() == BookingStatus.CANCELLED) {
                notify(b.getCustomer(), NotificationChannel.EMAIL,
                        "Booking " + b.getBookingReference() + " cancelled",
                        "Your booking has been cancelled. " + (b.getCancellationReason() == null
                                ? "" : b.getCancellationReason()),
                        "Booking", b.getId(), b.getCreatedAt().plusSeconds(172800));
            }
        }
    }

    /* ------------------------------------------------------------- Permits */

    /**
     * Issues permits across a deliberate spread of states so the permits screen shows healthy,
     * expiring-soon and at-risk trips on a fresh install.
     */
    private void seedPermits(List<Booking> bookingList, User requestedBy) {
        LocalDate today = LocalDate.now();

        List<Booking> future = bookingList.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .filter(b -> !b.getTripDate().isBefore(today))
                .sorted(java.util.Comparator.comparing(Booking::getTripDate))
                .toList();

        List<Booking> past = bookingList.stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
                .sorted(java.util.Comparator.comparing(Booking::getTripDate))
                .toList();

        // Healthy: approved with plenty of runway.
        if (future.size() > 0) {
            permit(future.get(0), requestedBy, PermitStatus.APPROVED,
                    tripEnd(future.get(0)).plusDays(30), today.minusDays(20),
                    "Standard group entry permit.", 0);
        }
        // Expiring soon: approved but lapses just inside the warning window.
        if (future.size() > 1) {
            permit(future.get(1), requestedBy, PermitStatus.APPROVED,
                    today.plusDays(9), today.minusDays(30),
                    "Short-validity permit issued during the peak-season quota.", 0);
        }
        // At risk: approved but expires before the trip finishes.
        if (future.size() > 2) {
            Booking b = future.get(2);
            permit(b, requestedBy, PermitStatus.APPROVED,
                    b.getTripDate().plusDays(1), today.minusDays(12),
                    "Provisional permit - park office to extend once the quota is confirmed.", 1);
        }
        // Still pending with the authority.
        if (future.size() > 3) {
            permit(future.get(3), requestedBy, PermitStatus.PENDING,
                    tripEnd(future.get(3)).plusDays(7), null,
                    "Submitted to the park authority, awaiting reference number.", 0);
        }
        // Historic, already expired.
        if (past.size() > 0) {
            permit(past.get(past.size() - 1), requestedBy, PermitStatus.APPROVED,
                    tripEnd(past.get(past.size() - 1)).plusDays(7),
                    past.get(past.size() - 1).getTripDate().minusDays(21),
                    "Completed trip - permit retained for audit.", 0);
        }
    }

    /* ------------------------------------------------------------- Finance */

    /**
     * Creates the payment ledger implied by the seeded bookings, then adds a deliberate spread of
     * edge cases: a part-paid booking, an overdue balance, a declined attempt and a live refund.
     */
    private void seedFinance(List<Booking> bookingList, User financeUser) {
        LocalDate today = LocalDate.now();

        // Settled payments behind every booking that is already paid.
        for (Booking b : bookingList) {
            if (b.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
                payment(b, b.getAmountPaid(), PaymentStatus.SUCCESS, PaymentMethod.CREDIT_CARD,
                        financeUser, b.getCreatedAt().plusSeconds(86400), null);
            }
        }

        List<Booking> pending = bookingList.stream()
                .filter(b -> b.getStatus() == BookingStatus.PENDING)
                .toList();

        // A part-paid booking: deposit taken, balance still outstanding.
        if (pending.size() > 0) {
            Booking b = pending.get(0);
            BigDecimal deposit = b.getTotalPrice().multiply(new BigDecimal("0.40"))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            b.setAmountPaid(deposit);
            bookings.save(b);
            payment(b, deposit, PaymentStatus.SUCCESS, PaymentMethod.CREDIT_CARD, financeUser,
                    b.getCreatedAt().plusSeconds(172800), null);
        }

        // An overdue balance: due date already passed with nothing paid.
        if (pending.size() > 1) {
            Booking b = pending.get(1);
            b.setPaymentDueDate(today.minusDays(6));
            bookings.save(b);
        }

        // A declined attempt, so the finance dashboard shows a failed payment.
        if (pending.size() > 2) {
            Booking b = pending.get(2);
            payment(b, b.getTotalPrice(), PaymentStatus.DECLINED, PaymentMethod.CREDIT_CARD,
                    financeUser, null, "Card declined by the issuing bank");
        }

        // A cancelled booking that had been paid, with a refund awaiting a finance decision.
        bookingList.stream()
                .filter(b -> b.getStatus() == BookingStatus.CANCELLED)
                .filter(b -> b.getTripDate().isAfter(today))
                .findFirst()
                .ifPresent(b -> {
                    b.setAmountPaid(b.getTotalPrice());
                    bookings.save(b);
                    payment(b, b.getTotalPrice(), PaymentStatus.SUCCESS, PaymentMethod.BANK_TRANSFER,
                            financeUser, b.getCreatedAt().plusSeconds(86400), null);
                    refund(b);
                });
    }

    private void payment(Booking booking, BigDecimal amount, PaymentStatus status,
                         PaymentMethod method, User processedBy, java.time.Instant paidAt,
                         String failureReason) {
        Payment p = new Payment();
        p.setPaymentReference(refs.payment());
        p.setBooking(booking);
        p.setAmount(amount);
        p.setMethod(method);
        p.setStatus(status);
        p.setGatewayReference("SIMGW-" + Long.toHexString(Math.abs(
                (booking.getBookingReference() + amount).hashCode())).toUpperCase());
        p.setFailureReason(failureReason);
        p.setCardLast4(method == PaymentMethod.BANK_TRANSFER ? null : "4242");
        p.setCardHolderName(method == PaymentMethod.BANK_TRANSFER ? null : booking.getCustomer().getFullName());
        p.setPaidAt(paidAt);
        p.setProcessedBy(processedBy);
        p.setCreatedAt(paidAt == null ? booking.getCreatedAt().plusSeconds(3600) : paidAt);
        paymentsRepo.save(p);
    }

    /** Applies the same cancellation-window policy the RefundService uses. */
    private void refund(Booking booking) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), booking.getTripDate());
        int percent = days > 7 ? 100 : days >= 2 ? 50 : 0;
        String policy = days > 7
                ? "Full refund (more than 7 days before departure)"
                : days >= 2
                ? "Half refund (within 7 days of departure)"
                : "No refund (within 48 hours of departure)";

        Refund r = new Refund();
        r.setRefundReference(refs.refund());
        r.setBooking(booking);
        r.setAmountPaidAtRequest(booking.getAmountPaid());
        r.setCalculatedAmount(booking.getAmountPaid().multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP));
        r.setRefundPercentage(percent);
        r.setPolicyApplied(policy);
        r.setDaysBeforeTrip((int) days);
        r.setStatus(RefundStatus.REQUESTED);
        r.setReason("Booking cancelled: " + booking.getCancellationReason());
        r.setRequestedAt(booking.getCancelledAt());
        refundsRepo.save(r);
    }

    private LocalDate tripEnd(Booking b) {
        return b.getTripDate().plusDays(Math.max(0, b.getSafariPackage().getDurationDays() - 1));
    }

    private void permit(Booking booking, User requestedBy, PermitStatus status,
                        LocalDate expiry, LocalDate issued, String notes, int renewals) {
        Permit p = new Permit();
        p.setPermitNumber(refs.permit());
        p.setBooking(booking);
        p.setPark(booking.getSafariPackage().getPark());
        p.setStatus(status);
        p.setIssueDate(issued);
        p.setExpiryDate(expiry);
        p.setFeeAmount(booking.getSafariPackage().getPark().getEntryFeePerPerson()
                .multiply(BigDecimal.valueOf(booking.getParticipants())));
        p.setCoveredParticipants(booking.getParticipants());
        p.setNotes(notes);
        p.setRequestedBy(requestedBy);
        p.setRenewalCount(renewals);
        if (status == PermitStatus.APPROVED && issued != null) {
            p.setApprovedAt(issued.atStartOfDay(ZoneOffset.UTC).toInstant());
        }
        permits.save(p);
    }

    private void notify(User recipient, NotificationChannel channel, String subject, String body,
                        String relatedEntity, Long relatedId, java.time.Instant when) {
        if (channel == NotificationChannel.SMS
                && (recipient.getPhone() == null || recipient.getPhone().isBlank())) {
            return;
        }
        NotificationLog n = new NotificationLog();
        n.setRecipient(recipient);
        n.setChannel(channel);
        n.setRecipientAddress(channel == NotificationChannel.SMS ? recipient.getPhone() : recipient.getEmail());
        n.setSubject(subject);
        n.setBody(body);
        n.setStatus(NotificationStatus.SENT);
        n.setRelatedEntity(relatedEntity);
        n.setRelatedId(relatedId);
        n.setCreatedAt(when);
        n.setSentAt(when);
        notificationLogs.save(n);
    }

    private Complaint complaint(User customer, Booking booking, String subject, String description,
                                ComplaintCategory category, ComplaintPriority priority,
                                ComplaintStatus status, User assignedTo, int daysAgo) {
        Complaint c = new Complaint();
        c.setReference(refs.complaint());
        c.setCustomer(customer);
        c.setBooking(booking);
        c.setSubject(subject);
        c.setDescription(description);
        c.setCategory(category);
        c.setPriority(priority);
        c.setStatus(status);
        c.setAssignedTo(assignedTo);
        c.setCreatedAt(java.time.Instant.now().minusSeconds(60L * 60 * 24 * daysAgo));
        c.setUpdatedAt(c.getCreatedAt());
        return complaints.save(c);
    }

    private void note(Complaint complaint, User customer, CommunicationType type,
                      CommunicationDirection direction, String subject, String message, User author) {
        CommunicationLog entry = new CommunicationLog();
        entry.setCustomer(customer);
        entry.setComplaint(complaint);
        entry.setBooking(complaint.getBooking());
        entry.setType(type);
        entry.setDirection(direction);
        entry.setSubject(subject);
        entry.setMessage(message);
        entry.setAuthor(author);
        entry.setCreatedAt(complaint.getCreatedAt().plusSeconds(60L * 90 * (communications.count() % 7 + 1)));
        communications.save(entry);
    }

    private void assign(Booking booking, Guide guide, Vehicle vehicle, User assignedBy, String notes) {
        Assignment a = new Assignment();
        a.setBooking(booking);
        a.setGuide(guide);
        a.setVehicle(vehicle);
        a.setAssignmentDate(booking.getTripDate());
        a.setDurationDays(booking.getSafariPackage().getDurationDays());
        a.syncEndDate();
        a.setStatus(AssignmentStatus.SCHEDULED);
        a.setNotes(notes);
        a.setAssignedBy(assignedBy);
        assignments.save(a);
    }
}
