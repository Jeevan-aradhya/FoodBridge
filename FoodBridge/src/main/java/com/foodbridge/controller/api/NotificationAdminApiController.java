package com.foodbridge.controller.api;

import com.foodbridge.dto.AuditDto;
import com.foodbridge.dto.DonationDto;
import com.foodbridge.dto.NotificationDto;
import com.foodbridge.dto.PageResponse;
import com.foodbridge.dto.UserDto;
import com.foodbridge.dto.WeeklySeries;
import com.foodbridge.enums.AccountStatus;
import com.foodbridge.enums.Role;
import com.foodbridge.security.AppUserDetails;
import com.foodbridge.service.AuditService;
import com.foodbridge.service.DonationService;
import com.foodbridge.service.NotificationService;
import com.foodbridge.service.StatsService;
import com.foodbridge.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Notifications, reports and admin")
public class NotificationAdminApiController {

    private final NotificationService notifications;
    private final StatsService stats;
    private final UserService users;
    private final DonationService donations;
    private final AuditService audit;

    public record StatusBody(AccountStatus status) { }

    public record ReasonBody(String reason) { }

    // ---------------------------------------------------------------- notifications

    @Operation(summary = "My notifications")
    @GetMapping("/notifications")
    public PageResponse<NotificationDto> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "15") int size,
                                              @AuthenticationPrincipal AppUserDetails me) {
        return PageResponse.of(notifications.list(me.getId(), page, Math.min(size, 50)));
    }

    @Operation(summary = "Number of unread notifications")
    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unread(@AuthenticationPrincipal AppUserDetails me) {
        return Map.of("count", notifications.unreadCount(me.getId()));
    }

    @Operation(summary = "Mark one notification as read")
    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> read(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        notifications.markRead(me.getId(), id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- reports

    @Operation(summary = "Weekly activity for the chart: my own (donor) or the whole platform (admin)")
    @GetMapping("/reports/weekly")
    public WeeklySeries weekly(@AuthenticationPrincipal AppUserDetails me) {
        return stats.weekly(me.getRole() == Role.ADMIN ? null : me.getId());
    }

    // ---------------------------------------------------------------- admin

    @Operation(summary = "Platform summary (admin)")
    @GetMapping("/admin/stats")
    public Map<String, Long> adminStats() {
        return stats.adminSummary();
    }

    @Operation(summary = "List users (admin)")
    @GetMapping("/admin/users")
    public PageResponse<UserDto> users(@RequestParam(required = false) String q, @RequestParam(required = false) Role role,
                                       @RequestParam(required = false) AccountStatus status,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "15") int size) {
        return PageResponse.of(users.adminList(q, role, status, page, size));
    }

    @Operation(summary = "Suspend or reactivate a user (admin). Admins cannot suspend themselves or other admins")
    @PatchMapping("/admin/users/{id}/status")
    public UserDto setStatus(@PathVariable Long id, @RequestBody StatusBody body, @AuthenticationPrincipal AppUserDetails me) {
        return users.setStatus(me.getId(), id, body.status());
    }

    @Operation(summary = "All listings, optionally only reported ones (admin)")
    @GetMapping("/admin/donations")
    public PageResponse<DonationDto> adminDonations(@RequestParam(defaultValue = "false") boolean reported,
                                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
        return PageResponse.of(donations.adminList(reported, page, size));
    }

    @Operation(summary = "Disable a listing with a reason (admin)")
    @PostMapping("/admin/donations/{id}/disable")
    public ResponseEntity<Void> disable(@PathVariable Long id, @RequestBody ReasonBody body, @AuthenticationPrincipal AppUserDetails me) {
        donations.disable(me.getId(), id, body.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Re-enable a disabled listing (admin)")
    @PostMapping("/admin/donations/{id}/enable")
    public ResponseEntity<Void> enable(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        donations.enable(me.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Audit trail (admin)")
    @GetMapping("/admin/audit")
    public PageResponse<AuditDto> auditTrail(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(audit.list(page, Math.min(size, 100)));
    }
}
