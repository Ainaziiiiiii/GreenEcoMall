package greenecomall.dto.response;

import greenecomall.enums.AdminActionType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record AdminActionLogResponse(
        UUID id,
        UUID adminId,
        String adminName,
        AdminActionType actionType,
        String actionLabel,
        UUID targetId,
        String targetName,
        String details,
        LocalDateTime createdAt
) {}
