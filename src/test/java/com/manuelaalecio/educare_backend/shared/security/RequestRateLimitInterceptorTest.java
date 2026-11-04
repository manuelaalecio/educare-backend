package com.manuelaalecio.educare_backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class RequestRateLimitInterceptorTest {

	private static final UUID ANA = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");

	private final RequestRateLimiter limiter = mock(RequestRateLimiter.class);

	private final RequestRateLimitInterceptor interceptor = new RequestRateLimitInterceptor(limiter);

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void shouldAcquireForUserWhenRequestIsAuthenticated() {
		// given
		authenticate(new AuthenticatedUserAuthentication(new AuthenticatedUser(ANA, "EDUCADOR")));

		// when
		boolean proceed = preHandle();

		// then
		assertThat(proceed).isTrue();
		verify(limiter).acquire(ANA);
	}

	@Test
	void shouldPassWithoutAcquiringWhenThereIsNoAuthentication() {
		// when
		boolean proceed = preHandle();

		// then
		assertThat(proceed).isTrue();
		verifyNoInteractions(limiter);
	}

	@Test
	void shouldPassWithoutAcquiringWhenRequestIsAnonymous() {
		// given
		authenticate(new AnonymousAuthenticationToken("key", "anonymousUser",
				List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

		// when
		boolean proceed = preHandle();

		// then
		assertThat(proceed).isTrue();
		verifyNoInteractions(limiter);
	}

	@Test
	void shouldPassWithoutAcquiringWhenPrincipalIsNotAnAuthenticatedUser() {
		// given
		authenticate(new TestingAuthenticationToken("outro", null));

		// when
		boolean proceed = preHandle();

		// then
		assertThat(proceed).isTrue();
		verifyNoInteractions(limiter);
	}

	private boolean preHandle() {
		return interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
	}

	private static void authenticate(Authentication authentication) {
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

}
