package greenecomall.dto.response;

import java.math.BigDecimal;

public record RoiSummaryResponse(
        /** Всего активных участников (без админов) */
        long totalActiveUsers,

        /** Итого собрано вступительных взносов платформой */
        BigDecimal totalAdminIncome,

        /** Итого выплачено подтверждённых бонусов участникам */
        BigDecimal totalUserEarned,

        /** Средний доход платформы на одного участника */
        BigDecimal avgAdminIncomePerUser,

        /** Средний заработок одного участника */
        BigDecimal avgUserEarned
) {}
