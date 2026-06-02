package greenecomall.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAdminRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,

        @NotBlank
        @Pattern(regexp = "\\+996\\d{9}", message = "Формат телефона: +996XXXXXXXXX")
        String phone,

        @NotBlank
        @Size(min = 6, message = "Пароль минимум 6 символов")
        String password
) {}
