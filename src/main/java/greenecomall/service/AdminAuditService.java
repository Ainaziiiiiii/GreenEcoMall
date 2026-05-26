package greenecomall.service;

import greenecomall.dto.response.AdminActionLogResponse;
import greenecomall.entity.AdminActionLog;
import greenecomall.entity.User;
import greenecomall.enums.AdminActionType;
import greenecomall.repository.AdminActionLogRepository;
import greenecomall.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminActionLogRepository logRepo;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User admin, AdminActionType actionType,
                    UUID targetId, String targetName, String details) {
        logRepo.save(AdminActionLog.builder()
                .admin(admin)
                .actionType(actionType)
                .targetId(targetId)
                .targetName(targetName)
                .details(details)
                .build());
    }

    @Transactional(readOnly = true)
    public Page<AdminActionLogResponse> getHistory(UUID adminId, AdminActionType actionType, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return logRepo.findFiltered(adminId, actionType, pageable).map(this::toResponse);
    }

    private AdminActionLogResponse toResponse(AdminActionLog l) {
        return AdminActionLogResponse.builder()
                .id(l.getId())
                .adminId(l.getAdmin().getId())
                .adminName(l.getAdmin().getFirstName() + " " + l.getAdmin().getLastName())
                .actionType(l.getActionType())
                .actionLabel(label(l.getActionType()))
                .targetId(l.getTargetId())
                .targetName(l.getTargetName())
                .details(l.getDetails())
                .createdAt(l.getCreatedAt())
                .build();
    }

    private String label(AdminActionType t) {
        return switch (t) {
            case USER_BLOCKED                  -> "Пользователь заблокирован";
            case USER_ACTIVATED                -> "Пользователь активирован";
            case USER_CREATED                  -> "Пользователь создан";
            case WITHDRAWAL_APPROVED           -> "Вывод одобрен";
            case WITHDRAWAL_REJECTED           -> "Вывод отклонён";
            case ACCELERATOR_MOVED             -> "Ускоритель перемещён";
            case STAGE2_PARTNER_MOVED          -> "Партнёр Этапа 2 перемещён";
            case STAGE2_TRIGGERED              -> "Этап 2 завершён вручную";
            case REPAIR_TREE_POSITIONS         -> "Repair: позиции дерева";
            case REPAIR_STAGE1_COMPLETIONS     -> "Repair: завершения Этапа 1";
            case REPAIR_STAGE2_PLACEMENTS      -> "Repair: размещения Этапа 2";
            case REPAIR_STAGE3_COMPLETIONS     -> "Repair: завершения Этапа 3";
            case NEWS_CREATED                  -> "Новость опубликована";
            case NEWS_DRAFT_SAVED              -> "Черновик сохранён";
            case NEWS_UPDATED                  -> "Новость обновлена";
            case NEWS_PUBLISHED                -> "Новость опубликована";
            case NEWS_ARCHIVED                 -> "Новость архивирована";
            case NEWS_RESTORED                 -> "Новость восстановлена";
            case NEWS_PINNED                   -> "Новость закреплена / откреплена";
            case NEWS_DELETED                  -> "Новость удалена";
            case NEWS_COMMENT_DELETED          -> "Комментарий удалён";
            case NEWS_MEDIA_ADDED              -> "Медиа добавлено к новости";
            case NEWS_MEDIA_DELETED            -> "Медиа удалено из новости";
            case TEST_USERS_CREATED            -> "Тестовые пользователи созданы";
            case TEST_FAST_START_USERS_CREATED -> "Fast Start тестовые пользователи созданы";
        };
    }
}
