package com.manuelaalecio.educare_backend.auth.api;

import com.manuelaalecio.educare_backend.auth.api.dto.AccessTokenResponse;
import com.manuelaalecio.educare_backend.auth.api.dto.MeResponse;
import com.manuelaalecio.educare_backend.shared.security.IssuedAccessToken;
import com.manuelaalecio.educare_backend.user.application.UserAccount;
import org.springframework.stereotype.Component;

/**
 * Converts the results of the authentication use cases into the API representation, in plain Java.
 */
@Component
public class AuthMapper {

	static final String BEARER = "Bearer";

	public AccessTokenResponse toResponse(IssuedAccessToken token) {
		return new AccessTokenResponse(token.value(), BEARER, token.expiresIn());
	}

	public MeResponse toResponse(UserAccount account) {
		return new MeResponse(account.id(), account.name(), account.login(), account.role(), account.createdAt(),
				account.updatedAt());
	}

}
