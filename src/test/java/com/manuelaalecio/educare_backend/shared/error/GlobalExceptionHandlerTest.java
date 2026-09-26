package com.manuelaalecio.educare_backend.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void shouldReturnBadRequestWithFieldErrorsWhenArgumentIsInvalid() throws Exception {
		// given
		var bindingResult = new BeanPropertyBindingResult(new Sample(null, null), "sample");
		bindingResult.rejectValue("name", "NotBlank", "não deve estar em branco");
		bindingResult.rejectValue("login", "Email", "deve ser um endereço de e-mail bem formado");
		bindingResult.reject("global", "erro sem campo");
		var parameter = new MethodParameter(Sample.class.getDeclaredMethod("accept", Sample.class), 0);
		var exception = new MethodArgumentNotValidException(parameter, bindingResult);

		// when
		ResponseEntity<Object> response = handler.handleException(exception,
				new ServletWebRequest(new MockHttpServletRequest()));

		// then
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isInstanceOfSatisfying(ProblemDetail.class, problem -> {
			assertThat(problem.getStatus()).isEqualTo(400);
			assertThat(problem.getDetail()).isEqualTo("Um ou mais campos são inválidos");
			assertThat(problem.getProperties()).containsEntry(GlobalExceptionHandler.ERRORS_PROPERTY, List.of(
					Map.of("field", "name", "message", "não deve estar em branco"),
					Map.of("field", "login", "message", "deve ser um endereço de e-mail bem formado")));
		});
	}

	@Test
	void shouldReturnNotFoundWithExceptionMessageWhenResourceDoesNotExist() {
		// when
		ProblemDetail problem = handler.handleNotFound(new SampleNotFoundException());

		// then
		assertThat(problem.getStatus()).isEqualTo(404);
		assertThat(problem.getTitle()).isEqualTo("Not Found");
		assertThat(problem.getDetail()).isEqualTo("Amostra não encontrada");
	}

	@Test
	void shouldReturnConflictWithExceptionMessageWhenStateConflicts() {
		// when
		ProblemDetail problem = handler.handleConflict(new SampleConflictException());

		// then
		assertThat(problem.getStatus()).isEqualTo(409);
		assertThat(problem.getTitle()).isEqualTo("Conflict");
		assertThat(problem.getDetail()).isEqualTo("Amostra em conflito");
	}

	@Test
	void shouldReturnBadRequestWhenSortPropertyIsNotAllowed() {
		// when
		ProblemDetail problem = handler.handleInvalidSortProperty(new InvalidSortPropertyException("password"));

		// then
		assertThat(problem.getStatus()).isEqualTo(400);
		assertThat(problem.getDetail()).isEqualTo("Ordenação não permitida pela propriedade 'password'");
	}

	@Test
	void shouldReturnBadRequestWhenPropertyDoesNotExist() {
		// when
		ProblemDetail problem = handler.handlePropertyReference(mock(PropertyReferenceException.class));

		// then
		assertThat(problem.getStatus()).isEqualTo(400);
		assertThat(problem.getDetail()).isEqualTo("Propriedade inexistente na requisição");
	}

	record Sample(String name, String login) {

		static void accept(Sample sample) {
		}

	}

	static class SampleNotFoundException extends NotFoundException {

		SampleNotFoundException() {
			super("Amostra não encontrada");
		}

	}

	static class SampleConflictException extends ConflictException {

		SampleConflictException() {
			super("Amostra em conflito");
		}

	}

}
