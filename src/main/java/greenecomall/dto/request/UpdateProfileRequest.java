package greenecomall.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        String firstName,
        String lastName,
        String passportNumber,

        @Email(message = "Некорректный email")
        String email,

        @Size(min = 6, message = "Пароль минимум 6 символов")
        String password,

        @Pattern(regexp = "\\+996\\d{9}", message = "Формат телефона: +996XXXXXXXXX")
        String finikPhone,

        /** Object key из ответа POST /api/upload?type=AVATAR */
        String avatarKey
) {}
