package com.foodbridge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.foodbridge.dto.DonationForm;
import com.foodbridge.entity.FoodDonation;
import com.foodbridge.entity.User;
import com.foodbridge.enums.DonationStatus;
import com.foodbridge.enums.FoodCategory;
import com.foodbridge.enums.FoodUnit;
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
class DonationServiceTest {

    @Mock FoodDonationRepository donations;
    @Mock DonationRequestRepository requests;
    @Mock PickupAssignmentRepository assignments;
    @Mock UserRepository users;
    @Mock OrganizationProfileRepository orgs;
    @Mock RequestService requestService;
    @Mock NotificationService notifications;
    @Mock AuditService audit;
    @Mock FileStorageService files;

    private DonationService service;
    private LocalDateTime now;
    private User donor;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneId.of("UTC"));
        DtoMapper mapper = new DtoMapper(clock);
        service = new DonationService(donations, requests, assignments, users, orgs, requestService,
                notifications, audit, files, mapper);
        now = mapper.now();
        donor = TestData.user(10L, Role.DONOR);
    }

    private DonationForm validForm() {
        DonationForm f = new DonationForm();
        f.setName("Dal and rice");
        f.setCategory(FoodCategory.COOKED_MEALS);
        f.setQuantity(20);
        f.setUnit(FoodUnit.PORTIONS);
        f.setPreparedAt(now.minusHours(2));
        f.setExpiresAt(now.plusHours(6));
        f.setPickupDeadline(now.plusHours(4));
        f.setPickupAddress("1 Test Street");
        f.setCity("Bengaluru");
        return f;
    }

    @Test
    void validTimesAreAccepted() {
        assertThatCode(() -> service.validateTimes(validForm(), now)).doesNotThrowAnyException();
    }

    @Test
    void preparationTimeInTheFutureIsRejected() {
        DonationForm f = validForm();
        f.setPreparedAt(now.plusHours(1));
        assertThatThrownBy(() -> service.validateTimes(f, now))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Preparation time");
    }

    @Test
    void expiryBeforePreparationIsRejected() {
        DonationForm f = validForm();
        f.setExpiresAt(f.getPreparedAt().minusMinutes(1));
        assertThatThrownBy(() -> service.validateTimes(f, now))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Expiry time");
    }

    @Test
    void pickupDeadlineInThePastIsRejected() {
        DonationForm f = validForm();
        f.setPickupDeadline(now.minusMinutes(1));
        assertThatThrownBy(() -> service.validateTimes(f, now))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("must be in the future");
    }

    @Test
    void pickupDeadlineAfterExpiryIsRejected() {
        DonationForm f = validForm();
        f.setPickupDeadline(f.getExpiresAt().plusMinutes(1));
        assertThatThrownBy(() -> service.validateTimes(f, now))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cannot be later");
    }

    @Test
    void onlyDonorsCanCreateDonations() {
        User recipient = TestData.user(20L, Role.RECIPIENT);
        when(users.findById(20L)).thenReturn(Optional.of(recipient));
        assertThatThrownBy(() -> service.create(20L, validForm(), null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createdDonationStartsAvailableWithFullRemainingQuantity() {
        when(users.findById(10L)).thenReturn(Optional.of(donor));
        when(orgs.findByUserId(10L)).thenReturn(Optional.empty());
        when(donations.save(org.mockito.ArgumentMatchers.any(FoodDonation.class))).thenAnswer(inv -> {
            FoodDonation d = inv.getArgument(0);
            d.setId(5L);
            d.setCreatedAt(now);
            return d;
        });

        var dto = service.create(10L, validForm(), null);

        assertThat(dto.quantity()).isEqualTo(20);
        assertThat(dto.remainingQuantity()).isEqualTo(20);
        assertThat(dto.status()).isEqualTo("AVAILABLE");
        assertThat(dto.pickupAddress()).isNotNull();   // the owner sees the address
    }

    @Test
    void donorCannotEditSomeoneElsesDonation() {
        FoodDonation d = TestData.donation(1L, donor, 10, now.plusHours(3));
        when(donations.findByIdForUpdate(1L)).thenReturn(Optional.of(d));
        assertThatThrownBy(() -> service.update(999L, 1L, validForm(), null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void quantityCannotDropBelowWhatIsAlreadyReserved() {
        FoodDonation d = TestData.donation(1L, donor, 4, now.plusHours(3));   // 10 offered, 6 reserved
        when(donations.findByIdForUpdate(1L)).thenReturn(Optional.of(d));
        DonationForm f = validForm();
        f.setQuantity(5);
        assertThatThrownBy(() -> service.update(10L, 1L, f, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already reserved");
    }

    @Test
    void expiredListingsCannotBeEdited() {
        FoodDonation d = TestData.donation(1L, donor, 4, now.plusHours(3));
        d.setStatus(DonationStatus.EXPIRED);
        when(donations.findByIdForUpdate(1L)).thenReturn(Optional.of(d));
        assertThatThrownBy(() -> service.update(10L, 1L, validForm(), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only active listings");
    }

    @Test
    void donationWithAcceptedRequestsCannotBeWithdrawn() {
        FoodDonation d = TestData.donation(1L, donor, 4, now.plusHours(3));
        when(donations.findByIdForUpdate(1L)).thenReturn(Optional.of(d));
        when(requests.existsByDonationIdAndStatus(1L, com.foodbridge.enums.RequestStatus.ACCEPTED)).thenReturn(true);
        assertThatThrownBy(() -> service.withdraw(10L, 1L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Accepted requests");
    }

    @Test
    void overdueListingsAreShownAsExpiredEvenBeforeTheSchedulerRuns() {
        FoodDonation d = TestData.donation(1L, donor, 5, now.minusMinutes(10));
        var dto = new DtoMapper(Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneId.of("UTC"))).toDonation(d, false);
        assertThat(dto.status()).isEqualTo("EXPIRED");
        assertThat(dto.available()).isFalse();
        assertThat(dto.pickupAddress()).isNull();      // address hidden from the public
    }
}
