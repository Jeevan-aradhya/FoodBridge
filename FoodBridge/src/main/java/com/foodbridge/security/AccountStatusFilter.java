package com.foodbridge.security;

import com.foodbridge.enums.AccountStatus;
import com.foodbridge.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Suspending an account must take effect immediately, not only at the next login.
 * On every request from a logged-in user we re-check the account status in the database.
 */
public class AccountStatusFilter extends OncePerRequestFilter {

    private final UserRepository users;

    public AccountStatusFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserDetails principal) {
            AccountStatus status = users.findStatusById(principal.getId()).orElse(null);
            if (status != AccountStatus.ACTIVE) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.sendRedirect(request.getContextPath() + "/login?suspended");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
