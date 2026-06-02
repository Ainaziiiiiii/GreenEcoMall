package greenecomall.controller;

import greenecomall.dto.response.AdminActionLogResponse;
import greenecomall.dto.response.AdminStatsResponse;
import greenecomall.dto.response.ApiResponse;
import greenecomall.dto.response.BonusResponse;
import greenecomall.dto.response.BranchStatsResponse;
import greenecomall.dto.response.NewsCommentResponse;
import greenecomall.dto.response.NewsItemResponse;
import greenecomall.dto.response.NewsStatsResponse;
import greenecomall.dto.response.StagesOverviewResponse;
import greenecomall.dto.response.TeamActivityResponse;
import greenecomall.dto.response.TreeResponse;
import greenecomall.dto.response.WithdrawalItemResponse;
import greenecomall.dto.response.WithdrawalStatsResponse;
import greenecomall.entity.Bonus;
import greenecomall.dto.request.CreateNewsRequest;
import greenecomall.dto.request.RegisterRequest;
import greenecomall.dto.request.UpdateNewsRequest;
import greenecomall.entity.User;
import greenecomall.entity.Withdrawal;
import greenecomall.enums.AdminActionType;
import greenecomall.enums.BonusStatus;
import greenecomall.enums.BonusType;
import greenecomall.enums.NewsStatus;
import greenecomall.enums.NotificationType;
import greenecomall.enums.Role;
import greenecomall.service.AdminAuditService;
import greenecomall.service.AuthService;
import greenecomall.service.NewsService;
import greenecomall.service.NotificationService;
import greenecomall.service.PaymentService;
import greenecomall.service.TreeService;
import greenecomall.service.WithdrawalService;
import greenecomall.enums.AccountStatus;
import greenecomall.enums.WithdrawalStatus;
import greenecomall.exception.BusinessException;
import greenecomall.exception.ErrorCode;
import greenecomall.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Панель администратора (только для роли ADMIN)")
public class AdminController {

    private final UserRepository userRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final WithdrawalService withdrawalService;
    private final BonusRepository bonusRepository;
    private final AuthService authService;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final TreeService treeService;
    private final NewsService newsService;
    private final AdminAuditService auditService;
    private final NotificationService notificationService;

    @Operation(
            summary = "Список пользователей",
            description = """
                    Возвращает пользователей с пагинацией и фильтрацией.

                    **Параметры фильтрации (все необязательны, комбинируются):**
                    - `search` — поиск по ФИО (имя/фамилия) или номеру паспорта, регистронезависимо
                    - `status` — статус аккаунта: `PENDING` | `ACTIVE` | `BLOCKED`
                    - `level` — текущий уровень: 0–4
                    - `stage` — текущий этап: 1–4
                    """)
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<User>>> getUsers(
            @Parameter(description = "Поиск по ФИО или паспорту")
            @RequestParam(required = false) String search,
            @Parameter(description = "Статус: PENDING | ACTIVE | BLOCKED")
            @RequestParam(required = false) AccountStatus status,
            @Parameter(description = "Уровень 0–4")
            @RequestParam(required = false) Integer level,
            @Parameter(description = "Этап 1–4")
            @RequestParam(required = false) Integer stage,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        org.springframework.data.jpa.domain.Specification<User> spec =
                org.springframework.data.jpa.domain.Specification.where(
                        (root, query, cb) -> cb.notEqual(root.get("role"), greenecomall.enums.Role.ADMIN)
                );

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("firstName")),  pattern),
                    cb.like(cb.lower(root.get("lastName")),   pattern),
                    cb.like(cb.lower(root.get("passportNumber")), pattern)
            ));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("accountStatus"), status));
        }
        if (level != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("currentLevel"), level));
        }
        if (stage != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("currentStage"), stage));
        }

        return ResponseEntity.ok(ApiResponse.ok(userRepository.findAll(spec, pageable)));
    }

    @Operation(summary = "Карточка пользователя")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Пользователь найден"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Не найден", content = @Content)
    })
    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<User>> getUser(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    @Operation(summary = "Заблокировать пользователя")
    @PatchMapping("/users/{id}/block")
    public ResponseEntity<ApiResponse<Void>> blockUser(@PathVariable UUID id,
            @AuthenticationPrincipal User admin) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
        user.setAccountStatus(AccountStatus.BLOCKED);
        userRepository.save(user);
        auditService.log(admin, AdminActionType.USER_BLOCKED, id,
                user.getFirstName() + " " + user.getLastName(), null);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "Вступительные взносы (оплаченные)",
            description = "Список успешно оплаченных взносов (ENTRY_FEE, status=SUCCESS), сортировка по дате убыванием.")
    @GetMapping("/payments/entry-fees")
    public ResponseEntity<ApiResponse<Page<greenecomall.dto.response.AdminPaymentResponse>>> getEntryFees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(page, size);

        Page<greenecomall.dto.response.AdminPaymentResponse> result =
                paymentRepository.findPaidEntryFees(pageable)
                        .map(p -> new greenecomall.dto.response.AdminPaymentResponse(
                                p.getId(),
                                p.getUser().getId(),
                                p.getUser().getFirstName(),
                                p.getUser().getLastName(),
                                p.getUser().getPhone(),
                                p.getUser().getRegistrationPlan(),
                                p.getAmount(),
                                p.getStatus(),
                                p.getCreatedAt(),
                                p.getPaidAt()
                        ));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Непройденные вступительные взносы (PENDING / FAILED / EXPIRED)",
            description = "Платежи, которые ещё не оплачены или упали. Можно вручную поменять статус через PATCH /payments/{id}/status.")
    @GetMapping("/payments/entry-fees/unpaid")
    public ResponseEntity<ApiResponse<Page<greenecomall.dto.response.AdminPaymentResponse>>> getUnpaidEntryFees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(page, size);

        Page<greenecomall.dto.response.AdminPaymentResponse> result =
                paymentRepository.findUnpaidEntryFees(pageable)
                        .map(p -> new greenecomall.dto.response.AdminPaymentResponse(
                                p.getId(),
                                p.getUser().getId(),
                                p.getUser().getFirstName(),
                                p.getUser().getLastName(),
                                p.getUser().getPhone(),
                                p.getUser().getRegistrationPlan(),
                                p.getAmount(),
                                p.getStatus(),
                                p.getCreatedAt(),
                                p.getPaidAt()
                        ));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Вручную изменить статус платежа",
            description = """
                    Позволяет вручную перевести платёж в нужный статус.
                    Если статус меняется на **SUCCESS** — пользователь автоматически активируется (как при реальной оплате).
                    Допустимые статусы: `SUCCESS`, `FAILED`, `EXPIRED`.
                    """)
    @PatchMapping("/payments/{id}/status")
    public ResponseEntity<ApiResponse<greenecomall.dto.response.AdminPaymentResponse>> updatePaymentStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User admin) {

        greenecomall.enums.PaymentStatus newStatus;
        try {
            newStatus = greenecomall.enums.PaymentStatus.valueOf(body.get("status").toUpperCase());
        } catch (Exception e) {
            throw BusinessException.of(ErrorCode.NOT_FOUND);
        }

        greenecomall.entity.Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> BusinessException.of(ErrorCode.PAYMENT_NOT_FOUND));

        payment.setStatus(newStatus);
        if (newStatus == greenecomall.enums.PaymentStatus.SUCCESS) {
            payment.setPaidAt(java.time.LocalDateTime.now());
            paymentRepository.save(payment);
            paymentService.activateUserById(payment.getUser().getId());
        } else {
            paymentRepository.save(payment);
        }

        auditService.log(admin, AdminActionType.PAYMENT_STATUS_CHANGED, id,
                payment.getUser().getFirstName() + " " + payment.getUser().getLastName(),
                newStatus.name());

        return ResponseEntity.ok(ApiResponse.ok(new greenecomall.dto.response.AdminPaymentResponse(
                payment.getId(),
                payment.getUser().getId(),
                payment.getUser().getFirstName(),
                payment.getUser().getLastName(),
                payment.getUser().getPhone(),
                payment.getUser().getRegistrationPlan(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getPaidAt()
        )));
    }

    @Operation(summary = "Статистика вступительных взносов",
            description = "totalCount, totalAmount, todayCount, todayAmount, pendingCount")
    @GetMapping("/payments/entry-fees/stats")
    public ResponseEntity<ApiResponse<greenecomall.dto.response.EntryFeeStatsResponse>> getEntryFeeStats() {
        java.time.LocalDateTime todayStart = java.time.LocalDate.now().atStartOfDay();
        return ResponseEntity.ok(ApiResponse.ok(new greenecomall.dto.response.EntryFeeStatsResponse(
                paymentRepository.countPaidEntryFees(),
                paymentRepository.sumSuccessfulEntryFees(),
                paymentRepository.countPaidEntryFeesSince(todayStart),
                paymentRepository.sumPaidEntryFeesSince(todayStart),
                paymentRepository.countPendingEntryFees()
        )));
    }

    @Operation(summary = "Финансовое досье пользователя",
            description = """
                    Баланс, бонусы по типам (реферальные / структурные / дивиденды),
                    итого выведено и сумма ожидающих выводов.
                    Вызывается по кнопке «Подробнее» рядом с заявкой на вывод.
                    """)
    @GetMapping("/users/{userId}/finance")
    public ResponseEntity<ApiResponse<greenecomall.dto.response.UserFinanceResponse>> getUserFinance(
            @PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));

        BigDecimal stage = bonusRepository.sumConfirmedByUserAndType(user, greenecomall.enums.BonusType.STAGE)
                .add(bonusRepository.sumConfirmedByUserAndType(user, greenecomall.enums.BonusType.REFERRAL_DIRECT))
                .add(bonusRepository.sumConfirmedByUserAndType(user, greenecomall.enums.BonusType.REFERRAL_INDIRECT))
                .add(bonusRepository.sumConfirmedByUserAndType(user, greenecomall.enums.BonusType.DIVIDEND));
        BigDecimal total = bonusRepository.sumByUserAndStatus(user, greenecomall.enums.BonusStatus.CONFIRMED);

        return ResponseEntity.ok(ApiResponse.ok(new greenecomall.dto.response.UserFinanceResponse(
                user.getId(),
                user.getFirstName() + " " + user.getLastName(),
                user.getPhone(),
                user.getBalance(),
                stage,
                total,
                withdrawalRepository.sumApprovedByUser(user),
                withdrawalRepository.sumPendingByUser(user)
        )));
    }

    @Operation(summary = "Статистика выплат — 4 карточки вверху",
            description = "pendingCount, pendingSum, approvedToday, totalPaid")
    @GetMapping("/withdrawals/stats")
    public ResponseEntity<ApiResponse<WithdrawalStatsResponse>> getWithdrawalStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return ResponseEntity.ok(ApiResponse.ok(WithdrawalStatsResponse.builder()
                .pendingCount(withdrawalRepository.countByStatus(WithdrawalStatus.PENDING))
                .pendingSum(withdrawalRepository.sumByStatus(WithdrawalStatus.PENDING))
                .approvedToday(withdrawalRepository.countByStatusAndReviewedAtAfter(WithdrawalStatus.APPROVED, todayStart))
                .totalPaid(withdrawalRepository.sumTotalApproved())
                .build()));
    }

    @Operation(summary = "Заявки на вывод",
            description = "status: PENDING | APPROVED | REJECTED. period: ALL | MONTH (по умолчанию ALL)")
    @GetMapping("/withdrawals")
    public ResponseEntity<ApiResponse<Page<WithdrawalItemResponse>>> getWithdrawals(
            @Parameter(description = "Статус: PENDING | APPROVED | REJECTED | ALL")
            @RequestParam(required = false) WithdrawalStatus status,
            @Parameter(description = "Период: ALL | MONTH")
            @RequestParam(defaultValue = "ALL") String period,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        LocalDateTime from = "MONTH".equalsIgnoreCase(period)
                ? LocalDateTime.now().minusMonths(1) : null;

        Page<Withdrawal> raw;
        if (status != null && from != null) {
            raw = withdrawalRepository.findByStatusAndCreatedAtAfter(status, from, pageable);
        } else if (status != null) {
            raw = withdrawalRepository.findByStatus(status, pageable);
        } else if (from != null) {
            raw = withdrawalRepository.findByCreatedAtAfter(from, pageable);
        } else {
            raw = withdrawalRepository.findAll(pageable);
        }

        Page<WithdrawalItemResponse> result = raw.map(w -> WithdrawalItemResponse.builder()
                .id(w.getId())
                .userId(w.getUser().getId())
                .userName(w.getUser().getFirstName() + " " + w.getUser().getLastName())
                .userPhone(w.getUser().getPhone())
                .amount(w.getAmount())
                .status(w.getStatus())
                .method(w.getMethod().name())
                .requisite(w.getRequisite())
                .bankName(w.getBankName())
                .adminNote(w.getAdminNote())
                .createdAt(w.getCreatedAt())
                .reviewedAt(w.getReviewedAt())
                .build());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Одобрить заявку на вывод")
    @PatchMapping("/withdrawals/{id}/approve")
    public ResponseEntity<ApiResponse<WithdrawalItemResponse>> approveWithdrawal(@PathVariable UUID id,
            @AuthenticationPrincipal User admin) {
        Withdrawal w = withdrawalService.approve(id);
        auditService.log(admin, AdminActionType.WITHDRAWAL_APPROVED, id,
                w.getUser().getFirstName() + " " + w.getUser().getLastName(),
                w.getAmount() + " сом");
        return ResponseEntity.ok(ApiResponse.ok(toWithdrawalItem(w)));
    }

    @Operation(summary = "Отклонить заявку на вывод",
            description = "Возвращает сумму на баланс пользователя.")
    @PatchMapping("/withdrawals/{id}/reject")
    public ResponseEntity<ApiResponse<WithdrawalItemResponse>> rejectWithdrawal(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User admin) {
        Withdrawal w = withdrawalService.reject(id, body.get("note"));
        auditService.log(admin, AdminActionType.WITHDRAWAL_REJECTED, id,
                w.getUser().getFirstName() + " " + w.getUser().getLastName(),
                body.get("note"));
        return ResponseEntity.ok(ApiResponse.ok(toWithdrawalItem(w)));
    }

    private WithdrawalItemResponse toWithdrawalItem(Withdrawal w) {
        return WithdrawalItemResponse.builder()
                .id(w.getId())
                .userId(w.getUser().getId())
                .userName(w.getUser().getFirstName() + " " + w.getUser().getLastName())
                .userPhone(w.getUser().getPhone())
                .amount(w.getAmount())
                .status(w.getStatus())
                .method(w.getMethod().name())
                .requisite(w.getRequisite())
                .bankName(w.getBankName())
                .adminNote(w.getAdminNote())
                .createdAt(w.getCreatedAt())
                .reviewedAt(w.getReviewedAt())
                .build();
    }

    @Operation(
            summary = "Распределение участников по уровням и этапам",
            description = """
                    Возвращает количество активных участников (без ADMIN) по уровням 0–4
                    с разбивкой по этапам. Уровень 0 = Fast Start.

                    Пример ответа:
                    ```json
                    {
                      "total": 78,
                      "byLevel": {
                        "level0": { "total": 1,  "stages": {"stage1": 1} },
                        "level1": { "total": 77, "stages": {"stage1": 50, "stage2": 20, "stage3": 5, "stage4": 2} },
                        "level2": { "total": 0,  "stages": {} },
                        "level3": { "total": 0,  "stages": {} },
                        "level4": { "total": 0,  "stages": {} }
                      }
                    }
                    ```
                    """)
    @GetMapping("/stats/distribution")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLevelDistribution() {
        List<Object[]> rows = userRepository.countByCurrentLevelAndStage();

        // Инициализируем уровни 0–4
        Map<String, Object> byLevel = new LinkedHashMap<>();
        for (int i = 0; i <= 4; i++) {
            Map<String, Object> levelMap = new LinkedHashMap<>();
            levelMap.put("total", 0L);
            levelMap.put("stages", new LinkedHashMap<String, Long>());
            byLevel.put("level" + i, levelMap);
        }

        long grandTotal = 0L;
        for (Object[] row : rows) {
            int lvl    = ((Number) row[0]).intValue();
            int stg    = ((Number) row[1]).intValue();
            long count = ((Number) row[2]).longValue();
            if (lvl < 0 || lvl > 4) continue;

            @SuppressWarnings("unchecked")
            Map<String, Object> levelMap = (Map<String, Object>) byLevel.get("level" + lvl);
            levelMap.put("total", (Long) levelMap.get("total") + count);

            @SuppressWarnings("unchecked")
            Map<String, Long> stages = (Map<String, Long>) levelMap.get("stages");
            stages.put("stage" + stg, count);

            grandTotal += count;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", grandTotal);
        result.put("byLevel", byLevel);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Добавить участника (создать аккаунт вручную)",
            description = """
                    Администратор может зарегистрировать нового участника напрямую.
                    Пропускает шаги OTP и оплаты — аккаунт сразу создаётся в статусе PENDING.
                    Для активации нужно будет провести оплату или использовать эндпоинт активации.
                    """)
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createUser(
            @Valid @RequestBody RegisterRequest req,
            @AuthenticationPrincipal User admin) {
        var result = authService.registerByAdmin(req);
        auditService.log(admin, AdminActionType.USER_CREATED, result.userId(),
                req.firstName() + " " + req.lastName(), null);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "userId", result.userId(),
                "paymentId", result.paymentId()
        )));
    }

    @Operation(summary = "Активировать аккаунт пользователя (без оплаты)",
            description = "Администратор может вручную активировать аккаунт пользователя, обходя оплату. " +
                    "Запускает полный флоу активации: размещение в дереве, начисление реферальных бонусов.")
    @PatchMapping("/users/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activateUser(@PathVariable UUID id,
            @AuthenticationPrincipal User admin) {
        paymentService.activateUserById(id);
        User u = userRepository.findById(id).orElse(null);
        auditService.log(admin, AdminActionType.USER_ACTIVATED, id,
                u != null ? u.getFirstName() + " " + u.getLastName() : null, null);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "Экспорт участников в CSV",
            description = "Скачивает CSV-файл со списком всех активных участников (ID, имя, телефон, уровень, этап, баланс, дата активации).")
    @GetMapping(value = "/export/users", produces = "text/csv")
    public ResponseEntity<byte[]> exportUsers() {
        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getAccountStatus() == greenecomall.enums.AccountStatus.ACTIVE)
                .toList();

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Имя,Фамилия,Телефон,Уровень,Этап,Баланс,Дата активации\n");
        for (User u : users) {
            csv.append(u.getId()).append(',')
               .append(escape(u.getFirstName())).append(',')
               .append(escape(u.getLastName())).append(',')
               .append(u.getPhone()).append(',')
               .append(u.getCurrentLevel()).append(',')
               .append(u.getCurrentStage()).append(',')
               .append(u.getBalance()).append(',')
               .append(u.getActivatedAt() != null ? u.getActivatedAt().toLocalDate() : "").append('\n');
        }

        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"users.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @Operation(summary = "Восстановить позиции в дереве",
            description = """
                    Находит всех активных участников у которых есть inviter_id, но нет записи в tree_positions.
                    Размещает их в дерево через BFS. Используй если участники активные но не видны в дереве.
                    Возвращает список что было исправлено.
                    """)
    @PostMapping("/repair/tree-positions")
    public ResponseEntity<ApiResponse<List<String>>> repairTreePositions(
            @AuthenticationPrincipal User admin) {
        List<String> report = treeService.repairMissingPositions();
        auditService.log(admin, AdminActionType.REPAIR_TREE_POSITIONS, null,
                null, report.size() + " исправлено");
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    @Operation(summary = "Repair: завершить Stage 1 для участников, у которых матрица уже полная",
            description = "Находит всех Stage-1 пользователей где tier1+tier2 >= 6, но onStage1Completed не был вызван (последний слот заняли ускорители). Вызывает переход вручную.")
    @PostMapping("/repair/stage1-completions")
    public ResponseEntity<ApiResponse<List<String>>> repairStage1Completions(
            @AuthenticationPrincipal User admin) {
        List<String> report = treeService.repairMissingStage1Completions();
        auditService.log(admin, AdminActionType.REPAIR_STAGE1_COMPLETIONS, null,
                null, report.size() + " исправлено");
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    @Operation(summary = "Repair: разместить Stage-2 участников которые не были связаны в дереве",
            description = "Находит активных участников на Stage 2+, которые не являются чьим-либо fixedPartnerLeft/Right, и повторно запускает размещение. Исправляет отвязанные поддеревья.")
    @PostMapping("/repair/stage2-placements")
    public ResponseEntity<ApiResponse<List<String>>> repairStage2Placements(
            @AuthenticationPrincipal User admin) {
        List<String> report = treeService.repairStage2Placements();
        auditService.log(admin, AdminActionType.REPAIR_STAGE2_PLACEMENTS, null,
                null, report.size() + " исправлено");
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    @Operation(summary = "Repair: завершить Stage 3 для участников у которых вся команда уже на Stage 3",
            description = "Ищет участников на Stage 3, у которых оба fixedPartner уже >=Stage3, и вызывает onStage3Completed.")
    @PostMapping("/repair/stage3-completions")
    public ResponseEntity<ApiResponse<List<String>>> repairStage3Completions(
            @AuthenticationPrincipal User admin) {
        List<String> report = treeService.repairStage3Completions();
        auditService.log(admin, AdminActionType.REPAIR_STAGE3_COMPLETIONS, null,
                null, report.size() + " исправлено");
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    @Operation(summary = "Принудительно завершить Stage 2 для пользователя",
            description = "Вызывает onStage2Completed если оба fixed-partner слота уже заполнены, но стейдж не обновился. Тело: { \"userId\": \"uuid\" }")
    @PostMapping("/repair/trigger-stage2-complete")
    public ResponseEntity<ApiResponse<String>> triggerStage2Complete(@RequestBody Map<String, UUID> body,
            @AuthenticationPrincipal User admin) {
        UUID userId = body.get("userId");
        if (userId == null) throw BusinessException.of(ErrorCode.USER_NOT_FOUND);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
        if (user.getFixedPartnerLeft() == null || user.getFixedPartnerRight() == null) {
            return ResponseEntity.ok(ApiResponse.ok("SKIPPED: не оба слота заполнены"));
        }
        if (user.getCurrentStage() != 2) {
            return ResponseEntity.ok(ApiResponse.ok("SKIPPED: пользователь не на Stage 2 (текущий: " + user.getCurrentStage() + ")"));
        }
        treeService.onStage2Completed(user, user.getCurrentLevel());
        auditService.log(admin, AdminActionType.STAGE2_TRIGGERED, userId,
                user.getFirstName() + " " + user.getLastName(), null);
        return ResponseEntity.ok(ApiResponse.ok("OK: " + user.getFirstName() + " " + user.getLastName() + " переведён на Stage 3"));
    }

    @Operation(summary = "Переместить Stage-2 участника под нового хоста",
            description = """
                    Снимает пользователя с текущего хоста (если есть) и ставит под нового.
                    Если у нового хоста оба слота заполнятся — автоматически вызывается onStage2Completed.
                    Тело: { "userToMoveId": "uuid", "newHostId": "uuid" }
                    """)
    @PostMapping("/repair/move-stage2-partner")
    public ResponseEntity<ApiResponse<String>> moveStage2Partner(
            @RequestBody Map<String, UUID> body,
            @AuthenticationPrincipal User admin) {
        UUID userToMoveId = body.get("userToMoveId");
        UUID newHostId    = body.get("newHostId");
        if (userToMoveId == null || newHostId == null) {
            throw BusinessException.of(ErrorCode.USER_NOT_FOUND);
        }
        User userToMove = userRepository.findById(userToMoveId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
        User newHost = userRepository.findById(newHostId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
        String result = treeService.moveStage2Partner(userToMove, newHost);
        auditService.log(admin, AdminActionType.STAGE2_PARTNER_MOVED, userToMoveId,
                userToMove.getFirstName() + " " + userToMove.getLastName(),
                "→ " + newHost.getFirstName() + " " + newHost.getLastName());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Переставить пользователя в дереве вручную",
            description = """
                    Структурная правка дерева: меняет позицию пользователя под новым родителем.

                    **Что делает:**
                    - Переставляет запись в `tree_positions`
                    - Для `stage=2` также обновляет `fixedPartnerLeft/Right` у старого и нового родителя

                    **Что НЕ делает (намеренно):**
                    - Не пересчитывает бонусы
                    - Не вызывает завершение этапов
                    - Не запускает алгоритм размещения

                    Используй только для ручного исправления ошибок алгоритма.
                    После перестановки при необходимости вызови `/repair/trigger-stage2` или `/repair/stage2-placements`.

                    **position:** `1` = левый слот, `2` = правый слот
                    """)
    @PostMapping("/tree/reposition-user")
    public ResponseEntity<ApiResponse<String>> repositionUser(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User admin) {

        UUID userId      = UUID.fromString((String) body.get("userId"));
        UUID newParentId = UUID.fromString((String) body.get("newParentId"));
        int  position    = (Integer) body.get("position");
        int  level       = (Integer) body.get("level");
        int  stage       = (Integer) body.get("stage");

        User user      = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
        User newParent = userRepository.findById(newParentId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));

        String result = treeService.moveUserPosition(user, newParent, position, level, stage);

        auditService.log(admin, AdminActionType.USER_REPOSITIONED, userId,
                user.getFirstName() + " " + user.getLastName(),
                "→ " + newParent.getFirstName() + " " + newParent.getLastName()
                + " pos=" + position + " L" + level + "S" + stage);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ── Тестовые пользователи ────────────────────────────────────────────────

    @Operation(
            summary = "Создать тестовых пользователей",
            description = """
                    Создаёт и сразу активирует пользователей без OTP и без оплаты.
                    Телефон и паспорт генерируются автоматически.
                    `count` — сколько создать под этим реф-кодом (по умолчанию 6).
                    firstName/lastName необязательны.

                    Пример тела запроса:
                    ```json
                    [
                      {"inviterCode": "AZAMAT123", "count": 6},
                      {"inviterCode": "ALIYA_REF", "count": 3}
                    ]
                    ```
                    Ответ: имя, userId, referralCode для каждого созданного пользователя.
                    """)
    @PostMapping("/test/create-users")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> createTestUsers(
            @RequestBody List<TestUserEntry> entries,
            @AuthenticationPrincipal User admin) {

        List<Map<String, String>> results = new ArrayList<>();
        long base = System.currentTimeMillis() % 10_000_000L;
        int counter = 0;

        for (TestUserEntry e : entries) {
            int count = (e.count() != null && e.count() > 0) ? e.count() : 6;
            for (int i = 1; i <= count; i++) {
                long idx = base + counter++;
                String phone = "+99670" + String.format("%07d", idx);
                String firstName = (e.firstName() != null && !e.firstName().isBlank())
                        ? e.firstName() + i : "Test" + idx;
                String lastName  = (e.lastName()  != null && !e.lastName().isBlank())
                        ? e.lastName() : "User" + idx;

                greenecomall.dto.request.RegisterRequest req = new greenecomall.dto.request.RegisterRequest(
                        firstName, lastName, phone, null, "TEST" + idx, "test123456",
                        e.inviterCode(), null);

                greenecomall.dto.response.RegisterResponse reg = authService.registerByAdmin(req);
                paymentService.activateUserById(reg.userId());

                greenecomall.entity.User created = userRepository.findById(reg.userId()).orElseThrow();
                Map<String, String> row = new LinkedHashMap<>();
                row.put("name", created.getFirstName() + " " + created.getLastName());
                row.put("userId", reg.userId().toString());
                row.put("phone", phone);
                row.put("referralCode", created.getReferralCode());
                results.add(row);
            }
        }

        auditService.log(admin, AdminActionType.TEST_USERS_CREATED, null,
                null, results.size() + " пользователей создано");
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    public record TestUserEntry(String firstName, String lastName, String inviterCode, Integer count) {}

    @Operation(
            summary = "Быстрая регистрация тестового пользователя",
            description = """
                    Только имя, фамилия и реферальный код — телефон, паспорт и пароль генерируются автоматически.
                    Аккаунт сразу активируется. Пароль всегда `test123456`.

                    Пример:
                    ```json
                    { "firstName": "Айгуль", "lastName": "Иванова", "referralCode": "GEMADMIN" }
                    ```
                    """)
    @PostMapping("/test/quick-register")
    public ResponseEntity<ApiResponse<Map<String, String>>> quickRegister(
            @RequestBody QuickRegisterRequest req) {

        long idx = System.currentTimeMillis() % 100_000_000L;
        String phone    = "+99672" + String.format("%07d", idx);
        String passport = "QR" + idx;

        greenecomall.dto.request.RegisterRequest regReq = new greenecomall.dto.request.RegisterRequest(
                req.firstName(), req.lastName(), phone, req.email(), passport,
                "test123456", req.referralCode(), null);

        greenecomall.dto.response.RegisterResponse reg = authService.registerByAdmin(regReq);
        paymentService.activateUserById(reg.userId());

        greenecomall.entity.User created = userRepository.findById(reg.userId()).orElseThrow();

        Map<String, String> result = new LinkedHashMap<>();
        result.put("name",         created.getFirstName() + " " + created.getLastName());
        result.put("userId",       created.getId().toString());
        result.put("phone",        phone);
        result.put("password",     "test123456");
        result.put("referralCode", created.getReferralCode());

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    public record QuickRegisterRequest(String firstName, String lastName, String referralCode, String email) {}

    @Operation(
            summary = "Создать тестовых пользователей Уровня 0 (Fast Start)",
            description = """
                    Создаёт и сразу активирует Fast Start пользователей (уровень 0) без OTP и без оплаты.
                    Телефон и паспорт генерируются автоматически.
                    Пользователи попадают в очередь Быстрого Старта и автоматически спариваются.

                    Пример тела запроса:
                    ```json
                    [
                      {"firstName": "Тест", "lastName": "Один",  "inviterCode": "AZAMAT123"},
                      {"firstName": "Тест", "lastName": "Два",   "inviterCode": "AZAMAT123"},
                      {"firstName": "Тест", "lastName": "Три",   "inviterCode": "AZAMAT123"}
                    ]
                    ```
                    Ответ: имя, userId, phone, referralCode для каждого созданного пользователя.
                    """)
    @PostMapping("/test/create-fast-start-users")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> createFastStartTestUsers(
            @RequestBody List<TestUserEntry> entries) {

        List<Map<String, String>> results = new ArrayList<>();
        long base = System.currentTimeMillis() % 10_000_000L;
        int counter = 0;

        for (TestUserEntry e : entries) {
            for (int pair = 1; pair <= 2; pair++) {
                long idx = base + counter++;
                String phone = "+99671" + String.format("%07d", idx);
                String firstName = (e.firstName() != null && !e.firstName().isBlank())
                        ? e.firstName() + (pair == 2 ? "2" : "") : "FST" + idx;
                String lastName  = (e.lastName()  != null && !e.lastName().isBlank())
                        ? e.lastName()  + (pair == 2 ? "2" : "") : "User" + idx;

                greenecomall.dto.request.RegisterRequest req = new greenecomall.dto.request.RegisterRequest(
                        firstName, lastName,
                        phone,
                        null,
                        "FST" + idx,
                        "test123456",
                        e.inviterCode(),
                        greenecomall.enums.RegistrationPlan.FAST_START
                );

                greenecomall.dto.response.RegisterResponse reg = authService.registerByAdmin(req);
                paymentService.activateUserById(reg.userId());

                greenecomall.entity.User created = userRepository.findById(reg.userId()).orElseThrow();
                Map<String, String> row = new LinkedHashMap<>();
                row.put("name", firstName + " " + lastName);
                row.put("userId", reg.userId().toString());
                row.put("phone", phone);
                row.put("referralCode", created.getReferralCode());
                results.add(row);
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    // ── Новости ──────────────────────────────────────────────────────────────

    @Operation(summary = "Статистика новостей — 4 карточки")
    @GetMapping("/news/stats")
    public ResponseEntity<ApiResponse<NewsStatsResponse>> getNewsStats() {
        return ResponseEntity.ok(ApiResponse.ok(newsService.getStats()));
    }

    @Operation(summary = "Список всех новостей",
            description = "status: DRAFT | SCHEDULED | PUBLISHED | ARCHIVED | null (все). search — поиск по заголовку.")
    @GetMapping("/news")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<NewsItemResponse>>> adminNewsList(
            @RequestParam(required = false) NewsStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(newsService.adminList(status, search, page, size)));
    }

    @Operation(summary = "Создать и опубликовать новость")
    @PostMapping("/news")
    public ResponseEntity<ApiResponse<NewsItemResponse>> createNews(
            @RequestBody @jakarta.validation.Valid CreateNewsRequest req,
            @AuthenticationPrincipal User admin) {
        NewsItemResponse result = newsService.create(req);
        auditService.log(admin, AdminActionType.NEWS_CREATED, result.id(), result.title(), null);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Сохранить черновик")
    @PostMapping("/news/draft")
    public ResponseEntity<ApiResponse<NewsItemResponse>> saveDraft(
            @RequestBody @jakarta.validation.Valid CreateNewsRequest req,
            @AuthenticationPrincipal User admin) {
        NewsItemResponse result = newsService.saveDraft(req);
        auditService.log(admin, AdminActionType.NEWS_DRAFT_SAVED, result.id(), result.title(), null);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Редактировать новость")
    @PutMapping("/news/{id}")
    public ResponseEntity<ApiResponse<NewsItemResponse>> updateNews(
            @PathVariable UUID id,
            @RequestBody UpdateNewsRequest req,
            @AuthenticationPrincipal User admin) {
        NewsItemResponse result = newsService.update(id, req);
        auditService.log(admin, AdminActionType.NEWS_UPDATED, id, result.title(), null);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Опубликовать черновик/запланированную")
    @PatchMapping("/news/{id}/publish")
    public ResponseEntity<ApiResponse<NewsItemResponse>> publishNews(@PathVariable UUID id,
            @AuthenticationPrincipal User admin) {
        NewsItemResponse result = newsService.publish(id);
        auditService.log(admin, AdminActionType.NEWS_PUBLISHED, id, result.title(), null);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Архивировать новость")
    @PatchMapping("/news/{id}/archive")
    public ResponseEntity<ApiResponse<NewsItemResponse>> archiveNews(@PathVariable UUID id,
            @AuthenticationPrincipal User admin) {
        NewsItemResponse result = newsService.archive(id);
        auditService.log(admin, AdminActionType.NEWS_ARCHIVED, id, result.title(), null);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Восстановить из архива (переводит в черновик)")
    @PatchMapping("/news/{id}/restore")
    public ResponseEntity<ApiResponse<NewsItemResponse>> restoreNews(@PathVariable UUID id,
            @AuthenticationPrincipal User admin) {
        NewsItemResponse result = newsService.restore(id);
        auditService.log(admin, AdminActionType.NEWS_RESTORED, id, result.title(), null);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Закрепить / открепить новость")
    @PatchMapping("/news/{id}/pin")
    public ResponseEntity<ApiResponse<NewsItemResponse>> togglePin(@PathVariable UUID id,
            @AuthenticationPrincipal User admin) {
        NewsItemResponse result = newsService.togglePin(id);
        auditService.log(admin, AdminActionType.NEWS_PINNED, id, result.title(), null);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Удалить новость навсегда")
    @DeleteMapping("/news/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNews(@PathVariable UUID id,
            @AuthenticationPrincipal User admin) {
        auditService.log(admin, AdminActionType.NEWS_DELETED, id, null, null);
        newsService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(
            summary = "Добавить медиа к новости",
            description = """
                    Прикрепляет загруженный файл к новости.

                    **Шаг 1:** Загрузи файл через `POST /api/upload?type=NEWS_MEDIA` → получишь `objectKey`.
                    **Шаг 2:** Передай `objectKey` в это тело: `{"objectKey": "news/media/uuid.jpg"}`.

                    Возвращает новый медиа-объект с presigned URL.
                    """
    )
    @PostMapping("/news/{id}/media")
    public ResponseEntity<ApiResponse<greenecomall.dto.response.NewsMediaResponse>> addNewsMedia(
            @PathVariable UUID id,
            @RequestBody java.util.Map<String, String> body,
            @AuthenticationPrincipal User admin) {
        String objectKey = body.get("objectKey");
        var result = newsService.addMedia(id, objectKey);
        auditService.log(admin, AdminActionType.NEWS_MEDIA_ADDED, id, null, objectKey);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(
            summary = "Комментарии к новости",
            description = "Возвращает все комментарии к новости постранично (новые снизу). Работает для любого статуса новости.")
    @GetMapping("/news/{id}/comments")
    public ResponseEntity<ApiResponse<Page<NewsCommentResponse>>> getNewsComments(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(newsService.adminGetComments(id, page, size)));
    }

    @Operation(
            summary = "Удалить комментарий (админ)",
            description = "Администратор может удалить любой комментарий к любой новости.")
    @DeleteMapping("/news/{id}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteNewsComment(
            @PathVariable UUID id,
            @PathVariable UUID commentId,
            @AuthenticationPrincipal User admin) {
        newsService.adminDeleteComment(id, commentId);
        auditService.log(admin, AdminActionType.NEWS_COMMENT_DELETED, commentId, null,
                "newsId=" + id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(summary = "Удалить медиа из новости", description = "Удаляет медиа-вложение из новости и из хранилища.")
    @DeleteMapping("/news/{id}/media/{mediaId}")
    public ResponseEntity<ApiResponse<Void>> deleteNewsMedia(
            @PathVariable UUID id,
            @PathVariable UUID mediaId,
            @AuthenticationPrincipal User admin) {
        newsService.deleteMedia(id, mediaId);
        auditService.log(admin, AdminActionType.NEWS_MEDIA_DELETED, mediaId, null,
                "newsId=" + id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // РАЗБЛОКИРОВКА
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Разблокировать пользователя")
    @PatchMapping("/users/{id}/unblock")
    public ResponseEntity<ApiResponse<Void>> unblockUser(@PathVariable UUID id,
            @AuthenticationPrincipal User admin) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
        user.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(user);
        auditService.log(admin, AdminActionType.USER_UNBLOCKED, id,
                user.getFirstName() + " " + user.getLastName(), null);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ИСТОРИЯ БОНУСОВ ПОЛЬЗОВАТЕЛЯ
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(summary = "История бонусов пользователя",
            description = "Фильтры: type (REFERRAL|STAGE_BONUS|...), status (PENDING|CONFIRMED|CANCELLED). Сортировка: новые сверху.")
    @GetMapping("/users/{id}/bonuses")
    public ResponseEntity<ApiResponse<Page<BonusResponse>>> getUserBonuses(
            @PathVariable UUID id,
            @RequestParam(required = false) BonusType type,
            @RequestParam(required = false) BonusStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Bonus> raw;
        if (type != null && status != null) {
            raw = bonusRepository.findByUserAndTypeAndStatus(user, type, status, pageable);
        } else if (type != null) {
            raw = bonusRepository.findByUserAndType(user, type, pageable);
        } else if (status != null) {
            raw = bonusRepository.findByUserAndStatus(user, status, pageable);
        } else {
            raw = bonusRepository.findByUser(user, pageable);
        }

        Page<BonusResponse> result = raw.map(b -> BonusResponse.builder()
                .id(b.getId())
                .type(b.getType())
                .amount(b.getAmount())
                .status(b.getStatus())
                .level(b.getLevel())
                .stage(b.getStage())
                .description(b.getDescription())
                .fromUserName(b.getFromUser() != null
                        ? b.getFromUser().getFirstName() + " " + b.getFromUser().getLastName() : null)
                .confirmedAt(b.getConfirmedAt())
                .createdAt(b.getCreatedAt())
                .build());

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ОТПРАВИТЬ УВЕДОМЛЕНИЕ
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Отправить уведомление",
            description = """
                    Отправляет внутреннее уведомление (и опционально email) одному пользователю или всем активным.
                    - `userId` — если null, рассылка всем активным участникам
                    - `sendEmail` — дополнительно отправить письмо на email (только у кого он указан)
                    """)
    @PostMapping("/notifications/send")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendNotification(
            @RequestBody SendNotificationRequest req,
            @AuthenticationPrincipal User admin) {

        List<User> targets;
        if (req.userId() != null) {
            targets = List.of(userRepository.findById(req.userId())
                    .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND)));
        } else {
            targets = userRepository.findAll().stream()
                    .filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE
                            && u.getRole() != Role.ADMIN)
                    .toList();
        }

        int sent = 0;
        for (User u : targets) {
            notificationService.send(u, NotificationType.ANNOUNCEMENT, req.title(), req.body());
            sent++;
        }

        auditService.log(admin, AdminActionType.NOTIFICATION_SENT, req.userId(),
                req.title(), "push=" + sent);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("notificationsSent", sent)));
    }

    public record SendNotificationRequest(UUID userId, String title, String body) {}

    // ─────────────────────────────────────────────────────────────────────────
    // РЕЙТИНГ РЕФЕРАЛОВ
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Топ рефералов",
            description = "Возвращает топ-N пользователей по количеству приглашённых активных участников. По умолчанию топ-20.")
    @GetMapping("/stats/referrals")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTopReferrers(
            @RequestParam(defaultValue = "20") int limit) {

        Pageable pageable = PageRequest.of(0, limit);
        List<Object[]> rows = userRepository.findTopReferrers(pageable);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            User u = (User) row[0];
            long count = ((Number) row[1]).longValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", u.getId());
            item.put("name", u.getFirstName() + " " + u.getLastName());
            item.put("phone", u.getPhone());
            item.put("referralCode", u.getReferralCode());
            item.put("invitedCount", count);
            result.add(item);
        }

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ЭКСПОРТ ВЫВОДОВ В CSV
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Экспорт заявок на вывод в CSV",
            description = "Скачивает CSV с заявками на вывод. Фильтр `status`: PENDING | APPROVED | REJECTED (без фильтра — все).")
    @GetMapping(value = "/export/withdrawals", produces = "text/csv")
    public ResponseEntity<byte[]> exportWithdrawals(
            @RequestParam(required = false) WithdrawalStatus status) {

        List<Withdrawal> withdrawals = status != null
                ? withdrawalRepository.findByStatus(status, Pageable.unpaged()).getContent()
                : withdrawalRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Пользователь,Телефон,Сумма,Статус,Метод,Реквизиты,Банк,Примечание,Создано,Рассмотрено\n");
        for (Withdrawal w : withdrawals) {
            csv.append(w.getId()).append(',')
               .append(escape(w.getUser().getFirstName() + " " + w.getUser().getLastName())).append(',')
               .append(w.getUser().getPhone()).append(',')
               .append(w.getAmount()).append(',')
               .append(w.getStatus()).append(',')
               .append(w.getMethod()).append(',')
               .append(escape(w.getRequisite())).append(',')
               .append(escape(w.getBankName())).append(',')
               .append(escape(w.getAdminNote())).append(',')
               .append(w.getCreatedAt() != null ? w.getCreatedAt().toLocalDate() : "").append(',')
               .append(w.getReviewedAt() != null ? w.getReviewedAt().toLocalDate() : "").append('\n');
        }

        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"withdrawals.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    private String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @Operation(summary = "Динамика роста — данные для графика",
            description = """
                    Возвращает массив точек {date, newUsers, totalUsers} по дням за период.
                    **days** — количество дней назад (7, 30, 90). По умолчанию 30.
                    `totalUsers` — накопительный итог (cumulative), именно его рисуй на графике.
                    """)
    @GetMapping("/stats/growth")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getGrowthStats(
            @RequestParam(defaultValue = "30") int days) {

        LocalDateTime from = LocalDateTime.now().minusDays(days).toLocalDate().atStartOfDay();
        long baseCount = userRepository.countActiveBefore(from);

        List<Object[]> rows = userRepository.countActivatedByDay(from);
        Map<java.time.LocalDate, Long> byDay = new LinkedHashMap<>();
        for (Object[] row : rows) {
            java.time.LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            byDay.put(date, (Long) row[1]);
        }

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        long cumulative = baseCount;
        java.time.LocalDate today = java.time.LocalDate.now();
        for (java.time.LocalDate d = from.toLocalDate(); !d.isAfter(today); d = d.plusDays(1)) {
            long newUsers = byDay.getOrDefault(d, 0L);
            cumulative += newUsers;
            result.add(Map.of(
                    "date", d.toString(),
                    "newUsers", newUsers,
                    "totalUsers", cumulative
            ));
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Статистика платформы")
    @GetMapping({"/stats", "/dashboard/stats"})
    public ResponseEntity<ApiResponse<AdminStatsResponse>> getStats() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByAccountStatus(AccountStatus.ACTIVE);
        long pendingWithdrawals = withdrawalRepository.countByStatus(WithdrawalStatus.PENDING);
        BigDecimal totalVolume = paymentRepository.sumSuccessfulEntryFees();

        return ResponseEntity.ok(ApiResponse.ok(AdminStatsResponse.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .totalVolume(totalVolume)
                .pendingWithdrawals(pendingWithdrawals)
                .pendingPayments(0)
                .build()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // УПРАВЛЕНИЕ УСКОРИТЕЛЯМИ
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Переставить ускоритель вручную",
            description = """
                    Перемещает ускоритель указанного владельца под нового родителя на заданном уровне.
                    После перемещения автоматически проверяется завершение Этапа 1 у нового родителя.

                    Тело запроса:
                    - `acceleratorOwnerUserId` — ID пользователя, чей ускоритель переставляем
                    - `newParentUserId` — ID пользователя, под кого ставим ускоритель
                    - `level` — уровень дерева (обычно 1)

                    Вернёт ошибку если ускоритель не найден или у нового родителя нет свободных слотов.
                    """)
    @PostMapping("/tree/move-accelerator")
    public ResponseEntity<ApiResponse<Void>> moveAccelerator(
            @Valid @RequestBody greenecomall.dto.request.MoveAcceleratorRequest req,
            @AuthenticationPrincipal User admin) {
        treeService.adminMoveAccelerator(
                req.acceleratorOwnerUserId(),
                req.newParentUserId(),
                req.level());
        auditService.log(admin, AdminActionType.ACCELERATOR_MOVED,
                req.acceleratorOwnerUserId(), null,
                "→ parent=" + req.newParentUserId() + " level=" + req.level());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(
            summary = "История ускорителей пользователя",
            description = """
                    Возвращает список ускорителей, которые помогли данному пользователю завершить Этап 1.
                    Показывается только если `acceleratorAssisted = true` в карточке пользователя.
                    Каждая запись содержит имя и инициалы владельца ускорителя, уровень и дату завершения.
                    """)
    @GetMapping("/users/{id}/accelerator-history")
    public ResponseEntity<ApiResponse<List<greenecomall.dto.response.AcceleratorHistoryResponse>>> getAcceleratorHistory(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(treeService.getAcceleratorHistory(id)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ИСТОРИЯ ВЫВОДОВ И ДЕРЕВО
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "История выводов пользователя",
            description = """
                    Возвращает постраничный список всех заявок на вывод конкретного пользователя,
                    отсортированных от новых к старым.

                    **status** (необязательно): `PENDING` | `APPROVED` | `REJECTED`
                    """)
    @GetMapping("/users/{id}/withdrawals")
    public ResponseEntity<ApiResponse<Page<WithdrawalItemResponse>>> getUserWithdrawals(
            @PathVariable UUID id,
            @Parameter(description = "Статус: PENDING | APPROVED | REJECTED")
            @RequestParam(required = false) WithdrawalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Withdrawal> raw = (status != null)
                ? withdrawalRepository.findByUserAndStatus(user, status, pageable)
                : withdrawalRepository.findByUser(user, pageable);

        Page<WithdrawalItemResponse> result = raw.map(w -> WithdrawalItemResponse.builder()
                .id(w.getId())
                .userId(w.getUser().getId())
                .userName(w.getUser().getFirstName() + " " + w.getUser().getLastName())
                .userPhone(w.getUser().getPhone())
                .amount(w.getAmount())
                .status(w.getStatus())
                .method(w.getMethod().name())
                .requisite(w.getRequisite())
                .bankName(w.getBankName())
                .adminNote(w.getAdminNote())
                .createdAt(w.getCreatedAt())
                .reviewedAt(w.getReviewedAt())
                .build());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(
            summary = "Дерево от имени любого пользователя",
            description = """
                    Позволяет администратору просматривать дерево любого участника — как будто
                    тот сам смотрит свой экран. Параметры уровня и этапа те же что у `/api/tree/my`.

                    Используй `userId` первого реального пользователя чтобы увидеть корень всей сети.
                    """)
    @GetMapping("/tree/view/{userId}")
    public ResponseEntity<ApiResponse<greenecomall.dto.response.TreeResponse>> viewUserTree(
            @PathVariable UUID userId,
            @Parameter(description = "Уровень 1-4", example = "1") @RequestParam(defaultValue = "1") int level,
            @Parameter(description = "Этап 1-4",   example = "1") @RequestParam(defaultValue = "1") int stage) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.ok(treeService.getTree(user, level, stage)));
    }

    @Operation(
            summary = "Обзор этапов от имени любого пользователя",
            description = "Показывает все 4 этапа (overview) для указанного участника — аналог `/api/tree/overview`.")
    @GetMapping("/tree/overview/{userId}")
    public ResponseEntity<ApiResponse<greenecomall.dto.response.StagesOverviewResponse>> viewUserOverview(
            @PathVariable UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.ok(treeService.getStagesOverview(user)));
    }

    @Operation(
            summary = "Лента активности от имени любого пользователя",
            description = "Показывает события команды для указанного участника — аналог `/api/tree/activity`.")
    @GetMapping("/tree/activity/{userId}")
    public ResponseEntity<ApiResponse<List<greenecomall.dto.response.TeamActivityResponse>>> viewUserActivity(
            @PathVariable UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.ok(treeService.getTeamActivity(user)));
    }

    @Operation(
            summary = "Статистика веток от имени любого пользователя",
            description = "Показывает левую и правую ветку дерева для указанного участника — аналог `/api/tree/branches`.")
    @GetMapping("/tree/branches/{userId}")
    public ResponseEntity<ApiResponse<greenecomall.dto.response.BranchStatsResponse>> viewUserBranches(
            @PathVariable UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.ok(treeService.getBranchStats(user)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ИСТОРИЯ ДЕЙСТВИЙ АДМИНИСТРАТОРА
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "История действий администраторов",
            description = """
                    Возвращает лог всех действий администраторов: блокировки, выводы,
                    работа с деревом, новости и repair-операции.

                    **Фильтры (все необязательны):**
                    - `adminId` — показать только действия конкретного администратора
                    - `actionType` — тип действия (см. enum ниже)

                    **Типы действий (`actionType`):**
                    `USER_BLOCKED` | `USER_ACTIVATED` | `USER_CREATED` |
                    `WITHDRAWAL_APPROVED` | `WITHDRAWAL_REJECTED` |
                    `ACCELERATOR_MOVED` | `STAGE2_PARTNER_MOVED` | `STAGE2_TRIGGERED` |
                    `REPAIR_TREE_POSITIONS` | `REPAIR_STAGE1_COMPLETIONS` |
                    `REPAIR_STAGE2_PLACEMENTS` | `REPAIR_STAGE3_COMPLETIONS` |
                    `NEWS_CREATED` | `NEWS_DRAFT_SAVED` | `NEWS_UPDATED` |
                    `NEWS_PUBLISHED` | `NEWS_ARCHIVED` | `NEWS_RESTORED` |
                    `NEWS_PINNED` | `NEWS_DELETED` | `NEWS_COMMENT_DELETED` |
                    `NEWS_MEDIA_ADDED` | `NEWS_MEDIA_DELETED` |
                    `TEST_USERS_CREATED` | `TEST_FAST_START_USERS_CREATED`
                    """)
    @GetMapping("/activity")
    public ResponseEntity<ApiResponse<Page<AdminActionLogResponse>>> getAdminActivity(
            @Parameter(description = "ID администратора (необязательно)")
            @RequestParam(required = false) UUID adminId,
            @Parameter(description = "Тип действия (необязательно)")
            @RequestParam(required = false) AdminActionType actionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                auditService.getHistory(adminId, actionType, page, size)));
    }

    @Operation(summary = "Аналитика доходов по каждому пользователю",
            description = """
                    Для каждого участника показывает с позиции платформы:
                    — paidToAdmin       : взнос, который сам пользователь заплатил платформе
                    — teamSize          : сколько активных человек в его команде (прямые рефералы)
                    — teamPaidToAdmin   : сумма взносов его команды, поступивших платформе
                    — totalAdminIncome  : итого доход платформы с него + его команды
                    — userEarned        : сколько сам пользователь заработал (подтверждённые бонусы)

                    Только ACTIVE пользователи, без ADMIN.
                    """)
    @GetMapping("/analytics/roi")
    public ResponseEntity<ApiResponse<Page<greenecomall.dto.response.UserRoiResponse>>> getRoiAnalytics(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<User> users = userRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("accountStatus"), AccountStatus.ACTIVE),
                        cb.notEqual(root.get("role"), greenecomall.enums.Role.ADMIN)
                ), pageable);

        Page<greenecomall.dto.response.UserRoiResponse> result = users.map(u -> {
            BigDecimal paidToAdmin     = paymentRepository.sumPaidEntryFeeByUser(u);
            long teamSize              = userRepository.countByInviterAndAccountStatus(u, AccountStatus.ACTIVE);
            BigDecimal teamPaid        = paymentRepository.sumPaidEntryFeeByTeam(u);
            BigDecimal totalAdminIncome = paidToAdmin.add(teamPaid);
            BigDecimal userEarned      = bonusRepository.sumByUserAndStatus(u, greenecomall.enums.BonusStatus.CONFIRMED);

            return new greenecomall.dto.response.UserRoiResponse(
                    u.getId(),
                    u.getFirstName() + " " + u.getLastName(),
                    u.getPhone(),
                    u.getRegistrationPlan(),
                    paidToAdmin,
                    teamSize,
                    teamPaid,
                    totalAdminIncome,
                    userEarned
            );
        });

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Суммарная аналитика платформы",
            description = """
                    Общие цифры по всей платформе:
                    — totalActiveUsers       : всего активных участников
                    — totalAdminIncome       : итого собрано взносов платформой
                    — totalUserEarned        : итого выплачено бонусов участникам
                    — avgAdminIncomePerUser  : средний доход платформы на участника
                    — avgUserEarned          : средний заработок участника
                    """)
    @GetMapping("/analytics/summary")
    public ResponseEntity<ApiResponse<greenecomall.dto.response.RoiSummaryResponse>> getAnalyticsSummary() {
        long activeUsers      = userRepository.countByAccountStatus(AccountStatus.ACTIVE);
        BigDecimal totalFees  = paymentRepository.sumSuccessfulEntryFees();
        BigDecimal totalBonus = bonusRepository.sumTotalConfirmed();
        BigDecimal avgIncome  = activeUsers > 0
                ? totalFees.divide(BigDecimal.valueOf(activeUsers), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal avgEarned  = activeUsers > 0
                ? totalBonus.divide(BigDecimal.valueOf(activeUsers), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return ResponseEntity.ok(ApiResponse.ok(new greenecomall.dto.response.RoiSummaryResponse(
                activeUsers,
                totalFees,
                totalBonus,
                avgIncome,
                avgEarned
        )));
    }
}
