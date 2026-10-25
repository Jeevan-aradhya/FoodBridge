package com.foodbridge.service;

import com.foodbridge.dto.DeliveryDto;
import com.foodbridge.dto.DonationDto;
import com.foodbridge.dto.RequestDto;
import com.foodbridge.dto.UserDto;
import com.foodbridge.entity.DonationRequest;
import com.foodbridge.entity.FoodDonation;
import com.foodbridge.entity.PickupAssignment;
import com.foodbridge.entity.User;
import com.foodbridge.enums.DonationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Converts entities to DTOs so that JPA objects never leave the service layer. */
@Component
@RequiredArgsConstructor
public class DtoMapper {

    private final Clock clock;

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** A donation can be requested only while AVAILABLE, not past its deadline, with quantity left. */
    public boolean isOpen(FoodDonation d) {
        return d.getStatus() == DonationStatus.AVAILABLE
                && d.getPickupDeadline().isAfter(now())
                && d.getRemainingQuantity() > 0;
    }

    public DonationDto toDonation(FoodDonation d, boolean revealAddress) {
        long minutes = Duration.between(now(), d.getPickupDeadline()).toMinutes();
        boolean open = isOpen(d);
        String urgency;
        if (minutes <= 0) {
            urgency = "EXPIRED";
        } else if (minutes <= 120) {
            urgency = "URGENT";
        } else if (minutes <= 24 * 60) {
            urgency = "SOON";
        } else {
            urgency = "OK";
        }
        // Expired listings are shown as EXPIRED even if the scheduler has not run yet.
        String status = d.getStatus().name();
        if (d.getStatus() == DonationStatus.AVAILABLE && minutes <= 0) {
            status = DonationStatus.EXPIRED.name();
        }
        return new DonationDto(d.getId(), d.getName(), d.getCategory().getLabel(), d.getCategory().name(),
                d.getDescription(), d.getQuantity(), d.getRemainingQuantity(), d.getUnit().getLabel(),
                d.getPreparedAt(), d.getExpiresAt(), d.getPickupDeadline(),
                d.getDietaryInfo(), d.getAllergenInfo(), d.getStorageInfo(),
                d.getCity(),
                revealAddress ? d.getPickupAddress() : null,
                revealAddress ? d.getPickupInstructions() : null,
                revealAddress ? d.getLatitude() : null,
                revealAddress ? d.getLongitude() : null,
                imageUrl(d), d.getDonor().getId(), d.getDonorDisplayName(),
                status, open, Math.max(minutes, 0), urgency,
                d.isReported(), d.getReportReason(), d.getCreatedAt());
    }

    public String imageUrl(FoodDonation d) {
        String p = d.getImagePath();
        if (p == null || p.isBlank()) {
            return "/images/placeholders/" + d.getCategory().name().toLowerCase() + ".svg";
        }
        return p.startsWith("http") ? p : "/uploads/" + p;
    }

    public RequestDto toRequest(DonationRequest r, PickupAssignment a) {
        FoodDonation d = r.getDonation();
        return new RequestDto(r.getId(), d.getId(), d.getName(), d.getCategory().name(),
                r.getQuantity(), d.getUnit().getLabel(), r.getStatus().name(), r.getMessage(),
                r.getRecipient().getId(), r.getRecipientDisplayName(),
                d.getDonor().getId(), d.getDonorDisplayName(),
                d.getCity(), r.getDropOffAddress(), r.getDropOffCity(),
                a == null ? null : a.getId(), a == null ? null : a.getStatus().name(),
                r.getCreatedAt(), r.getCompletedAt());
    }

    /** @param reveal true when the viewer may see street addresses and instructions. */
    public DeliveryDto toDelivery(PickupAssignment a, boolean reveal) {
        DonationRequest r = a.getRequest();
        FoodDonation d = r.getDonation();
        User v = a.getVolunteer();
        return new DeliveryDto(a.getId(), r.getId(), d.getId(), d.getName(), r.getQuantity(), d.getUnit().getLabel(),
                a.getStatus().name(), v == null ? null : v.getId(), v == null ? null : v.getFullName(),
                d.getDonorDisplayName(), r.getRecipientDisplayName(),
                d.getCity(), r.getDropOffCity(),
                reveal ? d.getPickupAddress() : null,
                reveal ? d.getPickupInstructions() : null,
                reveal ? r.getDropOffAddress() : null,
                d.getPickupDeadline(), a.getCreatedAt(), a.getAssignedAt(), a.getPickedUpAt(), a.getDeliveredAt());
    }

    public UserDto toUser(User u) {
        return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getCity(),
                u.getRole().name(), u.getStatus().name(), u.getCreatedAt());
    }
}
