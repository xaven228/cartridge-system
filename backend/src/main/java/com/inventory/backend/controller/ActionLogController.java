package com.inventory.backend.controller;

import com.inventory.backend.dto.ActionLogResponse;
import com.inventory.backend.entity.ActionLogType;
import com.inventory.backend.service.ActionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/action-logs")
@RequiredArgsConstructor
@CrossOrigin
public class ActionLogController {

    private final ActionLogService actionLogService;

    @GetMapping
    public List<ActionLogResponse> getAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String targetName
    ) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom must be less than or equal to dateTo");
        }

        LocalDateTime createdFrom = dateFrom == null ? null : dateFrom.atStartOfDay();
        LocalDateTime createdTo = dateTo == null ? null : dateTo.atTime(LocalTime.MAX);
        ActionLogType parsedActionType = parseActionType(actionType);
        if (createdFrom == null && createdTo == null && parsedActionType == null
                && (actor == null || actor.isBlank()) && (targetName == null || targetName.isBlank())) {
            return actionLogService.getAll();
        }

        return actionLogService.getFiltered(createdFrom, createdTo, actor, parsedActionType, targetName);
    }

    private ActionLogType parseActionType(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            return null;
        }
        try {
            return ActionLogType.valueOf(actionType.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported actionType: " + actionType);
        }
    }
}
