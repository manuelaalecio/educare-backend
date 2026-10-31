package com.manuelaalecio.educare_backend.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUser;
import com.manuelaalecio.educare_backend.shared.security.TokenOwner;
import com.manuelaalecio.educare_backend.user.domain.Role;
import com.manuelaalecio.educare_backend.user.domain.User;
import com.manuelaalecio.educare_backend.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserAuthenticationLookupTest {

	private static final UUID ID = UUID.fromString("0190b6f0-0000-7000-8000-000000000001");

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private UserAuthenticationLookup userAuthenticationLookup;

	@Test
	void shouldReturnIdAndCurrentRoleWhenUserExists() {
		// given
		var user = new User("Ana Souza", "ana.souza@educare.org", "$2a$10$hash", Role.ADMIN);
		ReflectionTestUtils.setField(user, "id", ID);
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));

		// when
		var result = userAuthenticationLookup.findById(ID);

		// then
		assertThat(result).contains(new TokenOwner(new AuthenticatedUser(ID, "ADMIN"), null));
	}

	@Test
	void shouldReturnLastPasswordChangeWhenUserChangedPassword() {
		// given
		var user = new User("Ana Souza", "ana.souza@educare.org", "$2a$10$hash", Role.USER);
		ReflectionTestUtils.setField(user, "id", ID);
		user.changePassword("$2a$10$other", Instant.parse("2026-03-01T10:30:00Z"));
		when(userRepository.findById(ID)).thenReturn(Optional.of(user));

		// when
		var result = userAuthenticationLookup.findById(ID);

		// then
		assertThat(result).contains(
				new TokenOwner(new AuthenticatedUser(ID, "USER"), Instant.parse("2026-03-01T10:30:00Z")));
	}

	@Test
	void shouldReturnEmptyWhenUserDoesNotExist() {
		// given
		when(userRepository.findById(ID)).thenReturn(Optional.empty());

		// when
		var result = userAuthenticationLookup.findById(ID);

		// then
		assertThat(result).isEmpty();
	}

}
