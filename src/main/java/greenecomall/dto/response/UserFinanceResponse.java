package greenecomall.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record UserFinanceResponse(
        UUID userId,
        String fullName,
        String phone,

        /** Текущий баланс на счёте */
        BigDecimal balance,

        /** Структурные бонусы за этапы */
        BigDecimal bonusStage,

        /** Итого всех подтверждённых бонусов */
        BigDecimal bonusTotal,

        /** Уже выведено (APPROVED) */
        BigDecimal totalWithdrawn,

        /** На рассмотрении (PENDING) */
        BigDecimal pendingWithdrawal
) {}
