package com.inventory.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "refill_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefillHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartridge_id", nullable = false)
    private Cartridge cartridge;

    @Column(name = "sent_at")
    private LocalDate sentAt;

    @Column(name = "returned_at")
    private LocalDate returnedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefillStatus status;

    @Column(nullable = false)
    private Integer quantity;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_by")
    private String createdBy;
}
