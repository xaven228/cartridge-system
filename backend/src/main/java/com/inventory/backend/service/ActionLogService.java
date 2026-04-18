package com.inventory.backend.service;

import com.inventory.backend.dto.ActionLogResponse;
import com.inventory.backend.entity.ActionLog;
import com.inventory.backend.entity.ActionLogType;
import com.inventory.backend.repository.ActionLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActionLogService {

    private final ActionLogRepository actionLogRepository;

    public List<ActionLogResponse> getAll() {
        return actionLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ActionLogResponse> getFiltered(
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            String actor,
            ActionLogType actionType,
            String targetName
    ) {
        Specification<ActionLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }
            if (actor != null && !actor.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("actor")), "%" + actor.trim().toLowerCase() + "%"));
            }
            if (actionType != null) {
                predicates.add(cb.equal(root.get("actionType"), actionType));
            }
            if (targetName != null && !targetName.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("targetName")), "%" + targetName.trim().toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return actionLogRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")).stream()
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
