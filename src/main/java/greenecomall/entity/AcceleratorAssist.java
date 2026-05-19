package greenecomall.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "accelerator_assists",
        indexes = {
                @Index(name = "idx_accel_assist_beneficiary", columnList = "beneficiary_id, level")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcceleratorAssist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    private User beneficiary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accelerator_owner_id", nullable = false)
    private User acceleratorOwner;

    @Column(nullable = false)
    private Integer level;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
