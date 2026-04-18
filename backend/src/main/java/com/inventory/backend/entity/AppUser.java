package com.inventory.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @NotBlank
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @NotBlank
    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private UserRole role;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "can_view_catalog", nullable = false)
    @Builder.Default
    private Boolean canViewCatalog = true;

    @Column(name = "can_edit_catalog", nullable = false)
    @Builder.Default
    private Boolean canEditCatalog = false;

    @Column(name = "can_operate", nullable = false)
    @Builder.Default
    private Boolean canOperate = false;

    @Column(name = "can_view_logs", nullable = false)
    @Builder.Default
    private Boolean canViewLogs = false;

    @Column(name = "can_export_reports", nullable = false)
    @Builder.Default
    private Boolean canExportReports = false;

    @Column(name = "can_manage_users", nullable = false)
    @Builder.Default
    private Boolean canManageUsers = false;

    @Column(name = "can_manage_thresholds", nullable = false)
    @Builder.Default
    private Boolean canManageThresholds = false;

    @Column(name = "can_manual_datetime", nullable = false)
    @Builder.Default
    private Boolean canManualDatetime = false;
}
