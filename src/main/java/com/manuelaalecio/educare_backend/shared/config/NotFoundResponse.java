package com.manuelaalecio.educare_backend.shared.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * Declares in the OpenAPI contract the {@code 404} response, with a {@code ProblemDetail} body, of an operation
 * that looks up a resource by id. On a controller class, it applies to every operation of the class.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ApiResponse(responseCode = "404", description = "Not Found", content = @Content(mediaType = ProblemResponses.MEDIA_TYPE,
		schema = @Schema(ref = ProblemResponses.PROBLEM_DETAIL_REF)))
public @interface NotFoundResponse {
}
