package com.manuelaalecio.educare_backend.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.user.domain.InvalidCurrentPasswordException;
import com.manuelaalecio.educare_backend.user.domain.LastAdminException;
import com.manuelaalecio.educare_backend.user.domain.LoginAlreadyInUseException;
import com.manuelaalecio.educare_backend.user.domain.Role;
import com.manuelaalecio.educare_backend.user.domain.SelfDeletionException;
import com.manuelaalecio.educare_backend.user.domain.User;
import com.manuelaalecio.educare_backend.user.domain.UserNotFoundException;
import com.manuelaalecio.educare_backend.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	private static final UUID ID = UUID.fromString("0190b6f0-0000-7000-8000-000000000001");
	private static final UUID ACTOR_ID = UUID.fromString("0190b6f0-0000-7000-8000-000000000002");
	private static final Instant CREATED_AT = Instant.parse("2026-03-01T10:00:00Z");
	private static final Instant UPDATED_AT = Instant.parse("2026-03-02T09:00:00Z");
	private static final String PASSWORD = "s3nh@Forte";
	private static final String HASH = "$2a$10$encodedHash";
	private static final String OLD_HASH = "$2a$10$oldHash";

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private UserService userService;

	private static User existingUser() {
		return new User("Ana Souza", "ana.souza@educare.org", OLD_HASH, Role.USER);
	}

	private static User admin(String login) {
		return new User("Administrador", login, OLD_HASH, Role.ADMIN);
	}

	private static User persisted() {
		return new User("Persisted", "persisted@educare.org", HASH, Role.USER);
	}

	// create

	@Test
	void shouldCreateUserWithEncodedPasswordWhenLoginIsFree() {
		// given
		var saved = persisted();
		when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
		when(userRepository.existsByLogin("ana.souza@educare.org")).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class))).thenReturn(saved);

		// when
		var result = userService.create("  Ana Souza ", "Ana.Souza@Educare.org", PASSWORD, Role.USER);

		// then
		assertThat(result).isSameAs(saved);
		var captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(captor.capture());
		var toSave = captor.getValue();
		assertThat(toSave.getName()).isEqualTo("Ana Souza");
		assertThat(toSave.getLogin()).isEqualTo("ana.souza@educare.org");
		assertThat(toSave.getPasswordHash()).isEqualTo(HASH);
		assertThat(toSave.getRole()).isEqualTo(Role.USER);
		verify(userRepository).existsByLogin("ana.souza@educare.org");
		verify(passwordEncoder).encode(PASSWORD);
	}

	@Test
	void shouldCreateUserWithUserRoleWhenRoleIsNull() {
		// given
		when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
		when(userRepository.existsByLogin("ana.souza@educare.org")).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// when
		var result = userService.create("Ana Souza", "ana.souza@educare.org", PASSWORD, null);

		// then
		assertThat(result.getRole()).isEqualTo(Role.USER);
		assertThat(result.getPasswordHash()).isEqualTo(HASH);
	}

	@Test
	void shouldCreateUserWithAdminRoleWhenRoleIsAdmin() {
		// given
		when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
		when(userRepository.existsByLogin("ana.souza@educare.org")).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// when
		var result = userService.create("Ana Souza", "ana.souza@educare.org", PASSWORD, Role.ADMIN);

		// then
		assertThat(result.getRole()).isEqualTo(Role.ADMIN);
		assertThat(result.getPasswordHash()).isEqualTo(HASH);
	}

	@Test
	void shouldRejectCreateAndSaveNothingWhenLoginIsAlreadyInUse() {
		// given
		when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
		when(userRepository.existsByLogin("ana.souza@educare.org")).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> userService.create("Ana Souza", "ANA.SOUZA@educare.org", PASSWORD, null))
				.isInstanceOf(LoginAlreadyInUseException.class);
		verify(userRepository).existsByLogin("ana.souza@educare.org");
		verify(userRepository, never()).saveAndFlush(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	void shouldRejectCreateWhenConcurrentInsertViolatesLoginConstraint() {
		// given
		var violation = new DataIntegrityViolationException("users_login_key");
		when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
		when(userRepository.existsByLogin("ana.souza@educare.org")).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class))).thenThrow(violation);

		// when / then
		assertThatThrownBy(() -> userService.create("Ana Souza", "ana.souza@educare.org", PASSWORD, Role.USER))
				.isInstanceOf(LoginAlreadyInUseException.class)
				.hasMessage("Login já está em uso");
		verify(userRepository).saveAndFlush(any(User.class));
	}

	// findById

	@Test
	void shouldReturnUserWhenFoundById() {
		// given
		var user = existingUser();
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));

		// when
		var result = userService.findById(ID);

		// then
		assertThat(result).isSameAs(user);
	}

	@Test
	void shouldThrowNotFoundWhenFindingUnknownId() {
		// given
		when(userRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> userService.findById(ID))
				.isInstanceOf(UserNotFoundException.class)
				.hasMessage("Usuário não encontrado");
	}

	// list

	@Test
	void shouldReturnRepositoryPageWhenListing() {
		// given
		Pageable pageable = PageRequest.of(1, 5);
		Page<User> page = new PageImpl<>(List.of(existingUser()), pageable, 6);
		when(userRepository.findAll(pageable)).thenReturn(page);

		// when
		var result = userService.list(pageable);

		// then
		assertThat(result).isSameAs(page);
		verify(userRepository).findAll(pageable);
	}

	// update

	@Test
	void shouldUpdateNameLoginAndRoleWhenLoginIsFree() {
		// given
		var user = existingUser();
		var saved = persisted();
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));
		when(userRepository.existsByLoginAndIdNot("beatriz@educare.org", ID)).thenReturn(false);
		when(userRepository.saveAndFlush(user)).thenReturn(saved);

		// when
		var result = userService.update(ID, " Beatriz Lima ", "Beatriz@Educare.org", Role.ADMIN, ACTOR_ID);

		// then
		assertThat(result).isSameAs(saved);
		assertThat(user.getName()).isEqualTo("Beatriz Lima");
		assertThat(user.getLogin()).isEqualTo("beatriz@educare.org");
		assertThat(user.getRole()).isEqualTo(Role.ADMIN);
		assertThat(user.getPasswordHash()).isEqualTo(OLD_HASH);
		verify(userRepository).saveAndFlush(user);
		verifyNoInteractions(passwordEncoder);
	}

	@Test
	void shouldChangeRoleWhenUpdatingAdminToUserAndAnotherAdminExists() {
		// given
		var user = new User("Ana Souza", "ana.souza@educare.org", OLD_HASH, Role.ADMIN);
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));
		when(userRepository.existsByLoginAndIdNot("ana.souza@educare.org", ID)).thenReturn(false);
		when(userRepository.findAllByRoleForUpdate(Role.ADMIN)).thenReturn(List.of(user, admin("bruno@educare.org")));
		when(userRepository.saveAndFlush(user)).thenReturn(user);

		// when
		var result = userService.update(ID, "Ana Souza", "ana.souza@educare.org", Role.USER, ACTOR_ID);

		// then
		assertThat(result.getRole()).isEqualTo(Role.USER);
	}

	@Test
	void shouldAllowUpdateWhenKeepingOwnLogin() {
		// given
		var user = existingUser();
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));
		when(userRepository.existsByLoginAndIdNot("ana.souza@educare.org", ID)).thenReturn(false);
		when(userRepository.saveAndFlush(user)).thenReturn(user);

		// when
		var result = userService.update(ID, "Ana Souza Lima", "ANA.Souza@educare.org", Role.USER, ACTOR_ID);

		// then
		assertThat(result.getLogin()).isEqualTo("ana.souza@educare.org");
		assertThat(result.getName()).isEqualTo("Ana Souza Lima");
		verify(userRepository).existsByLoginAndIdNot("ana.souza@educare.org", ID);
		verify(userRepository, never()).existsByLogin(anyString());
	}

	@Test
	void shouldRejectUpdateAndKeepUserUnchangedWhenLoginBelongsToAnotherUser() {
		// given
		var user = existingUser();
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));
		when(userRepository.existsByLoginAndIdNot("beatriz@educare.org", ID)).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> userService.update(ID, "Beatriz Lima", "BEATRIZ@educare.org", Role.ADMIN, ACTOR_ID))
				.isInstanceOf(LoginAlreadyInUseException.class);
		assertThat(user.getName()).isEqualTo("Ana Souza");
		assertThat(user.getLogin()).isEqualTo("ana.souza@educare.org");
		assertThat(user.getRole()).isEqualTo(Role.USER);
		verify(userRepository, never()).saveAndFlush(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	void shouldRejectUpdateWhenConcurrentChangeViolatesLoginConstraint() {
		// given
		var user = existingUser();
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));
		when(userRepository.existsByLoginAndIdNot("beatriz@educare.org", ID)).thenReturn(false);
		when(userRepository.saveAndFlush(user)).thenThrow(new DataIntegrityViolationException("users_login_key"));

		// when / then
		assertThatThrownBy(() -> userService.update(ID, "Beatriz Lima", "beatriz@educare.org", Role.USER, ACTOR_ID))
				.isInstanceOf(LoginAlreadyInUseException.class)
				.hasMessage("Login já está em uso");
		verify(userRepository).saveAndFlush(user);
	}

	@Test
	void shouldThrowNotFoundAndSaveNothingWhenUpdatingUnknownUser() {
		// given
		when(userRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> userService.update(ID, "Beatriz Lima", "beatriz@educare.org", Role.USER, ACTOR_ID))
				.isInstanceOf(UserNotFoundException.class);
		verify(userRepository, never()).existsByLoginAndIdNot(anyString(), any());
		verify(userRepository, never()).saveAndFlush(any());
	}

	// changePassword

	@Test
	void shouldStoreEncodedHashWhenChangingPassword() {
		// given
		var user = existingUser();
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));
		when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);

		// when
		userService.changePassword(ID, PASSWORD);

		// then
		var captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(captor.capture());
		assertThat(captor.getValue()).isSameAs(user);
		assertThat(user.getPasswordHash()).isEqualTo(HASH);
		verify(passwordEncoder).encode(PASSWORD);
	}

	@Test
	void shouldThrowNotFoundAndEncodeNothingWhenChangingPasswordOfUnknownUser() {
		// given
		when(userRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> userService.changePassword(ID, PASSWORD))
				.isInstanceOf(UserNotFoundException.class);
		verifyNoInteractions(passwordEncoder);
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldUpdateUserWithoutQueryingAdminsWhenTargetIsUser() {
		// given
		var user = existingUser();
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));
		when(userRepository.existsByLoginAndIdNot("ana.souza@educare.org", ID)).thenReturn(false);
		when(userRepository.saveAndFlush(user)).thenReturn(user);

		// when
		var result = userService.update(ID, "Ana Lima", "ana.souza@educare.org", Role.USER, ACTOR_ID);

		// then
		assertThat(result.getName()).isEqualTo("Ana Lima");
		verify(userRepository, never()).findAllByRoleForUpdate(any());
	}

	@Test
	void shouldUpdateAdminWithoutQueryingAdminsWhenRoleStaysAdmin() {
		// given
		var user = admin("ana.souza@educare.org");
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));
		when(userRepository.existsByLoginAndIdNot("ana.souza@educare.org", ID)).thenReturn(false);
		when(userRepository.saveAndFlush(user)).thenReturn(user);

		// when
		var result = userService.update(ID, "Ana Souza", "ana.souza@educare.org", Role.ADMIN, ID);

		// then
		assertThat(result.getRole()).isEqualTo(Role.ADMIN);
		verify(userRepository, never()).findAllByRoleForUpdate(any());
	}

	@Test
	void shouldDemoteSelfWhenAnotherAdminExists() {
		// given
		var user = admin("ana.souza@educare.org");
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));
		when(userRepository.existsByLoginAndIdNot("ana.souza@educare.org", ID)).thenReturn(false);
		when(userRepository.findAllByRoleForUpdate(Role.ADMIN)).thenReturn(List.of(user, admin("bruno@educare.org")));
		when(userRepository.saveAndFlush(user)).thenReturn(user);

		// when
		var result = userService.update(ID, "Ana Souza", "ana.souza@educare.org", Role.USER, ID);

		// then
		assertThat(result.getRole()).isEqualTo(Role.USER);
		verify(userRepository).saveAndFlush(user);
	}

	@Test
	void shouldRejectDemotionAndSaveNothingWhenTargetIsTheLastAdmin() {
		// given
		var user = admin("ana.souza@educare.org");
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));
		when(userRepository.existsByLoginAndIdNot("ana.souza@educare.org", ID)).thenReturn(false);
		when(userRepository.findAllByRoleForUpdate(Role.ADMIN)).thenReturn(List.of(user));

		// when / then
		assertThatThrownBy(() -> userService.update(ID, "Ana Lima", "ana.souza@educare.org", Role.USER, ID))
				.isInstanceOf(LastAdminException.class)
				.hasMessage("O sistema precisa ter ao menos um usuário ADMIN");
		assertThat(user.getRole()).isEqualTo(Role.ADMIN);
		assertThat(user.getName()).isEqualTo("Administrador");
		verify(userRepository, never()).saveAndFlush(any());
		verify(userRepository, never()).save(any());
	}

	// delete

	@Test
	void shouldDeleteUserWithoutQueryingAdminsWhenTargetIsUser() {
		// given
		var user = existingUser();
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));

		// when
		userService.delete(ID, ACTOR_ID);

		// then
		verify(userRepository).delete(user);
		verify(userRepository, never()).findAllByRoleForUpdate(any());
	}

	@Test
	void shouldDeleteAnotherAdminWhenTwoAdminsExist() {
		// given
		var user = admin("bruno@educare.org");
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));
		when(userRepository.findAllByRoleForUpdate(Role.ADMIN))
				.thenReturn(List.of(admin("ana.souza@educare.org"), user));

		// when
		userService.delete(ID, ACTOR_ID);

		// then
		verify(userRepository).delete(user);
	}

	@Test
	void shouldRejectDeletionAndDeleteNothingWhenTargetIsTheLastAdmin() {
		// given
		var user = admin("bruno@educare.org");
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));
		when(userRepository.findAllByRoleForUpdate(Role.ADMIN)).thenReturn(List.of(user));

		// when / then
		assertThatThrownBy(() -> userService.delete(ID, ACTOR_ID))
				.isInstanceOf(LastAdminException.class);
		verify(userRepository, never()).delete(any());
		verify(userRepository, never()).deleteById(any());
	}

	@Test
	void shouldRejectDeletionAndDeleteNothingWhenActorDeletesThemselves() {
		// when / then
		assertThatThrownBy(() -> userService.delete(ID, ID))
				.isInstanceOf(SelfDeletionException.class)
				.hasMessage("Não é permitido excluir o próprio usuário");
		verify(userRepository, never()).delete(any());
		verify(userRepository, never()).deleteById(any());
		verify(userRepository, never()).findAllByRoleForUpdate(any());
	}

	@Test
	void shouldThrowNotFoundAndDeleteNothingWhenDeletingUnknownUser() {
		// given
		when(userRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> userService.delete(ID, ACTOR_ID))
				.isInstanceOf(UserNotFoundException.class);
		verify(userRepository, never()).delete(any());
		verify(userRepository, never()).deleteById(any());
	}

	// authenticate

	@Test
	void shouldReturnAccountWhenCredentialsAreCorrect() {
		// given
		var user = persistedWithId(existingUser());
		when(userRepository.findByLogin("ana.souza@educare.org")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(PASSWORD, OLD_HASH)).thenReturn(true);

		// when
		var result = userService.authenticate("ana.souza@educare.org", PASSWORD);

		// then
		assertThat(result).contains(new UserAccount(ID, "Ana Souza", "ana.souza@educare.org", "USER", CREATED_AT,
				UPDATED_AT));
	}

	@Test
	void shouldNormalizeLoginWhenAuthenticatingWithUpperCaseLogin() {
		// given
		var user = persistedWithId(existingUser());
		when(userRepository.findByLogin("ana.souza@educare.org")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(PASSWORD, OLD_HASH)).thenReturn(true);

		// when
		var result = userService.authenticate("Ana.SOUZA@Educare.org", PASSWORD);

		// then
		assertThat(result).map(UserAccount::id).contains(ID);
		verify(userRepository).findByLogin("ana.souza@educare.org");
	}

	@Test
	void shouldReturnEmptyWhenPasswordIsWrong() {
		// given
		when(userRepository.findByLogin("ana.souza@educare.org")).thenReturn(Optional.of(existingUser()));
		when(passwordEncoder.matches("senhaErrada", OLD_HASH)).thenReturn(false);

		// when
		var result = userService.authenticate("ana.souza@educare.org", "senhaErrada");

		// then
		assertThat(result).isEmpty();
	}

	@Test
	void shouldReturnEmptyAndStillCompareAPasswordWhenLoginDoesNotExist() {
		// given
		when(userRepository.findByLogin("ninguem@educare.org")).thenReturn(Optional.empty());

		// when
		var result = userService.authenticate("ninguem@educare.org", PASSWORD);

		// then
		assertThat(result).isEmpty();
		verify(passwordEncoder).matches(PASSWORD, UserService.UNKNOWN_USER_PASSWORD_HASH);
	}

	@Test
	void shouldUseBcryptHashWithEncoderCostWhenComparingUnknownLogin() {
		// given
		var encoder = new BCryptPasswordEncoder();

		// when
		boolean matches = encoder.matches(PASSWORD, UserService.UNKNOWN_USER_PASSWORD_HASH);

		// then: a well-formed hash with cost 10, so the comparison takes as long as for a real user
		assertThat(UserService.UNKNOWN_USER_PASSWORD_HASH).matches("^\\$2a\\$10\\$[./A-Za-z0-9]{53}$");
		assertThat(matches).isFalse();
	}

	// findAccount

	@Test
	void shouldReturnAccountWithoutPasswordWhenFindingExistingAccount() {
		// given
		var user = persistedWithId(new User("Ana Souza", "ana.souza@educare.org", OLD_HASH, Role.ADMIN));
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));

		// when
		var result = userService.findAccount(ID);

		// then
		assertThat(result).isEqualTo(new UserAccount(ID, "Ana Souza", "ana.souza@educare.org", "ADMIN", CREATED_AT,
				UPDATED_AT));
	}

	@Test
	void shouldThrowNotFoundWhenFindingUnknownAccount() {
		// given
		when(userRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> userService.findAccount(ID))
				.isInstanceOf(UserNotFoundException.class);
	}

	// changeOwnPassword

	@Test
	void shouldStoreEncodedHashWhenCurrentPasswordMatches() {
		// given
		var user = existingUser();
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("senhaAtual1", OLD_HASH)).thenReturn(true);
		when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);

		// when
		userService.changeOwnPassword(ID, "senhaAtual1", PASSWORD);

		// then
		assertThat(user.getPasswordHash()).isEqualTo(HASH);
		verify(userRepository).saveAndFlush(user);
	}

	@Test
	void shouldRejectAndSaveNothingWhenCurrentPasswordDoesNotMatch() {
		// given
		var user = existingUser();
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("senhaErrada", OLD_HASH)).thenReturn(false);

		// when / then
		assertThatThrownBy(() -> userService.changeOwnPassword(ID, "senhaErrada", PASSWORD))
				.isInstanceOf(InvalidCurrentPasswordException.class)
				.hasMessage("Senha atual incorreta")
				.extracting("field").isEqualTo("currentPassword");
		assertThat(user.getPasswordHash()).isEqualTo(OLD_HASH);
		verify(passwordEncoder, never()).encode(anyString());
		verify(userRepository, never()).saveAndFlush(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	void shouldThrowNotFoundAndEncodeNothingWhenChangingOwnPasswordOfUnknownUser() {
		// given
		when(userRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> userService.changeOwnPassword(ID, "senhaAtual1", PASSWORD))
				.isInstanceOf(UserNotFoundException.class);
		verifyNoInteractions(passwordEncoder);
		verify(userRepository, never()).saveAndFlush(any());
	}

	private static User persistedWithId(User user) {
		ReflectionTestUtils.setField(user, "id", ID);
		ReflectionTestUtils.setField(user, "createdAt", CREATED_AT);
		ReflectionTestUtils.setField(user, "updatedAt", UPDATED_AT);
		return user;
	}

}
