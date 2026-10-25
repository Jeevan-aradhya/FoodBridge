package com.foodbridge.controller;

import com.foodbridge.dto.ContactForm;
import com.foodbridge.enums.Role;
import com.foodbridge.security.AppUserDetails;
import com.foodbridge.service.DonationService;
import com.foodbridge.service.NotificationService;
import com.foodbridge.service.StatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final StatsService stats;
    private final DonationService donations;
    private final NotificationService notifications;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("impact", stats.impact());
        model.addAttribute("closingSoon", donations.closingSoon());
        return "index";
    }

    @GetMapping("/about")
    public String about() {
        return "pages/about";
    }

    @GetMapping("/contact")
    public String contact(Model model) {
        model.addAttribute("form", new ContactForm());
        return "pages/contact";
    }

    /** Contact messages are delivered as in-app notifications to all administrators. */
    @PostMapping("/contact")
    public String sendContact(@Valid @ModelAttribute("form") ContactForm form, BindingResult br, RedirectAttributes ra) {
        if (br.hasErrors()) {
            return "pages/contact";
        }
        notifications.notifyRole(Role.ADMIN, "Contact message from " + form.getName(),
                form.getEmail() + ": " + form.getMessage(), "/admin");
        ra.addFlashAttribute("success", "Thanks, your message was sent. We will reply by email.");
        return "redirect:/contact";
    }

    /** After login every role lands on its own dashboard. */
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserDetails me) {
        Role role = me.getRole();
        return switch (role) {
            case DONOR -> "redirect:/donor/dashboard";
            case RECIPIENT -> "redirect:/recipient/dashboard";
            case VOLUNTEER -> "redirect:/volunteer/dashboard";
            case ADMIN -> "redirect:/admin";
        };
    }
}
