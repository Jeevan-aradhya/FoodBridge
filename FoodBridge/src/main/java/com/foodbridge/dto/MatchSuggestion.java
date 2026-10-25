package com.foodbridge.dto;

import java.util.List;

/** A rule-based suggestion: the donation, a score and plain-language reasons. */
public record MatchSuggestion(DonationDto donation, int score, List<String> reasons) {
}
