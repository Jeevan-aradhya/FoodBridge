package com.foodbridge.dto;

/** Platform statistics calculated live from database records. Nothing here is estimated. */
public record ImpactStats(long totalDonations, long unitsListed, long unitsDelivered, long completedPickups,
                          long activeDonors, long participatingOrganizations, long volunteers) {
}
