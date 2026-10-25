package com.foodbridge.repository;

import com.foodbridge.entity.OrganizationProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationProfileRepository extends JpaRepository<OrganizationProfile, Long> {
    Optional<OrganizationProfile> findByUserId(Long userId);
}
