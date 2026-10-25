package com.foodbridge.service;

import com.foodbridge.dto.DonationFilter;
import com.foodbridge.entity.FoodDonation;
import com.foodbridge.enums.DonationStatus;
import com.foodbridge.enums.FoodCategory;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Builds the dynamic WHERE clause for the donation search (all values are bound parameters: no SQL injection). */
public final class DonationSpecs {

    private DonationSpecs() { }

    public static Specification<FoodDonation> search(DonationFilter f, LocalDateTime now) {
        return (root, query, cb) -> {
            // Load the donor in the same query (avoids N+1). Skip for the COUNT query used by paging.
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("donor", JoinType.LEFT);
            }
            List<Predicate> p = new ArrayList<>();

            // Hidden listings never appear in public search.
            p.add(root.get("status").in(DonationStatus.AVAILABLE, DonationStatus.RESERVED,
                    DonationStatus.COMPLETED, DonationStatus.EXPIRED));

            if (f.getQ() != null && !f.getQ().isBlank()) {
                String like = "%" + f.getQ().trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            if (f.getCategory() != null && !f.getCategory().isBlank()) {
                try {
                    p.add(cb.equal(root.get("category"), FoodCategory.valueOf(f.getCategory())));
                } catch (IllegalArgumentException ignored) {
                    // unknown category value: ignore the filter
                }
            }
            if (f.getCity() != null && !f.getCity().isBlank()) {
                p.add(cb.like(cb.lower(root.get("city")), "%" + f.getCity().trim().toLowerCase() + "%"));
            }
            if (f.getMinQuantity() != null && f.getMinQuantity() > 0) {
                p.add(cb.greaterThanOrEqualTo(root.get("remainingQuantity"), f.getMinQuantity()));
            }
            if (f.getWithinHours() != null && f.getWithinHours() > 0) {
                p.add(cb.lessThanOrEqualTo(root.get("pickupDeadline"), now.plusHours(f.getWithinHours())));
            }
            if (!"ALL".equalsIgnoreCase(f.getAvailability())) {
                p.add(cb.equal(root.get("status"), DonationStatus.AVAILABLE));
                p.add(cb.greaterThan(root.get("pickupDeadline"), now));
                p.add(cb.greaterThan(root.get("remainingQuantity"), 0));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
