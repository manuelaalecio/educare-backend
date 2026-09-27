package com.manuelaalecio.educare_backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthenticatedUserAuditorTest {

	private static final UUID USER_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");

	private final AuthenticatedUserAuditor auditor = new AuthenticatedUserAuditor();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void shouldReturnUserIdWhenPrincipalIsAuthenticatedUser() {
		// given
		SecurityContextHolder.getContext()
			.setAuthentication(new AuthenticatedUserAuthentication(new AuthenticatedUser(USER_ID, "USER")));

		// when
		Optional<UUID> auditor = this.auditor.getCurrentAuditor();

		// then
		assertThat(auditor).contains(USER_ID);
	}

	@Test
	void shouldReturnEmptyWhenThereIsNoAuthentication() {
		// given
		SecurityContextHolder.clearContext();

		// when
		Optional<UUID> auditor = this.auditor.getCurrentAuditor();

		// then
		assertThat(auditor).isEmpty();
	}

	@Test
	void shouldReturnEmptyWhenAuthenticationIsAnonymous() {
		// given
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

		// when
		Optional<UUID> auditor = this.auditor.getCurrentAuditor();

		// then
		assertThat(auditor).isEmpty();
	}

	@Test
	void shouldReturnEmptyWhenPrincipalIsOfAnotherType() {
		// given
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
				USER_ID.toString(), null, AuthorityUtils.createAuthorityList("ROLE_ADMIN")));

		// when
		Optional<UUID> auditor = this.auditor.getCurrentAuditor();

		// then
		assertThat(auditor).isEmpty();
	}

}
