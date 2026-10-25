package com.foodbridge.dto;

import java.util.List;

/** Data for the Chart.js weekly activity chart. */
public record WeeklySeries(List<String> labels, List<Long> donationsListed, List<Long> unitsDelivered) {
}
