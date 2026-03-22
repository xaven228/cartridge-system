package com.inventory.backend.service;

import com.inventory.backend.dto.CartridgeResponse;
import com.inventory.backend.dto.CreateCartridgeRequest;
import com.inventory.backend.dto.InstallCartridgeRequest;
import com.inventory.backend.entity.Cartridge;
import com.inventory.backend.entity.CartridgeModel;
import com.inventory.backend.entity.CartridgeStatus;
import com.inventory.backend.entity.Department;
import com.inventory.backend.entity.Printer;
import com.inventory.backend.entity.PrinterInstallation;
import com.inventory.backend.repository.CartridgeModelRepository;
import com.inventory.backend.repository.CartridgeRepository;
import com.inventory.backend.repository.DepartmentRepository;
import com.inventory.backend.repository.PrinterInstallationRepository;
import com.inventory.backend.repository.PrinterRepository;
import com.inventory.backend.repository.RefillHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartridgeServiceTest {

    @Mock
    private CartridgeRepository cartridgeRepository;

    @Mock
    private CartridgeModelRepository cartridgeModelRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PrinterRepository printerRepository;

    @Mock
    private PrinterInstallationRepository printerInstallationRepository;

    @Mock
    private RefillHistoryRepository refillHistoryRepository;

    @Mock
    private ActionLogService actionLogService;

    @InjectMocks
    private CartridgeService cartridgeService;

    @Test
    void createShouldReturnResponseWithLocalDateTimeAuditFields() {
        CartridgeModel model = CartridgeModel.builder()
                .id(10L)
                .name("HP 83A")
                .build();

        Department department = Department.builder()
                .id(20L)
                .name("Склад")
                .build();

        CreateCartridgeRequest request = new CreateCartridgeRequest();
        request.setCartridgeModelId(model.getId());
        request.setQuantity(2);
        request.setRefillable(true);
        request.setStatus(CartridgeStatus.IN_STOCK);
        request.setComment("new item");

        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 21, 15, 0, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 21, 15, 1, 0);

        Cartridge saved = Cartridge.builder()
                .id(100L)
                .inventoryCode("CRT-A1B2C3D4")
                .cartridgeModel(model)
                .department(department)
                .quantity(request.getQuantity())
                .status(request.getStatus())
                .refillCount(0)
                .comment(request.getComment())
                .build();
        saved.setCreatedAt(createdAt);
        saved.setUpdatedAt(updatedAt);

        when(cartridgeModelRepository.findById(model.getId())).thenReturn(Optional.of(model));
        when(departmentRepository.findByNameIgnoreCase("Склад")).thenReturn(Optional.of(department));
        when(cartridgeRepository.existsByInventoryCodeIgnoreCase(any())).thenReturn(false);
        when(cartridgeRepository.save(any(Cartridge.class))).thenReturn(saved);
        when(printerInstallationRepository.findByCartridgeId(saved.getId())).thenReturn(java.util.List.of());

        CartridgeResponse response = cartridgeService.create(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
        assertThat(response.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(response.getInventoryCode()).isEqualTo("CRT-A1B2C3D4");
        assertThat(response.getDepartmentId()).isEqualTo(20L);
        assertThat(response.getCartridgeModelId()).isEqualTo(10L);
    }

    @Test
    void installShouldSplitAggregateStockRowAndLeaveRestInStock() {
        CartridgeModel model = CartridgeModel.builder()
                .id(10L)
                .name("TK-1120")
                .build();

        Department department = Department.builder()
                .id(20L)
                .name("Склад")
                .build();

        Cartridge stockCartridge = Cartridge.builder()
                .id(100L)
                .inventoryCode("CRT-STOCK")
                .cartridgeModel(model)
                .department(department)
                .quantity(3)
                .refillable(true)
                .empty(false)
                .status(CartridgeStatus.IN_STOCK)
                .refillCount(0)
                .build();

        Printer printer = Printer.builder()
                .id(30L)
                .name("IT / Точка 1")
                .cartridgeModel(model)
                .build();

        InstallCartridgeRequest request = new InstallCartridgeRequest();
        request.setPrinterId(printer.getId());
        request.setQuantity(1);
        request.setComment("install");

        AtomicLong generatedId = new AtomicLong(500L);

        when(cartridgeRepository.findById(stockCartridge.getId())).thenReturn(Optional.of(stockCartridge));
        when(printerRepository.findById(printer.getId())).thenReturn(Optional.of(printer));
        when(printerInstallationRepository.findFirstByPrinterIdAndQuantityGreaterThan(printer.getId(), 0))
                .thenReturn(Optional.empty());
        when(cartridgeRepository.existsByInventoryCodeIgnoreCase(any())).thenReturn(false);
        when(cartridgeRepository.save(any(Cartridge.class))).thenAnswer(invocation -> {
            Cartridge saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(generatedId.getAndIncrement());
            }
            return saved;
        });
        when(printerInstallationRepository.save(any(PrinterInstallation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printerRepository.save(any(Printer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printerInstallationRepository.findByCartridgeId(eq(500L))).thenReturn(List.of(
                PrinterInstallation.builder()
                        .printer(printer)
                        .cartridge(stockCartridge)
                        .quantity(1)
                        .build()
        ));

        CartridgeResponse response = cartridgeService.installToPrinter(stockCartridge.getId(), request);

        assertThat(stockCartridge.getQuantity()).isEqualTo(2);
        assertThat(response.getId()).isEqualTo(500L);
        assertThat(response.getStatus()).isEqualTo(CartridgeStatus.INSTALLED);
        assertThat(response.getQuantity()).isEqualTo(0);
        assertThat(response.getInstalledQuantity()).isEqualTo(1);
        verify(printerInstallationRepository).save(any(PrinterInstallation.class));
    }
}
