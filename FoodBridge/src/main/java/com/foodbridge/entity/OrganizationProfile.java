package com.foodbridge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Business (donor) or organization (NGO/recipient) details. One per user. */
@Entity
@Table(name = "organization_profiles")
@Getter
@Setter
@NoArgsConstructor
public class OrganizationProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 150)
    private String organizationName;

    @Column(length = 60)
    private String organizationType;

    @Column(length = 300)
    private String address;

    @Column(length = 80)
    private String city;

    @Column(length = 60)
    private String registrationNumber;

    /** Comma separated FoodCategory names, used by the matching engine. */
    @Column(length = 300)
    private String preferredCategories;

    @Column(length = 500)
    private String description;
}
