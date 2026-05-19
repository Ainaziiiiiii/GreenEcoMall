package greenecomall.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record AcceleratorHistoryResponse(
        UUID acceleratorOwnerUserId,
        String name,
        String initials,
        int level,
        LocalDateTime completedAt
) {}
