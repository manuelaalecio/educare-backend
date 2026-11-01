package com.manuelaalecio.educare_backend.shared.error;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Converts every error raised by the API into an RFC 9457 {@link ProblemDetail}. The MVC errors (unreadable
 * body, malformed path variable, unsupported method etc.) are handled by {@link ResponseEntityExceptionHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	static final String ERRORS_PROPERTY = "errors";

	static final String INVALID_FIELDS_DETAIL = "Um ou mais campos são inválidos";

	static final String INVALID_FORMAT_MESSAGE = "valor em formato inválido";

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ex.getBody();
		problem.setDetail(INVALID_FIELDS_DETAIL);
		List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> fieldError(error.getField(), String.valueOf(error.getDefaultMessage())))
				.toList();
		problem.setProperty(ERRORS_PROPERTY, errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	/**
	 * A body that Jackson could not map to a field (malformed date, wrong type etc.) becomes an {@code errors}
	 * entry for that field, without echoing the received value. Without an identifiable field (broken JSON,
	 * missing body), the generic {@code 400} is kept.
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Optional<String> field = fieldOf(ex);
		if (field.isEmpty()) {
			return super.handleHttpMessageNotReadable(ex, headers, status, request);
		}
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, INVALID_FIELDS_DETAIL);
		problem.setProperty(ERRORS_PROPERTY, List.of(fieldError(field.get(), INVALID_FORMAT_MESSAGE)));
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@ExceptionHandler(InvalidFieldException.class)
	ProblemDetail handleInvalidField(InvalidFieldException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_FIELDS_DETAIL);
		problem.setProperty(ERRORS_PROPERTY, List.of(fieldError(ex.getField(), ex.getMessage())));
		return problem;
	}

	@ExceptionHandler(UnauthorizedException.class)
	ProblemDetail handleUnauthorized(UnauthorizedException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail handleConflict(ConflictException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(TooManyRequestsException.class)
	ProblemDetail handleTooManyRequests(TooManyRequestsException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
	}

	@ExceptionHandler(InvalidSortPropertyException.class)
	ProblemDetail handleInvalidSortProperty(InvalidSortPropertyException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(PropertyReferenceException.class)
	ProblemDetail handlePropertyReference(PropertyReferenceException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Propriedade inexistente na requisição");
	}

	private static Map<String, String> fieldError(String field, String message) {
		return Map.of("field", field, "message", message);
	}

	/**
	 * The field path of the first Jackson mapping error in the cause chain: property names joined by {@code .},
	 * list indexes as {@code [n]}. Empty for syntax errors and mapping errors without a path.
	 */
	static Optional<String> fieldOf(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof MismatchedInputException mismatch) {
				return pathOf(mismatch.getPath());
			}
		}
		return Optional.empty();
	}

	private static Optional<String> pathOf(List<JacksonException.Reference> references) {
		StringBuilder path = new StringBuilder();
		for (JacksonException.Reference reference : references) {
			if (reference.getPropertyName() != null) {
				if (!path.isEmpty()) {
					path.append('.');
				}
				path.append(reference.getPropertyName());
			}
			else if (reference.getIndex() >= 0) {
				path.append('[').append(reference.getIndex()).append(']');
			}
		}
		return path.isEmpty() ? Optional.empty() : Optional.of(path.toString());
	}

}
