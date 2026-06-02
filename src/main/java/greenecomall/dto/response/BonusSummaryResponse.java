package greenecomall.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;

@Builder
public record BonusSummaryResponse(
        /** Текущий баланс */
        BigDecimal available,
        /** Итого заработано бонусами */
        BigDecimal total,
        /** Итого выплачено (выведено) */
        BigDecimal paid,
        /** Разбивка по типам бонусов */
        Map<String, BigDecimal> byType
) {}
