package com.foodbridge.controller.api;

import com.foodbridge.dto.DeliveryDto;
import com.foodbridge.dto.PageResponse;
import com.foodbridge.enums.DeliveryStatus;
import com.foodbridge.security.AppUserDetails;
import com.foodbridge.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
@Tag(name = "Pickup and delivery")
public class DeliveryApiController {

    private final DeliveryService deliveries;

    public record StatusUpdate(@NotNull DeliveryStatus status) { }

    @Operation(summary = "Open pickup tasks (volunteer). Street addresses are hidden until the task is accepted")
    @GetMapping("/open")
    public PageResponse<DeliveryDto> open(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return PageResponse.of(deliveries.openTasks(page, size));
    }

    @Operation(summary = "Deliveries that concern me (any role)")
    @GetMapping("/mine")
    public PageResponse<DeliveryDto> mine(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                          @AuthenticationPrincipal AppUserDetails me) {
        return PageResponse.of(deliveries.tracking(me.getId(), me.getRole(), page, size));
    }

    @Operation(summary = "Accept a pickup task (volunteer)")
    @PostMapping("/{id}/accept")
    public DeliveryDto accept(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        return deliveries.accept(me.getId(), id);
    }

    @Operation(summary = "Move a task to PICKED_UP, IN_TRANSIT or DELIVERED (assigned volunteer only)")
    @PostMapping("/{id}/status")
    public DeliveryDto status(@PathVariable Long id, @RequestBody StatusUpdate body, @AuthenticationPrincipal AppUserDetails me) {
        return deliveries.advance(me.getId(), id, body.status());
    }
}
