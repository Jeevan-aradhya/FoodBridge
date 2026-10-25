package com.foodbridge.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodbridge.dto.DonationDto;
import com.foodbridge.dto.RequestDto;
import com.foodbridge.dto.RequestForm;
import com.foodbridge.dto.UserDto;
import com.foodbridge.enums.DonationStatus;
import com.foodbridge.enums.Role;
import com.foodbridge.exception.BusinessRuleException;
import com.foodbridge.service.DonationService;
import com.foodbridge.service.RequestService;
import com.foodbridge.service.UserService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the double-booking protection: 10 portions are offered and 5 recipients each ask for 4.
 * All 5 requests are accepted at the same moment from 5 threads. Only 2 can fit (2 x 4 = 8 of 10).
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyIntegrationTest {

    @Autowired UserService users;
    @Autowired DonationService donations;
    @Autowired RequestService requests;

    @Test
    void simultaneousAcceptancesNeverReserveMoreThanTheRemainingQuantity() throws Exception {
        UserDto donor = IntegrationSupport.newUser(users, Role.DONOR);
        DonationDto donation = IntegrationSupport.newDonation(donations, donor.id(), 10);

        List<Long> requestIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UserDto recipient = IntegrationSupport.newUser(users, Role.RECIPIENT);
            RequestForm form = new RequestForm();
            form.setQuantity(4);
            RequestDto r = requests.submit(recipient.id(), donation.id(), form);
            requestIds.add(r.id());
        }

        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch ready = new CountDownLatch(5);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (Long requestId : requestIds) {
            Callable<Boolean> task = () -> {
                ready.countDown();
                go.await();
                try {
                    requests.accept(donor.id(), requestId);
                    return true;
                } catch (BusinessRuleException expected) {
                    return false;      // "Not enough quantity left"
                }
            };
            results.add(pool.submit(task));
        }
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();

        int accepted = 0;
        for (Future<Boolean> f : results) {
            if (f.get(30, TimeUnit.SECONDS)) {
                accepted++;
            }
        }
        pool.shutdown();

        DonationDto after = donations.get(donation.id(), donor.id(), Role.DONOR);
        assertThat(accepted).isEqualTo(2);
        assertThat(after.remainingQuantity()).isEqualTo(2);
        assertThat(after.status()).isEqualTo(DonationStatus.AVAILABLE.name());
    }

    @Test
    void acceptingTheSameRequestTwiceOnlyWorksOnce() throws Exception {
        UserDto donor = IntegrationSupport.newUser(users, Role.DONOR);
        UserDto recipient = IntegrationSupport.newUser(users, Role.RECIPIENT);
        DonationDto donation = IntegrationSupport.newDonation(donations, donor.id(), 10);
        RequestForm form = new RequestForm();
        form.setQuantity(3);
        RequestDto r = requests.submit(recipient.id(), donation.id(), form);

        requests.accept(donor.id(), r.id());
        try {
            requests.accept(donor.id(), r.id());
            org.junit.jupiter.api.Assertions.fail("second accept must fail");
        } catch (BusinessRuleException expected) {
            assertThat(expected.getMessage()).contains("cannot be accepted");
        }
        assertThat(donations.get(donation.id(), donor.id(), Role.DONOR).remainingQuantity()).isEqualTo(7);
    }
}
