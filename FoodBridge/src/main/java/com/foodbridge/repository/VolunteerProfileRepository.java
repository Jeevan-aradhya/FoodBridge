package com.foodbridge.repository;

import com.foodbridge.entity.VolunteerProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VolunteerProfileRepository extends JpaRepository<VolunteerProfile, Long> {
    Optional<VolunteerProfile> findByUserId(Long userId);
}
