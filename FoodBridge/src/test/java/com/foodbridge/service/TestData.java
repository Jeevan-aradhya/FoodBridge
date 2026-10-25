package com.foodbridge.service;

import com.foodbridge.entity.FoodDonation;
import com.foodbridge.entity.User;
import com.foodbridge.enums.AccountStatus;
import com.foodbridge.enums.DonationStatus;
import com.foodbridge.enums.FoodCategory;
import com.foodbridge.enums.FoodUnit;
import com.foodbridge.enums.Role;
import java.time.LocalDateTime;

/** Small helpers that build entities for unit tests. */
final class TestData {

    private TestData() { }

    static User user(Long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail("user" + id + "@foodbridge.test");
        u.setPasswordHash("hash");
        u.setFullName("User " + id);
        u.setCity("Bengaluru");
        u.setRole(role);
        u.setStatus(AccountStatus.ACTIVE);
        return u;
    }

    static FoodDonation donation(Long id, User donor, int remaining, LocalDateTime deadline) {
        FoodDonation d = new FoodDonation();
        d.setId(id);
        d.setDonor(donor);
        d.setDonorDisplayName("Test Kitchen");
        d.setName("Veg biryani");
        d.setCategory(FoodCategory.COOKED_MEALS);
        d.setQuantity(10);
        d.setRemainingQuantity(remaining);
        d.setUnit(FoodUnit.PORTIONS);
        d.setPreparedAt(deadline.minusHours(5));
        d.setExpiresAt(deadline.plusHours(2));
        d.setPickupDeadline(deadline);
        d.setPickupAddress("12 Test Street");
        d.setCity("Bengaluru");
        d.setStatus(DonationStatus.AVAILABLE);
        d.setCreatedAt(deadline.minusHours(6));
        return d;
    }
}
