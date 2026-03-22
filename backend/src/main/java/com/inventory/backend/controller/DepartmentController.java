package com.inventory.backend.controller;

import com.inventory.backend.entity.Department;
import com.inventory.backend.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
@CrossOrigin
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public List<Department> getAll() {
        return departmentService.getAll();
    }

    @PostMapping
    public Department create(@Valid @RequestBody Department department) {
        return departmentService.create(department);
    }
}