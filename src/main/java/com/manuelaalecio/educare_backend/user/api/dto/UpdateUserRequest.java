package com.manuelaalecio.educare_backend.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/users/{id}}. It replaces name, login and role, so all three are required.
 */
public record UpdateUserRequest(

		@NotBlank(message = "é obrigatório")
		@Size(max = 150, message = "deve ter no máximo {max} caracteres")
		String name,

		@NotBlank(message = "é obrigatório")
		@Size(max = 254, message = "deve ter no máximo {max} caracteres")
		@Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "deve ser um email no formato nome@dominio.tld")
		String login,

		@NotNull(message = "é obrigatória")
		@Pattern(regexp = "ADMIN|USER", message = "deve ser ADMIN ou USER")
		String role) {
}
