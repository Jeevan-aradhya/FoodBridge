package com.foodbridge.entity;

import com.foodbridge.enums.DonationStatus;
import com.foodbridge.enums.FoodCategory;
import com.foodbridge.enums.FoodUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A listing of surplus food.
 * quantity          = total offered.
 * remainingQuantity = part that is NOT yet reserved by an accepted request.
 */
@Entity
@Table(name = "food_donations", indexes = {
        @Index(name = "idx_donation_status_deadline", columnList = "status, pickup_deadline"),
        @Index(name = "idx_donation_category", columnList = "category"),
        @Index(name = "idx_donation_city", columnList = "city"),
        @Index(name = "idx_donation_donor", columnList = "donor_id")})
@Getter
@Setter
@NoArgsConstructor
public class FoodDonation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_id", nullable = false)
    private User donor;

    /** Snapshot of the business/person name shown on cards. */
    @Column(nullable = false, length = 150)
    private String donorDisplayName;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FoodCategory category;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private int remainingQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FoodUnit unit;

    @Column(nullable = false)
    private LocalDateTime preparedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime pickupDeadline;

    @Column(length = 200)
    private String dietaryInfo;

    @Column(length = 300)
    private String allergenInfo;

    @Column(length = 300)
    private String storageInfo;

    /** Full address: only revealed to people involved in an accepted request. */
    @Column(nullable = false, length = 300)
    private String pickupAddress;

    @Column(nullable = false, length = 80)
    private String city;

    private Double latitude;
    private Double longitude;

    @Column(length = 500)
    private String pickupInstructions;

    /** Uploaded file name, or an http(s) URL. */
    @Column(length = 255)
    private String imagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DonationStatus status = DonationStatus.AVAILABLE;

    @Column(nullable = false)
    private boolean reported;

    @Column(length = 500)
    private String reportReason;

    /** Optimistic-lock safety net in addition to pessimistic locking in RequestService. */
    @Version
    private Long version;
}
