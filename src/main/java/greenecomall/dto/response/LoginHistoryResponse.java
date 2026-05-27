package greenecomall.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record LoginHistoryResponse(
        UUID id,
        String phone,
        String ipAddress,
        String userAgent,
        LocalDateTime createdAt
) {}
