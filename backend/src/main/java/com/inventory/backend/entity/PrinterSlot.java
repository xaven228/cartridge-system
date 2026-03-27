package com.inventory.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
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
@Table(name = "printer_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrinterSlot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_id", nullable = false)
    private Printer printer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartridge_model_id")
    private CartridgeModel cartridgeModel;

    @Column(name = "previous_replacement_date")
    private LocalDate previousReplacementDate;

    @Column(name = "last_replacement_date")
    private LocalDate lastReplacementDate;

    @JsonIgnore
    @OneToMany(mappedBy = "printerSlot")
    @Builder.Default
    private List<PrinterInstallation> installations = new ArrayList<>();
}
