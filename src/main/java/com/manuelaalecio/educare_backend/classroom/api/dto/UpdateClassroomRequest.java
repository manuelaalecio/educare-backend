package com.manuelaalecio.educare_backend.classroom.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/classrooms/{id}}. The name is stripped when the record is built, so the validation (and its length limit)
 * applies to the value that will be stored.
 */
public record UpdateClassroomRequest(

		@NotBlank(message = "é obrigatório")
		@Size(max = 60, message = "deve ter no máximo {max} caracteres")
		String name) {

	public UpdateClassroomRequest {
		name = name == null ? null : name.strip();
	}

}
