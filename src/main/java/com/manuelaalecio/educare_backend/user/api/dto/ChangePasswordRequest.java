package com.manuelaalecio.educare_backend.user.api.dto;

import com.manuelaalecio.educare_backend.shared.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/users/{id}/password}.
 */
public record ChangePasswordRequest(

		@NotNull(message = "é obrigatória")
		@Size(min = 8, max = 72, message = "deve ter entre {min} e {max} caracteres")
		@MaxUtf8Bytes(72)
		String password) {
}
