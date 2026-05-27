package greenecomall.repository;

import greenecomall.entity.LoginHistory;
import greenecomall.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, UUID> {
    Page<LoginHistory> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
}
