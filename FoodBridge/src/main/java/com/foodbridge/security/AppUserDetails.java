package com.foodbridge.security;

import com.foodbridge.entity.User;
import com.foodbridge.enums.AccountStatus;
import com.foodbridge.enums.Role;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** The logged-in user as Spring Security sees it (stored in the HTTP session). */
public class AppUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final Role role;
    private final boolean enabled;

    public AppUserDetails(User u) {
        this.id = u.getId();
        this.email = u.getEmail();
        this.passwordHash = u.getPasswordHash();
        this.fullName = u.getFullName();
        this.role = u.getRole();
        this.enabled = u.getStatus() == AccountStatus.ACTIVE;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
