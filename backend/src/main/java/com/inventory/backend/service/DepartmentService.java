package com.inventory.backend.service;

import com.inventory.backend.entity.Department;
import com.inventory.backend.exception.ConflictException;
import com.inventory.backend.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public List<Department> getAll() {
        return departmentRepository.findAll();
    }

    public Department create(Department department) {
        boolean exists = departmentRepository.findAll()
                .stream()
                .anyMatch(d -> d.getName().equalsIgnoreCase(department.getName()));

        if (exists) {
            throw new ConflictException("Отдел с таким названием уже существует: " + department.getName());
        }

        return departmentRepository.save(department);
    }
}