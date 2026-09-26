package com.manuelaalecio.educare_backend.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.user.domain.LoginAlreadyInUseException;
import com.manuelaalecio.educare_backend.user.domain.Role;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	private static final UUID ID = UUID.fromString("0190b6f0-0000-7000-8000-000000000001");
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
		var result = userService.update(ID, " Beatriz Lima ", "Beatriz@Educare.org", Role.ADMIN);

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
	void shouldChangeRoleWhenUpdatingAdminToUser() {
		// given
		var user = new User("Ana Souza", "ana.souza@educare.org", OLD_HASH, Role.ADMIN);
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));
		when(userRepository.existsByLoginAndIdNot("ana.souza@educare.org", ID)).thenReturn(false);
		when(userRepository.saveAndFlush(user)).thenReturn(user);

		// when
		var result = userService.update(ID, "Ana Souza", "ana.souza@educare.org", Role.USER);

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
		var result = userService.update(ID, "Ana Souza Lima", "ANA.Souza@educare.org", Role.USER);

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
		assertThatThrownBy(() -> userService.update(ID, "Beatriz Lima", "BEATRIZ@educare.org", Role.ADMIN))
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
		assertThatThrownBy(() -> userService.update(ID, "Beatriz Lima", "beatriz@educare.org", Role.USER))
				.isInstanceOf(LoginAlreadyInUseException.class)
				.hasMessage("Login já está em uso");
		verify(userRepository).saveAndFlush(user);
	}

	@Test
	void shouldThrowNotFoundAndSaveNothingWhenUpdatingUnknownUser() {
		// given
		when(userRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> userService.update(ID, "Beatriz Lima", "beatriz@educare.org", Role.USER))
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

	// delete

	@Test
	void shouldDeleteUserWhenFound() {
		// given
		var user = existingUser();
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));

		// when
		userService.delete(ID);

		// then
		verify(userRepository).delete(user);
	}

	@Test
	void shouldThrowNotFoundAndDeleteNothingWhenDeletingUnknownUser() {
		// given
		when(userRepository.findById(ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> userService.delete(ID))
				.isInstanceOf(UserNotFoundException.class);
		verify(userRepository, never()).delete(any());
		verify(userRepository, never()).deleteById(any());
	}

}
