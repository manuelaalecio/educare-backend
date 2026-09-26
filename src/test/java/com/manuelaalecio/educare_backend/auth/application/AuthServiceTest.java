package com.manuelaalecio.educare_backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.auth.domain.InvalidCredentialsException;
import com.manuelaalecio.educare_backend.shared.security.AccessTokenIssuer;
import com.manuelaalecio.educare_backend.shared.security.IssuedAccessToken;
import com.manuelaalecio.educare_backend.user.application.UserAccount;
import com.manuelaalecio.educare_backend.user.application.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final UUID ID = UUID.fromString("0190b6f0-0000-7000-8000-000000000001");
	private static final String LOGIN = "ana.souza@educare.org";
	private static final String PASSWORD = "s3nh@Forte";
	private static final UserAccount ACCOUNT = new UserAccount(ID, "Ana Souza", LOGIN, "USER",
		Instant.parse("2026-03-01T10:00:00Z"), Instant.parse("2026-03-02T09:00:00Z"));

	@Mock
	private UserService userService;

	@Mock
	private AccessTokenIssuer accessTokenIssuer;

	@InjectMocks
	private AuthService authService;

	// login

	@Test
	void shouldReturnIssuedTokenForAccountIdWhenCredentialsAreValid() {
		// given
		var token = new IssuedAccessToken("header.payload.signature", 28800);
		when(userService.authenticate(LOGIN, PASSWORD)).thenReturn(Optional.of(ACCOUNT));
		when(accessTokenIssuer.issue(ID)).thenReturn(token);

		// when
		var result = authService.login(LOGIN, PASSWORD);

		// then
		assertThat(result).isSameAs(token);
	}

	@Test
	void shouldThrowInvalidCredentialsWithoutIssuingTokenWhenCredentialsAreInvalid() {
		// given
		when(userService.authenticate(LOGIN, "wrong")).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> authService.login(LOGIN, "wrong"))
			.isInstanceOf(InvalidCredentialsException.class)
			.hasMessage("Login ou senha inválidos");
		verifyNoInteractions(accessTokenIssuer);
	}

	// me

	@Test
	void shouldReturnAccountOfReceivedIdWhenReadingOwnData() {
		// given
		when(userService.findAccount(ID)).thenReturn(ACCOUNT);

		// when
		var result = authService.me(ID);

		// then
		assertThat(result).isSameAs(ACCOUNT);
	}

	// changeOwnPassword

	@Test
	void shouldDelegateToUserServiceWithReceivedIdWhenChangingOwnPassword() {
		// when
		authService.changeOwnPassword(ID, PASSWORD, "n0v@Senha");

		// then
		verify(userService).changeOwnPassword(ID, PASSWORD, "n0v@Senha");
		verifyNoInteractions(accessTokenIssuer);
	}

}
