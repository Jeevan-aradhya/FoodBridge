package com.foodbridge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foodbridge.dto.DeliveryDto;
import com.foodbridge.dto.DonationDto;
import com.foodbridge.dto.RequestDto;
import com.foodbridge.dto.RequestForm;
import com.foodbridge.dto.UserDto;
import com.foodbridge.enums.DeliveryStatus;
import com.foodbridge.enums.Role;
import com.foodbridge.exception.BusinessRuleException;
import com.foodbridge.service.DeliveryService;
import com.foodbridge.service.DonationService;
import com.foodbridge.service.RequestService;
import com.foodbridge.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DeliveryFlowIntegrationTest {

    @Autowired UserService users;
    @Autowired DonationService donations;
    @Autowired RequestService requests;
    @Autowired DeliveryService deliveries;

    @Test
    void fullLifecycleFromRequestToDeliveredAndInvalidTransitionsAreBlocked() {
        UserDto donor = IntegrationSupport.newUser(users, Role.DONOR);
        UserDto ngo = IntegrationSupport.newUser(users, Role.RECIPIENT);
        UserDto volunteer = IntegrationSupport.newUser(users, Role.VOLUNTEER);
        UserDto otherVolunteer = IntegrationSupport.newUser(users, Role.VOLUNTEER);
        DonationDto donation = IntegrationSupport.newDonation(donations, donor.id(), 6);

        RequestForm form = new RequestForm();
        form.setQuantity(6);
        RequestDto request = requests.submit(ngo.id(), donation.id(), form);
        RequestDto accepted = requests.accept(donor.id(), request.id());
        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        assertThat(donations.get(donation.id(), donor.id(), Role.DONOR).status()).isEqualTo("RESERVED");

        Long taskId = accepted.deliveryId();
        // the open task does not leak the street address
        DeliveryDto open = deliveries.openTasks(0, 50).getContent().stream()
                .filter(t -> t.id().equals(taskId)).findFirst().orElseThrow();
        assertThat(open.pickupAddress()).isNull();

        // cannot skip steps, and nobody but the assigned volunteer can update
        assertThatThrownBy(() -> deliveries.advance(volunteer.id(), taskId, DeliveryStatus.PICKED_UP))
                .isInstanceOf(AccessDeniedException.class);
        DeliveryDto assigned = deliveries.accept(volunteer.id(), taskId);
        assertThat(assigned.pickupAddress()).isNotNull();
        assertThatThrownBy(() -> deliveries.accept(otherVolunteer.id(), taskId)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> deliveries.advance(volunteer.id(), taskId, DeliveryStatus.DELIVERED))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Cannot change a task");
        assertThatThrownBy(() -> deliveries.advance(otherVolunteer.id(), taskId, DeliveryStatus.PICKED_UP))
                .isInstanceOf(AccessDeniedException.class);

        deliveries.advance(volunteer.id(), taskId, DeliveryStatus.PICKED_UP);
        deliveries.advance(volunteer.id(), taskId, DeliveryStatus.IN_TRANSIT);
        // the recipient can no longer cancel once the pickup has started
        assertThatThrownBy(() -> requests.cancel(ngo.id(), request.id())).isInstanceOf(BusinessRuleException.class);
        DeliveryDto done = deliveries.advance(volunteer.id(), taskId, DeliveryStatus.DELIVERED);

        assertThat(done.status()).isEqualTo("DELIVERED");
        assertThat(donations.get(donation.id(), donor.id(), Role.DONOR).status()).isEqualTo("COMPLETED");
        assertThat(requests.forRecipient(ngo.id(), 0, 10).getContent().get(0).status()).isEqualTo("COMPLETED");
    }

    @Test
    void cancellingAnAcceptedRequestBeforePickupReturnsTheQuantityAndClosesTheTask() {
        UserDto donor = IntegrationSupport.newUser(users, Role.DONOR);
        UserDto ngo = IntegrationSupport.newUser(users, Role.RECIPIENT);
        DonationDto donation = IntegrationSupport.newDonation(donations, donor.id(), 8);
        RequestForm form = new RequestForm();
        form.setQuantity(8);
        RequestDto request = requests.submit(ngo.id(), donation.id(), form);
        requests.accept(donor.id(), request.id());
        assertThat(donations.get(donation.id(), donor.id(), Role.DONOR).remainingQuantity()).isZero();

        requests.cancel(ngo.id(), request.id());

        DonationDto after = donations.get(donation.id(), donor.id(), Role.DONOR);
        assertThat(after.remainingQuantity()).isEqualTo(8);
        assertThat(after.status()).isEqualTo("AVAILABLE");
    }

    @Test
    void otherRecipientsCannotCancelSomeoneElsesRequest() {
        UserDto donor = IntegrationSupport.newUser(users, Role.DONOR);
        UserDto ngo = IntegrationSupport.newUser(users, Role.RECIPIENT);
        UserDto intruder = IntegrationSupport.newUser(users, Role.RECIPIENT);
        DonationDto donation = IntegrationSupport.newDonation(donations, donor.id(), 8);
        RequestForm form = new RequestForm();
        form.setQuantity(2);
        RequestDto request = requests.submit(ngo.id(), donation.id(), form);
        assertThatThrownBy(() -> requests.cancel(intruder.id(), request.id())).isInstanceOf(AccessDeniedException.class);
    }
}
