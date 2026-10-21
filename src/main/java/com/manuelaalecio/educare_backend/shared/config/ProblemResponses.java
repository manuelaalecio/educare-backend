package com.manuelaalecio.educare_backend.shared.config;

import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * Names shared by the {@code ProblemDetail} schema, the error meta-annotations ({@link BadRequestResponse} etc.) and
 * the customizers of the OpenAPI document.
 */
public final class ProblemResponses {

	public static final String MEDIA_TYPE = org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

	public static final String PROBLEM_DETAIL_SCHEMA = "ProblemDetail";

	public static final String PROBLEM_DETAIL_REF = "#/components/schemas/" + PROBLEM_DETAIL_SCHEMA;

	static final String FIELD_ERROR_SCHEMA = "FieldError";

	static final String FIELD_ERROR_REF = "#/components/schemas/" + FIELD_ERROR_SCHEMA;

	private ProblemResponses() {
	}

	/** An error response with a {@code ProblemDetail} body, for the customizers that add responses. */
	static ApiResponse problemResponse(String description) {
		return new ApiResponse().description(description)
			.content(new Content().addMediaType(MEDIA_TYPE,
					new MediaType().schema(new Schema<>().$ref(PROBLEM_DETAIL_REF))));
	}

}
