package greenecomall.service;

import greenecomall.dto.request.*;
import greenecomall.dto.response.InviterResponse;
import greenecomall.dto.response.LoginHistoryResponse;
import greenecomall.dto.response.LoginResponse;
import greenecomall.dto.response.RegisterResponse;
import greenecomall.entity.LoginHistory;
import greenecomall.entity.OtpCode;
import greenecomall.entity.Payment;
import greenecomall.entity.User;
import greenecomall.enums.*;
import greenecomall.exception.BusinessException;
import greenecomall.exception.ErrorCode;
import greenecomall.repository.LoginHistoryRepository;
import greenecomall.repository.OtpCodeRepository;
import greenecomall.repository.PaymentRepository;
import greenecomall.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import greenecomall.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final PaymentRepository paymentRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final SmsService smsService;
    private final PaymentService paymentService;

    private static final String REFERRAL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @org.springframework.beans.factory.annotation.Value("${app.admin.referral-code:GEMADMIN}")
    private String adminReferralCode;

    @org.springframework.beans.factory.annotation.Value("${app.admin.phone:+996700000000}")
    private String adminPhone;

    @Value("${app.payment.auto-approve:false}")
    private boolean autoApprovePayment;

    @Value("${app.fee.fast-start:1}") private BigDecimal feeFastStart;
    @Value("${app.fee.level1:1}")     private BigDecimal feeLevel1;
    @Value("${app.fee.level2:1}")     private BigDecimal feeLevel2;
    @Value("${app.fee.level3:1}")     private BigDecimal feeLevel3;
    @Value("${app.fee.level4:1}")     private BigDecimal feeLevel4;

    @Transactional
    public LocalDateTime sendOtp(String contact, String clientIp) {
        return smsService.sendOtp(contact);
    }

    @Transactional
    public void verifyOtp(String contact, String code) {
        String key = smsService.formatPhone(contact);
        OtpCode otp = otpCodeRepository
                .findFirstByPhoneAndIsUsedFalseOrderByCreatedAtDesc(key)
                .orElseThrow(() -> BusinessException.of(ErrorCode.INVALID_OTP));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now()) || !otp.getCode().equals(code)) {
            throw BusinessException.of(ErrorCode.INVALID_OTP);
        }

        otp.setIsUsed(true);
    }

    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        return registerInternal(req, true);
    }

    @Transactional
    public RegisterResponse registerByAdmin(RegisterRequest req) {
        return registerInternal(req, false);
    }

    private RegisterResponse registerInternal(RegisterRequest req, boolean requireOtp) {
        // requireOtp parameter kept for admin flow but normal registration no longer
        // requires prior OTP — phone is verified in steps 2-3 after account creation

        if (userRepository.existsByPhone(req.phone())) {
            throw BusinessException.of(ErrorCode.PHONE_ALREADY_EXISTS);
        }
        if (userRepository.existsByPassportNumber(req.passportNumber())) {
            throw BusinessException.of(ErrorCode.PASSPORT_ALREADY_EXISTS);
        }

        User inviter = userRepository.findByReferralCode(req.referralCode())
                .orElseThrow(() -> BusinessException.of(ErrorCode.INVALID_REFERRAL_CODE));

        RegistrationPlan plan = req.plan() != null ? req.plan() : RegistrationPlan.STANDARD;
        int startingLevel = switch (plan) {
            case FAST_START -> 0;
            case STANDARD   -> 1;
            case LEVEL_2    -> 2;
            case LEVEL_3    -> 3;
            case LEVEL_4    -> 4;
        };
        BigDecimal fee = switch (plan) {
            case FAST_START -> feeFastStart;
            case STANDARD   -> feeLevel1;
            case LEVEL_2    -> feeLevel2;
            case LEVEL_3    -> feeLevel3;
            case LEVEL_4    -> feeLevel4;
        };

        User user = User.builder()
                .firstName(req.firstName())
                .lastName(req.lastName())
                .phone(req.phone())
                .passportNumber(req.passportNumber())
                .passwordHash(passwordEncoder.encode(req.password()))
                .referralCode(generateUniqueReferralCode())
                .inviter(inviter)
                .role(Role.USER)
                .accountStatus(AccountStatus.PENDING)
                .currentLevel(startingLevel)
                .currentStage(1)
                .registrationPlan(plan)
                .codeWord(null)
                .build();

        if (req.email() != null && !req.email().isBlank()) {
            user.setEmail(req.email().toLowerCase());
        }

        user = userRepository.save(user);

        Payment payment = Payment.builder()
                .user(user)
                .type(PaymentType.ENTRY_FEE)
                .amount(fee)
                .status(PaymentStatus.PENDING)
                .build();
        payment = paymentRepository.save(payment);

        if (autoApprovePayment) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);
            paymentService.activateUserById(user.getId());
            log.info("Auto-approved payment for user {}", user.getId());
        }

        return new RegisterResponse(
                user.getId(),
                autoApprovePayment ? null : payment.getId(),
                jwtUtil.generateAccessToken(user.getId(), user.getRole()),
                jwtUtil.generateRefreshToken(user.getId())
        );
    }

    @Transactional
    public LocalDateTime forgotPassword(ForgotPasswordRequest req) {
        User user = userRepository.findByPhone(req.phone())
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
        String displayName = user.getFirstName() + " " + user.getLastName();
        return smsService.sendOtpForPasswordReset(req.phone(), displayName);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        String key = smsService.formatPhone(req.phone());

        OtpCode otp = otpCodeRepository
                .findFirstByPhoneAndIsUsedFalseOrderByCreatedAtDesc(key)
                .orElseThrow(() -> BusinessException.of(ErrorCode.INVALID_OTP));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now()) || !otp.getCode().equals(req.code())) {
            throw BusinessException.of(ErrorCode.INVALID_OTP);
        }
        otp.setIsUsed(true);

        User user = userRepository.findByPhone(req.phone())
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest req, String ipAddress, String userAgent) {
        User user = userRepository.findByPhone(req.phone())
                .orElseThrow(() -> BusinessException.of(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw BusinessException.of(ErrorCode.INVALID_CREDENTIALS);
        }

        if (user.getAccountStatus() == AccountStatus.BLOCKED) {
            throw BusinessException.of(ErrorCode.ACCOUNT_BLOCKED);
        }

        loginHistoryRepository.save(LoginHistory.builder()
                .user(user)
                .phone(req.phone())
                .ipAddress(ipAddress)
                .userAgent(userAgent != null && userAgent.length() > 512
                        ? userAgent.substring(0, 512) : userAgent)
                .build());

        if (user.getAccountStatus() == AccountStatus.PENDING) {
            Payment payment = paymentRepository
                    .findFirstByUserAndTypeOrderByCreatedAtDesc(user, PaymentType.ENTRY_FEE)
                    .orElse(null);
            return LoginResponse.builder()
                    .needsPayment(true)
                    .paymentId(payment != null ? payment.getId() : null)
                    .userId(user.getId())
                    .accessToken(jwtUtil.generateAccessToken(user.getId(), user.getRole()))
                    .refreshToken(jwtUtil.generateRefreshToken(user.getId()))
                    .build();
        }

        return LoginResponse.builder()
                .accessToken(jwtUtil.generateAccessToken(user.getId(), user.getRole()))
                .refreshToken(jwtUtil.generateRefreshToken(user.getId()))
                .userId(user.getId())
                .role(user.getRole().name())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<LoginHistoryResponse> getLoginHistory(User user, int page, int size) {
        return loginHistoryRepository
                .findByUserOrderByCreatedAtDesc(user, PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(h -> LoginHistoryResponse.builder()
                        .id(h.getId())
                        .phone(h.getPhone())
                        .ipAddress(h.getIpAddress())
                        .userAgent(h.getUserAgent())
                        .createdAt(h.getCreatedAt())
                        .build());
    }

    public LoginResponse refresh(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw BusinessException.of(ErrorCode.INVALID_TOKEN);
        }
        java.util.UUID userId = jwtUtil.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.USER_NOT_FOUND));

        return LoginResponse.builder()
                .accessToken(jwtUtil.generateAccessToken(user.getId(), user.getRole()))
                .refreshToken(jwtUtil.generateRefreshToken(user.getId()))
                .userId(user.getId())
                .role(user.getRole().name())
                .build();
    }

    public java.util.Map<String, String> getPlatformReferralCode() {
        // Return the admin's referral code so new users can join without a personal invite
        User admin = userRepository.findByPhone(adminPhone)
                .orElseGet(() -> userRepository.findByReferralCode(adminReferralCode).orElse(null));
        String code = (admin != null) ? admin.getReferralCode() : adminReferralCode;
        return java.util.Map.of(
                "referralCode", code,
                "note", "Используй этот код для регистрации если у тебя нет реферальной ссылки"
        );
    }

    public InviterResponse getInviterInfo(String referralCode) {
        User inviter = userRepository.findByReferralCode(referralCode)
                .orElseThrow(() -> BusinessException.of(ErrorCode.INVALID_REFERRAL_CODE));
        String firstName = inviter.getFirstName();
        String lastName = inviter.getLastName();
        String initials = String.valueOf(firstName.charAt(0)).toUpperCase()
                + String.valueOf(lastName.charAt(0)).toUpperCase();
        return InviterResponse.builder()
                .name(firstName + " " + lastName)
                .initials(initials)
                .currentLevel(inviter.getCurrentLevel())
                .currentStage(inviter.getCurrentStage())
                .referralCode(inviter.getReferralCode())
                .build();
    }

    private String generateUniqueReferralCode() {
        SecureRandom rng = new SecureRandom();
        String code;
        do {
            StringBuilder sb = new StringBuilder(14);
            for (int i = 0; i < 14; i++) {
                sb.append(REFERRAL_CHARS.charAt(rng.nextInt(REFERRAL_CHARS.length())));
            }
            code = sb.toString();
        } while (userRepository.findByReferralCode(code).isPresent());
        return code;
    }
}
