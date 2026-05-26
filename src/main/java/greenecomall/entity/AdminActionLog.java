package greenecomall.entity;

import greenecomall.enums.AdminActionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "admin_action_logs",
        indexes = {
                @Index(name = "idx_admin_log_admin_id", columnList = "admin_id"),
                @Index(name = "idx_admin_log_created_at", columnList = "created_at"),
                @Index(name = "idx_admin_log_action_type", columnList = "action_type")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private AdminActionType actionType;

    // ID затронутого объекта (userId, withdrawalId, newsId и т.д.)
    @Column(name = "target_id")
    private UUID targetId;

    // Имя затронутого пользователя/объекта для удобства чтения
    @Column(name = "target_name")
    private String targetName;

    // Дополнительные детали (причина отказа, количество созданных и т.д.)
    @Column(columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
