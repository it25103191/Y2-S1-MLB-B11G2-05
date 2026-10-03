package com.safari.tms.config;

import com.safari.tms.domain.*;
import com.safari.tms.domain.enums.*;
import com.safari.tms.repo.*;
import com.safari.tms.service.ReferenceGenerator;
import com.safari.tms.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Populates a realistic demo dataset the first time the application starts against an empty
 * database. Every screen therefore has something to show without any manual data entry.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** Shared demo password for every seeded account. */
    public static final String DEMO_PASSWORD = "Password123!";

    /** Staff logins that other seed steps look up by e-mail. */
    static final String OPS_MANAGER_EMAIL = "kamal@ceylontrails.lk";
    static final String RELATIONS_EMAIL = "sachini@ceylontrails.lk";

    /** Its presence marks a database that already has the Sri Lankan catalogue. */
    static final String FLAGSHIP_PARK = "Yala National Park";

    /** Starting LKR rate; finance keeps it current from the Payments screen. */
    static final BigDecimal DEFAULT_LKR_RATE = new BigDecimal("300.0000");

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
    private final ReplyTemplateRepository replyTemplates;
    private final KpiTargetRepository kpiTargets;
    private final ExchangeRateRepository exchangeRates;
    private final ReportService reportService;

    public DataSeeder(AppProperties props, PasswordEncoder encoder, ReferenceGenerator refs,
                      UserRepository users, ParkRepository parks, SafariPackageRepository packages,
                      BookingRepository bookings, VehicleRepository vehicles, GuideRepository guides,
                      AssignmentRepository assignments, ComplaintRepository complaints,
                      CommunicationLogRepository communications,
                      NotificationLogRepository notificationLogs, PermitRepository permits,
                      PaymentRepository paymentsRepo, RefundRepository refundsRepo,
                      ReplyTemplateRepository replyTemplates, KpiTargetRepository kpiTargets,
                      ExchangeRateRepository exchangeRates, ReportService reportService) {
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
        this.replyTemplates = replyTemplates;
        this.kpiTargets = kpiTargets;
        this.exchangeRates = exchangeRates;
        this.reportService = reportService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.getSeed().isEnabled()) {
            log.info("Seeding disabled (safari.seed.enabled=false)");
            return;
        }
        boolean existing = users.count() > 0;
        if (existing) {
            log.info("Core seed skipped - {} user(s) already present", users.count());
        } else {
            seedCore();
        }

        // Databases created before the Ceylon Trails rebrand hold an African catalogue. Add the Sri
        // Lankan parks and packages alongside it; existing records are never changed or removed.
        if (existing && parks.findAll().stream().noneMatch(p -> FLAGSHIP_PARK.equalsIgnoreCase(p.getName()))) {
            List<Park> added = seedParks();
            List<SafariPackage> addedPackages = seedPackages(added);
            log.info("Added the Sri Lankan catalogue: {} parks, {} packages", added.size(), addedPackages.size());
        }
        if (exchangeRates.count() == 0) {
            exchangeRates.save(new ExchangeRate(Currency.LKR, DEFAULT_LKR_RATE));
            log.info("Seeded exchange rate: 1 USD = {} LKR", DEFAULT_LKR_RATE);
        }

        // Modules added after the first release seed on their own, so a database created by an
        // earlier build still gets their demo data the first time the new version starts.
        if (replyTemplates.count() == 0) {
            seedReplyTemplates();
        }
        if (kpiTargets.count() == 0) {
            seedKpiTargets();
        }
    }

    private void seedCore() {
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

    /* ------------------------------------------------------ Reply templates */

    private void seedReplyTemplates() {
        User author = users.findByEmailIgnoreCase(RELATIONS_EMAIL).orElse(null);

        template(author, "Acknowledge a new case", null,
                "We're looking into {{caseReference}}",
                "Hi {{customerName}},\n\nThanks for getting in touch. Your case {{caseReference}} is now with"
                        + " our customer relations team and we will come back to you within one working day."
                        + "\n\nKind regards,\nCeylon Trails Customer Relations",
                true, 14);

        template(author, "Vehicle fault apology", ComplaintCategory.VEHICLE,
                "Sorry about your vehicle on {{bookingReference}}",
                "Hi {{customerName}},\n\nI'm sorry the vehicle let you down on {{bookingReference}}. I have"
                        + " passed the details to our fleet coordinator so the fault is fixed before its next"
                        + " departure, and I will confirm what we can offer to make up for it.",
                true, 5);

        template(author, "Refund on its way", ComplaintCategory.PAYMENT,
                "Your refund for {{bookingReference}}",
                "Hi {{customerName}},\n\nYour refund for booking {{bookingReference}} has been approved by"
                        + " our finance team. It should reach your account within five working days.",
                true, 8);

        template(author, "Dietary requirements noted", ComplaintCategory.PARK_EXPERIENCE,
                "Dietary requirements for {{bookingReference}}",
                "Hi {{customerName}},\n\nThank you for letting us know. I have briefed the camp kitchen for"
                        + " {{bookingReference}} so every meal meets your requirements.",
                true, 3);

        template(author, "Booking change confirmed", ComplaintCategory.BOOKING,
                "Changes to {{bookingReference}} confirmed",
                "Hi {{customerName}},\n\nThe change you asked for on booking {{bookingReference}} has been"
                        + " made. You can see the updated details under My Bookings.",
                true, 2);

        template(author, "Lost property search", ComplaintCategory.OTHER,
                "Lost property ({{caseReference}})",
                "Hi {{customerName}},\n\nWe searched the vehicle and checked the lost-property log but have"
                        + " not found the item yet. We will contact you straight away if it turns up.",
                false, 1);

        log.info("Seeded {} reply templates", replyTemplates.count());
    }

    private void template(User author, String title, ComplaintCategory category, String subject,
                          String body, boolean active, int usage) {
        ReplyTemplate t = new ReplyTemplate();
        t.setTitle(title);
        t.setCategory(category);
        t.setSubject(subject);
        t.setBody(body);
        t.setActive(active);
        t.setUsageCount(usage);
        t.setCreatedBy(author);
        replyTemplates.save(t);
    }

    /* ---------------------------------------------------------- KPI targets */

    /**
     * Sets targets for the three months before this one, this month and the next two. Values are
     * derived from the actual figures so the demo always shows a mix of Achieved, Missed,
     * In progress and Upcoming, whatever date the database is seeded on.
     */
    private void seedKpiTargets() {
        User owner = users.findByEmailIgnoreCase(OPS_MANAGER_EMAIL).orElse(null);
        ReportService.Snapshot data = reportService.snapshot();
        YearMonth now = YearMonth.now();

        for (int offset = -3; offset <= 2; offset++) {
            YearMonth month = now.plusMonths(offset);
            // Three and one months ago were hit; two months ago was missed.
            boolean hit = offset == -3 || offset == -1;

            BigDecimal revenue = reportService.actual(KpiMetric.REVENUE, List.of(month), data);
            BigDecimal bookingsActual = reportService.actual(KpiMetric.BOOKINGS, List.of(month), data);

            kpiTarget(owner, month, KpiMetric.REVENUE, revenueTarget(revenue, offset, hit), noteFor(offset, hit));
            kpiTarget(owner, month, KpiMetric.BOOKINGS, countTarget(bookingsActual, offset, hit), noteFor(offset, hit));
        }

        BigDecimal travellers = reportService.actual(KpiMetric.TRAVELLERS, List.of(now), data);
        kpiTarget(owner, now, KpiMetric.TRAVELLERS,
                travellers.multiply(new BigDecimal("1.3")).setScale(0, RoundingMode.CEILING)
                        .max(travellers.add(BigDecimal.valueOf(3))),
                "Fill the remaining seats on this month's departures.");

        kpiTarget(owner, now, KpiMetric.CANCELLATION_RATE, BigDecimal.TEN,
                "Keep cancellations at or below 10%.");
        kpiTarget(owner, now.minusMonths(1), KpiMetric.CANCELLATION_RATE, BigDecimal.TEN,
                "Keep cancellations at or below 10%.");

        kpiTarget(owner, now, KpiMetric.AVERAGE_BOOKING_VALUE, new BigDecimal("1200"),
                "Upsell longer itineraries and private vehicles.");

        log.info("Seeded {} KPI targets", kpiTargets.count());
    }

    private BigDecimal revenueTarget(BigDecimal actual, int offset, boolean hit) {
        BigDecimal step = new BigDecimal("100");
        if (offset < 0 && hit && actual.compareTo(new BigDecimal("200")) >= 0) {
            return roundTo(actual.multiply(new BigDecimal("0.9")), step, RoundingMode.FLOOR);
        }
        BigDecimal factor = offset < 0 ? new BigDecimal("1.25") : offset == 0 ? new BigDecimal("1.5") : new BigDecimal("1.2");
        BigDecimal floor = offset < 0 ? new BigDecimal("1000") : new BigDecimal("2000");
        return roundTo(actual.multiply(factor), step, RoundingMode.CEILING).max(floor);
    }

    private BigDecimal countTarget(BigDecimal actual, int offset, boolean hit) {
        if (offset < 0 && hit) {
            BigDecimal lower = actual.multiply(new BigDecimal("0.9")).setScale(0, RoundingMode.FLOOR);
            if (lower.signum() > 0) {
                return lower;
            }
        }
        BigDecimal factor = offset < 0 ? new BigDecimal("1.25") : offset == 0 ? new BigDecimal("1.5") : new BigDecimal("1.2");
        BigDecimal minimum = actual.add(BigDecimal.valueOf(offset == 0 ? 2 : 1)).max(BigDecimal.valueOf(2));
        return actual.multiply(factor).setScale(0, RoundingMode.CEILING).max(minimum);
    }

    private BigDecimal roundTo(BigDecimal value, BigDecimal step, RoundingMode mode) {
        return value.divide(step, 0, mode).multiply(step);
    }

    private String noteFor(int offset, boolean hit) {
        if (offset < 0) {
            return hit ? "Set from the sales forecast at the start of the month."
                    : "Stretch target that the month fell short of.";
        }
        return offset == 0 ? "Stretch target for the Gathering season." : "Provisional - review mid-month.";
    }

    private void kpiTarget(User owner, YearMonth month, KpiMetric metric, BigDecimal value, String notes) {
        KpiTarget t = new KpiTarget();
        t.setMetric(metric);
        t.setPeriodMonth(month.atDay(1));
        t.setTargetValue(value.setScale(2, RoundingMode.HALF_UP));
        t.setNotes(notes);
        t.setCreatedBy(owner);
        kpiTargets.save(t);
    }

    /* --------------------------------------------------------------- Users */

    private List<User> seedUsers() {
        List<User> created = new ArrayList<>();
        created.add(user("Kamal Perera", OPS_MANAGER_EMAIL, "+94 77 004 9910", Role.OPERATIONS_MANAGER));
        created.add(user("Sachini Silva", RELATIONS_EMAIL, "+94 71 118 2204", Role.CUSTOMER_RELATIONS_OFFICER));
        created.add(user("Saman Kumara", "saman@ceylontrails.lk", "+94 76 553 0187", Role.SAFARI_VEHICLE_COORDINATOR));
        created.add(user("Nuwan Jayasinghe", "nuwan@ceylontrails.lk", "+94 77 447 6610", Role.FINANCE_RESERVATIONS_EXECUTIVE));
        created.add(user("Ishara Dias", "ishara@ceylontrails.lk", "+94 72 220 7745", Role.FINANCE_ACCOUNTS_OFFICER));
        created.add(user("Sarah Smith", "sarah@example.com", "+44 7700 900321", Role.CUSTOMER));
        created.add(user("John Brown", "john@example.com", "+1 415 555 0142", Role.CUSTOMER));
        User amal = user("Amal Bandara", "amal@example.com", "+94 71 234 5678", Role.CUSTOMER);
        // A Sri Lankan resident, so the demo has a customer who sees prices in rupees.
        amal.setPreferredCurrency(Currency.LKR);
        created.add(users.save(amal));
        return created;
    }

    private User user(String name, String email, String phone, Role role) {
        return users.save(new User(name, email, encoder.encode(DEMO_PASSWORD), phone, role));
    }

    /* --------------------------------------------------------------- Parks */

    private List<Park> seedParks() {
        return List.of(
                parks.save(new Park(FLAGSHIP_PARK, "Southern Province",
                        "Dry-zone scrub, lagoons and granite outcrops on the south-east coast, with one of the "
                                + "highest leopard densities in the world. Block I usually closes for about six "
                                + "weeks from September.",
                        new BigDecimal("30.00"), "Department of Wildlife Conservation")),
                parks.save(new Park("Wilpattu National Park", "North Western Province",
                        "Sri Lanka's largest park: quiet dry forest set around some sixty natural lakes, or "
                                + "villus. Leopards, sloth bears and very few other jeeps.",
                        new BigDecimal("25.00"), "Department of Wildlife Conservation")),
                parks.save(new Park("Minneriya National Park", "North Central Province",
                        "An ancient irrigation tank whose shrinking shoreline draws hundreds of elephants each "
                                + "dry season - the Gathering, from July to October.",
                        new BigDecimal("25.00"), "Department of Wildlife Conservation")),
                parks.save(new Park("Udawalawe National Park", "Sabaragamuwa & Uva Provinces",
                        "Open grassland around a reservoir, where elephant herds can be seen in every season. "
                                + "Home to the Elephant Transit Home for orphaned calves.",
                        new BigDecimal("25.00"), "Department of Wildlife Conservation")));
    }

    /* ------------------------------------------------------------ Packages */

    private List<SafariPackage> seedPackages(List<Park> parkList) {
        Park yala = parkList.get(0);
        Park wilpattu = parkList.get(1);
        Park minneriya = parkList.get(2);
        Park udawalawe = parkList.get(3);

        return List.of(
                packages.save(new SafariPackage("Leopards & Lagoons: Yala and Bundala",
                        "Five days on the edge of Yala Block I with a naturalist tracker on every drive, and a morning among the flamingos and painted storks of the Bundala wetlands.",
                        yala, 5, new BigDecimal("460.00"), 12,
                        "https://images.unsplash.com/photo-1566708627877-859df13ae63e?auto=format&fit=crop&w=1400&q=78",
                        "Tracker-led jeep|Bundala flamingos|Tented camp by Block I")),

                packages.save(new SafariPackage("Yala Dawn & Dusk",
                        "A short, small-group break built around the first jeep through the gate at dawn and the last light of the afternoon, with a sundowner at Kirinda temple rock.",
                        yala, 3, new BigDecimal("240.00"), 8,
                        "https://images.unsplash.com/photo-1743014118271-415197f9b0ef?auto=format&fit=crop&w=1400&q=78",
                        "Small group|First jeep in at dawn|Sundowner at Kirinda")),

                packages.save(new SafariPackage("Wilpattu Villus & Sloth Bears",
                        "Four days of full-day drives around the lakes of the island's largest park, with packed lunches, patient leopard waits and the best sloth bear sightings in the country.",
                        wilpattu, 4, new BigDecimal("380.00"), 10,
                        "https://images.unsplash.com/photo-1779111370141-4cc5d671be58?auto=format&fit=crop&w=1400&q=78",
                        "Full-day drives|Sloth bear season|Lakeside lodge")),

                packages.save(new SafariPackage("Wilpattu & Kalpitiya: Leopards and Dolphins",
                        "Six days pairing Wilpattu's quiet forest with the Kalpitiya lagoon, where spinner dolphins gather in their hundreds on calm winter mornings.",
                        wilpattu, 6, new BigDecimal("490.00"), 14,
                        "https://images.unsplash.com/photo-1616128417743-c3a6992a65e7?auto=format&fit=crop&w=1400&q=78",
                        "Leopard country|Spinner dolphins|Lagoon kayaking")),

                packages.save(new SafariPackage("The Gathering: Minneriya & Kaudulla",
                        "Three days timed to the herds' afternoon walk to the tank, with Kaudulla by jeep when they move on and a sunrise climb at Sigiriya. An excellent first safari for families.",
                        minneriya, 3, new BigDecimal("290.00"), 16,
                        "https://images.unsplash.com/photo-1719807633728-7ff13f7f2b61?auto=format&fit=crop&w=1400&q=78",
                        "Family friendly|Afternoon herd drives|Sigiriya sunrise")),

                packages.save(new SafariPackage("Cultural Triangle Elephant Weekend",
                        "A two-night escape from Colombo: an evening drive among the Minneriya herds, a village lunch beside the Habarana tank and a morning in the ancient city of Polonnaruwa.",
                        minneriya, 2, new BigDecimal("180.00"), 10,
                        "https://images.unsplash.com/photo-1720783963915-13129dccf433?auto=format&fit=crop&w=1400&q=78",
                        "Two nights|Village lunch|Polonnaruwa ruins")),

                packages.save(new SafariPackage("Udawalawe & the South Coast",
                        "Five days from the elephant herds of Udawalawe and the Elephant Transit Home to a dawn voyage for blue whales off Mirissa, ending in a coastal villa.",
                        udawalawe, 5, new BigDecimal("440.00"), 8,
                        "https://images.unsplash.com/photo-1731124655617-e74233ed4a4d?auto=format&fit=crop&w=1400&q=78",
                        "Elephant Transit Home|Blue whales off Mirissa|Coastal villa")));
    }

    /* ------------------------------------------------------------ Bookings */

    private List<Booking> seedBookings(List<User> allUsers, List<SafariPackage> packageList) {
        User sarah = allUsers.get(5);
        User john = allUsers.get(6);
        User amal = allUsers.get(7);
        LocalDate today = LocalDate.now();
        List<Booking> all = new ArrayList<>();

        // Completed trips in the recent past give the reporting dashboard real history.
        all.add(booking(sarah, packageList.get(0), today.minusDays(96), 2, BookingStatus.COMPLETED, true));
        all.add(booking(john, packageList.get(4), today.minusDays(74), 4, BookingStatus.COMPLETED, true));
        all.add(booking(amal, packageList.get(2), today.minusDays(58), 2, BookingStatus.COMPLETED, true));
        all.add(booking(sarah, packageList.get(5), today.minusDays(41), 3, BookingStatus.COMPLETED, true));
        all.add(booking(john, packageList.get(6), today.minusDays(24), 2, BookingStatus.COMPLETED, true));

        // A couple of cancellations so the cancellation-rate metric is not zero.
        Booking cancelledOne = booking(amal, packageList.get(1), today.minusDays(30), 2, BookingStatus.CANCELLED, false);
        cancelledOne.setCancellationReason("Customer rescheduled to next season");
        cancelledOne.setCancelledAt(cancelledOne.getCreatedAt());
        all.add(bookings.save(cancelledOne));

        Booking cancelledTwo = booking(john, packageList.get(3), today.plusDays(52), 3, BookingStatus.CANCELLED, false);
        cancelledTwo.setCancellationReason("Flight connection no longer available");
        cancelledTwo.setCancelledAt(cancelledTwo.getCreatedAt());
        all.add(bookings.save(cancelledTwo));

        // Confirmed future departures - these are what operations needs to crew.
        all.add(booking(sarah, packageList.get(2), today.plusDays(9), 2, BookingStatus.CONFIRMED, true));
        all.add(booking(john, packageList.get(0), today.plusDays(16), 4, BookingStatus.CONFIRMED, true));
        all.add(booking(amal, packageList.get(6), today.plusDays(23), 2, BookingStatus.CONFIRMED, true));
        all.add(booking(sarah, packageList.get(4), today.plusDays(31), 5, BookingStatus.CONFIRMED, true));
        all.add(booking(john, packageList.get(3), today.plusDays(44), 6, BookingStatus.CONFIRMED, true));

        // Pending departures still awaiting payment.
        all.add(booking(amal, packageList.get(5), today.plusDays(12), 2, BookingStatus.PENDING, false));
        all.add(booking(sarah, packageList.get(1), today.plusDays(27), 3, BookingStatus.PENDING, false));
        all.add(booking(john, packageList.get(2), today.plusDays(38), 2, BookingStatus.PENDING, false));

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
                vehicles.save(new Vehicle("WP KA-4471", "Toyota Land Cruiser 79", "4x4 Game Viewer", 7,
                        VehicleStatus.AVAILABLE, today.minusDays(38), "Pop-top roof, fridge, two spare wheels.")),
                vehicles.save(new Vehicle("SP CAB-2210", "Toyota Land Cruiser 78", "Extended Safari", 9,
                        VehicleStatus.AVAILABLE, today.minusDays(15), "Long wheelbase, charging points at every seat.")),
                vehicles.save(new Vehicle("NW PH-0932", "Nissan Patrol Safari", "4x4 Game Viewer", 6,
                        VehicleStatus.AVAILABLE, today.minusDays(61), "Wilpattu-based, fitted with a long-range tank.")),
                vehicles.save(new Vehicle("UVA KX-1188", "Land Rover Defender 130", "Open Game Viewer", 10,
                        VehicleStatus.MAINTENANCE, today.minusDays(4), "Gearbox rebuild - back on the road in two weeks.")),
                vehicles.save(new Vehicle("NC LB-7760", "Toyota Hilux", "Transfer", 5,
                        VehicleStatus.AVAILABLE, today.minusDays(22), "Airport and hotel transfers around the Cultural Triangle.")));
    }

    /* -------------------------------------------------------------- Guides */

    private List<Guide> seedGuides() {
        return List.of(
                guides.save(new Guide("Nimal Perera", "nimal.perera@ceylontrails.lk", "+94 77 145 9081",
                        "LK-G-4471", "English, Sinhala, German", "Leopard tracking", 12, GuideStatus.AVAILABLE)),
                guides.save(new Guide("Dilani Fernando", "dilani.fernando@ceylontrails.lk", "+94 71 662 3401",
                        "LK-G-5518", "English, Sinhala, French", "Birds and endemics", 8, GuideStatus.AVAILABLE)),
                guides.save(new Guide("Ruwan Silva", "ruwan.silva@ceylontrails.lk", "+94 76 559 1174",
                        "LK-G-2209", "English, Sinhala, Tamil", "Elephant behaviour", 15, GuideStatus.AVAILABLE)),
                guides.save(new Guide("Kasun Jayasuriya", "kasun.jayasuriya@ceylontrails.lk", "+94 72 448 2056",
                        "LK-G-1183", "English, Sinhala", "Sloth bears and night ecology", 6, GuideStatus.AVAILABLE)),
                guides.save(new Guide("Priyanthi Rathnayake", "priyanthi.r@ceylontrails.lk", "+94 77 220 6170",
                        "LK-G-7702", "English, Sinhala, Italian", "Marine life and whales", 10, GuideStatus.ON_LEAVE)));
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
        User sarah = people.get(5);
        User john = people.get(6);
        User amal = people.get(7);

        List<Booking> completed = bookingList.stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED).toList();

        // 1. Open, unassigned - shows up as new work on the relations dashboard.
        Complaint open = complaint(amal, completed.isEmpty() ? null : completed.get(2),
                "Vehicle air conditioning failed on day two",
                "The air conditioning in our vehicle stopped working on the second morning and was "
                        + "never fixed. With midday temperatures over 35C this made the drives very "
                        + "uncomfortable for my parents.",
                ComplaintCategory.VEHICLE, ComplaintPriority.HIGH, ComplaintStatus.OPEN, null, 3);
        note(open, amal, CommunicationType.IN_APP_NOTE, CommunicationDirection.INBOUND,
                open.getSubject(), open.getDescription(), amal);

        // 2. In progress, assigned and escalated.
        Complaint inProgress = complaint(john, completed.isEmpty() ? null : completed.get(1),
                "Charged twice for the same booking",
                "My card statement shows two identical charges for the Minneriya trip. I have attached "
                        + "the statement lines to this case. Please refund the duplicate.",
                ComplaintCategory.PAYMENT, ComplaintPriority.CRITICAL, ComplaintStatus.IN_PROGRESS,
                relations, 9);
        inProgress.setEscalatedToDepartment("Finance");
        inProgress.setEscalatedAt(inProgress.getCreatedAt().plusSeconds(7200));
        complaints.save(inProgress);
        note(inProgress, john, CommunicationType.IN_APP_NOTE, CommunicationDirection.INBOUND,
                inProgress.getSubject(), inProgress.getDescription(), john);
        note(inProgress, john, CommunicationType.EMAIL, CommunicationDirection.OUTBOUND,
                "We are looking into this",
                "Thanks for flagging this John. I can see two authorisations against your booking "
                        + "and have asked our finance team to confirm which one settled.", relations);
        note(inProgress, john, CommunicationType.ESCALATION, CommunicationDirection.INTERNAL,
                "Escalated to Finance",
                "Duplicate settlement confirmed by the gateway reference. Finance to raise the refund.",
                relations);

        // 3. Resolved case with a full trail.
        Complaint resolved = complaint(sarah, completed.isEmpty() ? null : completed.get(0),
                "Requested a vegetarian menu but none was provided",
                "I noted a vegetarian requirement when booking but the camp kitchen had not been told.",
                ComplaintCategory.PARK_EXPERIENCE, ComplaintPriority.MEDIUM, ComplaintStatus.RESOLVED,
                relations, 26);
        resolved.setResolutionNotes("Camp catering briefed, and a 10% credit applied to the customer's "
                + "next booking as a goodwill gesture. Customer confirmed they were happy.");
        resolved.setResolvedAt(resolved.getCreatedAt().plusSeconds(60 * 60 * 52));
        complaints.save(resolved);
        note(resolved, sarah, CommunicationType.IN_APP_NOTE, CommunicationDirection.INBOUND,
                resolved.getSubject(), resolved.getDescription(), sarah);
        note(resolved, sarah, CommunicationType.PHONE_CALL, CommunicationDirection.OUTBOUND,
                "Called the customer",
                "Spoke with Sarah for 10 minutes, apologised and offered a goodwill credit.", relations);
        note(resolved, sarah, CommunicationType.STATUS_CHANGE, CommunicationDirection.INTERNAL,
                "Case update", "Status IN_PROGRESS -> RESOLVED. Goodwill credit approved.", opsManager);

        // 4. Unresolved - closed without a fix, useful for reporting.
        Complaint unresolved = complaint(john, null,
                "Lost sunglasses in the vehicle",
                "I think I left a pair of sunglasses in the vehicle on the last afternoon drive.",
                ComplaintCategory.OTHER, ComplaintPriority.LOW, ComplaintStatus.UNRESOLVED, relations, 40);
        unresolved.setResolutionNotes("Vehicle searched and lost-property log checked. Nothing found.");
        unresolved.setResolvedAt(unresolved.getCreatedAt().plusSeconds(60 * 60 * 96));
        complaints.save(unresolved);
        note(unresolved, john, CommunicationType.IN_APP_NOTE, CommunicationDirection.INBOUND,
                unresolved.getSubject(), unresolved.getDescription(), john);
        note(unresolved, john, CommunicationType.EMAIL, CommunicationDirection.OUTBOUND,
                "No luck I am afraid",
                "We searched the vehicle and checked lost property but could not find them. Sorry.",
                relations);

        // 5. A general inquiry still open.
        Complaint inquiry = complaint(sarah, null,
                "Do you offer single-supplement-free departures?",
                "I travel alone and would like to know whether any departures waive the single supplement.",
                ComplaintCategory.GENERAL_INQUIRY, ComplaintPriority.LOW, ComplaintStatus.OPEN, null, 1);
        note(inquiry, sarah, CommunicationType.IN_APP_NOTE, CommunicationDirection.INBOUND,
                inquiry.getSubject(), inquiry.getDescription(), sarah);
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
                        "Ceylon Trails: " + b.getBookingReference() + " confirmed for " + b.getTripDate() + ".",
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
        // Each note lands 90 minutes after the previous one on the same case.
        int earlier = communications.findByComplaintId(complaint.getId()).size();
        entry.setCreatedAt(complaint.getCreatedAt().plusSeconds(60L * 90 * (earlier + 1)));
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
