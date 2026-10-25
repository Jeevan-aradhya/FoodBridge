package com.foodbridge.controller.api;

import com.foodbridge.dto.DonationDto;
import com.foodbridge.dto.DonationFilter;
import com.foodbridge.dto.DonationForm;
import com.foodbridge.dto.PageResponse;
import com.foodbridge.dto.RequestDto;
import com.foodbridge.dto.RequestForm;
import com.foodbridge.security.AppUserDetails;
import com.foodbridge.service.DonationService;
import com.foodbridge.service.RequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Donations and requests")
public class DonationApiController {

    private final DonationService donations;
    private final RequestService requests;

    @Operation(summary = "Search donations (public). Supports q, category, city, minQuantity, availability, withinHours, sort, page, size")
    @GetMapping("/donations")
    public PageResponse<DonationDto> search(@ModelAttribute DonationFilter filter) {
        return PageResponse.of(donations.search(filter));
    }

    @Operation(summary = "Donation details. Street address is only returned to people involved in the donation")
    @GetMapping("/donations/{id}")
    public DonationDto get(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        return donations.get(id, me == null ? null : me.getId(), me == null ? null : me.getRole());
    }

    @Operation(summary = "My donations (donor)")
    @GetMapping("/donations/mine")
    public PageResponse<DonationDto> mine(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                          @AuthenticationPrincipal AppUserDetails me) {
        return PageResponse.of(donations.forDonor(me.getId(), page, size));
    }

    @Operation(summary = "Create a donation (donor)")
    @PostMapping("/donations")
    public ResponseEntity<DonationDto> create(@Valid @RequestBody DonationForm form, @AuthenticationPrincipal AppUserDetails me) {
        return ResponseEntity.status(HttpStatus.CREATED).body(donations.create(me.getId(), form, null));
    }

    @Operation(summary = "Update my donation (donor, owner only)")
    @PutMapping("/donations/{id}")
    public DonationDto update(@PathVariable Long id, @Valid @RequestBody DonationForm form, @AuthenticationPrincipal AppUserDetails me) {
        return donations.update(me.getId(), id, form, null);
    }

    @Operation(summary = "Remove my donation (donor, owner only)")
    @DeleteMapping("/donations/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        donations.withdraw(me.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Request a donation (recipient)")
    @PostMapping("/donations/{id}/requests")
    public ResponseEntity<RequestDto> request(@PathVariable Long id, @Valid @RequestBody RequestForm form,
                                              @AuthenticationPrincipal AppUserDetails me) {
        return ResponseEntity.status(HttpStatus.CREATED).body(requests.submit(me.getId(), id, form));
    }

    @Operation(summary = "My requests (recipient)")
    @GetMapping("/requests/my")
    public PageResponse<RequestDto> myRequests(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                               @AuthenticationPrincipal AppUserDetails me) {
        return PageResponse.of(requests.forRecipient(me.getId(), page, size));
    }

    @Operation(summary = "Requests for my donations (donor)")
    @GetMapping("/requests/incoming")
    public PageResponse<RequestDto> incoming(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                             @RequestParam(required = false) com.foodbridge.enums.RequestStatus status,
                                             @AuthenticationPrincipal AppUserDetails me) {
        return PageResponse.of(requests.forDonor(me.getId(), status, page, size));
    }

    @Operation(summary = "Accept a request (donor). Reserves the quantity")
    @PostMapping("/requests/{id}/accept")
    public RequestDto accept(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        return requests.accept(me.getId(), id);
    }

    @Operation(summary = "Reject a request (donor)")
    @PostMapping("/requests/{id}/reject")
    public RequestDto reject(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        return requests.reject(me.getId(), id);
    }

    @Operation(summary = "Cancel a request (recipient)")
    @PostMapping("/requests/{id}/cancel")
    public RequestDto cancel(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        return requests.cancel(me.getId(), id);
    }
}
