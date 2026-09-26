package com.manuelaalecio.educare_backend.user.api.dto;

import com.manuelaalecio.educare_backend.shared.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/users}. The role is optional: when absent, the user is created as {@code USER}.
 */
public record CreateUserRequest(

		@NotBlank(message = "é obrigatório")
		@Size(max = 150, message = "deve ter no máximo {max} caracteres")
		String name,

		@NotBlank(message = "é obrigatório")
		@Size(max = 254, message = "deve ter no máximo {max} caracteres")
		@Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "deve ser um email no formato nome@dominio.tld")
		String login,

		@NotNull(message = "é obrigatória")
		@Size(min = 8, max = 72, message = "deve ter entre {min} e {max} caracteres")
		@MaxUtf8Bytes(72)
		String password,

		@Pattern(regexp = "ADMIN|USER", message = "deve ser ADMIN ou USER")
		String role) {
}
