package greenecomall.dto.response;

import greenecomall.enums.StageStatus;
import lombok.Builder;

@Builder
public record TreeResponse(
        TreeNodeResponse root,
        StageStatus stageStatus,
        TreeProgress progress,
        AcceleratorInfo accelerator,
        Integer fastStartNumber,
        BranchStats branches,
        /** true — пользователь зарегистрировался с более высокого уровня и пропустил этот */
        Boolean skipped
) {
    @Builder
    public record TreeProgress(int filled, int total) {}

    @Builder
    public record AcceleratorInfo(boolean active, Integer position) {}

    @Builder
    public record BranchStats(BranchSide left, BranchSide right) {}

    @Builder
    public record BranchSide(int size) {}
}
