package com.manuelaalecio.educare_backend.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {

	private static final String HASH = "$2a$10$hash";

	@Test
	void shouldNormalizeNameAndLoginWhenCreated() {
		// when
		var user = new User("  Ana Souza  ", "Ana.Souza@Educare.org", HASH, Role.COORDENADOR);

		// then
		assertThat(user.getName()).isEqualTo("Ana Souza");
		assertThat(user.getLogin()).isEqualTo("ana.souza@educare.org");
		assertThat(user.getPasswordHash()).isEqualTo(HASH);
		assertThat(user.getRole()).isEqualTo(Role.COORDENADOR);
	}

	@Test
	void shouldUseEducatorRoleWhenCreatedWithoutRole() {
		// when
		var user = new User("Ana Souza", "ana.souza@educare.org", HASH);

		// then
		assertThat(user.getRole()).isEqualTo(Role.EDUCADOR);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   "})
	void shouldRejectNameWhenBlank(String name) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new User(name, "ana.souza@educare.org", HASH))
				.withMessage("Nome é obrigatório");
	}

	@Test
	void shouldAcceptNameWhenItHasMaxLengthAfterStripping() {
		// when
		var user = new User(" " + "a".repeat(150) + " ", "ana.souza@educare.org", HASH);

		// then
		assertThat(user.getName()).hasSize(150);
	}

	@Test
	void shouldRejectNameWhenLongerThanMaxLength() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new User("a".repeat(151), "ana.souza@educare.org", HASH))
				.withMessage("Nome deve ter no máximo 150 caracteres");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"ana.souza", "ana@educare", "ana souza@educare.org", "ana@@educare.org", "@educare.org"})
	void shouldRejectLoginWhenNotAnEmail(String login) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new User("Ana Souza", login, HASH))
				.withMessage("Login deve ser um email válido");
	}

	@Test
	void shouldAcceptLoginWhenItHasMaxLength() {
		// given: 254 characters
		String login = "a".repeat(242) + "@educare.org";

		// when
		var user = new User("Ana Souza", login, HASH);

		// then
		assertThat(user.getLogin()).hasSize(254);
	}

	@Test
	void shouldRejectLoginWhenLongerThanMaxLength() {
		// given: 255 characters
		String login = "a".repeat(243) + "@educare.org";

		// when / then
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new User("Ana Souza", login, HASH))
				.withMessage("Login deve ter no máximo 254 caracteres");
	}

	@Test
	void shouldRejectRoleWhenNull() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new User("Ana Souza", "ana.souza@educare.org", HASH, null))
				.withMessage("Role é obrigatória");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"  "})
	void shouldRejectPasswordHashWhenBlank(String hash) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new User("Ana Souza", "ana.souza@educare.org", hash))
				.withMessage("Hash da senha é obrigatório");
	}

	@Test
	void shouldReplaceNameWhenRenamed() {
		// given
		var user = new User("Ana Souza", "ana.souza@educare.org", HASH);

		// when
		user.rename(" Ana Souza Lima ");

		// then
		assertThat(user.getName()).isEqualTo("Ana Souza Lima");
	}

	@Test
	void shouldReplaceLoginInLowerCaseWhenLoginChanges() {
		// given
		var user = new User("Ana Souza", "ana.souza@educare.org", HASH);

		// when
		user.changeLogin("Ana.Lima@Educare.org");

		// then
		assertThat(user.getLogin()).isEqualTo("ana.lima@educare.org");
	}

	@Test
	void shouldKeepCurrentStateWhenChangeIsRejected() {
		// given
		var user = new User("Ana Souza", "ana.souza@educare.org", HASH);

		// when
		assertThatIllegalArgumentException().isThrownBy(() -> user.changeLogin("ana"));
		assertThatIllegalArgumentException().isThrownBy(() -> user.rename(" "));

		// then
		assertThat(user.getLogin()).isEqualTo("ana.souza@educare.org");
		assertThat(user.getName()).isEqualTo("Ana Souza");
	}

	@Test
	void shouldReplaceRoleWhenRoleChanges() {
		// given
		var user = new User("Ana Souza", "ana.souza@educare.org", HASH);

		// when
		user.changeRole(Role.COORDENADOR);

		// then
		assertThat(user.getRole()).isEqualTo(Role.COORDENADOR);
	}

	@Test
	void shouldHaveNoPasswordChangeWhenUserIsNew() {
		// when
		var user = new User("Ana Souza", "ana.souza@educare.org", HASH);

		// then
		assertThat(user.getPasswordChangedAt()).isNull();
	}

	@Test
	void shouldReplaceHashAndRecordInstantTruncatedToSecondsWhenPasswordChanges() {
		// given
		var user = new User("Ana Souza", "ana.souza@educare.org", HASH);

		// when
		user.changePassword("$2a$10$other", Instant.parse("2026-03-01T10:00:00.900Z"));

		// then
		assertThat(user.getPasswordHash()).isEqualTo("$2a$10$other");
		assertThat(user.getPasswordChangedAt()).isEqualTo(Instant.parse("2026-03-01T10:00:00Z"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void shouldRejectPasswordChangeAndKeepStateWhenNewHashIsBlank(String hash) {
		// given
		var user = new User("Ana Souza", "ana.souza@educare.org", HASH);
		var changedAt = Instant.parse("2026-03-01T10:00:00Z");

		// when / then
		assertThatIllegalArgumentException()
				.isThrownBy(() -> user.changePassword(hash, changedAt))
				.withMessage("Hash da senha é obrigatório");
		assertThat(user.getPasswordHash()).isEqualTo(HASH);
		assertThat(user.getPasswordChangedAt()).isNull();
	}

	@Test
	void shouldRejectPasswordChangeAndKeepStateWhenInstantIsNull() {
		// given
		var user = new User("Ana Souza", "ana.souza@educare.org", HASH);

		// when / then
		assertThatIllegalArgumentException()
				.isThrownBy(() -> user.changePassword("$2a$10$other", null))
				.withMessage("Instante da troca de senha é obrigatório");
		assertThat(user.getPasswordHash()).isEqualTo(HASH);
		assertThat(user.getPasswordChangedAt()).isNull();
	}

	@Test
	void shouldNormalizeLoginToLowerCaseWhenComparing() {
		assertThat(User.normalizeLogin("ANA.SOUZA@EDUCARE.ORG")).isEqualTo("ana.souza@educare.org");
		assertThat(User.normalizeLogin(null)).isNull();
	}

	@Test
	void shouldNotExposeEmailWhenLoginIsAlreadyInUse() {
		// when
		var exception = new LoginAlreadyInUseException();

		// then
		assertThat(exception.getMessage()).isEqualTo("Login já está em uso").doesNotContain("@");
	}

	@Test
	void shouldDescribeMissingUserWhenNotFound() {
		assertThat(new UserNotFoundException().getMessage()).isEqualTo("Usuário não encontrado");
	}

}
