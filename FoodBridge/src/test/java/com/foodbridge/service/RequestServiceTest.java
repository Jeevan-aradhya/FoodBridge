package com.foodbridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodbridge.dto.RequestDto;
import com.foodbridge.dto.RequestForm;
import com.foodbridge.entity.DonationRequest;
import com.foodbridge.entity.FoodDonation;
import com.foodbridge.entity.OrganizationProfile;
import com.foodbridge.entity.PickupAssignment;
import com.foodbridge.entity.User;
import com.foodbridge.enums.DonationStatus;
import com.foodbridge.enums.RequestStatus;
import com.foodbridge.enums.Role;
import com.foodbridge.exception.BusinessRuleException;
import com.foodbridge.repository.DonationRequestRepository;
import com.foodbridge.repository.FoodDonationRepository;
import com.foodbridge.repository.OrganizationProfileRepository;
import com.foodbridge.repository.PickupAssignmentRepository;
import com.foodbridge.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {

    @Mock DonationRequestRepository requests;
    @Mock FoodDonationRepository donations;
    @Mock PickupAssignmentRepository assignments;
    @Mock UserRepository users;
    @Mock OrganizationProfileRepository orgs;
    @Mock NotificationService notifications;
    @Mock AuditService audit;

    private RequestService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneId.of("UTC"));
    private LocalDateTime now;
    private User donor;
    private User recipient;

    @BeforeEach
    void setUp() {
        DtoMapper mapper = new DtoMapper(clock);
        service = new RequestService(requests, donations, assignments, users, orgs, notifications, audit, mapper);
        now = mapper.now();
        donor = TestData.user(10L, Role.DONOR);
        recipient = TestData.user(20L, Role.RECIPIENT);
    }

    private static RequestForm form(int qty) {
        RequestForm f = new RequestForm();
        f.setQuantity(qty);
        return f;
    }

    private OrganizationProfile org() {
        OrganizationProfile o = new OrganizationProfile();
        o.setUser(recipient);
        o.setOrganizationName("Hope Meals");
        o.setAddress("22 Main Road");
        o.setCity("Bengaluru");
        return o;
    }

    private DonationRequest pending(FoodDonation d, int qty) {
        DonationRequest r = new DonationRequest();
        r.setId(100L);
        r.setDonation(d);
        r.setRecipient(recipient);
        r.setQuantity(qty);
        r.setStatus(RequestStatus.PENDING);
        r.setRecipientDisplayName("Hope Meals");
        r.setDropOffAddress("22 Main Road");
        r.setCreatedAt(now);
        return r;
    }

    private void lockReturns(FoodDonation d, DonationRequest r) {
        when(requests.findDonationIdById(100L)).thenReturn(Optional.of(d.getId()));
        when(donations.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));
        when(requests.findByIdForUpdate(100L)).thenReturn(Optional.of(r));
    }

    // ------------------------------------------------------------- submit

    @Test
    void submit_rejectsQuantityAboveRemaining() {
        FoodDonation d = TestData.donation(1L, donor, 5, now.plusHours(3));
        when(users.findById(20L)).thenReturn(Optional.of(recipient));
        when(orgs.findByUserId(20L)).thenReturn(Optional.of(org()));
        when(donations.findById(1L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.submit(20L, 1L, form(8)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only 5");
        verify(requests, never()).save(any());
    }

    @Test
    void submit_rejectsExpiredDonation() {
        FoodDonation d = TestData.donation(1L, donor, 5, now.minusMinutes(1));
        when(users.findById(20L)).thenReturn(Optional.of(recipient));
        when(orgs.findByUserId(20L)).thenReturn(Optional.of(org()));
        when(donations.findById(1L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.submit(20L, 1L, form(2)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    void submit_rejectsUsersWhoAreNotRecipients() {
        when(users.findById(10L)).thenReturn(Optional.of(donor));
        assertThatThrownBy(() -> service.submit(10L, 1L, form(1))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void submit_rejectsSecondPendingRequestForSameDonation() {
        FoodDonation d = TestData.donation(1L, donor, 5, now.plusHours(3));
        when(users.findById(20L)).thenReturn(Optional.of(recipient));
        when(orgs.findByUserId(20L)).thenReturn(Optional.of(org()));
        when(donations.findById(1L)).thenReturn(Optional.of(d));
        when(requests.existsByDonationIdAndRecipientIdAndStatus(1L, 20L, RequestStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.submit(20L, 1L, form(2)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already have a pending request");
    }

    @Test
    void submit_createsPendingRequestWithoutReservingQuantity() {
        FoodDonation d = TestData.donation(1L, donor, 5, now.plusHours(3));
        when(users.findById(20L)).thenReturn(Optional.of(recipient));
        when(orgs.findByUserId(20L)).thenReturn(Optional.of(org()));
        when(donations.findById(1L)).thenReturn(Optional.of(d));
        when(requests.save(any(DonationRequest.class))).thenAnswer(inv -> {
            DonationRequest r = inv.getArgument(0);
            r.setId(100L);
            r.setCreatedAt(now);
            return r;
        });

        RequestDto dto = service.submit(20L, 1L, form(3));

        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(d.getRemainingQuantity()).isEqualTo(5);   // nothing reserved yet
    }

    // ------------------------------------------------------------- accept

    @Test
    void accept_reservesQuantityAndCreatesDeliveryTask() {
        FoodDonation d = TestData.donation(1L, donor, 10, now.plusHours(3));
        DonationRequest r = pending(d, 4);
        lockReturns(d, r);
        when(assignments.save(any(PickupAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.accept(10L, 100L);

        assertThat(r.getStatus()).isEqualTo(RequestStatus.ACCEPTED);
        assertThat(d.getRemainingQuantity()).isEqualTo(6);
        assertThat(d.getStatus()).isEqualTo(DonationStatus.AVAILABLE);
        verify(assignments).save(any(PickupAssignment.class));
    }

    @Test
    void accept_marksDonationReservedWhenNothingIsLeft() {
        FoodDonation d = TestData.donation(1L, donor, 4, now.plusHours(3));
        DonationRequest r = pending(d, 4);
        lockReturns(d, r);
        when(assignments.save(any(PickupAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.accept(10L, 100L);

        assertThat(d.getRemainingQuantity()).isZero();
        assertThat(d.getStatus()).isEqualTo(DonationStatus.RESERVED);
    }

    @Test
    void accept_rejectsMoreThanRemaining() {
        FoodDonation d = TestData.donation(1L, donor, 3, now.plusHours(3));
        DonationRequest r = pending(d, 6);
        lockReturns(d, r);

        assertThatThrownBy(() -> service.accept(10L, 100L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Not enough quantity");
        assertThat(d.getRemainingQuantity()).isEqualTo(3);
    }

    @Test
    void accept_rejectsExpiredDonation() {
        FoodDonation d = TestData.donation(1L, donor, 10, now.minusMinutes(5));
        DonationRequest r = pending(d, 2);
        lockReturns(d, r);

        assertThatThrownBy(() -> service.accept(10L, 100L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void accept_rejectsInvalidStatusTransition() {
        FoodDonation d = TestData.donation(1L, donor, 10, now.plusHours(3));
        DonationRequest r = pending(d, 2);
        r.setStatus(RequestStatus.CANCELLED);
        lockReturns(d, r);

        assertThatThrownBy(() -> service.accept(10L, 100L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be accepted");
    }

    @Test
    void accept_isDeniedForSomeoneWhoDoesNotOwnTheDonation() {
        FoodDonation d = TestData.donation(1L, donor, 10, now.plusHours(3));
        DonationRequest r = pending(d, 2);
        lockReturns(d, r);

        assertThatThrownBy(() -> service.accept(999L, 100L)).isInstanceOf(AccessDeniedException.class);
        assertThat(r.getStatus()).isEqualTo(RequestStatus.PENDING);
    }

    // ------------------------------------------------------------- reject / cancel

    @Test
    void reject_isDeniedForOtherDonors() {
        FoodDonation d = TestData.donation(1L, donor, 10, now.plusHours(3));
        DonationRequest r = pending(d, 2);
        when(requests.findByIdForUpdate(100L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.reject(999L, 100L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancel_isDeniedForOtherRecipients() {
        FoodDonation d = TestData.donation(1L, donor, 10, now.plusHours(3));
        DonationRequest r = pending(d, 2);
        lockReturns(d, r);

        assertThatThrownBy(() -> service.cancel(777L, 100L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancel_ofAcceptedRequestGivesTheQuantityBack() {
        FoodDonation d = TestData.donation(1L, donor, 0, now.plusHours(3));
        d.setStatus(DonationStatus.RESERVED);
        DonationRequest r = pending(d, 4);
        r.setStatus(RequestStatus.ACCEPTED);
        lockReturns(d, r);
        when(assignments.findByRequestIdForUpdate(100L)).thenReturn(Optional.empty());

        service.cancel(20L, 100L);

        assertThat(r.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(d.getRemainingQuantity()).isEqualTo(4);
        assertThat(d.getStatus()).isEqualTo(DonationStatus.AVAILABLE);
    }

    @Test
    void cancel_ofCompletedRequestIsRejected() {
        FoodDonation d = TestData.donation(1L, donor, 0, now.plusHours(3));
        DonationRequest r = pending(d, 4);
        r.setStatus(RequestStatus.COMPLETED);
        lockReturns(d, r);

        assertThatThrownBy(() -> service.cancel(20L, 100L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("can no longer be cancelled");
    }
}
