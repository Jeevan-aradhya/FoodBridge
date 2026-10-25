package com.foodbridge.repository;

import com.foodbridge.entity.DonationRequest;
import com.foodbridge.enums.RequestStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
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

public interface DonationRequestRepository extends JpaRepository<DonationRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DonationRequest r where r.id = :id")
    Optional<DonationRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("select r.donation.id from DonationRequest r where r.id = :id")
    Optional<Long> findDonationIdById(@Param("id") Long id);

    // EntityGraph = load the related rows in the same query (avoids N+1 selects).
    @EntityGraph(attributePaths = {"donation", "recipient"})
    Page<DonationRequest> findByRecipientId(Long recipientId, Pageable pageable);

    @EntityGraph(attributePaths = {"donation", "recipient"})
    Page<DonationRequest> findByDonationDonorId(Long donorId, Pageable pageable);

    @EntityGraph(attributePaths = {"donation", "recipient"})
    Page<DonationRequest> findByDonationDonorIdAndStatus(Long donorId, RequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"donation", "recipient"})
    Page<DonationRequest> findByStatus(RequestStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"donation", "recipient"})
    Page<DonationRequest> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"donation", "recipient"})
    List<DonationRequest> findByDonationIdAndStatus(Long donationId, RequestStatus status);

    boolean existsByDonationIdAndRecipientIdAndStatus(Long donationId, Long recipientId, RequestStatus status);

    boolean existsByDonationIdAndStatus(Long donationId, RequestStatus status);

    boolean existsByDonationIdAndRecipientIdAndStatusIn(Long donationId, Long recipientId, Collection<RequestStatus> statuses);

    long countByStatus(RequestStatus status);

    long countByRecipientIdAndStatus(Long recipientId, RequestStatus status);

    long countByDonationDonorIdAndStatus(Long donorId, RequestStatus status);

    @Query("select coalesce(sum(r.quantity), 0) from DonationRequest r where r.status = :status")
    long sumQuantityByStatus(@Param("status") RequestStatus status);

    @Query("select coalesce(sum(r.quantity), 0) from DonationRequest r where r.donation.donor.id = :donorId and r.status = :status")
    long sumQuantityByDonorAndStatus(@Param("donorId") Long donorId, @Param("status") RequestStatus status);

    @Query("select coalesce(sum(r.quantity), 0) from DonationRequest r where r.recipient.id = :recipientId and r.status = :status")
    long sumQuantityByRecipientAndStatus(@Param("recipientId") Long recipientId, @Param("status") RequestStatus status);

    List<DonationRequest> findByStatusAndCompletedAtAfter(RequestStatus status, LocalDateTime since);

    List<DonationRequest> findByDonationDonorIdAndStatusAndCompletedAtAfter(Long donorId, RequestStatus status, LocalDateTime since);

    @Query("select count(distinct r.recipient.id) from DonationRequest r")
    long countDistinctRecipients();
}
