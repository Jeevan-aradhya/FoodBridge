package com.foodbridge.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodbridge.enums.DeliveryStatus;
import com.foodbridge.enums.RequestStatus;
import com.foodbridge.util.CsvUtil;
import java.util.List;
import org.junit.jupiter.api.Test;

class StateAndUtilityTest {

    @Test
    void requestStatusTransitionsFollowTheRules() {
        assertThat(RequestStatus.PENDING.canTransitionTo(RequestStatus.ACCEPTED)).isTrue();
        assertThat(RequestStatus.PENDING.canTransitionTo(RequestStatus.COMPLETED)).isFalse();
        assertThat(RequestStatus.ACCEPTED.canTransitionTo(RequestStatus.COMPLETED)).isTrue();
        assertThat(RequestStatus.ACCEPTED.canTransitionTo(RequestStatus.REJECTED)).isFalse();
        for (RequestStatus terminal : new RequestStatus[]{RequestStatus.REJECTED, RequestStatus.CANCELLED, RequestStatus.COMPLETED}) {
            for (RequestStatus next : RequestStatus.values()) {
                assertThat(terminal.canTransitionTo(next)).as(terminal + " -> " + next).isFalse();
            }
        }
    }

    @Test
    void deliveryStatusMustGoStepByStep() {
        assertThat(DeliveryStatus.OPEN.canTransitionTo(DeliveryStatus.ASSIGNED)).isTrue();
        assertThat(DeliveryStatus.ASSIGNED.canTransitionTo(DeliveryStatus.PICKED_UP)).isTrue();
        assertThat(DeliveryStatus.PICKED_UP.canTransitionTo(DeliveryStatus.IN_TRANSIT)).isTrue();
        assertThat(DeliveryStatus.IN_TRANSIT.canTransitionTo(DeliveryStatus.DELIVERED)).isTrue();
        // skipping or going backwards is not allowed
        assertThat(DeliveryStatus.ASSIGNED.canTransitionTo(DeliveryStatus.DELIVERED)).isFalse();
        assertThat(DeliveryStatus.IN_TRANSIT.canTransitionTo(DeliveryStatus.PICKED_UP)).isFalse();
        assertThat(DeliveryStatus.DELIVERED.canTransitionTo(DeliveryStatus.ASSIGNED)).isFalse();
        assertThat(DeliveryStatus.PICKED_UP.canTransitionTo(DeliveryStatus.CANCELLED)).isFalse();
    }

    @Test
    void csvEscapingStopsFormulaInjectionAndQuotesSpecialCharacters() {
        String csv = CsvUtil.toCsv(List.of("name", "note"), List.of(List.of("=HYPERLINK(\"x\")", "a,b")));
        assertThat(csv).contains("'=HYPERLINK").contains("\"a,b\"");
    }

    @Test
    void uploadsAreIdentifiedByContentNotByFileName() {
        assertThat(FileStorageService.detectExtension(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0})).isEqualTo("jpg");
        assertThat(FileStorageService.detectExtension(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0})).isEqualTo("png");
        assertThat(FileStorageService.detectExtension("<?php echo 1; ?>".getBytes())).isNull();
    }
}
