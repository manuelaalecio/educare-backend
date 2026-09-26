package com.manuelaalecio.educare_backend.auth.api.dto;

import com.manuelaalecio.educare_backend.shared.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/auth/me/password}. The new password follows the same rules as the user creation.
 */
public record ChangeOwnPasswordRequest(

		@NotBlank(message = "é obrigatória")
		@Size(max = 72, message = "deve ter no máximo {max} caracteres")
		@MaxUtf8Bytes(72)
		String currentPassword,

		@NotNull(message = "é obrigatória")
		@Size(min = 8, max = 72, message = "deve ter entre {min} e {max} caracteres")
		@MaxUtf8Bytes(72)
		String newPassword) {
}
