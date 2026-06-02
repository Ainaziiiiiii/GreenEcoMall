package greenecomall.dto.response;

import greenecomall.enums.PaymentStatus;
import greenecomall.enums.RegistrationPlan;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdminPaymentResponse(
        UUID paymentId,
        UUID userId,
        String firstName,
        String lastName,
        String phone,
        RegistrationPlan registrationPlan,
        BigDecimal amount,
        PaymentStatus status,
        LocalDateTime createdAt,
        LocalDateTime paidAt
) {}
