package com.inventory.backend.controller;

import com.inventory.backend.dto.ActionLogResponse;
import com.inventory.backend.service.ActionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/action-logs")
@RequiredArgsConstructor
@CrossOrigin
public class ActionLogController {

    private final ActionLogService actionLogService;

    @GetMapping
    public List<ActionLogResponse> getAll() {
        return actionLogService.getAll();
    }
}
