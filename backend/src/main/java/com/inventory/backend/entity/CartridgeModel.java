package com.inventory.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cartridge_models")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartridgeModel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "printer_model")
    private String printerModel;

    private String manufacturer;

    @Column(name = "color_type")
    private String colorType;

    @Column(name = "resource_pages")
    private Integer resourcePages;

    @Builder.Default
    @Column(name = "refillable", nullable = false)
    private Boolean refillable = true;

    @Builder.Default
    @Column(name = "minimum_quantity", nullable = false)
    private Integer minimumQuantity = 0;

    @JsonIgnore
    @OneToMany(mappedBy = "cartridgeModel")
    @Builder.Default
    private List<Cartridge> cartridges = new ArrayList<>();
}
