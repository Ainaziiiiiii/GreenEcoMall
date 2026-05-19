package greenecomall.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoveAcceleratorRequest(
        @NotNull UUID acceleratorOwnerUserId,
        @NotNull UUID newParentUserId,
        @NotNull Integer level
) {}
