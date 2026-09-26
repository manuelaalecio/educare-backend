package com.manuelaalecio.educare_backend.shared.error;

import java.util.List;
import java.util.Map;

import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converts every error raised by the API into an RFC 9457 {@link ProblemDetail}. The MVC errors (unreadable
 * body, malformed path variable, unsupported method etc.) are handled by {@link ResponseEntityExceptionHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	static final String ERRORS_PROPERTY = "errors";

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ex.getBody();
		problem.setDetail("Um ou mais campos são inválidos");
		List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
				.toList();
		problem.setProperty(ERRORS_PROPERTY, errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail handleConflict(ConflictException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(InvalidSortPropertyException.class)
	ProblemDetail handleInvalidSortProperty(InvalidSortPropertyException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(PropertyReferenceException.class)
	ProblemDetail handlePropertyReference(PropertyReferenceException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Propriedade inexistente na requisição");
	}

}
