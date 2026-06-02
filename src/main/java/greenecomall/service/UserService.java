package greenecomall.service;

import greenecomall.dto.request.UpdateProfileRequest;
import greenecomall.dto.response.UserProfileResponse;
import greenecomall.dto.response.UserProgressResponse;
import greenecomall.dto.response.UserProgressResponse.LevelProgress;
import greenecomall.dto.response.UserProgressResponse.LevelStatus;
import greenecomall.dto.response.UserProgressResponse.StageProgress;
import greenecomall.dto.response.UserProgressResponse.StageStatus;
import greenecomall.entity.User;
import greenecomall.enums.RegistrationPlan;
import greenecomall.exception.BusinessException;
import greenecomall.exception.ErrorCode;
import greenecomall.repository.BonusRepository;
import greenecomall.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MinioService minioService;
    private final BonusRepository bonusRepository;

    @Value("${app.base-url:https://green-eco-mall-client.up.railway.app}")
    private String baseUrl;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(User user) {
        User u = userRepository.findById(user.getId()).orElse(user);
        User inviter = u.getInviter();
        return UserProfileResponse.builder()
                .id(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .phone(u.getPhone())
                .email(u.getEmail())
                .passportNumber(u.getPassportNumber())
                .referralCode(u.getReferralCode())
                .referralLink(baseUrl + "/join?ref=" + u.getReferralCode())
                .role(u.getRole())
                .accountStatus(u.getAccountStatus())
                .currentLevel(u.getCurrentLevel())
                .currentStage(u.getCurrentStage())
                .balance(u.getBalance())
                .finikPhone(u.getFinikPhone())
                .inviterId(inviter != null ? inviter.getId() : null)
                .inviterName(inviter != null ? inviter.getFirstName() + " " + inviter.getLastName() : null)
                .inviterReferralCode(inviter != null ? inviter.getReferralCode() : null)
                .createdAt(u.getCreatedAt())
                .activatedAt(u.getActivatedAt())
                .registrationPlan(u.getRegistrationPlan())
                .fastStartNumber(u.getFastStartNumber())
                .acceleratorAssisted(Boolean.TRUE.equals(u.getAcceleratorAssisted()))
                .avatarUrl(minioService.presign(u.getAvatarKey()))
                .build();
    }

    @Transactional(readOnly = true)
    public UserProgressResponse getProgress(User user) {
        User u = userRepository.findById(user.getId()).orElse(user);

        int curLevel = u.getCurrentLevel();
        int curStage = u.getCurrentStage();

        int startingLevel = switch (u.getRegistrationPlan()) {
            case FAST_START -> 1; // Fast Start выходит на уровень 1
            case STANDARD   -> 1;
            case LEVEL_2    -> 2;
            case LEVEL_3    -> 3;
            case LEVEL_4    -> 4;
        };

        // Всего этапов в маршруте пользователя: с startingLevel до 4, по 4 этапа
        int totalStages = (4 - startingLevel + 1) * 4;
        int completedStages = 0;

        var levels = new ArrayList<LevelProgress>();

        for (int lvl = 1; lvl <= 4; lvl++) {
            LevelStatus levelStatus;
            if (lvl < startingLevel) {
                levelStatus = LevelStatus.SKIPPED;
            } else if (lvl < curLevel) {
                levelStatus = LevelStatus.COMPLETED;
            } else if (lvl == curLevel) {
                levelStatus = LevelStatus.CURRENT;
            } else {
                levelStatus = LevelStatus.LOCKED;
            }

            var stages = new ArrayList<StageProgress>();
            int stagesDone = 0;

            for (int stg = 1; stg <= 4; stg++) {
                StageStatus stageStatus;
                BigDecimal earned = BigDecimal.ZERO;

                if (levelStatus == LevelStatus.SKIPPED) {
                    stageStatus = StageStatus.SKIPPED;
                } else if (levelStatus == LevelStatus.COMPLETED
                        || (lvl == curLevel && stg < curStage)) {
                    stageStatus = StageStatus.COMPLETED;
                    earned = bonusRepository.sumConfirmedByUserAndLevelAndStage(u, lvl, stg);
                    stagesDone++;
                    if (lvl >= startingLevel) completedStages++;
                } else if (lvl == curLevel && stg == curStage) {
                    stageStatus = StageStatus.CURRENT;
                } else {
                    stageStatus = StageStatus.LOCKED;
                }

                stages.add(new StageProgress(stg, stageStatus, earned));
            }

            // Для полностью завершённого уровня добираем этапы через completed
            if (levelStatus == LevelStatus.COMPLETED) {
                stagesDone = 4;
                // completedStages уже посчитан выше только для CURRENT level,
                // поэтому для COMPLETED уровней добавляем вручную
                // (сброшенный счётчик — пересчитаем ниже)
            }

            levels.add(new LevelProgress(lvl, levelStatus, stagesDone, stages));
        }

        // Пересчёт completedStages: суммируем по всем COMPLETED уровням + CURRENT
        completedStages = 0;
        for (LevelProgress lp : levels) {
            if (lp.status() == LevelStatus.COMPLETED) {
                completedStages += 4;
            } else if (lp.status() == LevelStatus.CURRENT) {
                completedStages += (int) lp.stages().stream()
                        .filter(s -> s.status() == StageStatus.COMPLETED).count();
            }
        }

        int progressPercent = totalStages > 0
                ? (int) Math.round((completedStages * 100.0) / totalStages)
                : 0;

        return new UserProgressResponse(
                curLevel, curStage, startingLevel,
                u.getRegistrationPlan(),
                totalStages, completedStages, progressPercent,
                levels
        );
    }

    @Transactional
    public UserProfileResponse updateProfile(User user, UpdateProfileRequest req) {
        if (req.firstName() != null && !req.firstName().isBlank())
            user.setFirstName(req.firstName().trim());
        if (req.lastName() != null && !req.lastName().isBlank())
            user.setLastName(req.lastName().trim());
        if (req.passportNumber() != null && !req.passportNumber().isBlank()) {
            String passport = req.passportNumber().trim();
            if (!passport.equals(user.getPassportNumber())
                    && userRepository.existsByPassportNumber(passport)) {
                throw BusinessException.of(ErrorCode.PASSPORT_ALREADY_EXISTS);
            }
            user.setPassportNumber(passport);
        }
        if (req.email() != null && !req.email().isBlank()) {
            user.setEmail(req.email().toLowerCase().trim());
        }
        if (req.password() != null && !req.password().isBlank())
            user.setPasswordHash(passwordEncoder.encode(req.password()));
        if (req.finikPhone() != null)
            user.setFinikPhone(req.finikPhone());
        if (req.avatarKey() != null && !req.avatarKey().isBlank()) {
            if (user.getAvatarKey() != null) {
                minioService.delete(user.getAvatarKey());
            }
            user.setAvatarKey(req.avatarKey());
        }
        userRepository.save(user);
        return getProfile(user);
    }
}
