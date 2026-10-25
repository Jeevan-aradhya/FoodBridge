package com.foodbridge.controller;

import com.foodbridge.dto.DonationDto;
import com.foodbridge.dto.DonationFilter;
import com.foodbridge.dto.DonationForm;
import com.foodbridge.dto.RequestForm;
import com.foodbridge.enums.FoodCategory;
import com.foodbridge.enums.FoodUnit;
import com.foodbridge.exception.BusinessRuleException;
import com.foodbridge.security.AppUserDetails;
import com.foodbridge.service.DonationService;
import com.foodbridge.service.RequestService;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Browse/search donations (public), donation details, and the donor's create/edit/remove pages. */
@Controller
@RequiredArgsConstructor
public class DonationController {

    private final DonationService donationService;
    private final RequestService requestService;

    @ModelAttribute("categories")
    public FoodCategory[] categories() {
        return FoodCategory.values();
    }

    @ModelAttribute("units")
    public FoodUnit[] units() {
        return FoodUnit.values();
    }

    // ------------------------------------------------------------ public browsing

    @GetMapping("/donations")
    public String list(@ModelAttribute("filter") DonationFilter filter, Model model) {
        Page<DonationDto> page = donationService.search(filter);
        model.addAttribute("page", page);
        model.addAttribute("basePath", "/donations");
        model.addAttribute("baseQuery", query(filter));
        return "donations/list";
    }

    @GetMapping("/donations/{id}")
    public String details(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, Model model) {
        DonationDto d = donationService.get(id, me == null ? null : me.getId(), me == null ? null : me.getRole());
        model.addAttribute("donation", d);
        model.addAttribute("requestForm", new RequestForm());
        model.addAttribute("isOwner", me != null && me.getId().equals(d.donorId()));
        return "donations/detail";
    }

    @PostMapping("/donations/{id}/request")
    public String request(@PathVariable Long id, @Valid @ModelAttribute("requestForm") RequestForm form,
                          BindingResult br, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        if (br.hasErrors()) {
            ra.addFlashAttribute("error", br.getFieldError().getDefaultMessage());
            return "redirect:/donations/" + id;
        }
        requestService.submit(me.getId(), id, form);
        ra.addFlashAttribute("success", "Request sent. The donor will review it shortly.");
        return "redirect:/requests/my";
    }

    @PostMapping("/donations/{id}/report")
    public String report(@PathVariable Long id, @RequestParam String reason,
                         @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        donationService.report(me.getId(), id, reason);
        ra.addFlashAttribute("success", "Thank you. An administrator will review this listing.");
        return "redirect:/donations/" + id;
    }

    // ------------------------------------------------------------ donor pages

    @GetMapping("/donor/donations/new")
    public String newForm(Model model) {
        model.addAttribute("form", new DonationForm());
        model.addAttribute("editing", false);
        return "donations/form";
    }

    @PostMapping("/donor/donations")
    public String create(@Valid @ModelAttribute("form") DonationForm form, BindingResult br,
                         @RequestParam(value = "image", required = false) MultipartFile image,
                         @AuthenticationPrincipal AppUserDetails me, Model model, RedirectAttributes ra) {
        model.addAttribute("editing", false);
        if (br.hasErrors()) {
            return "donations/form";
        }
        try {
            DonationDto d = donationService.create(me.getId(), form, image);
            ra.addFlashAttribute("success", "Your donation is live.");
            return "redirect:/donations/" + d.id();
        } catch (BusinessRuleException ex) {
            br.reject("donation.invalid", ex.getMessage());
            return "donations/form";
        }
    }

    @GetMapping("/donor/donations/{id}/edit")
    public String editForm(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("form", donationService.toForm(me.getId(), id));
        model.addAttribute("editing", true);
        model.addAttribute("donationId", id);
        return "donations/form";
    }

    @PostMapping("/donor/donations/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") DonationForm form, BindingResult br,
                         @RequestParam(value = "image", required = false) MultipartFile image,
                         @AuthenticationPrincipal AppUserDetails me, Model model, RedirectAttributes ra) {
        model.addAttribute("editing", true);
        model.addAttribute("donationId", id);
        if (br.hasErrors()) {
            return "donations/form";
        }
        try {
            donationService.update(me.getId(), id, form, image);
            ra.addFlashAttribute("success", "Donation updated.");
            return "redirect:/donations/" + id;
        } catch (BusinessRuleException ex) {
            br.reject("donation.invalid", ex.getMessage());
            return "donations/form";
        }
    }

    @PostMapping("/donor/donations/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        donationService.withdraw(me.getId(), id);
        ra.addFlashAttribute("success", "Donation removed.");
        return "redirect:/donor/dashboard";
    }

    /** Builds "q=..&category=..&" so that pagination links keep the active filters. */
    static String query(DonationFilter f) {
        StringBuilder sb = new StringBuilder();
        add(sb, "q", f.getQ());
        add(sb, "category", f.getCategory());
        add(sb, "city", f.getCity());
        add(sb, "minQuantity", f.getMinQuantity() == null ? null : String.valueOf(f.getMinQuantity()));
        add(sb, "availability", f.getAvailability());
        add(sb, "withinHours", f.getWithinHours() == null ? null : String.valueOf(f.getWithinHours()));
        add(sb, "sort", f.getSort());
        return sb.toString();
    }

    private static void add(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8)).append('&');
        }
    }
}
