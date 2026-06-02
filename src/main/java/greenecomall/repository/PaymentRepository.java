package greenecomall.repository;

import greenecomall.entity.Payment;
import greenecomall.entity.User;
import greenecomall.enums.PaymentStatus;
import greenecomall.enums.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findFirstByUserAndTypeAndStatusOrderByCreatedAtDesc(
            User user, PaymentType type, PaymentStatus status);

    Optional<Payment> findByFinikTransactionId(String transactionId);

    Optional<Payment> findFirstByUserAndTypeOrderByCreatedAtDesc(User user, PaymentType type);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.type = greenecomall.enums.PaymentType.ENTRY_FEE AND p.status = greenecomall.enums.PaymentStatus.SUCCESS")
    java.math.BigDecimal sumSuccessfulEntryFees();

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.type = greenecomall.enums.PaymentType.ENTRY_FEE AND p.status = greenecomall.enums.PaymentStatus.SUCCESS")
    long countPaidEntryFees();

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.type = greenecomall.enums.PaymentType.ENTRY_FEE AND p.status = greenecomall.enums.PaymentStatus.SUCCESS AND p.paidAt >= :from")
    long countPaidEntryFeesSince(@Param("from") java.time.LocalDateTime from);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.type = greenecomall.enums.PaymentType.ENTRY_FEE AND p.status = greenecomall.enums.PaymentStatus.SUCCESS AND p.paidAt >= :from")
    java.math.BigDecimal sumPaidEntryFeesSince(@Param("from") java.time.LocalDateTime from);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.type = greenecomall.enums.PaymentType.ENTRY_FEE AND p.status = greenecomall.enums.PaymentStatus.PENDING")
    long countPendingEntryFees();

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.user = :user AND p.type = greenecomall.enums.PaymentType.ENTRY_FEE AND p.status = greenecomall.enums.PaymentStatus.SUCCESS")
    java.math.BigDecimal sumPaidEntryFeeByUser(@Param("user") greenecomall.entity.User user);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.user.inviter = :inviter AND p.type = greenecomall.enums.PaymentType.ENTRY_FEE AND p.status = greenecomall.enums.PaymentStatus.SUCCESS")
    java.math.BigDecimal sumPaidEntryFeeByTeam(@Param("inviter") greenecomall.entity.User inviter);

    @Query("""
            SELECT p FROM Payment p
            JOIN FETCH p.user u
            WHERE p.type = greenecomall.enums.PaymentType.ENTRY_FEE
              AND p.status = greenecomall.enums.PaymentStatus.SUCCESS
            ORDER BY p.createdAt DESC
            """)
    Page<Payment> findPaidEntryFees(Pageable pageable);

    @Query("""
            SELECT p FROM Payment p
            JOIN FETCH p.user u
            WHERE p.type = greenecomall.enums.PaymentType.ENTRY_FEE
              AND p.status <> greenecomall.enums.PaymentStatus.SUCCESS
            ORDER BY p.createdAt DESC
            """)
    Page<Payment> findUnpaidEntryFees(Pageable pageable);
}
