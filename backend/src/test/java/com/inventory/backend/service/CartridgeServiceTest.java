package com.inventory.backend.service;

import com.inventory.backend.dto.CartridgeResponse;
import com.inventory.backend.dto.CreateCartridgeRequest;
import com.inventory.backend.entity.Cartridge;
import com.inventory.backend.entity.CartridgeModel;
import com.inventory.backend.entity.CartridgeStatus;
import com.inventory.backend.entity.Department;
import com.inventory.backend.repository.CartridgeModelRepository;
import com.inventory.backend.repository.CartridgeRepository;
import com.inventory.backend.repository.DepartmentRepository;
import com.inventory.backend.repository.RefillHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private RefillHistoryRepository refillHistoryRepository;

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
                .name("IT")
                .build();

        CreateCartridgeRequest request = new CreateCartridgeRequest();
        request.setInventoryCode("INV-NEW-001");
        request.setCartridgeModelId(model.getId());
        request.setDepartmentId(department.getId());
        request.setQuantity(2);
        request.setStatus(CartridgeStatus.IN_STOCK);
        request.setComment("new item");

        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 21, 15, 0, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 3, 21, 15, 1, 0);

        Cartridge saved = Cartridge.builder()
                .id(100L)
                .inventoryCode(request.getInventoryCode())
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
        when(departmentRepository.findById(department.getId())).thenReturn(Optional.of(department));
        when(cartridgeRepository.findAll()).thenReturn(List.of());
        when(cartridgeRepository.save(any(Cartridge.class))).thenReturn(saved);

        CartridgeResponse response = cartridgeService.create(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
        assertThat(response.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(response.getInventoryCode()).isEqualTo("INV-NEW-001");
        assertThat(response.getDepartmentId()).isEqualTo(20L);
        assertThat(response.getCartridgeModelId()).isEqualTo(10L);
    }
}
