package com.manuelaalecio.educare_backend.classroom.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/classrooms}. The name is stripped when the record is built, so the validation (and its length limit)
 * applies to the value that will be stored.
 */
public record CreateClassroomRequest(

		@NotBlank(message = "é obrigatório")
		@Size(max = 60, message = "deve ter no máximo {max} caracteres")
		String name) {

	public CreateClassroomRequest {
		name = name == null ? null : name.strip();
	}

}
