package com.foodbridge.config;

import com.foodbridge.repository.UserRepository;
import com.foodbridge.security.AccountStatusFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * Session-based security for a server-rendered app.
 * URL rules here are the first gate; services re-check ownership (second gate).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserRepository users) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/about", "/contact", "/login", "/register",
                        "/css/**", "/js/**", "/images/**", "/uploads/**", "/error", "/error/**",
                        "/actuator/health", "/actuator/health/**", "/actuator/info",
                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                        "/api/stats/impact", "/api/auth/register").permitAll()
                .requestMatchers("/donor/**", "/api/donations/mine", "/api/requests/incoming").hasRole("DONOR")
                .requestMatchers(HttpMethod.GET, "/donations", "/donations/{id}", "/api/donations", "/api/donations/{id}").permitAll()
                .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/donations").hasRole("DONOR")
                .requestMatchers(HttpMethod.PUT, "/api/donations/{id}").hasRole("DONOR")
                .requestMatchers(HttpMethod.DELETE, "/api/donations/{id}").hasRole("DONOR")
                .requestMatchers("/recipient/**", "/requests/my", "/api/requests/my").hasRole("RECIPIENT")
                .requestMatchers(HttpMethod.POST, "/donations/{id}/request", "/api/donations/{id}/requests").hasRole("RECIPIENT")
                .requestMatchers("/volunteer/**", "/api/deliveries/open").hasRole("VOLUNTEER")
                .requestMatchers(HttpMethod.POST, "/requests/{id}/accept", "/requests/{id}/reject",
                        "/api/requests/{id}/accept", "/api/requests/{id}/reject").hasRole("DONOR")
                .requestMatchers(HttpMethod.POST, "/requests/{id}/cancel", "/api/requests/{id}/cancel").hasRole("RECIPIENT")
                .requestMatchers(HttpMethod.POST, "/api/deliveries/{id}/accept", "/api/deliveries/{id}/status").hasRole("VOLUNTEER")
                .requestMatchers("/api/reports/**").hasAnyRole("DONOR", "ADMIN")
                .anyRequest().authenticated())
            // The register API is for non-browser clients, so it is exempt from CSRF. Everything else is protected.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/auth/register"))
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll())
            .exceptionHandling(ex -> ex.accessDeniedPage("/error/403"))
            .headers(h -> h
                .contentTypeOptions(c -> { })
                .frameOptions(f -> f.deny())
                .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
            .addFilterAfter(new AccountStatusFilter(users), SecurityContextHolderFilter.class);
        return http.build();
    }
}
