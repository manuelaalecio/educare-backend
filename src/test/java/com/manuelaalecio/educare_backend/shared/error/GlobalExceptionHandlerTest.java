package com.manuelaalecio.educare_backend.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

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
	void shouldReturnBadRequestWithFieldErrorWhenFieldIsRefusedByRule() {
		// when
		ProblemDetail problem = handler.handleInvalidField(new SampleInvalidFieldException());

		// then
		assertThat(problem.getStatus()).isEqualTo(400);
		assertThat(problem.getTitle()).isEqualTo("Bad Request");
		assertThat(problem.getDetail()).isEqualTo("Um ou mais campos são inválidos");
		assertThat(problem.getProperties()).containsExactly(Map.entry(GlobalExceptionHandler.ERRORS_PROPERTY,
				List.of(Map.of("field", "currentPassword", "message", "Senha atual incorreta"))));
	}

	@Test
	void shouldReturnUnauthorizedWithExceptionMessageWhenCredentialsAreRefused() {
		// when
		ProblemDetail problem = handler.handleUnauthorized(new SampleUnauthorizedException());

		// then
		assertThat(problem.getStatus()).isEqualTo(401);
		assertThat(problem.getTitle()).isEqualTo("Unauthorized");
		assertThat(problem.getDetail()).isEqualTo("Credenciais recusadas");
		assertThat(problem.getProperties()).isNullOrEmpty();
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
	void shouldReturnTooManyRequestsWithExceptionMessageAndNoErrorsWhenAttemptsAreExhausted() {
		// when
		ProblemDetail problem = handler.handleTooManyRequests(new SampleTooManyRequestsException());

		// then
		assertThat(problem.getStatus()).isEqualTo(429);
		assertThat(problem.getTitle()).isEqualTo("Too Many Requests");
		assertThat(problem.getDetail()).isEqualTo("Tentativas demais");
		assertThat(problem.getProperties()).isNullOrEmpty();
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

	@Test
	void shouldReturnFieldErrorWhenDateHasAnotherFormat() {
		// when
		ResponseEntity<Object> response = handleUnreadable("{\"birthDate\": \"10/05/2018\"}");

		// then
		assertFieldErrors(response, "birthDate");
	}

	@Test
	void shouldReturnFieldErrorWhenDateDoesNotExist() {
		// when
		ResponseEntity<Object> response = handleUnreadable("{\"birthDate\": \"2018-02-30\"}");

		// then
		assertFieldErrors(response, "birthDate");
	}

	@Test
	void shouldReturnFieldErrorWhenFieldHasWrongType() {
		// when
		ResponseEntity<Object> response = handleUnreadable("{\"name\": {\"first\": \"Maria\"}}");

		// then
		assertFieldErrors(response, "name");
	}

	@Test
	void shouldJoinPropertiesWithDotWhenFieldIsNested() {
		// when
		ResponseEntity<Object> response = handleUnreadable("{\"address\": {\"since\": \"ontem\"}}");

		// then
		assertFieldErrors(response, "address.since");
	}

	@Test
	void shouldWriteIndexInBracketsWhenFieldIsInList() {
		// when
		ResponseEntity<Object> response = handleUnreadable("{\"addresses\": [{\"since\": \"ontem\"}]}");

		// then
		assertFieldErrors(response, "addresses[0].since");
	}

	@Test
	void shouldNotEchoReceivedValueWhenFieldIsUnreadable() {
		// when
		ResponseEntity<Object> response = handleUnreadable("{\"birthDate\": \"52998224725\"}");

		// then
		assertThat(response.getBody()).isInstanceOfSatisfying(ProblemDetail.class,
				problem -> assertThat(problem.toString()).doesNotContain("52998224725"));
	}

	@Test
	void shouldReturnBadRequestWithoutErrorsWhenJsonIsBroken() {
		// when
		ResponseEntity<Object> response = handleUnreadable("{\"name\": \"Maria\"");

		// then
		assertNoFieldErrors(response);
	}

	@Test
	void shouldReturnBadRequestWithoutErrorsWhenBodyIsMissing() throws Exception {
		// given
		var exception = new HttpMessageNotReadableException("Required request body is missing",
				new MockHttpInputMessage(new byte[0]));

		// when
		ResponseEntity<Object> response = handler.handleException(exception,
				new ServletWebRequest(new MockHttpServletRequest()));

		// then
		assertNoFieldErrors(response);
	}

	private ResponseEntity<Object> handleUnreadable(String json) {
		JacksonException cause = readError(json);
		var exception = new HttpMessageNotReadableException("JSON parse error", cause,
				new MockHttpInputMessage(json.getBytes()));
		try {
			return handler.handleException(exception, new ServletWebRequest(new MockHttpServletRequest()));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static JacksonException readError(String json) {
		try {
			JsonMapper.builder().build().readValue(json, Person.class);
		}
		catch (JacksonException ex) {
			return ex;
		}
		throw new IllegalStateException("JSON lido sem erro: " + json);
	}

	private static void assertFieldErrors(ResponseEntity<Object> response, String field) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isInstanceOfSatisfying(ProblemDetail.class, problem -> {
			assertThat(problem.getDetail()).isEqualTo("Um ou mais campos são inválidos");
			assertThat(problem.getProperties()).containsExactly(Map.entry(GlobalExceptionHandler.ERRORS_PROPERTY,
					List.of(Map.of("field", field, "message", "valor em formato inválido"))));
		});
	}

	private static void assertNoFieldErrors(ResponseEntity<Object> response) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isInstanceOfSatisfying(ProblemDetail.class,
				problem -> assertThat(problem.getProperties()).isNullOrEmpty());
	}

	record Person(String name, LocalDate birthDate, Address address, List<Address> addresses) {
	}

	record Address(LocalDate since) {
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

	static class SampleTooManyRequestsException extends TooManyRequestsException {

		SampleTooManyRequestsException() {
			super("Tentativas demais");
		}

	}

	static class SampleConflictException extends ConflictException {

		SampleConflictException() {
			super("Amostra em conflito");
		}

	}

	static class SampleInvalidFieldException extends InvalidFieldException {

		SampleInvalidFieldException() {
			super("currentPassword", "Senha atual incorreta");
		}

	}

	static class SampleUnauthorizedException extends UnauthorizedException {

		SampleUnauthorizedException() {
			super("Credenciais recusadas");
		}

	}

}
