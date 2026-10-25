package com.foodbridge.service;

import com.foodbridge.dto.ImpactStats;
import com.foodbridge.dto.WeeklySeries;
import com.foodbridge.entity.DonationRequest;
import com.foodbridge.entity.FoodDonation;
import com.foodbridge.enums.DeliveryStatus;
import com.foodbridge.enums.DonationStatus;
import com.foodbridge.enums.RequestStatus;
import com.foodbridge.enums.Role;
import com.foodbridge.repository.DonationRequestRepository;
import com.foodbridge.repository.FoodDonationRepository;
import com.foodbridge.repository.PickupAssignmentRepository;
import com.foodbridge.repository.UserRepository;
import com.foodbridge.util.CsvUtil;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every number here is computed from real rows. Nothing is estimated or invented. */
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final int WEEKS = 8;

    private final FoodDonationRepository donations;
    private final DonationRequestRepository requests;
    private final PickupAssignmentRepository assignments;
    private final UserRepository users;
    private final DtoMapper mapper;

    @Transactional(readOnly = true)
    public ImpactStats impact() {
        return new ImpactStats(
                donations.count(),
                donations.sumQuantityListed(),
                requests.sumQuantityByStatus(RequestStatus.COMPLETED),
                assignments.countByStatus(DeliveryStatus.DELIVERED),
                donations.countDistinctDonors(),
                users.countByRole(Role.RECIPIENT),
                users.countByRole(Role.VOLUNTEER));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> donorSummary(Long donorId) {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("listings", donations.countByDonorId(donorId));
        m.put("active", donations.countByDonorIdAndStatus(donorId, DonationStatus.AVAILABLE));
        m.put("pending", requests.countByDonationDonorIdAndStatus(donorId, RequestStatus.PENDING));
        m.put("completed", requests.countByDonationDonorIdAndStatus(donorId, RequestStatus.COMPLETED));
        m.put("unitsDelivered", requests.sumQuantityByDonorAndStatus(donorId, RequestStatus.COMPLETED));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> recipientSummary(Long recipientId) {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("pending", requests.countByRecipientIdAndStatus(recipientId, RequestStatus.PENDING));
        m.put("accepted", requests.countByRecipientIdAndStatus(recipientId, RequestStatus.ACCEPTED));
        m.put("completed", requests.countByRecipientIdAndStatus(recipientId, RequestStatus.COMPLETED));
        m.put("unitsReceived", requests.sumQuantityByRecipientAndStatus(recipientId, RequestStatus.COMPLETED));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> volunteerSummary(Long volunteerId) {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("open", assignments.countByStatus(DeliveryStatus.OPEN));
        m.put("active", assignments.countByVolunteerIdAndStatusIn(volunteerId,
                List.of(DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT)));
        m.put("delivered", assignments.countByVolunteerIdAndStatus(volunteerId, DeliveryStatus.DELIVERED));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> adminSummary() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("users", users.count());
        m.put("donations", donations.count());
        m.put("availableNow", donations.countByStatus(DonationStatus.AVAILABLE));
        m.put("pendingRequests", requests.countByStatus(RequestStatus.PENDING));
        m.put("openTasks", assignments.countByStatus(DeliveryStatus.OPEN));
        m.put("delivered", assignments.countByStatus(DeliveryStatus.DELIVERED));
        return m;
    }

    /** Last 8 weeks. donorId == null means the whole platform (admin). */
    @Transactional(readOnly = true)
    public WeeklySeries weekly(Long donorId) {
        LocalDate start = mapper.now().toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(WEEKS - 1L);
        LocalDateTime since = start.atStartOfDay();
        List<FoodDonation> listed = donorId == null ? donations.findByCreatedAtAfter(since)
                : donations.findByDonorIdAndCreatedAtAfter(donorId, since);
        List<DonationRequest> done = donorId == null
                ? requests.findByStatusAndCompletedAtAfter(RequestStatus.COMPLETED, since)
                : requests.findByDonationDonorIdAndStatusAndCompletedAtAfter(donorId, RequestStatus.COMPLETED, since);

        long[] listedPerWeek = new long[WEEKS];
        long[] unitsPerWeek = new long[WEEKS];
        for (FoodDonation d : listed) {
            int idx = (int) ChronoUnit.WEEKS.between(start, d.getCreatedAt().toLocalDate());
            if (idx >= 0 && idx < WEEKS) {
                listedPerWeek[idx]++;
            }
        }
        for (DonationRequest r : done) {
            int idx = (int) ChronoUnit.WEEKS.between(start, r.getCompletedAt().toLocalDate());
            if (idx >= 0 && idx < WEEKS) {
                unitsPerWeek[idx] += r.getQuantity();
            }
        }
        List<String> labels = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM");
        for (int i = 0; i < WEEKS; i++) {
            labels.add(start.plusWeeks(i).format(fmt));
        }
        return new WeeklySeries(labels, Arrays.stream(listedPerWeek).boxed().toList(),
                Arrays.stream(unitsPerWeek).boxed().toList());
    }

    /** CSV of one donor's listings (donorId) or of all listings (null, admin). */
    @Transactional(readOnly = true)
    public String donationsCsv(Long donorId) {
        List<FoodDonation> list = donorId == null ? donations.findAll() : donations.findByDonorIdOrderByCreatedAtDesc(donorId);
        List<List<String>> rows = new ArrayList<>();
        for (FoodDonation d : list) {
            rows.add(List.of(String.valueOf(d.getId()), d.getName(), d.getCategory().getLabel(), d.getDonorDisplayName(),
                    String.valueOf(d.getQuantity()), String.valueOf(d.getRemainingQuantity()), d.getUnit().getLabel(),
                    d.getStatus().name(), d.getCity(), String.valueOf(d.getPickupDeadline()), String.valueOf(d.getCreatedAt())));
        }
        return CsvUtil.toCsv(List.of("id", "name", "category", "donor", "quantity", "remaining", "unit",
                "status", "city", "pickup_deadline", "created_at"), rows);
    }
}
