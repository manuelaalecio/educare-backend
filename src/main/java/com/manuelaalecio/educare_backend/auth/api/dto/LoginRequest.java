package com.manuelaalecio.educare_backend.auth.api.dto;

import com.manuelaalecio.educare_backend.shared.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/login}. The login format is not validated: a value that is not an email simply
 * matches no user. The password limit avoids the BCrypt error above 72 bytes.
 */
public record LoginRequest(

		@NotBlank(message = "é obrigatório")
		@Size(max = 254, message = "deve ter no máximo {max} caracteres")
		String login,

		@NotBlank(message = "é obrigatória")
		@Size(max = 72, message = "deve ter no máximo {max} caracteres")
		@MaxUtf8Bytes(72)
		String password) {
}
