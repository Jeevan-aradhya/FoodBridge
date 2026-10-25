package com.foodbridge.repository;

import com.foodbridge.entity.PickupAssignment;
import com.foodbridge.enums.DeliveryStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PickupAssignmentRepository extends JpaRepository<PickupAssignment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PickupAssignment a where a.id = :id")
    Optional<PickupAssignment> findByIdForUpdate(@Param("id") Long id);

    /** Lock order used everywhere: donation -> request -> assignment (prevents deadlocks). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PickupAssignment a where a.request.id = :requestId")
    Optional<PickupAssignment> findByRequestIdForUpdate(@Param("requestId") Long requestId);

    @Query("select a.request.id from PickupAssignment a where a.id = :id")
    Optional<Long> findRequestIdById(@Param("id") Long id);

    @Query("select a.request.donation.id from PickupAssignment a where a.id = :id")
    Optional<Long> findDonationIdById(@Param("id") Long id);

    Optional<PickupAssignment> findByRequestId(Long requestId);

    List<PickupAssignment> findByRequestIdIn(Collection<Long> requestIds);

    @EntityGraph(attributePaths = {"request", "request.donation", "volunteer"})
    Page<PickupAssignment> findByStatus(DeliveryStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"request", "request.donation", "volunteer"})
    Page<PickupAssignment> findByVolunteerId(Long volunteerId, Pageable pageable);

    @EntityGraph(attributePaths = {"request", "request.donation", "volunteer"})
    Page<PickupAssignment> findByRequestDonationDonorId(Long donorId, Pageable pageable);

    @EntityGraph(attributePaths = {"request", "request.donation", "volunteer"})
    Page<PickupAssignment> findByRequestRecipientId(Long recipientId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"request", "request.donation", "volunteer"})
    Page<PickupAssignment> findAll(Pageable pageable);

    boolean existsByRequestDonationIdAndVolunteerId(Long donationId, Long volunteerId);

    long countByStatus(DeliveryStatus status);

    long countByVolunteerIdAndStatus(Long volunteerId, DeliveryStatus status);

    long countByVolunteerIdAndStatusIn(Long volunteerId, Collection<DeliveryStatus> statuses);
}
