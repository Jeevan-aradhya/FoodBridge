package com.foodbridge.controller;

import com.foodbridge.enums.AccountStatus;
import com.foodbridge.enums.RequestStatus;
import com.foodbridge.enums.Role;
import com.foodbridge.security.AppUserDetails;
import com.foodbridge.service.AuditService;
import com.foodbridge.service.DeliveryService;
import com.foodbridge.service.DonationService;
import com.foodbridge.service.RequestService;
import com.foodbridge.service.StatsService;
import com.foodbridge.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Everything under /admin/** requires ROLE_ADMIN (SecurityConfig) and the services check it again. */
@Controller
@org.springframework.web.bind.annotation.RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final DonationService donationService;
    private final RequestService requestService;
    private final DeliveryService deliveryService;
    private final StatsService stats;
    private final AuditService audit;

    public AdminController(UserService userService, DonationService donationService, RequestService requestService,
                           DeliveryService deliveryService, StatsService stats, AuditService audit) {
        this.userService = userService;
        this.donationService = donationService;
        this.requestService = requestService;
        this.deliveryService = deliveryService;
        this.stats = stats;
        this.audit = audit;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("summary", stats.adminSummary());
        model.addAttribute("impact", stats.impact());
        model.addAttribute("recentAudit", audit.list(0, 8).getContent());
        model.addAttribute("chartScope", "all");
        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String users(@RequestParam(required = false) String q, @RequestParam(required = false) Role role,
                        @RequestParam(required = false) AccountStatus status, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", userService.adminList(q, role, status, page, 15));
        model.addAttribute("roles", Role.values());
        model.addAttribute("q", q);
        model.addAttribute("selectedRole", role);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("basePath", "/admin/users");
        model.addAttribute("baseQuery", (q == null || q.isBlank() ? "" : "q=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8) + "&")
                + (role == null ? "" : "role=" + role.name() + "&") + (status == null ? "" : "status=" + status.name() + "&"));
        return "admin/users";
    }

    @PostMapping("/users/{id}/status")
    public String setStatus(@PathVariable Long id, @RequestParam AccountStatus status,
                            @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        userService.setStatus(me.getId(), id, status);
        ra.addFlashAttribute("success", status == AccountStatus.SUSPENDED ? "Account suspended." : "Account reactivated.");
        return "redirect:/admin/users";
    }

    @GetMapping("/donations")
    public String donations(@RequestParam(defaultValue = "false") boolean reported, @RequestParam(defaultValue = "0") int page,
                            @RequestParam(required = false) RequestStatus requestStatus, Model model) {
        model.addAttribute("page", donationService.adminList(reported, page, 12));
        model.addAttribute("requestsPage", requestService.forAdmin(requestStatus, 0, 10));
        model.addAttribute("statuses", RequestStatus.values());
        model.addAttribute("selectedRequestStatus", requestStatus);
        model.addAttribute("reportedOnly", reported);
        model.addAttribute("tasks", deliveryService.tracking(0L, Role.ADMIN, 0, 8).getContent());
        model.addAttribute("basePath", "/admin/donations");
        model.addAttribute("baseQuery", reported ? "reported=true&" : "");
        return "admin/donations";
    }

    @PostMapping("/donations/{id}/disable")
    public String disable(@PathVariable Long id, @RequestParam String reason,
                          @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        donationService.disable(me.getId(), id, reason);
        ra.addFlashAttribute("success", "Listing disabled and the donor was notified.");
        return "redirect:/admin/donations";
    }

    @PostMapping("/donations/{id}/enable")
    public String enable(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        donationService.enable(me.getId(), id);
        ra.addFlashAttribute("success", "Listing re-enabled.");
        return "redirect:/admin/donations";
    }

    @GetMapping("/audit")
    public String auditPage(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", audit.list(page, 20));
        model.addAttribute("basePath", "/admin/audit");
        model.addAttribute("baseQuery", "");
        return "admin/audit";
    }

    @GetMapping("/report.csv")
    public ResponseEntity<byte[]> csv() {
        return DashboardController.csv(stats.donationsCsv(null), "all-donations.csv");
    }
}
