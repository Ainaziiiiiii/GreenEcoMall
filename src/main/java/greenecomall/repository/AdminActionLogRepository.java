package greenecomall.repository;

import greenecomall.entity.AdminActionLog;
import greenecomall.entity.User;
import greenecomall.enums.AdminActionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, UUID> {

    Page<AdminActionLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AdminActionLog> findByAdminOrderByCreatedAtDesc(User admin, Pageable pageable);

    Page<AdminActionLog> findByActionTypeOrderByCreatedAtDesc(AdminActionType actionType, Pageable pageable);

    @Query("SELECT l FROM AdminActionLog l WHERE " +
           "(:adminId IS NULL OR l.admin.id = :adminId) AND " +
           "(:actionType IS NULL OR l.actionType = :actionType) " +
           "ORDER BY l.createdAt DESC")
    Page<AdminActionLog> findFiltered(
            @Param("adminId") UUID adminId,
            @Param("actionType") AdminActionType actionType,
            Pageable pageable);
}
