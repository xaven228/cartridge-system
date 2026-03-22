package com.inventory.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cartridges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cartridge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "inventory_code", nullable = false, unique = true)
    private String inventoryCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartridge_model_id", nullable = false)
    private CartridgeModel cartridgeModel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Min(0)
    @Column(nullable = false)
    private Integer quantity;

    @Builder.Default
    @Column(name = "refillable", nullable = false)
    private Boolean refillable = true;

    @Builder.Default
    @Column(name = "empty", nullable = false)
    private Boolean empty = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CartridgeStatus status;

    @Column(name = "refill_count", nullable = false)
    private Integer refillCount;

    @Column(name = "last_refill_date")
    private LocalDate lastRefillDate;

    @Column(length = 1000)
    private String comment;

    @JsonIgnore
    @OneToMany(mappedBy = "cartridge", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RefillHistory> refillHistory = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "cartridge", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PrinterInstallation> printerInstallations = new ArrayList<>();
}
