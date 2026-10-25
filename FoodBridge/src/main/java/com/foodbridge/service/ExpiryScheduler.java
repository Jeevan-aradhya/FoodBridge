package com.foodbridge.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 5 minutes: listings past their pickup deadline become EXPIRED. */
@Component
@RequiredArgsConstructor
public class ExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiryScheduler.class);

    private final DonationService donationService;

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void expireOverdueListings() {
        try {
            int n = donationService.expireOverdue();
            if (n > 0) {
                log.info("Marked {} donation(s) as expired", n);
            }
        } catch (Exception ex) {
            log.warn("Expiry job failed, will retry: {}", ex.getMessage());
        }
    }
}
