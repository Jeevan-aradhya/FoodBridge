package com.foodbridge.entity;

import com.foodbridge.enums.AccountStatus;
import com.foodbridge.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        indexes = @Index(name = "idx_users_role", columnList = "role"))
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String email;

    /** BCrypt hash. The plain password is never stored. */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(length = 80)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;
}
