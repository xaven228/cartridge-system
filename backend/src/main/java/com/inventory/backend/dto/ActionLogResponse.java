package com.inventory.backend.dto;

import com.inventory.backend.entity.ActionLogType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionLogResponse {
    private Long id;
    private ActionLogType actionType;
    private String targetName;
    private String details;
    private String actor;
    private LocalDateTime createdAt;
}
