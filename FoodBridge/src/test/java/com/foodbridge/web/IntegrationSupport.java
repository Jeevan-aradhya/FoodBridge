package com.foodbridge.web;

import com.foodbridge.dto.DonationDto;
import com.foodbridge.dto.DonationForm;
import com.foodbridge.dto.RegisterForm;
import com.foodbridge.dto.UserDto;
import com.foodbridge.enums.FoodCategory;
import com.foodbridge.enums.FoodUnit;
import com.foodbridge.enums.Role;
import com.foodbridge.service.DonationService;
import com.foodbridge.service.UserService;
import java.time.LocalDateTime;
import java.util.UUID;

/** Creates real users/donations in the in-memory H2 database for integration tests. */
final class IntegrationSupport {

    static final String PASSWORD = "Passw0rd123";

    private IntegrationSupport() { }

    static UserDto newUser(UserService users, Role role) {
        RegisterForm f = new RegisterForm();
        f.setFullName("Test " + role);
        f.setEmail(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@foodbridge.test");
        f.setPassword(PASSWORD);
        f.setCity("Bengaluru");
        f.setRole(role);
        f.setOrganizationName("Org " + UUID.randomUUID().toString().substring(0, 6));
        f.setAddress("1 Test Street, Bengaluru");
        return users.register(f);
    }

    static DonationForm donationForm(int quantity, int deadlineHours) {
        LocalDateTime now = LocalDateTime.now();
        DonationForm f = new DonationForm();
        f.setName("Test curry");
        f.setCategory(FoodCategory.COOKED_MEALS);
        f.setQuantity(quantity);
        f.setUnit(FoodUnit.PORTIONS);
        f.setPreparedAt(now.minusHours(1));
        f.setExpiresAt(now.plusHours(deadlineHours + 2));
        f.setPickupDeadline(now.plusHours(deadlineHours));
        f.setPickupAddress("9 Secret Lane, Bengaluru");
        f.setCity("Bengaluru");
        return f;
    }

    static DonationDto newDonation(DonationService donations, Long donorId, int quantity) {
        return donations.create(donorId, donationForm(quantity, 5), null);
    }
}
