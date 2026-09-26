package com.manuelaalecio.educare_backend.shared.security;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Authentication of a request with a valid token: the principal is the {@link AuthenticatedUser}, with the authority
 * {@code ROLE_<role>} of the role the user has now.
 */
public class AuthenticatedUserAuthentication extends AbstractAuthenticationToken {

	private final AuthenticatedUser user;

	public AuthenticatedUserAuthentication(AuthenticatedUser user) {
		super(List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
		this.user = user;
		setAuthenticated(true);
	}

	@Override
	public AuthenticatedUser getPrincipal() {
		return user;
	}

	/** The token was already validated; it is not kept. */
	@Override
	public Object getCredentials() {
		return null;
	}

	@Override
	public String getName() {
		return user.id().toString();
	}

}
