package com.inventory.backend.repository;

import com.inventory.backend.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findAllByOrderByNameAsc();
    List<Room> findByDepartmentIdOrderByNameAsc(Long departmentId);
    long countByDepartmentId(Long departmentId);
    boolean existsByDepartmentIdAndNameIgnoreCase(Long departmentId, String name);
    boolean existsByDepartmentIdAndNameIgnoreCaseAndIdNot(Long departmentId, String name, Long id);
}
