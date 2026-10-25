package com.foodbridge.config;

import com.foodbridge.dto.DonationForm;
import com.foodbridge.dto.RegisterForm;
import com.foodbridge.dto.RequestDto;
import com.foodbridge.dto.RequestForm;
import com.foodbridge.dto.UserDto;
import com.foodbridge.entity.DonationRequest;
import com.foodbridge.entity.FoodDonation;
import com.foodbridge.entity.PickupAssignment;
import com.foodbridge.enums.DeliveryStatus;
import com.foodbridge.enums.DonationStatus;
import com.foodbridge.enums.FoodCategory;
import com.foodbridge.enums.FoodUnit;
import com.foodbridge.enums.RequestStatus;
import com.foodbridge.enums.Role;
import com.foodbridge.repository.DonationRequestRepository;
import com.foodbridge.repository.FoodDonationRepository;
import com.foodbridge.repository.OrganizationProfileRepository;
import com.foodbridge.repository.PickupAssignmentRepository;
import com.foodbridge.repository.UserRepository;
import com.foodbridge.service.DeliveryService;
import com.foodbridge.service.DonationService;
import com.foodbridge.service.RequestService;
import com.foodbridge.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * LOCAL DEMO DATA ONLY. Runs only when foodbridge.seed.enabled=true (the "dev" profile or SEED_ENABLED=true)
 * and only when the database has no users yet. All names, addresses and e-mails are fictional.
 * Never enable this on a public server: the demo accounts share one well-known password.
 */
@Component
@ConditionalOnProperty(name = "foodbridge.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final UserService userService;
    private final DonationService donationService;
    private final RequestService requestService;
    private final DeliveryService deliveryService;
    private final FoodDonationRepository donationRepo;
    private final DonationRequestRepository requestRepo;
    private final PickupAssignmentRepository assignmentRepo;
    private final OrganizationProfileRepository orgRepo;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Value("${foodbridge.seed.demo-password}")
    private String demoPassword;

    public DataSeeder(UserRepository users, UserService userService, DonationService donationService,
                      RequestService requestService, DeliveryService deliveryService,
                      FoodDonationRepository donationRepo, DonationRequestRepository requestRepo,
                      PickupAssignmentRepository assignmentRepo, OrganizationProfileRepository orgRepo,
                      JdbcTemplate jdbc, Clock clock) {
        this.users = users;
        this.userService = userService;
        this.donationService = donationService;
        this.requestService = requestService;
        this.deliveryService = deliveryService;
        this.donationRepo = donationRepo;
        this.requestRepo = requestRepo;
        this.assignmentRepo = assignmentRepo;
        this.orgRepo = orgRepo;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        log.warn("Seeding DEMO data (fictional). Do not enable this in production.");

        userService.createAdmin("admin@foodbridge.test", "Demo Admin", demoPassword);

        Long d1 = donor("donor1@foodbridge.test", "Anita Rao", "Green Leaf Kitchen", "Restaurant", "12 Church Street, Bengaluru", "Bengaluru");
        Long d2 = donor("donor2@foodbridge.test", "Imran Sheikh", "Sunrise Bakery", "Bakery", "45 Indiranagar 100 Feet Road, Bengaluru", "Bengaluru");
        Long d3 = donor("donor3@foodbridge.test", "Kavita Menon", "FreshMart Supermarket", "Supermarket", "8 Koramangala 5th Block, Bengaluru", "Bengaluru");
        Long n1 = recipient("ngo1@foodbridge.test", "Suresh Iyer", "Hope Meals Foundation", "NGO", "22 Shivajinagar Main Road, Bengaluru", "Bengaluru");
        Long n2 = recipient("ngo2@foodbridge.test", "Lakshmi Devi", "Asha Shelter Trust", "Shelter", "7 Jayanagar 4th Block, Bengaluru", "Bengaluru");
        Long v1 = volunteer("volunteer1@foodbridge.test", "Ravi Kumar", "Bike");
        Long v2 = volunteer("volunteer2@foodbridge.test", "Meera Nair", "Car");

        // ---- live listings (deadlines relative to "now" so the demo always looks fresh)
        Long biryani = donation(d1, "Veg biryani and raita", FoodCategory.COOKED_MEALS, "Freshly cooked from today's lunch buffet. Packed in clean containers.",
                60, FoodUnit.PORTIONS, -2, 5, 4, "Contains dairy (raita)", "Vegetarian", "Keep covered and consume within 4 hours", "12 Church Street, Bengaluru", "Bengaluru", "Use the staff entrance at the back.");
        Long dal = donation(d1, "Dal tadka and jeera rice", FoodCategory.COOKED_MEALS, "Surplus from a corporate event.",
                40, FoodUnit.PORTIONS, -1, 6, 3, "None declared", "Vegetarian", "Keep hot or refrigerate", "12 Church Street, Bengaluru", "Bengaluru", "Ask for the kitchen manager.");
        Long bread = donation(d2, "Assorted bread loaves", FoodCategory.BAKERY, "Day-old sandwich bread and buns, still soft.",
                35, FoodUnit.ITEMS, -10, 20, 10, "Contains gluten, may contain soy", "Vegetarian", "Store in a cool dry place", "45 Indiranagar 100 Feet Road, Bengaluru", "Bengaluru", "Counter 2, evenings only.");
        Long pastries = donation(d2, "Veg puffs and croissants", FoodCategory.BAKERY, "Unsold pastries from the afternoon batch.",
                24, FoodUnit.ITEMS, -6, 8, 2, "Contains gluten, butter", "Vegetarian", "Eat the same day", "45 Indiranagar 100 Feet Road, Bengaluru", "Bengaluru", "Pickup from the side door.");
        Long veg = donation(d3, "Mixed vegetables crate", FoodCategory.FRUITS_VEGETABLES, "Tomatoes, onions, carrots and beans. Slight cosmetic blemishes.",
                25, FoodUnit.KG, -5, 48, 30, "None", "Vegan", "Refrigerate or keep in a cool place", "8 Koramangala 5th Block, Bengaluru", "Bengaluru", "Loading bay B.");
        Long fruit = donation(d3, "Bananas and apples", FoodCategory.FRUITS_VEGETABLES, "Ripe fruit that must be sold or used soon.",
                18, FoodUnit.KG, -8, 30, 12, "None", "Vegan", "Keep at room temperature", "8 Koramangala 5th Block, Bengaluru", "Bengaluru", "Loading bay B.");
        Long milk = donation(d3, "Packaged milk (500 ml)", FoodCategory.DAIRY, "Sealed packets close to the best-before date.",
                48, FoodUnit.PACKS, -20, 30, 18, "Contains milk", "Vegetarian", "Keep refrigerated at 4 degrees Celsius", "8 Koramangala 5th Block, Bengaluru", "Bengaluru", "Cold storage, ask for Mr. Nair.");
        Long biscuits = donation(d3, "Biscuit and snack packs", FoodCategory.PACKAGED, "Sealed packs, labels intact, short dated.",
                80, FoodUnit.PACKS, -48, 120, 72, "Contains gluten, may contain nuts", "Vegetarian", "Store in a dry place", "8 Koramangala 5th Block, Bengaluru", "Bengaluru", "Warehouse office.");
        donation(d1, "Fruit juice cartons", FoodCategory.BEVERAGES, "Sealed 200 ml cartons from a cancelled event.",
                120, FoodUnit.PACKS, -24, 96, 48, "None declared", "Vegan", "Store in a cool place", "12 Church Street, Bengaluru", "Bengaluru", "Back entrance.");
        donation(d2, "Whole wheat rolls", FoodCategory.BAKERY, "Baked this morning; surplus after a bulk order was cancelled.",
                50, FoodUnit.ITEMS, -4, 12, 7, "Contains gluten", "Vegan", "Keep dry", "45 Indiranagar 100 Feet Road, Bengaluru", "Bengaluru", "Counter 1.");
        Long soup = donation(d1, "Tomato soup", FoodCategory.COOKED_MEALS, "Large batch from a catering order.",
                30, FoodUnit.LITRES, -1, 4, 3, "May contain dairy", "Vegetarian", "Keep hot (above 60 degrees Celsius)", "12 Church Street, Bengaluru", "Bengaluru", "Bring your own containers if possible.");
        Long idli = donation(d1, "Idli and sambar", FoodCategory.COOKED_MEALS, "Breakfast surplus from a hotel buffet.",
                45, FoodUnit.PORTIONS, -1, 4, 2, "None declared", "Vegetarian", "Serve warm", "12 Church Street, Bengaluru", "Bengaluru", "Kitchen door.");

        // ---- requests in different valid states (all created through the real services)
        // Pending
        requestService.submit(n1, bread, req(15, "We run a morning feeding programme."));
        requestService.submit(n2, soup, req(20, "For evening meals at our shelter."));

        // Accepted + open delivery task
        RequestDto accepted = requestService.submit(n1, dal, req(25, "Dinner for 25 people."));
        requestService.accept(d1, accepted.id());

        // Accepted + assigned to a volunteer
        RequestDto assigned = requestService.submit(n2, veg, req(10, "For our community kitchen."));
        requestService.accept(d3, assigned.id());
        deliveryService.accept(v1, assignmentOf(assigned.id()));

        // In transit
        RequestDto transit = requestService.submit(n1, milk, req(24, "Milk for children's breakfast."));
        requestService.accept(d3, transit.id());
        Long transitTask = assignmentOf(transit.id());
        deliveryService.accept(v2, transitTask);
        deliveryService.advance(v2, transitTask, DeliveryStatus.PICKED_UP);
        deliveryService.advance(v2, transitTask, DeliveryStatus.IN_TRANSIT);

        // Delivered (completed)
        RequestDto delivered = requestService.submit(n2, pastries, req(24, "Snacks for evening class."));
        requestService.accept(d2, delivered.id());
        Long deliveredTask = assignmentOf(delivered.id());
        deliveryService.accept(v1, deliveredTask);
        deliveryService.advance(v1, deliveredTask, DeliveryStatus.PICKED_UP);
        deliveryService.advance(v1, deliveredTask, DeliveryStatus.IN_TRANSIT);
        deliveryService.advance(v1, deliveredTask, DeliveryStatus.DELIVERED);

        // Rejected and cancelled
        RequestDto rejected = requestService.submit(n1, fruit, req(10, "Fruit for the kids."));
        requestService.reject(d3, rejected.id());
        RequestDto cancelled = requestService.submit(n2, biscuits, req(30, "Snack packs."));
        requestService.cancel(n2, cancelled.id());
        requestService.submit(n2, biryani, req(30, "Lunch for residents."));
        requestService.submit(n1, idli, req(25, "Breakfast programme."));

        // ---- one expired listing (created directly because the service refuses past deadlines)
        history(d2, n1, "Rye bread (expired listing)", FoodCategory.BAKERY, 12, DonationStatus.EXPIRED, false, 3);

        // ---- historical completed deliveries so the weekly chart has real rows to draw
        history(d1, n1, "Paneer curry and rotis", FoodCategory.COOKED_MEALS, 40, DonationStatus.COMPLETED, true, 1);
        history(d3, n2, "Packaged pulses", FoodCategory.PACKAGED, 60, DonationStatus.COMPLETED, true, 2);
        history(d2, n2, "Sandwich loaves", FoodCategory.BAKERY, 30, DonationStatus.COMPLETED, true, 3);
        history(d1, n1, "Vegetable pulao", FoodCategory.COOKED_MEALS, 55, DonationStatus.COMPLETED, true, 4);
        history(d3, n1, "Seasonal fruit boxes", FoodCategory.FRUITS_VEGETABLES, 35, DonationStatus.COMPLETED, true, 5);
        history(d2, n2, "Buns and rolls", FoodCategory.BAKERY, 45, DonationStatus.COMPLETED, true, 6);

        log.info("Demo data ready. Login with e.g. donor1@foodbridge.test / (value of SEED_DEMO_PASSWORD)");
    }

    // ------------------------------------------------------------------ helpers

    private Long donor(String email, String name, String org, String type, String address, String city) {
        return register(email, name, Role.DONOR, org, type, address, city);
    }

    private Long recipient(String email, String name, String org, String type, String address, String city) {
        return register(email, name, Role.RECIPIENT, org, type, address, city);
    }

    private Long volunteer(String email, String name, String vehicle) {
        RegisterForm f = new RegisterForm();
        f.setFullName(name);
        f.setEmail(email);
        f.setPassword(demoPassword);
        f.setCity("Bengaluru");
        f.setRole(Role.VOLUNTEER);
        f.setVehicleType(vehicle);
        f.setServiceArea("Bengaluru Central");
        return userService.register(f).id();
    }

    private Long register(String email, String name, Role role, String org, String type, String address, String city) {
        RegisterForm f = new RegisterForm();
        f.setFullName(name);
        f.setEmail(email);
        f.setPassword(demoPassword);
        f.setCity(city);
        f.setRole(role);
        f.setOrganizationName(org);
        f.setOrganizationType(type);
        f.setAddress(address);
        UserDto u = userService.register(f);
        return u.id();
    }

    private Long donation(Long donorId, String name, FoodCategory cat, String desc, int qty, FoodUnit unit,
                          int preparedHours, int expiresHours, int deadlineHours, String allergens, String dietary,
                          String storage, String address, String city, String instructions) {
        LocalDateTime now = LocalDateTime.now(clock);
        DonationForm f = new DonationForm();
        f.setName(name);
        f.setCategory(cat);
        f.setDescription(desc);
        f.setQuantity(qty);
        f.setUnit(unit);
        f.setPreparedAt(now.plusHours(preparedHours));
        f.setExpiresAt(now.plusHours(expiresHours));
        f.setPickupDeadline(now.plusHours(deadlineHours));
        f.setAllergenInfo(allergens);
        f.setDietaryInfo(dietary);
        f.setStorageInfo(storage);
        f.setPickupAddress(address);
        f.setCity(city);
        f.setPickupInstructions(instructions);
        return donationService.create(donorId, f, null).id();
    }

    private static RequestForm req(int qty, String msg) {
        RequestForm f = new RequestForm();
        f.setQuantity(qty);
        f.setMessage(msg);
        return f;
    }

    private Long assignmentOf(Long requestId) {
        return assignmentRepo.findByRequestId(requestId).orElseThrow().getId();
    }

    /** Past records written directly, with created_at moved back so that the weekly chart shows history. */
    void history(Long donorId, Long recipientId, String name, FoodCategory cat, int qty, DonationStatus status,
                 boolean delivered, int weeksAgo) {
        LocalDateTime when = LocalDateTime.now(clock).minusWeeks(weeksAgo);
        FoodDonation d = new FoodDonation();
        d.setDonor(users.getReferenceById(donorId));
        d.setDonorDisplayName(orgRepo.findByUserId(donorId).orElseThrow().getOrganizationName());
        d.setName(name);
        d.setCategory(cat);
        d.setDescription("Historical demo record.");
        d.setQuantity(qty);
        d.setRemainingQuantity(0);
        d.setUnit(FoodUnit.PORTIONS);
        d.setPreparedAt(when.minusHours(3));
        d.setExpiresAt(when.plusHours(8));
        d.setPickupDeadline(when.plusHours(5));
        d.setPickupAddress("Demo address, Bengaluru");
        d.setCity("Bengaluru");
        d.setStatus(status);
        if (!delivered) {
            d.setRemainingQuantity(qty);
        }
        donationRepo.save(d);
        jdbc.update("update food_donations set created_at = ? where id = ?", when, d.getId());
        if (delivered) {
            DonationRequest r = new DonationRequest();
            r.setDonation(d);
            r.setRecipient(users.getReferenceById(recipientId));
            r.setQuantity(qty);
            r.setStatus(RequestStatus.COMPLETED);
            r.setRecipientDisplayName(orgRepo.findByUserId(recipientId).orElseThrow().getOrganizationName());
            r.setDropOffAddress("Demo address, Bengaluru");
            r.setDropOffCity("Bengaluru");
            r.setCompletedAt(when.plusHours(4));
            requestRepo.save(r);
            PickupAssignment a = new PickupAssignment();
            a.setRequest(r);
            a.setStatus(DeliveryStatus.DELIVERED);
            a.setDeliveredAt(when.plusHours(4));
            assignmentRepo.save(a);
        }
    }
}
