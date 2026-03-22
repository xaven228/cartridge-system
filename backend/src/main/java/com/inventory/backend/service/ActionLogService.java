package com.inventory.backend.service;

import com.inventory.backend.dto.ActionLogResponse;
import com.inventory.backend.entity.ActionLog;
import com.inventory.backend.entity.ActionLogType;
import com.inventory.backend.repository.ActionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActionLogService {

    private final ActionLogRepository actionLogRepository;

    public List<ActionLogResponse> getAll() {
        return actionLogRepository.findAll().stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .map(this::toResponse)
                .toList();
    }

    public void log(ActionLogType actionType, String targetName, String details, String actor) {
        actionLogRepository.save(ActionLog.builder()
                .actionType(actionType)
                .targetName(targetName)
                .details(details)
                .actor(actor == null || actor.isBlank() ? "Система" : actor)
                .build());
    }

    private ActionLogResponse toResponse(ActionLog actionLog) {
        return ActionLogResponse.builder()
                .id(actionLog.getId())
                .actionType(actionLog.getActionType())
                .targetName(actionLog.getTargetName())
                .details(actionLog.getDetails())
                .actor(actionLog.getActor())
                .createdAt(actionLog.getCreatedAt())
                .build();
    }
}
