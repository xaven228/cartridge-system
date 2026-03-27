package com.inventory.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "printer_installations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrinterInstallation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_slot_id", nullable = false)
    private PrinterSlot printerSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartridge_id", nullable = false)
    private Cartridge cartridge;

    @Min(0)
    @Column(nullable = false)
    private Integer quantity;
}
