package com.foodbridge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Append-only trail of important actions. No foreign key on purpose: logs outlive users. */
@Entity
@Table(name = "audit_logs", indexes = @Index(name = "idx_audit_created", columnList = "created_at"))
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends BaseEntity {

    private Long actorId;

    @Column(length = 150)
    private String actorEmail;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 40)
    private String entityType;

    private Long entityId;

    @Column(length = 500)
    private String details;
}
