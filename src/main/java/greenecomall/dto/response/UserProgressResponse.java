package greenecomall.dto.response;

import greenecomall.enums.RegistrationPlan;

import java.math.BigDecimal;
import java.util.List;

public record UserProgressResponse(
        int currentLevel,
        int currentStage,
        /** С какого уровня пользователь начал (зависит от тарифа) */
        int startingLevel,
        RegistrationPlan registrationPlan,
        /** Всего этапов в маршруте пользователя (от startingLevel до конца) */
        int totalStages,
        /** Сколько этапов фактически завершено */
        int completedStages,
        /** Процент прогресса по маршруту */
        int progressPercent,
        List<LevelProgress> levels
) {
    public enum LevelStatus { SKIPPED, COMPLETED, CURRENT, LOCKED }
    public enum StageStatus { SKIPPED, COMPLETED, CURRENT, LOCKED }

    public record LevelProgress(
            int level,
            LevelStatus status,
            int stagesCompleted,
            List<StageProgress> stages
    ) {}

    public record StageProgress(
            int stage,
            StageStatus status,
            /** Фактически начисленный бонус (из БД). 0 если этап не завершён. */
            BigDecimal bonusEarned
    ) {}
}
