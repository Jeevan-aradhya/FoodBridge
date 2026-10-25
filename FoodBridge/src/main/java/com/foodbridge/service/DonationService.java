package com.foodbridge.service;

import com.foodbridge.dto.DonationDto;
import com.foodbridge.dto.DonationFilter;
import com.foodbridge.dto.DonationForm;
import com.foodbridge.entity.DonationRequest;
import com.foodbridge.entity.FoodDonation;
import com.foodbridge.entity.OrganizationProfile;
import com.foodbridge.entity.PickupAssignment;
import com.foodbridge.entity.User;
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
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DonationService {

    private final FoodDonationRepository donations;
    private final DonationRequestRepository requests;
    private final PickupAssignmentRepository assignments;
    private final UserRepository users;
    private final OrganizationProfileRepository orgs;
    private final RequestService requestService;
    private final NotificationService notifications;
    private final AuditService audit;
    private final FileStorageService files;
    private final DtoMapper mapper;

    // ----------------------------------------------------------------- donor actions

    @Transactional
    public DonationDto create(Long donorId, DonationForm f, MultipartFile image) {
        User donor = users.findById(donorId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (donor.getRole() != Role.DONOR) {
            throw new AccessDeniedException("Only donors can list food.");
        }
        validateTimes(f, mapper.now());
        FoodDonation d = new FoodDonation();
        d.setDonor(donor);
        d.setDonorDisplayName(orgs.findByUserId(donorId).map(OrganizationProfile::getOrganizationName).orElse(donor.getFullName()));
        apply(d, f);
        d.setRemainingQuantity(f.getQuantity());
        d.setStatus(DonationStatus.AVAILABLE);
        if (image != null && !image.isEmpty()) {
            d.setImagePath(files.store(image));
        }
        donations.save(d);
        audit.log(donor, "DONATION_CREATED", "FoodDonation", d.getId(), d.getName());
        return mapper.toDonation(d, true);
    }

    @Transactional
    public DonationDto update(Long actorId, Long id, DonationForm f, MultipartFile image) {
        FoodDonation d = donations.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Donation not found"));
        assertOwner(d, actorId);
        if (d.getStatus() != DonationStatus.AVAILABLE && d.getStatus() != DonationStatus.RESERVED) {
            throw new BusinessRuleException("Only active listings can be edited. Create a new listing instead.");
        }
        validateTimes(f, mapper.now());
        int reserved = d.getQuantity() - d.getRemainingQuantity();
        if (f.getQuantity() < reserved) {
            throw new BusinessRuleException("Quantity cannot be lower than the " + reserved + " units already reserved.");
        }
        apply(d, f);
        d.setRemainingQuantity(f.getQuantity() - reserved);
        d.setStatus(d.getRemainingQuantity() == 0 ? DonationStatus.RESERVED : DonationStatus.AVAILABLE);
        if (image != null && !image.isEmpty()) {
            String old = d.getImagePath();
            d.setImagePath(files.store(image));
            files.deleteQuietly(old);
        }
        audit.log(d.getDonor(), "DONATION_UPDATED", "FoodDonation", d.getId(), d.getName());
        return mapper.toDonation(d, true);
    }

    /** "Delete" = withdraw: the listing disappears, history is kept. */
    @Transactional
    public void withdraw(Long actorId, Long id) {
        FoodDonation d = donations.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Donation not found"));
        assertOwner(d, actorId);
        if (d.getStatus() != DonationStatus.AVAILABLE && d.getStatus() != DonationStatus.RESERVED
                && d.getStatus() != DonationStatus.EXPIRED) {
            throw new BusinessRuleException("This listing cannot be removed (status " + d.getStatus() + ").");
        }
        if (requests.existsByDonationIdAndStatus(id, RequestStatus.ACCEPTED)) {
            throw new BusinessRuleException("Accepted requests are still waiting for delivery. Wait until they are delivered or cancelled.");
        }
        requestService.rejectPending(d, "the donor withdrew this listing.");
        d.setStatus(DonationStatus.WITHDRAWN);
        audit.log(d.getDonor(), "DONATION_WITHDRAWN", "FoodDonation", d.getId(), d.getName());
    }

    // ----------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Page<DonationDto> search(DonationFilter f) {
        Sort sort;
        String key = f.getSort() == null ? "deadline" : f.getSort();
        if ("newest".equals(key)) {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        } else if ("quantity".equals(key)) {
            sort = Sort.by(Sort.Direction.DESC, "remainingQuantity");
        } else {
            sort = Sort.by(Sort.Direction.ASC, "pickupDeadline");
        }
        int size = Math.min(Math.max(f.getSize(), 1), 50);
        int page = Math.max(f.getPage(), 0);
        return donations.findAll(DonationSpecs.search(f, mapper.now()), PageRequest.of(page, size, sort))
                .map(d -> mapper.toDonation(d, false));
    }

    /** Landing page: the four open listings whose pickup deadline is closest. */
    @Transactional(readOnly = true)
    public List<DonationDto> closingSoon() {
        return donations.findTop4ByStatusAndPickupDeadlineAfterAndRemainingQuantityGreaterThanOrderByPickupDeadlineAsc(
                DonationStatus.AVAILABLE, mapper.now(), 0).stream().map(d -> mapper.toDonation(d, false)).toList();
    }

    @Transactional(readOnly = true)
    public DonationDto get(Long id, Long viewerId, Role viewerRole) {
        FoodDonation d = donations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Donation not found"));
        boolean owner = viewerId != null && d.getDonor().getId().equals(viewerId);
        boolean admin = viewerRole == Role.ADMIN;
        if ((d.getStatus() == DonationStatus.DISABLED || d.getStatus() == DonationStatus.WITHDRAWN) && !owner && !admin) {
            throw new ResourceNotFoundException("Donation not found");
        }
        boolean reveal = owner || admin
                || (viewerRole == Role.RECIPIENT && viewerId != null && requests.existsByDonationIdAndRecipientIdAndStatusIn(
                        id, viewerId, List.of(RequestStatus.ACCEPTED, RequestStatus.COMPLETED)))
                || (viewerRole == Role.VOLUNTEER && viewerId != null
                        && assignments.existsByRequestDonationIdAndVolunteerId(id, viewerId));
        return mapper.toDonation(d, reveal);
    }

    @Transactional(readOnly = true)
    public Page<DonationDto> forDonor(Long donorId, int page, int size) {
        return donations.findByDonorId(donorId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"))).map(d -> mapper.toDonation(d, true));
    }

    /** Used by the edit form: the donor's own listing as a form object. */
    @Transactional(readOnly = true)
    public DonationForm toForm(Long actorId, Long id) {
        FoodDonation d = donations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Donation not found"));
        assertOwner(d, actorId);
        DonationForm f = new DonationForm();
        f.setName(d.getName());
        f.setCategory(d.getCategory());
        f.setDescription(d.getDescription());
        f.setQuantity(d.getQuantity());
        f.setUnit(d.getUnit());
        f.setPreparedAt(d.getPreparedAt());
        f.setExpiresAt(d.getExpiresAt());
        f.setPickupDeadline(d.getPickupDeadline());
        f.setDietaryInfo(d.getDietaryInfo());
        f.setAllergenInfo(d.getAllergenInfo());
        f.setStorageInfo(d.getStorageInfo());
        f.setPickupAddress(d.getPickupAddress());
        f.setCity(d.getCity());
        f.setLatitude(d.getLatitude());
        f.setLongitude(d.getLongitude());
        f.setPickupInstructions(d.getPickupInstructions());
        return f;
    }

    // ----------------------------------------------------------------- reports & admin

    @Transactional
    public void report(Long reporterId, Long donationId, String reason) {
        User reporter = users.findById(reporterId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        FoodDonation d = donations.findById(donationId).orElseThrow(() -> new ResourceNotFoundException("Donation not found"));
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("Please tell us what is wrong with this listing.");
        }
        String clean = reason.trim().length() > 300 ? reason.trim().substring(0, 300) : reason.trim();
        d.setReported(true);
        d.setReportReason(clean);
        audit.log(reporter, "DONATION_REPORTED", "FoodDonation", d.getId(), clean);
        notifications.notifyRole(Role.ADMIN, "Listing reported", d.getName() + ": " + clean, "/admin/donations");
    }

    @Transactional(readOnly = true)
    public Page<DonationDto> adminList(boolean reportedOnly, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<FoodDonation> result = reportedOnly ? donations.findByReportedTrue(pr) : donations.findAll(pr);
        return result.map(d -> mapper.toDonation(d, true));
    }

    @Transactional
    public void disable(Long adminId, Long donationId, String reason) {
        User admin = requireAdmin(adminId);
        FoodDonation d = donations.findByIdForUpdate(donationId).orElseThrow(() -> new ResourceNotFoundException("Donation not found"));
        if (d.getStatus() == DonationStatus.DISABLED) {
            throw new BusinessRuleException("This listing is already disabled.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("A reason is required to disable a listing.");
        }
        List<DonationRequest> accepted = requests.findByDonationIdAndStatus(donationId, RequestStatus.ACCEPTED);
        for (DonationRequest r : accepted) {
            PickupAssignment a = assignments.findByRequestId(r.getId()).orElse(null);
            if (a != null && (a.getStatus() == DeliveryStatus.PICKED_UP || a.getStatus() == DeliveryStatus.IN_TRANSIT)) {
                throw new BusinessRuleException("A delivery is already in progress. Disable the listing after it is delivered.");
            }
        }
        requestService.rejectPending(d, "the listing was disabled by an administrator.");
        for (DonationRequest r : accepted) {
            requestService.cancelAcceptedBySystem(r, "The listing " + d.getName() + " was disabled by an administrator.");
        }
        d.setStatus(DonationStatus.DISABLED);
        notifications.notify(d.getDonor(), "Listing disabled",
                "Your listing \"" + d.getName() + "\" was disabled: " + reason.trim(), "/donor/dashboard");
        audit.log(admin, "DONATION_DISABLED", "FoodDonation", d.getId(), reason.trim());
    }

    @Transactional
    public void enable(Long adminId, Long donationId) {
        User admin = requireAdmin(adminId);
        FoodDonation d = donations.findByIdForUpdate(donationId).orElseThrow(() -> new ResourceNotFoundException("Donation not found"));
        if (d.getStatus() != DonationStatus.DISABLED) {
            throw new BusinessRuleException("Only disabled listings can be re-enabled.");
        }
        if (!d.getPickupDeadline().isAfter(mapper.now())) {
            d.setStatus(DonationStatus.EXPIRED);
        } else {
            d.setStatus(d.getRemainingQuantity() == 0 ? DonationStatus.RESERVED : DonationStatus.AVAILABLE);
        }
        d.setReported(false);
        d.setReportReason(null);
        audit.log(admin, "DONATION_ENABLED", "FoodDonation", d.getId(), null);
    }

    // ----------------------------------------------------------------- expiry

    /** Marks listings whose pickup deadline passed as EXPIRED. Called every few minutes by ExpiryScheduler. */
    @Transactional
    public int expireOverdue() {
        LocalDateTime now = mapper.now();
        int count = 0;
        for (FoodDonation candidate : donations.findByStatusAndPickupDeadlineBefore(DonationStatus.AVAILABLE, now)) {
            FoodDonation d = donations.findByIdForUpdate(candidate.getId()).orElse(null);
            if (d == null || d.getStatus() != DonationStatus.AVAILABLE || !d.getPickupDeadline().isBefore(now)) {
                continue;
            }
            d.setStatus(DonationStatus.EXPIRED);
            requestService.rejectPending(d, "the pickup deadline has passed.");
            notifications.notify(d.getDonor(), "Listing expired",
                    "\"" + d.getName() + "\" passed its pickup deadline and is no longer available.", "/donor/dashboard");
            audit.log(null, "DONATION_EXPIRED", "FoodDonation", d.getId(), d.getName());
            count++;
        }
        return count;
    }

    // ----------------------------------------------------------------- helpers

    private User requireAdmin(Long adminId) {
        User admin = users.findById(adminId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (admin.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Admins only.");
        }
        return admin;
    }

    private void assertOwner(FoodDonation d, Long actorId) {
        if (actorId == null || !d.getDonor().getId().equals(actorId)) {
            throw new AccessDeniedException("You can only manage your own donations.");
        }
    }

    /** Time rules: prepared <= now, expiry after preparation, deadline in the future and not after expiry. */
    void validateTimes(DonationForm f, LocalDateTime now) {
        if (f.getPreparedAt().isAfter(now.plusMinutes(5))) {
            throw new BusinessRuleException("Preparation time cannot be in the future.");
        }
        if (!f.getExpiresAt().isAfter(f.getPreparedAt())) {
            throw new BusinessRuleException("Expiry time must be after the preparation time.");
        }
        if (!f.getPickupDeadline().isAfter(now)) {
            throw new BusinessRuleException("Pickup deadline must be in the future.");
        }
        if (f.getPickupDeadline().isAfter(f.getExpiresAt())) {
            throw new BusinessRuleException("Pickup deadline cannot be later than the food's expiry time.");
        }
    }

    private void apply(FoodDonation d, DonationForm f) {
        d.setName(f.getName().trim());
        d.setCategory(f.getCategory());
        d.setDescription(f.getDescription());
        d.setQuantity(f.getQuantity());
        d.setUnit(f.getUnit());
        d.setPreparedAt(f.getPreparedAt());
        d.setExpiresAt(f.getExpiresAt());
        d.setPickupDeadline(f.getPickupDeadline());
        d.setDietaryInfo(f.getDietaryInfo());
        d.setAllergenInfo(f.getAllergenInfo());
        d.setStorageInfo(f.getStorageInfo());
        d.setPickupAddress(f.getPickupAddress().trim());
        d.setCity(f.getCity().trim());
        d.setLatitude(f.getLatitude());
        d.setLongitude(f.getLongitude());
        d.setPickupInstructions(f.getPickupInstructions());
    }
}
