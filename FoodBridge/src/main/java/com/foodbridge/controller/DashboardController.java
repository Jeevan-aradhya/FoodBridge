package com.foodbridge.controller;

import com.foodbridge.dto.DonationDto;
import com.foodbridge.dto.RequestDto;
import com.foodbridge.enums.RequestStatus;
import com.foodbridge.security.AppUserDetails;
import com.foodbridge.service.DeliveryService;
import com.foodbridge.service.DonationService;
import com.foodbridge.service.MatchingService;
import com.foodbridge.service.RequestService;
import com.foodbridge.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;

/** Role dashboards plus the request/delivery action endpoints used by their buttons. */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final StatsService stats;
    private final DonationService donationService;
    private final RequestService requestService;
    private final DeliveryService deliveryService;
    private final MatchingService matching;

    // ------------------------------------------------------------------ donor

    @GetMapping("/donor/dashboard")
    public String donor(@AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("summary", stats.donorSummary(me.getId()));
        model.addAttribute("listings", donationService.forDonor(me.getId(), 0, 6).getContent());
        model.addAttribute("pendingRequests", requestService.forDonor(me.getId(), RequestStatus.PENDING, 0, 5).getContent());
        model.addAttribute("chartScope", "mine");
        return "dashboards/donor";
    }

    @GetMapping("/donor/donations")
    public String donorListings(@RequestParam(defaultValue = "0") int page, @AuthenticationPrincipal AppUserDetails me, Model model) {
        Page<DonationDto> p = donationService.forDonor(me.getId(), page, 10);
        model.addAttribute("page", p);
        model.addAttribute("basePath", "/donor/donations");
        model.addAttribute("baseQuery", "");
        return "dashboards/donor-listings";
    }

    @GetMapping("/donor/requests")
    public String donorRequests(@RequestParam(required = false) RequestStatus status, @RequestParam(defaultValue = "0") int page,
                                @AuthenticationPrincipal AppUserDetails me, Model model) {
        Page<RequestDto> p = requestService.forDonor(me.getId(), status, page, 10);
        model.addAttribute("page", p);
        model.addAttribute("statuses", RequestStatus.values());
        model.addAttribute("selected", status);
        model.addAttribute("basePath", "/donor/requests");
        model.addAttribute("baseQuery", status == null ? "" : "status=" + status.name() + "&");
        model.addAttribute("mode", "donor");
        return "donations/requests";
    }

    @GetMapping("/donor/report.csv")
    public ResponseEntity<byte[]> donorCsv(@AuthenticationPrincipal AppUserDetails me) {
        return csv(stats.donationsCsv(me.getId()), "my-donations.csv");
    }

    // ------------------------------------------------------------------ recipient

    @GetMapping("/recipient/dashboard")
    public String recipient(@AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("summary", stats.recipientSummary(me.getId()));
        model.addAttribute("suggestions", matching.suggest(me.getId(), 4));
        model.addAttribute("recent", requestService.forRecipient(me.getId(), 0, 5).getContent());
        return "dashboards/recipient";
    }

    @GetMapping("/requests/my")
    public String myRequests(@RequestParam(defaultValue = "0") int page, @AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("page", requestService.forRecipient(me.getId(), page, 10));
        model.addAttribute("basePath", "/requests/my");
        model.addAttribute("baseQuery", "");
        model.addAttribute("mode", "recipient");
        return "donations/requests";
    }

    // ------------------------------------------------------------------ request actions

    @PostMapping("/requests/{id}/accept")
    public String accept(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        requestService.accept(me.getId(), id);
        ra.addFlashAttribute("success", "Request accepted. Volunteers have been notified.");
        return "redirect:/donor/requests";
    }

    @PostMapping("/requests/{id}/reject")
    public String reject(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        requestService.reject(me.getId(), id);
        ra.addFlashAttribute("success", "Request declined.");
        return "redirect:/donor/requests";
    }

    @PostMapping("/requests/{id}/cancel")
    public String cancel(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        requestService.cancel(me.getId(), id);
        ra.addFlashAttribute("success", "Request cancelled.");
        return "redirect:/requests/my";
    }

    // ------------------------------------------------------------------ volunteer

    @GetMapping("/volunteer/dashboard")
    public String volunteer(@AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("summary", stats.volunteerSummary(me.getId()));
        model.addAttribute("openTasks", deliveryService.openTasks(0, 10).getContent());
        model.addAttribute("myTasks", deliveryService.mine(me.getId(), 0, 10).getContent());
        return "dashboards/volunteer";
    }

    @PostMapping("/volunteer/tasks/{id}/accept")
    public String acceptTask(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        deliveryService.accept(me.getId(), id);
        ra.addFlashAttribute("success", "Task accepted. Pickup and drop-off details are now visible.");
        return "redirect:/volunteer/dashboard";
    }

    @PostMapping("/volunteer/tasks/{id}/status")
    public String updateTask(@PathVariable Long id, @RequestParam com.foodbridge.enums.DeliveryStatus status,
                             @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        deliveryService.advance(me.getId(), id, status);
        ra.addFlashAttribute("success", "Task updated to " + status.name().replace('_', ' ').toLowerCase() + ".");
        return "redirect:/volunteer/dashboard";
    }

    // ------------------------------------------------------------------ tracking (all roles)

    @GetMapping("/tracking")
    public String tracking(@RequestParam(defaultValue = "0") int page, @AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("page", deliveryService.tracking(me.getId(), me.getRole(), page, 8));
        model.addAttribute("basePath", "/tracking");
        model.addAttribute("baseQuery", "");
        return "dashboards/tracking";
    }

    static ResponseEntity<byte[]> csv(String content, String filename) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        h.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(h).body(content.getBytes(StandardCharsets.UTF_8));
    }
}
