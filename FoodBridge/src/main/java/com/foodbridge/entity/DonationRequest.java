package com.foodbridge.entity;

import com.foodbridge.enums.RequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A recipient's request for part (or all) of a donation. */
@Entity
@Table(name = "donation_requests", indexes = {
        @Index(name = "idx_request_donation", columnList = "donation_id"),
        @Index(name = "idx_request_recipient", columnList = "recipient_id"),
        @Index(name = "idx_request_status", columnList = "status")})
@Getter
@Setter
@NoArgsConstructor
public class DonationRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donation_id", nullable = false)
    private FoodDonation donation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.PENDING;

    /** Snapshots taken when the request is made (so lists need no extra joins). */
    @Column(nullable = false, length = 150)
    private String recipientDisplayName;

    @Column(nullable = false, length = 300)
    private String dropOffAddress;

    @Column(length = 80)
    private String dropOffCity;

    private LocalDateTime completedAt;
}
