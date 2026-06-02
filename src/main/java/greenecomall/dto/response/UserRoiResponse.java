package greenecomall.dto.response;

import greenecomall.enums.RegistrationPlan;

import java.math.BigDecimal;
import java.util.UUID;

public record UserRoiResponse(
        UUID userId,
        String fullName,
        String phone,
        RegistrationPlan plan,

        /** Взнос, который пользователь заплатил платформе */
        BigDecimal paidToAdmin,

        /** Активных человек в его команде (прямые рефералы) */
        long teamSize,

        /** Сумма взносов его команды, поступивших платформе */
        BigDecimal teamPaidToAdmin,

        /** Итого доход платформы с него + его команды */
        BigDecimal totalAdminIncome,

        /** Сколько сам пользователь заработал (подтверждённые бонусы) */
        BigDecimal userEarned
) {}
