package com.manuelaalecio.educare_backend.classroom.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import com.manuelaalecio.educare_backend.classroom.api.dto.CreateClassroomRequest;
import com.manuelaalecio.educare_backend.classroom.api.dto.UpdateClassroomRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Both requests strip the name before the validation, so the length is counted without the surrounding spaces.
 */
class ClassroomRequestValidationTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void createValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	static Stream<Arguments> requests() {
		return Stream.of(
				Arguments.of("create", (Function<String, Object>) CreateClassroomRequest::new),
				Arguments.of("update", (Function<String, Object>) UpdateClassroomRequest::new));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("requests")
	void shouldAcceptAndStripNameWhenItHasMaxLengthWithSurroundingSpaces(String kind,
			Function<String, Object> request) {
		// given
		Object body = request.apply(" " + "a".repeat(60) + " ");

		// when
		Set<ConstraintViolation<Object>> violations = validator.validate(body);

		// then
		assertThat(violations).isEmpty();
		assertThat(nameOf(body)).isEqualTo("a".repeat(60));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("requests")
	void shouldRejectNameWhenLongerThanMaxLength(String kind, Function<String, Object> request) {
		assertThat(violationsOfName(request.apply("a".repeat(61))))
				.containsExactly("deve ter no máximo 60 caracteres");
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("requests")
	void shouldRejectNameWhenOnlySpaces(String kind, Function<String, Object> request) {
		assertThat(violationsOfName(request.apply("   "))).containsExactly("é obrigatório");
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("requests")
	void shouldRejectNameWhenNull(String kind, Function<String, Object> request) {
		assertThat(violationsOfName(request.apply(null))).containsExactly("é obrigatório");
	}

	private static Stream<String> violationsOfName(Object body) {
		return validator.validate(body).stream()
				.filter(violation -> violation.getPropertyPath().toString().equals("name"))
				.map(ConstraintViolation::getMessage);
	}

	private static String nameOf(Object body) {
		return body instanceof CreateClassroomRequest create ? create.name() : ((UpdateClassroomRequest) body).name();
	}

}
