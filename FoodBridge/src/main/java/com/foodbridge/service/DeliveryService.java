package com.foodbridge.service;

import com.foodbridge.dto.DeliveryDto;
import com.foodbridge.entity.DonationRequest;
import com.foodbridge.entity.PickupAssignment;
import com.foodbridge.entity.User;
import com.foodbridge.enums.DeliveryStatus;
import com.foodbridge.enums.Role;
import com.foodbridge.exception.BusinessRuleException;
import com.foodbridge.exception.ResourceNotFoundException;
import com.foodbridge.repository.DonationRequestRepository;
import com.foodbridge.repository.FoodDonationRepository;
import com.foodbridge.repository.PickupAssignmentRepository;
import com.foodbridge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Volunteer pickup tasks: OPEN -> ASSIGNED -> PICKED_UP -> IN_TRANSIT -> DELIVERED. */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final PickupAssignmentRepository assignments;
    private final DonationRequestRepository requests;
    private final FoodDonationRepository donations;
    private final UserRepository users;
    private final RequestService requestService;
    private final NotificationService notifications;
    private final AuditService audit;
    private final DtoMapper mapper;

    /** Tasks nobody has taken yet. Street addresses are hidden until a volunteer accepts. */
    @Transactional(readOnly = true)
    public Page<DeliveryDto> openTasks(int page, int size) {
        return assignments.findByStatus(DeliveryStatus.OPEN, pageable(page, size, Sort.Direction.ASC))
                .map(a -> mapper.toDelivery(a, false));
    }

    @Transactional(readOnly = true)
    public Page<DeliveryDto> mine(Long volunteerId, int page, int size) {
        return assignments.findByVolunteerId(volunteerId, pageable(page, size, Sort.Direction.DESC))
                .map(a -> mapper.toDelivery(a, true));
    }

    /** Tracking page: each role sees the deliveries that concern them. */
    @Transactional(readOnly = true)
    public Page<DeliveryDto> tracking(Long userId, Role role, int page, int size) {
        Pageable p = pageable(page, size, Sort.Direction.DESC);
        Page<PickupAssignment> result = switch (role) {
            case DONOR -> assignments.findByRequestDonationDonorId(userId, p);
            case RECIPIENT -> assignments.findByRequestRecipientId(userId, p);
            case VOLUNTEER -> assignments.findByVolunteerId(userId, p);
            case ADMIN -> assignments.findAll(p);
        };
        return result.map(a -> mapper.toDelivery(a, true));
    }

    @Transactional
    public DeliveryDto accept(Long volunteerId, Long assignmentId) {
        User v = users.findById(volunteerId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (v.getRole() != Role.VOLUNTEER) {
            throw new AccessDeniedException("Only volunteers can accept pickup tasks.");
        }
        PickupAssignment a = assignments.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        if (a.getStatus() != DeliveryStatus.OPEN) {
            throw new BusinessRuleException("This task was already taken or is no longer available.");
        }
        a.setVolunteer(v);
        a.setStatus(DeliveryStatus.ASSIGNED);
        a.setAssignedAt(mapper.now());
        String food = a.getRequest().getDonation().getName();
        notifications.notify(a.getRequest().getDonation().getDonor(), "Volunteer assigned",
                v.getFullName() + " will collect " + food + ".", "/tracking");
        notifications.notify(a.getRequest().getRecipient(), "Volunteer assigned",
                v.getFullName() + " will deliver " + food + " to you.", "/tracking");
        audit.log(v, "DELIVERY_ASSIGNED", "PickupAssignment", a.getId(), food);
        return mapper.toDelivery(a, true);
    }

    @Transactional
    public DeliveryDto advance(Long volunteerId, Long assignmentId, DeliveryStatus next) {
        if (next == null || next == DeliveryStatus.OPEN || next == DeliveryStatus.ASSIGNED || next == DeliveryStatus.CANCELLED) {
            throw new BusinessRuleException("Choose PICKED_UP, IN_TRANSIT or DELIVERED.");
        }
        // Lock order: donation -> request -> assignment (see RequestService).
        Long requestId = assignments.findRequestIdById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        Long donationId = assignments.findDonationIdById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        donations.findByIdForUpdate(donationId).orElseThrow(() -> new ResourceNotFoundException("Donation not found"));
        DonationRequest request = requests.findByIdForUpdate(requestId).orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        PickupAssignment a = assignments.findByRequestIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        if (a.getVolunteer() == null || !a.getVolunteer().getId().equals(volunteerId)) {
            throw new AccessDeniedException("This task is assigned to someone else.");
        }
        if (!a.getStatus().canTransitionTo(next)) {
            throw new BusinessRuleException("Cannot change a task from " + a.getStatus() + " to " + next + ".");
        }
        a.setStatus(next);
        String food = request.getDonation().getName();
        if (next == DeliveryStatus.PICKED_UP) {
            a.setPickedUpAt(mapper.now());
        } else if (next == DeliveryStatus.DELIVERED) {
            a.setDeliveredAt(mapper.now());
            requestService.complete(request);
        }
        if (next != DeliveryStatus.DELIVERED) {
            notifications.notify(request.getRecipient(), "Delivery update",
                    food + " is now " + next.name().replace('_', ' ').toLowerCase() + ".", "/tracking");
        }
        audit.log(a.getVolunteer(), "DELIVERY_" + next.name(), "PickupAssignment", a.getId(), food);
        return mapper.toDelivery(a, true);
    }

    private static PageRequest pageable(int page, int size, Sort.Direction dir) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(dir, "createdAt"));
    }
}
