package com.foodbridge.controller;

import com.foodbridge.dto.NotificationDto;
import com.foodbridge.dto.PasswordForm;
import com.foodbridge.dto.ProfileForm;
import com.foodbridge.enums.FoodCategory;
import com.foodbridge.exception.BusinessRuleException;
import com.foodbridge.security.AppUserDetails;
import com.foodbridge.service.NotificationService;
import com.foodbridge.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    private final NotificationService notifications;

    @ModelAttribute("categories")
    public FoodCategory[] categories() {
        return FoodCategory.values();
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("form", userService.getProfile(me.getId()));
        model.addAttribute("account", userService.get(me.getId()));
        model.addAttribute("passwordForm", new PasswordForm());
        return "pages/profile";
    }

    @PostMapping("/profile")
    public String save(@Valid @ModelAttribute("form") ProfileForm form, BindingResult br,
                       @AuthenticationPrincipal AppUserDetails me, Model model, RedirectAttributes ra) {
        if (br.hasErrors()) {
            model.addAttribute("account", userService.get(me.getId()));
            model.addAttribute("passwordForm", new PasswordForm());
            return "pages/profile";
        }
        try {
            userService.updateProfile(me.getId(), form);
        } catch (BusinessRuleException ex) {
            br.reject("profile.invalid", ex.getMessage());
            model.addAttribute("account", userService.get(me.getId()));
            model.addAttribute("passwordForm", new PasswordForm());
            return "pages/profile";
        }
        ra.addFlashAttribute("success", "Profile saved.");
        return "redirect:/profile";
    }

    @PostMapping("/profile/password")
    public String password(@Valid @ModelAttribute("passwordForm") PasswordForm form, BindingResult br,
                           @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", br.getFieldError().getDefaultMessage());
            return "redirect:/profile";
        }
        userService.changePassword(me.getId(), form);
        ra.addFlashAttribute("success", "Password changed.");
        return "redirect:/profile";
    }

    // ------------------------------------------------------------------ notifications

    @GetMapping("/notifications")
    public String notificationsPage(@RequestParam(defaultValue = "0") int page, @AuthenticationPrincipal AppUserDetails me, Model model) {
        Page<NotificationDto> p = notifications.list(me.getId(), page, 15);
        model.addAttribute("page", p);
        model.addAttribute("basePath", "/notifications");
        model.addAttribute("baseQuery", "");
        return "pages/notifications";
    }

    @PostMapping("/notifications/read-all")
    public String readAll(@AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        notifications.markAllRead(me.getId());
        ra.addFlashAttribute("success", "All notifications marked as read.");
        return "redirect:/notifications";
    }

    @PostMapping("/notifications/{id}/read")
    public String readOne(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me) {
        notifications.markRead(me.getId(), id);
        return "redirect:/notifications";
    }
}
