package com.foodbridge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodbridge.dto.DonationDto;
import com.foodbridge.dto.UserDto;
import com.foodbridge.entity.User;
import com.foodbridge.enums.Role;
import com.foodbridge.repository.UserRepository;
import com.foodbridge.security.AppUserDetails;
import com.foodbridge.service.DonationService;
import com.foodbridge.service.UserService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Full application context (H2 database) + MockMvc: registration, login, authorization and REST API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAndApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired DonationService donationService;
    @Autowired UserRepository userRepository;
    @Autowired ObjectMapper json;

    private AppUserDetails principal(UserDto dto) {
        User u = userRepository.findById(dto.id()).orElseThrow();
        return new AppUserDetails(u);
    }

    // ------------------------------------------------------------ registration and login

    @Test
    void registrationStoresABcryptHashNotThePlainPassword() throws Exception {
        String email = "new-" + UUID.randomUUID() + "@foodbridge.test";
        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Priya Sharma").param("email", email).param("password", "Secret123")
                        .param("city", "Bengaluru").param("role", "DONOR").param("organizationName", "Priya Cafe"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login"));
        User saved = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo("Secret123");
        assertThat(saved.getRole()).isEqualTo(Role.DONOR);
    }

    @Test
    void registrationRejectsAWeakPasswordAndShowsTheFormAgain() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Weak").param("email", "weak-" + UUID.randomUUID() + "@foodbridge.test")
                        .param("password", "short").param("city", "Bengaluru").param("role", "DONOR"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"));
    }

    @Test
    void adminRoleCannotBeSelectedInTheRegistrationForm() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Sneaky").param("email", "sneaky-" + UUID.randomUUID() + "@foodbridge.test")
                        .param("password", "Secret123").param("city", "Bengaluru").param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        UserDto existing = IntegrationSupport.newUser(userService, Role.DONOR);
        mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Copy").param("email", existing.email()).param("password", "Secret123")
                        .param("city", "Bengaluru").param("role", "DONOR"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"));
    }

    @Test
    void loginSucceedsWithCorrectPasswordAndFailsWithWrongOne() throws Exception {
        UserDto u = IntegrationSupport.newUser(userService, Role.VOLUNTEER);
        mvc.perform(formLogin("/login").user(u.email()).password(IntegrationSupport.PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/dashboard"));
        mvc.perform(formLogin("/login").user(u.email()).password("wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login?error"));
    }

    // ------------------------------------------------------------ role-based authorization

    @Test
    void anonymousUsersAreSentToTheLoginPage() throws Exception {
        mvc.perform(get("/donor/dashboard")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://localhost/login"));
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/notifications")).andExpect(status().is3xxRedirection());
    }

    @Test
    void publicPagesAreOpen() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/donations")).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void donorCannotOpenAdminPages() throws Exception {
        UserDto donor = IntegrationSupport.newUser(userService, Role.DONOR);
        mvc.perform(get("/admin").with(user(principal(donor)))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/users").with(user(principal(donor)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/stats").with(user(principal(donor)))).andExpect(status().isForbidden());
    }

    @Test
    void recipientCannotCreateDonationsAndVolunteerCannotRequestFood() throws Exception {
        UserDto recipient = IntegrationSupport.newUser(userService, Role.RECIPIENT);
        UserDto volunteer = IntegrationSupport.newUser(userService, Role.VOLUNTEER);
        mvc.perform(get("/donor/donations/new").with(user(principal(recipient)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/donations").with(user(principal(recipient))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(IntegrationSupport.donationForm(5, 3))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/donations/1/request").with(user(principal(volunteer))).with(csrf()).param("quantity", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void stateChangingRequestsWithoutACsrfTokenAreRejected() throws Exception {
        UserDto donor = IntegrationSupport.newUser(userService, Role.DONOR);
        mvc.perform(post("/donor/donations/1/delete").with(user(principal(donor)))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ REST API

    @Test
    void publicSearchReturnsPagedJsonWithoutTheStreetAddress() throws Exception {
        UserDto donor = IntegrationSupport.newUser(userService, Role.DONOR);
        IntegrationSupport.newDonation(donationService, donor.id(), 12);
        mvc.perform(get("/api/donations").param("q", "Test curry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.content[0].pickupAddress").doesNotExist());
    }

    @Test
    void apiRejectsAnInvalidDonationWithFieldErrors() throws Exception {
        UserDto donor = IntegrationSupport.newUser(userService, Role.DONOR);
        mvc.perform(post("/api/donations").with(user(principal(donor))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.quantity").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void donorCanCreateADonationThroughTheApi() throws Exception {
        UserDto donor = IntegrationSupport.newUser(userService, Role.DONOR);
        mvc.perform(post("/api/donations").with(user(principal(donor))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(IntegrationSupport.donationForm(15, 4))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.remainingQuantity").value(15))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void apiRefusesAPastPickupDeadlineWithA409() throws Exception {
        UserDto donor = IntegrationSupport.newUser(userService, Role.DONOR);
        var form = IntegrationSupport.donationForm(5, 3);
        form.setPickupDeadline(java.time.LocalDateTime.now().minusHours(1));
        mvc.perform(post("/api/donations").with(user(principal(donor))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(form)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Pickup deadline must be in the future."));
    }

    @Test
    void aDonorCannotEditAnotherDonorsDonation() throws Exception {
        UserDto owner = IntegrationSupport.newUser(userService, Role.DONOR);
        UserDto other = IntegrationSupport.newUser(userService, Role.DONOR);
        DonationDto d = IntegrationSupport.newDonation(donationService, owner.id(), 10);
        mvc.perform(put("/api/donations/" + d.id()).with(user(principal(other))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(IntegrationSupport.donationForm(10, 3))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void unknownDonationGivesAConsistent404Body() throws Exception {
        mvc.perform(get("/api/donations/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/donations/999999"));
    }

    @Test
    void recipientRequestFlowOverTheApiRespectsRemainingQuantity() throws Exception {
        UserDto donor = IntegrationSupport.newUser(userService, Role.DONOR);
        UserDto recipient = IntegrationSupport.newUser(userService, Role.RECIPIENT);
        DonationDto d = IntegrationSupport.newDonation(donationService, donor.id(), 10);

        mvc.perform(post("/api/donations/" + d.id() + "/requests").with(user(principal(recipient))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":50}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Only 10")));

        mvc.perform(post("/api/donations/" + d.id() + "/requests").with(user(principal(recipient))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void thePublicImpactEndpointNeedsNoLogin() throws Exception {
        mvc.perform(get("/api/stats/impact")).andExpect(status().isOk()).andExpect(jsonPath("$.totalDonations").isNumber());
    }
}
