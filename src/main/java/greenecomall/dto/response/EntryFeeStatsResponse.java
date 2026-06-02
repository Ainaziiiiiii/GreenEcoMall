package greenecomall.dto.response;

import java.math.BigDecimal;

public record EntryFeeStatsResponse(
        /** Всего оплаченных взносов */
        long totalCount,
        /** Сумма всех оплаченных взносов */
        BigDecimal totalAmount,
        /** Оплачено сегодня */
        long todayCount,
        /** Сумма оплат сегодня */
        BigDecimal todayAmount,
        /** Ожидают оплаты */
        long pendingCount
) {}
