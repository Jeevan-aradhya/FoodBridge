package com.foodbridge.service;

import com.foodbridge.dto.RequestDto;
import com.foodbridge.dto.RequestForm;
import com.foodbridge.entity.DonationRequest;
import com.foodbridge.entity.FoodDonation;
import com.foodbridge.entity.OrganizationProfile;
import com.foodbridge.entity.PickupAssignment;
import com.foodbridge.entity.User;
import com.foodbridge.enums.AccountStatus;
import com.foodbridge.enums.DeliveryStatus;
import com.foodbridge.enums.DonationStatus;
import com.foodbridge.enums.RequestStatus;
import com.foodbridge.enums.Role;
import com.foodbridge.exception.BusinessRuleException;
import com.foodbridge.exception.ResourceNotFoundException;
import com.foodbridge.repository.DonationRequestRepository;
import com.foodbridge.repository.FoodDonationRepository;
import com.foodbridge.repository.OrganizationProfileRepository;
import com.foodbridge.repository.PickupAssignmentRepository;
import com.foodbridge.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Request lifecycle and quantity reservation.
 *
 * HOW DOUBLE-BOOKING IS PREVENTED
 * Quantity is only reserved when a donor ACCEPTS a request. accept() first locks the donation row and
 * then the request row with SELECT ... FOR UPDATE (pessimistic lock). Two donors/tabs accepting at the
 * same time are therefore processed one after the other, and the second one sees the reduced
 * remainingQuantity. (FoodDonation also has an @Version column as a second safety net.)
 *
 * LOCK ORDER (always the same, so transactions can not deadlock): donation -> request -> assignment.
 */
@Service
@RequiredArgsConstructor
public class RequestService {

    private final DonationRequestRepository requests;
    private final FoodDonationRepository donations;
    private final PickupAssignmentRepository assignments;
    private final UserRepository users;
    private final OrganizationProfileRepository orgs;
    private final NotificationService notifications;
    private final AuditService audit;
    private final DtoMapper mapper;

    /** A recipient asks for some quantity. This does NOT reserve anything yet. */
    @Transactional
    public RequestDto submit(Long recipientId, Long donationId, RequestForm f) {
        User recipient = users.findById(recipientId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (recipient.getRole() != Role.RECIPIENT) {
            throw new AccessDeniedException("Only NGO / recipient accounts can request food.");
        }
        OrganizationProfile org = orgs.findByUserId(recipientId)
                .orElseThrow(() -> new BusinessRuleException("Complete your organization profile first."));
        if (org.getAddress() == null || org.getAddress().isBlank()) {
            throw new BusinessRuleException("Add your organization's address in your profile before requesting food.");
        }
        FoodDonation d = donations.findById(donationId).orElseThrow(() -> new ResourceNotFoundException("Donation not found"));
        if (!mapper.isOpen(d)) {
            throw new BusinessRuleException("This donation is no longer available.");
        }
        if (f.getQuantity() > d.getRemainingQuantity()) {
            throw new BusinessRuleException("Only " + d.getRemainingQuantity() + " " + d.getUnit().getLabel()
                    + " remain. Request a smaller quantity.");
        }
        if (requests.existsByDonationIdAndRecipientIdAndStatus(donationId, recipientId, RequestStatus.PENDING)) {
            throw new BusinessRuleException("You already have a pending request for this donation.");
        }
        DonationRequest r = new DonationRequest();
        r.setDonation(d);
        r.setRecipient(recipient);
        r.setQuantity(f.getQuantity());
        r.setMessage(f.getMessage());
        r.setStatus(RequestStatus.PENDING);
        r.setRecipientDisplayName(org.getOrganizationName());
        r.setDropOffAddress(org.getAddress());
        r.setDropOffCity(org.getCity());
        requests.save(r);

        notifications.notify(d.getDonor(), "New request for " + d.getName(),
                org.getOrganizationName() + " requested " + r.getQuantity() + " " + d.getUnit().getLabel() + ".",
                "/donor/requests");
        audit.log(recipient, "REQUEST_SUBMITTED", "DonationRequest", r.getId(), "Donation " + donationId + ", qty " + r.getQuantity());
        return mapper.toRequest(r, null);
    }

    /** The donor accepts: this is the moment quantity is reserved and a delivery task is created. */
    @Transactional
    public RequestDto accept(Long donorId, Long requestId) {
        Long donationId = requests.findDonationIdById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        FoodDonation d = donations.findByIdForUpdate(donationId)
                .orElseThrow(() -> new ResourceNotFoundException("Donation not found"));
        DonationRequest r = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        if (!d.getDonor().getId().equals(donorId)) {
            throw new AccessDeniedException("This is not your donation.");
        }
        if (!r.getStatus().canTransitionTo(RequestStatus.ACCEPTED)) {
            throw new BusinessRuleException("This request is " + r.getStatus() + " and cannot be accepted.");
        }
        if (d.getStatus() != DonationStatus.AVAILABLE || !d.getPickupDeadline().isAfter(mapper.now())) {
            throw new BusinessRuleException("This donation is expired, fully reserved or unavailable.");
        }
        if (r.getQuantity() > d.getRemainingQuantity()) {
            throw new BusinessRuleException("Not enough quantity left: only " + d.getRemainingQuantity() + " "
                    + d.getUnit().getLabel() + " remain.");
        }
        d.setRemainingQuantity(d.getRemainingQuantity() - r.getQuantity());
        if (d.getRemainingQuantity() == 0) {
            d.setStatus(DonationStatus.RESERVED);
        }
        r.setStatus(RequestStatus.ACCEPTED);

        PickupAssignment a = new PickupAssignment();
        a.setRequest(r);
        a.setStatus(DeliveryStatus.OPEN);
        assignments.save(a);

        notifications.notify(r.getRecipient(), "Request accepted",
                "Your request for " + d.getName() + " was accepted. A volunteer will be assigned.", "/tracking");
        users.findTop50ByRoleAndStatusAndCityIgnoreCase(Role.VOLUNTEER, AccountStatus.ACTIVE, d.getCity())
                .forEach(v -> notifications.notify(v, "New pickup task in " + d.getCity(),
                        "Pickup of " + d.getName() + " is waiting for a volunteer.", "/volunteer/dashboard"));
        audit.log(d.getDonor(), "REQUEST_ACCEPTED", "DonationRequest", r.getId(), "Reserved " + r.getQuantity());
        return mapper.toRequest(r, a);
    }

    @Transactional
    public RequestDto reject(Long donorId, Long requestId) {
        DonationRequest r = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        FoodDonation d = r.getDonation();
        if (!d.getDonor().getId().equals(donorId)) {
            throw new AccessDeniedException("This is not your donation.");
        }
        if (!r.getStatus().canTransitionTo(RequestStatus.REJECTED)) {
            throw new BusinessRuleException("This request is " + r.getStatus() + " and cannot be rejected.");
        }
        r.setStatus(RequestStatus.REJECTED);
        notifications.notify(r.getRecipient(), "Request declined",
                "Your request for " + d.getName() + " was declined by the donor.", "/requests/my");
        audit.log(d.getDonor(), "REQUEST_REJECTED", "DonationRequest", r.getId(), null);
        return mapper.toRequest(r, null);
    }

    /** The recipient cancels. If it was already accepted, the reserved quantity goes back to the donation. */
    @Transactional
    public RequestDto cancel(Long recipientId, Long requestId) {
        Long donationId = requests.findDonationIdById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        FoodDonation d = donations.findByIdForUpdate(donationId)
                .orElseThrow(() -> new ResourceNotFoundException("Donation not found"));
        DonationRequest r = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        if (!r.getRecipient().getId().equals(recipientId)) {
            throw new AccessDeniedException("This is not your request.");
        }
        if (!r.getStatus().canTransitionTo(RequestStatus.CANCELLED)) {
            throw new BusinessRuleException("This request is " + r.getStatus() + " and can no longer be cancelled.");
        }
        PickupAssignment a = null;
        if (r.getStatus() == RequestStatus.ACCEPTED) {
            a = assignments.findByRequestIdForUpdate(requestId).orElse(null);
            if (a != null && (a.getStatus() == DeliveryStatus.PICKED_UP || a.getStatus() == DeliveryStatus.IN_TRANSIT)) {
                throw new BusinessRuleException("The pickup has already started. Please contact the donor.");
            }
            d.setRemainingQuantity(d.getRemainingQuantity() + r.getQuantity());
            if (d.getStatus() == DonationStatus.RESERVED) {
                d.setStatus(d.getPickupDeadline().isAfter(mapper.now()) ? DonationStatus.AVAILABLE : DonationStatus.EXPIRED);
            }
            if (a != null) {
                if (a.getVolunteer() != null) {
                    notifications.notify(a.getVolunteer(), "Pickup cancelled",
                            "The recipient cancelled the pickup of " + d.getName() + ".", "/volunteer/dashboard");
                }
                a.setStatus(DeliveryStatus.CANCELLED);
            }
        }
        r.setStatus(RequestStatus.CANCELLED);
        notifications.notify(d.getDonor(), "Request cancelled",
                r.getRecipientDisplayName() + " cancelled a request for " + d.getName() + ".", "/donor/requests");
        audit.log(r.getRecipient(), "REQUEST_CANCELLED", "DonationRequest", r.getId(), null);
        return mapper.toRequest(r, a);
    }

    /** Called by DeliveryService when the volunteer marks the delivery as DELIVERED. */
    @Transactional
    public void complete(DonationRequest r) {
        if (!r.getStatus().canTransitionTo(RequestStatus.COMPLETED)) {
            throw new BusinessRuleException("This request cannot be completed from status " + r.getStatus() + ".");
        }
        r.setStatus(RequestStatus.COMPLETED);
        r.setCompletedAt(mapper.now());
        FoodDonation d = donations.findByIdForUpdate(r.getDonation().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Donation not found"));
        if (d.getRemainingQuantity() == 0 && !requests.existsByDonationIdAndStatus(d.getId(), RequestStatus.ACCEPTED)) {
            d.setStatus(DonationStatus.COMPLETED);
        }
        notifications.notify(d.getDonor(), "Food delivered",
                r.getQuantity() + " " + d.getUnit().getLabel() + " of " + d.getName() + " reached " + r.getRecipientDisplayName() + ".",
                "/tracking");
        notifications.notify(r.getRecipient(), "Food delivered",
                d.getName() + " has been delivered. Please follow food-safety guidelines when serving it.", "/tracking");
        audit.log(null, "REQUEST_COMPLETED", "DonationRequest", r.getId(), null);
    }

    /** Rejects every pending request of a donation (listing withdrawn, disabled or expired). */
    @Transactional
    public void rejectPending(FoodDonation d, String reason) {
        for (DonationRequest r : requests.findByDonationIdAndStatus(d.getId(), RequestStatus.PENDING)) {
            r.setStatus(RequestStatus.REJECTED);
            notifications.notify(r.getRecipient(), "Request closed",
                    "Your request for " + d.getName() + " was closed: " + reason, "/requests/my");
        }
    }

    /** Cancels an accepted request because the listing was disabled by an admin. */
    @Transactional
    public void cancelAcceptedBySystem(DonationRequest r, String reason) {
        PickupAssignment a = assignments.findByRequestIdForUpdate(r.getId()).orElse(null);
        if (a != null) {
            if (a.getVolunteer() != null) {
                notifications.notify(a.getVolunteer(), "Pickup cancelled", reason, "/volunteer/dashboard");
            }
            a.setStatus(DeliveryStatus.CANCELLED);
        }
        r.setStatus(RequestStatus.CANCELLED);
        notifications.notify(r.getRecipient(), "Request cancelled", reason, "/requests/my");
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Page<RequestDto> forRecipient(Long recipientId, int page, int size) {
        return toDtos(requests.findByRecipientId(recipientId, pageable(page, size)));
    }

    @Transactional(readOnly = true)
    public Page<RequestDto> forDonor(Long donorId, RequestStatus status, int page, int size) {
        return toDtos(status == null
                ? requests.findByDonationDonorId(donorId, pageable(page, size))
                : requests.findByDonationDonorIdAndStatus(donorId, status, pageable(page, size)));
    }

    @Transactional(readOnly = true)
    public Page<RequestDto> forAdmin(RequestStatus status, int page, int size) {
        return toDtos(status == null ? requests.findAll(pageable(page, size))
                : requests.findByStatus(status, pageable(page, size)));
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /** Loads all delivery tasks of the page in ONE query instead of one query per row. */
    private Page<RequestDto> toDtos(Page<DonationRequest> page) {
        List<Long> ids = page.getContent().stream().map(DonationRequest::getId).toList();
        Map<Long, PickupAssignment> byRequest = ids.isEmpty() ? Map.of()
                : assignments.findByRequestIdIn(ids).stream()
                        .collect(Collectors.toMap(a -> a.getRequest().getId(), Function.identity()));
        return page.map(r -> mapper.toRequest(r, byRequest.get(r.getId())));
    }
}
