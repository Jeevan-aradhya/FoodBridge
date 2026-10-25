package com.foodbridge.service;

import com.foodbridge.dto.PasswordForm;
import com.foodbridge.dto.ProfileForm;
import com.foodbridge.dto.RegisterForm;
import com.foodbridge.dto.UserDto;
import com.foodbridge.entity.OrganizationProfile;
import com.foodbridge.entity.User;
import com.foodbridge.entity.VolunteerProfile;
import com.foodbridge.enums.AccountStatus;
import com.foodbridge.enums.FoodCategory;
import com.foodbridge.enums.Role;
import com.foodbridge.exception.BusinessRuleException;
import com.foodbridge.exception.ResourceNotFoundException;
import com.foodbridge.repository.OrganizationProfileRepository;
import com.foodbridge.repository.UserRepository;
import com.foodbridge.repository.VolunteerProfileRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final OrganizationProfileRepository orgs;
    private final VolunteerProfileRepository volunteers;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final DtoMapper mapper;

    /** Public registration. ADMIN accounts can never be created here. */
    @Transactional
    public UserDto register(RegisterForm f) {
        if (f.getRole() == null || f.getRole() == Role.ADMIN) {
            throw new BusinessRuleException("Choose Donor, NGO / Recipient or Volunteer.");
        }
        String email = f.getEmail().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("An account with this email already exists.");
        }
        if (f.getRole() == Role.RECIPIENT) {
            if (isBlank(f.getOrganizationName())) {
                throw new BusinessRuleException("Organization name is required for NGOs and recipients.");
            }
            if (isBlank(f.getAddress())) {
                throw new BusinessRuleException("Organization address is required so volunteers know where to deliver.");
            }
        }
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(f.getPassword()));   // BCrypt
        u.setFullName(f.getFullName().trim());
        u.setPhone(f.getPhone());
        u.setCity(f.getCity().trim());
        u.setRole(f.getRole());
        u.setStatus(AccountStatus.ACTIVE);
        users.save(u);

        if (f.getRole() == Role.DONOR || f.getRole() == Role.RECIPIENT) {
            OrganizationProfile o = new OrganizationProfile();
            o.setUser(u);
            o.setOrganizationName(isBlank(f.getOrganizationName()) ? u.getFullName() : f.getOrganizationName().trim());
            o.setOrganizationType(f.getOrganizationType());
            o.setAddress(f.getAddress());
            o.setCity(u.getCity());
            orgs.save(o);
        } else {
            VolunteerProfile v = new VolunteerProfile();
            v.setUser(u);
            v.setVehicleType(f.getVehicleType());
            v.setServiceArea(isBlank(f.getServiceArea()) ? u.getCity() : f.getServiceArea());
            volunteers.save(v);
        }
        audit.log(u, "USER_REGISTERED", "User", u.getId(), "Role " + u.getRole());
        return mapper.toUser(u);
    }

    /** Used by the optional admin bootstrap and by demo seeding. Never exposed to the public. */
    @Transactional
    public UserDto createAdmin(String email, String fullName, String rawPassword) {
        User u = new User();
        u.setEmail(email.trim().toLowerCase());
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setFullName(fullName);
        u.setCity("Bengaluru");
        u.setRole(Role.ADMIN);
        u.setStatus(AccountStatus.ACTIVE);
        users.save(u);
        audit.log(null, "ADMIN_CREATED", "User", u.getId(), email);
        return mapper.toUser(u);
    }

    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return mapper.toUser(users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }

    @Transactional(readOnly = true)
    public ProfileForm getProfile(Long userId) {
        User u = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ProfileForm f = new ProfileForm();
        f.setFullName(u.getFullName());
        f.setPhone(u.getPhone());
        f.setCity(u.getCity());
        orgs.findByUserId(userId).ifPresent(o -> {
            f.setOrganizationName(o.getOrganizationName());
            f.setOrganizationType(o.getOrganizationType());
            f.setAddress(o.getAddress());
            f.setRegistrationNumber(o.getRegistrationNumber());
            f.setDescription(o.getDescription());
            f.setPreferredCategories(parseCategories(o.getPreferredCategories()));
        });
        volunteers.findByUserId(userId).ifPresent(v -> {
            f.setVehicleType(v.getVehicleType());
            f.setAvailability(v.getAvailability());
            f.setServiceArea(v.getServiceArea());
        });
        return f;
    }

    @Transactional
    public void updateProfile(Long userId, ProfileForm f) {
        User u = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        u.setFullName(f.getFullName().trim());
        u.setPhone(f.getPhone());
        u.setCity(f.getCity().trim());
        orgs.findByUserId(userId).ifPresent(o -> {
            if (u.getRole() == Role.RECIPIENT && isBlank(f.getOrganizationName())) {
                throw new BusinessRuleException("Organization name is required.");
            }
            o.setOrganizationName(isBlank(f.getOrganizationName()) ? u.getFullName() : f.getOrganizationName().trim());
            o.setOrganizationType(f.getOrganizationType());
            o.setAddress(f.getAddress());
            o.setCity(u.getCity());
            o.setRegistrationNumber(f.getRegistrationNumber());
            o.setDescription(f.getDescription());
            o.setPreferredCategories(f.getPreferredCategories() == null ? null
                    : f.getPreferredCategories().stream().map(Enum::name).collect(Collectors.joining(",")));
        });
        volunteers.findByUserId(userId).ifPresent(v -> {
            v.setVehicleType(f.getVehicleType());
            v.setAvailability(f.getAvailability());
            v.setServiceArea(f.getServiceArea());
        });
        audit.log(u, "PROFILE_UPDATED", "User", u.getId(), null);
    }

    @Transactional
    public void changePassword(Long userId, PasswordForm f) {
        User u = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!encoder.matches(f.getCurrentPassword(), u.getPasswordHash())) {
            throw new BusinessRuleException("Your current password is incorrect.");
        }
        u.setPasswordHash(encoder.encode(f.getNewPassword()));
        audit.log(u, "PASSWORD_CHANGED", "User", u.getId(), null);
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public Page<UserDto> adminList(String q, Role role, AccountStatus status, int page, int size) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("fullName")), like), cb.like(cb.lower(root.get("email")), like)));
            }
            if (role != null) {
                p.add(cb.equal(root.get("role"), role));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return users.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"))).map(mapper::toUser);
    }

    /** Safeguards: admins cannot suspend themselves or other admins. */
    @Transactional
    public UserDto setStatus(Long adminId, Long userId, AccountStatus status) {
        User admin = users.findById(adminId).orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        if (admin.getRole() != Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Admins only");
        }
        User target = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (target.getId().equals(adminId)) {
            throw new BusinessRuleException("You cannot change the status of your own account.");
        }
        if (target.getRole() == Role.ADMIN) {
            throw new BusinessRuleException("Administrator accounts cannot be suspended here.");
        }
        target.setStatus(status);
        audit.log(admin, status == AccountStatus.SUSPENDED ? "USER_SUSPENDED" : "USER_REACTIVATED",
                "User", target.getId(), target.getEmail());
        return mapper.toUser(target);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    static List<FoodCategory> parseCategories(String csv) {
        List<FoodCategory> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        Arrays.stream(csv.split(",")).map(String::trim).forEach(s -> {
            try {
                out.add(FoodCategory.valueOf(s));
            } catch (IllegalArgumentException ignored) {
                // skip unknown values
            }
        });
        return out;
    }
}
