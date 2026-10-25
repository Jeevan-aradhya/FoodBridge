package com.foodbridge.controller;

import com.foodbridge.dto.RegisterForm;
import com.foodbridge.enums.Role;
import com.foodbridge.exception.BusinessRuleException;
import com.foodbridge.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("form", new RegisterForm());
        return "auth/register";
    }

    @ModelAttribute("publicRoles")
    public Role[] publicRoles() {
        return new Role[]{Role.DONOR, Role.RECIPIENT, Role.VOLUNTEER};
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult br, RedirectAttributes ra) {
        if (br.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.register(form);
        } catch (BusinessRuleException ex) {
            br.reject("register.failed", ex.getMessage());
            return "auth/register";
        }
        ra.addFlashAttribute("success", "Your account is ready. Please sign in.");
        return "redirect:/login";
    }
}
