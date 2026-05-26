package greenecomall.controller;

import greenecomall.dto.response.ApiResponse;
import greenecomall.dto.response.LevelsOverviewResponse;
import greenecomall.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/levels")
@RequiredArgsConstructor
@Tag(name = "Levels", description = "Матрица 4 уровня × 4 этапа с бонусами")
public class LevelsController {

    @Operation(
            summary = "Обзор всех 4 уровней ⭐",
            description = """
                    Полная матрица прогресса: 4 уровня × 4 этапа.

                    Для каждого уровня и этапа показывает:
                    - Название и описание этапа
                    - Сумму бонуса (или тип награды для уровней 3-4)
                    - Флаги isCurrentLevel / isCurrentStage / isCompleted

                    **Суммы бонусов:**
                    | Уровень | Уровень 1 | Уровень 2 | Уровень 3 | Уровень 4 |
                    |---------|-----------|-----------|-----------|-----------|
                    | Этап 1  | 1 250 сом | 11 000 сом | 22 000 сом | 110 000 сом |
                    | Этап 2  | —         | —          | —          | —           |
                    | Этап 3  | 25 000 сом | 100 000 сом | Авт. BMW  | Квартира  |
                    | Этап 4  | Переход    | Переход    | Переход   | Акционер  |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Матрица уровней"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Не авторизован", content = @Content)
    })
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<LevelsOverviewResponse>> getOverview(@AuthenticationPrincipal User user) {
        int curLevel = user.getCurrentLevel();
        int curStage = user.getCurrentStage();

        List<LevelsOverviewResponse.LevelInfo> levels = List.of(
                buildLevel(1, "Уровень 1", new BigDecimal("10000"), curLevel, curStage),
                buildLevel(2, "Уровень 2", new BigDecimal("50000"), curLevel, curStage),
                buildLevel(3, "Уровень 3", new BigDecimal("200000"), curLevel, curStage),
                buildLevel(4, "Уровень 4", new BigDecimal("1000000"), curLevel, curStage)
        );

        return ResponseEntity.ok(ApiResponse.ok(LevelsOverviewResponse.builder()
                .currentLevel(curLevel)
                .currentStage(curStage)
                .levels(levels)
                .build()));
    }

    private LevelsOverviewResponse.LevelInfo buildLevel(int level, String title, BigDecimal entryFee,
            int curLevel, int curStage) {
        boolean isCurrent   = level == curLevel;
        boolean isCompleted = level < curLevel;

        List<LevelsOverviewResponse.StageInfo> stages = List.of(
                buildStage(level, 1, isCurrent, curStage, isCompleted),
                buildStage(level, 2, isCurrent, curStage, isCompleted),
                buildStage(level, 3, isCurrent, curStage, isCompleted),
                buildStage(level, 4, isCurrent, curStage, isCompleted)
        );

        return LevelsOverviewResponse.LevelInfo.builder()
                .level(level)
                .title(title)
                .entryFee(entryFee)
                .stages(stages)
                .isCurrentLevel(isCurrent)
                .isCompleted(isCompleted)
                .build();
    }

    private LevelsOverviewResponse.StageInfo buildStage(int level, int stage,
            boolean isCurLevel, int curStage, boolean levelCompleted) {
        boolean isCurrent   = isCurLevel && stage == curStage;
        boolean isCompleted = levelCompleted || (isCurLevel && stage < curStage);

        StageReward r = reward(level, stage);

        return LevelsOverviewResponse.StageInfo.builder()
                .stage(stage)
                .title(r.title())
                .bonusDescription(r.description())
                .bonusAmount(r.amount())
                .bonusType(r.type())
                .isCurrentStage(isCurrent)
                .isCompleted(isCompleted)
                .completionModal(LevelsOverviewResponse.CompletionModal.builder()
                        .title("Поздравляем!")
                        .message(r.modalMessage())
                        .build())
                .build();
    }

    // ── Данные по каждому этапу ───────────────────────────────────────────────

    private record StageReward(String title, String description, BigDecimal amount,
                               String type, String modalMessage) {}

    private StageReward reward(int level, int stage) {
        return switch (level) {

            case 1 -> switch (stage) {
                case 1 -> new StageReward(
                        "Формирование команды",
                        "5 000 сом",
                        new BigDecimal("5000"),
                        "CASH",
                        "Вы завершили 1 этап и получили бонус 5 000 сом! " +
                        "Впереди ждут ещё большие достижения!");
                case 2 -> new StageReward(
                        "Гонка партнёров",
                        "11 000 сом",
                        new BigDecimal("11000"),
                        "CASH",
                        "Вы завершили 2 этап и получили бонус 11 000 сом! " +
                        "Продолжайте в том же духе!");
                case 3 -> new StageReward(
                        "Лидерский этап",
                        "25 000 сом",
                        new BigDecimal("25000"),
                        "CASH",
                        "Отличная работа! Вы получили бонус 25 000 сом. " +
                        "Впереди ещё большие достижения!");
                case 4 -> new StageReward(
                        "Переход на Уровень 2",
                        "Бесплатный переход + продукция от компании на $500",
                        BigDecimal.ZERO,
                        "LEVEL_UP",
                        "Вы завершили Уровень 1! Вас ждёт бесплатный переход на Уровень 2 " +
                        "и продукция от компании на сумму 500$ в подарок на ваш выбор!");
                default -> empty();
            };

            case 2 -> switch (stage) {
                case 1 -> new StageReward(
                        "Формирование команды",
                        "11 000 сом",
                        new BigDecimal("11000"),
                        "CASH",
                        "Вы завершили 1 этап Уровня 2 и получили бонус 11 000 сом!");
                case 2 -> new StageReward(
                        "Поддержка команды",
                        "Ускоритель + бесплатная продукция от компании",
                        BigDecimal.ZERO,
                        "ACCELERATOR",
                        "Вы завершили 2 этап! Вам выдаётся ускоритель для поддержки команды " +
                        "и бесплатная продукция от компании в подарок!");
                case 3 -> new StageReward(
                        "Лидерский этап",
                        "100 000 сом",
                        new BigDecimal("100000"),
                        "CASH",
                        "Невероятный результат! Вы получили бонус 100 000 сом!");
                case 4 -> new StageReward(
                        "Переход на Уровень 3",
                        "Бесплатный переход + продукция от компании на $2 000",
                        BigDecimal.ZERO,
                        "LEVEL_UP",
                        "Вы завершили Уровень 2! Вас ждёт бесплатный переход на Уровень 3 " +
                        "и продукция от компании на сумму 2 000$ в подарок на ваш выбор!");
                default -> empty();
            };

            case 3 -> switch (stage) {
                case 1 -> new StageReward(
                        "Формирование команды",
                        "44 000 сом",
                        new BigDecimal("44000"),
                        "CASH",
                        "Вы завершили 1 этап Уровня 3 и получили бонус 44 000 сом!");
                case 2 -> new StageReward(
                        "Гонка партнёров",
                        "44 000 сом",
                        new BigDecimal("44000"),
                        "CASH",
                        "Вы завершили 2 этап и получили бонус 44 000 сом! Вы на верном пути!");
                case 3 -> new StageReward(
                        "Лидерский этап",
                        "Автомобиль стоимостью $12 000",
                        BigDecimal.ZERO,
                        "CAR",
                        "Поздравляем! Вы заработали автомобиль стоимостью 12 000$! " +
                        "Это награда за ваш выдающийся результат!");
                case 4 -> new StageReward(
                        "Переход на Уровень 4",
                        "Бесплатный переход + продукция от компании на $10 000",
                        BigDecimal.ZERO,
                        "LEVEL_UP",
                        "Вы завершили Уровень 3! Вас ждёт бесплатный переход на Уровень 4 " +
                        "и продукция от компании на сумму 10 000$ в подарок на ваш выбор!");
                default -> empty();
            };

            case 4 -> switch (stage) {
                case 1 -> new StageReward(
                        "Формирование команды",
                        "220 000 сом",
                        new BigDecimal("220000"),
                        "CASH",
                        "Вы завершили 1 этап Уровня 4 и получили бонус 220 000 сом!");
                case 2 -> new StageReward(
                        "Гонка партнёров",
                        "220 000 сом",
                        new BigDecimal("220000"),
                        "CASH",
                        "Вы завершили 2 этап и получили бонус 220 000 сом! " +
                        "Вы достигли невероятных высот!");
                case 3 -> new StageReward(
                        "Лидерский этап",
                        "Автомобиль стоимостью $25 000",
                        BigDecimal.ZERO,
                        "CAR",
                        "Поздравляем! Вы заработали автомобиль стоимостью 25 000$! " +
                        "Вы входите в элиту GreenEcoMall!");
                case 4 -> new StageReward(
                        "Вершина — Акционер",
                        "Квартира стоимостью $80 000",
                        BigDecimal.ZERO,
                        "APARTMENT",
                        "Поздравляем с достижением наивысшего уровня! " +
                        "Вы получаете квартиру стоимостью 80 000$! " +
                        "Добро пожаловать в статус Акционера GreenEcoMall!");
                default -> empty();
            };

            default -> empty();
        };
    }

    private StageReward empty() {
        return new StageReward("", "", BigDecimal.ZERO, "NONE", "");
    }
}
