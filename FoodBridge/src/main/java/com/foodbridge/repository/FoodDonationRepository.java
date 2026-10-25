package com.foodbridge.repository;

import com.foodbridge.entity.FoodDonation;
import com.foodbridge.enums.DonationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodDonationRepository extends JpaRepository<FoodDonation, Long>, JpaSpecificationExecutor<FoodDonation> {

    /** SELECT ... FOR UPDATE: other transactions wait until this one finishes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from FoodDonation d where d.id = :id")
    Optional<FoodDonation> findByIdForUpdate(@Param("id") Long id);

    Page<FoodDonation> findByDonorId(Long donorId, Pageable pageable);

    List<FoodDonation> findByDonorIdOrderByCreatedAtDesc(Long donorId);

    List<FoodDonation> findByStatusAndPickupDeadlineBefore(DonationStatus status, LocalDateTime time);

    List<FoodDonation> findTop4ByStatusAndPickupDeadlineAfterAndRemainingQuantityGreaterThanOrderByPickupDeadlineAsc(
            DonationStatus status, LocalDateTime time, int minRemaining);

    List<FoodDonation> findTop100ByStatusAndPickupDeadlineAfterAndRemainingQuantityGreaterThanOrderByPickupDeadlineAsc(
            DonationStatus status, LocalDateTime time, int minRemaining);

    long countByStatus(DonationStatus status);

    long countByDonorId(Long donorId);

    long countByDonorIdAndStatus(Long donorId, DonationStatus status);

    Page<FoodDonation> findByReportedTrue(Pageable pageable);

    List<FoodDonation> findByCreatedAtAfter(LocalDateTime since);

    List<FoodDonation> findByDonorIdAndCreatedAtAfter(Long donorId, LocalDateTime since);

    @Query("select count(distinct d.donor.id) from FoodDonation d")
    long countDistinctDonors();

    @Query("select coalesce(sum(d.quantity), 0) from FoodDonation d where d.status <> com.foodbridge.enums.DonationStatus.WITHDRAWN")
    long sumQuantityListed();
}
