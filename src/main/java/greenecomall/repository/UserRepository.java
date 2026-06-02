package greenecomall.repository;

import greenecomall.entity.User;
import greenecomall.enums.AccountStatus;
import greenecomall.enums.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByReferralCode(String referralCode);

    boolean existsByPhone(String phone);

    boolean existsByPassportNumber(String passportNumber);

    long countByAccountStatus(AccountStatus status);

    long countByRole(Role role);

    List<User> findByCurrentLevelAndCurrentStageAndAccountStatusOrderByActivatedAtAsc(
            int level, int stage, AccountStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT u.currentLevel, u.currentStage, COUNT(u) FROM User u WHERE u.accountStatus = 'ACTIVE' AND u.role != 'ADMIN' GROUP BY u.currentLevel, u.currentStage ORDER BY u.currentLevel, u.currentStage")
    List<Object[]> countByCurrentLevelAndStage();

    boolean existsByFixedPartnerLeft(User user);
    boolean existsByFixedPartnerRight(User user);

    @Query("SELECT u FROM User u WHERE u.fixedPartnerLeft = :partner OR u.fixedPartnerRight = :partner")
    Optional<User> findStage2HostOf(@Param("partner") User partner);

    // Level 0 (Fast Start) users waiting for their 1 person
    @Query("SELECT u FROM User u WHERE u.registrationPlan = 'FAST_START' AND u.currentLevel = 0 AND u.currentStage = 1 AND u.accountStatus = 'ACTIVE' ORDER BY u.activatedAt ASC")
    List<User> findWaitingLevel0Users();

    // Stage 2 users who still have at least 1 empty fixed partner slot
    @Query("SELECT u FROM User u WHERE u.currentLevel = 1 AND u.currentStage = 2 AND u.accountStatus = 'ACTIVE' AND (u.fixedPartnerLeft IS NULL OR u.fixedPartnerRight IS NULL) ORDER BY u.activatedAt ASC")
    List<User> findStage2UsersWithEmptySlots();

    // Fast Start graduates on Stage 2 with empty slots, sorted by activatedAt (earliest first)
    @Query("SELECT u FROM User u WHERE u.registrationPlan = 'FAST_START' AND u.currentStage = 2 AND u.accountStatus = 'ACTIVE' AND (u.fixedPartnerLeft IS NULL OR u.fixedPartnerRight IS NULL) ORDER BY u.activatedAt ASC")
    List<User> findFastStartStage2WithEmptySlots();

    // Next sequential number for Fast Start queue
    @Query("SELECT COALESCE(MAX(u.fastStartNumber), 0) + 1 FROM User u WHERE u.registrationPlan = 'FAST_START'")
    int getNextFastStartNumber();

    List<User> findByInviter(User inviter);

    long countByInviterAndAccountStatus(User inviter, AccountStatus status);

    // Количество новых активных пользователей по дням за период
    @Query("SELECT CAST(u.activatedAt AS date), COUNT(u) FROM User u " +
           "WHERE u.accountStatus = 'ACTIVE' AND u.activatedAt >= :from " +
           "GROUP BY CAST(u.activatedAt AS date) ORDER BY CAST(u.activatedAt AS date)")
    List<Object[]> countActivatedByDay(@Param("from") LocalDateTime from);

    // Общее кол-во активных пользователей до определённой даты (для накопительного графика)
    @Query("SELECT COUNT(u) FROM User u WHERE u.accountStatus = 'ACTIVE' AND u.activatedAt < :before")
    long countActiveBefore(@Param("before") LocalDateTime before);

    // Топ рефереров — [User inviter, Long count]
    @Query("SELECT u.inviter, COUNT(u) FROM User u WHERE u.inviter IS NOT NULL AND u.accountStatus = 'ACTIVE' GROUP BY u.inviter ORDER BY COUNT(u) DESC")
    List<Object[]> findTopReferrers(Pageable pageable);
}
