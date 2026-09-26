package com.manuelaalecio.educare_backend.auth.application;

import java.util.UUID;

import com.manuelaalecio.educare_backend.auth.domain.InvalidCredentialsException;
import com.manuelaalecio.educare_backend.shared.security.AccessTokenIssuer;
import com.manuelaalecio.educare_backend.shared.security.IssuedAccessToken;
import com.manuelaalecio.educare_backend.user.application.UserAccount;
import com.manuelaalecio.educare_backend.user.application.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Login and self-service of the authenticated user. Users and passwords belong to the {@code user} module, reached
 * only through {@link UserService}, which also owns the transactions.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserService userService;
	private final AccessTokenIssuer accessTokenIssuer;

	public IssuedAccessToken login(String login, String password) {
		UserAccount account = userService.authenticate(login, password)
			.orElseThrow(InvalidCredentialsException::new);
		return accessTokenIssuer.issue(account.id());
	}

	public UserAccount me(UUID userId) {
		return userService.findAccount(userId);
	}

	public void changeOwnPassword(UUID userId, String currentPassword, String newPassword) {
		userService.changeOwnPassword(userId, currentPassword, newPassword);
	}

}
