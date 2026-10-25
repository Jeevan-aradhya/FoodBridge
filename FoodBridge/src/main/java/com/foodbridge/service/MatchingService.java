package com.foodbridge.service;

import com.foodbridge.dto.MatchSuggestion;
import com.foodbridge.entity.FoodDonation;
import com.foodbridge.entity.OrganizationProfile;
import com.foodbridge.enums.DonationStatus;
import com.foodbridge.enums.FoodCategory;
import com.foodbridge.repository.FoodDonationRepository;
import com.foodbridge.repository.OrganizationProfileRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transparent, rule-based matching (NOT machine learning).
 * Points: preferred category +40, same city +30, pickup closes within 6h +20 / 24h +10, large quantity +10.
 * Every suggestion lists the reasons that produced its score.
 */
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final FoodDonationRepository donations;
    private final OrganizationProfileRepository orgs;
    private final DtoMapper mapper;

    @Transactional(readOnly = true)
    public List<MatchSuggestion> suggest(Long recipientId, int limit) {
        OrganizationProfile org = orgs.findByUserId(recipientId).orElse(null);
        List<FoodCategory> preferred = org == null ? List.of() : UserService.parseCategories(org.getPreferredCategories());
        String city = org == null ? null : org.getCity();

        List<FoodDonation> candidates = donations
                .findTop100ByStatusAndPickupDeadlineAfterAndRemainingQuantityGreaterThanOrderByPickupDeadlineAsc(
                        DonationStatus.AVAILABLE, mapper.now(), 0);
        List<MatchSuggestion> out = new ArrayList<>();
        for (FoodDonation d : candidates) {
            int score = 0;
            List<String> reasons = new ArrayList<>();
            if (preferred.contains(d.getCategory())) {
                score += 40;
                reasons.add("Matches your preferred category: " + d.getCategory().getLabel());
            }
            if (city != null && city.equalsIgnoreCase(d.getCity())) {
                score += 30;
                reasons.add("Pickup is in your city (" + d.getCity() + ")");
            }
            long hours = Duration.between(mapper.now(), d.getPickupDeadline()).toHours();
            if (hours <= 6) {
                score += 20;
                reasons.add("Pickup closes soon (within " + Math.max(hours, 1) + " h), so it needs a quick decision");
            } else if (hours <= 24) {
                score += 10;
                reasons.add("Pickup deadline is within 24 hours");
            }
            if (d.getRemainingQuantity() >= 20) {
                score += 10;
                reasons.add(d.getRemainingQuantity() + " " + d.getUnit().getLabel() + " still available");
            }
            if (score > 0) {
                out.add(new MatchSuggestion(mapper.toDonation(d, false), score, reasons));
            }
        }
        out.sort(Comparator.comparingInt(MatchSuggestion::score).reversed());
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }
}
