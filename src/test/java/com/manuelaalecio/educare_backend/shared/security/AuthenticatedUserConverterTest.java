package com.manuelaalecio.educare_backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserConverterTest {

	private static final UUID USER_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");

	@Mock
	private AuthenticatedUserLookup lookup;

	@InjectMocks
	private AuthenticatedUserConverter converter;

	private static Jwt jwt(String subject) {
		Jwt.Builder builder = Jwt.withTokenValue("token")
			.header("alg", "HS256")
			.issuer(AccessTokenIssuer.ISSUER)
			.issuedAt(Instant.parse("2026-01-10T12:00:00Z"))
			.expiresAt(Instant.parse("2026-01-10T20:00:00Z"));
		return subject == null ? builder.build() : builder.subject(subject).build();
	}

	@Test
	void shouldRejectTokenWhenSubjectIsNotUuid() {
		// given
		Jwt jwt = jwt("ana.souza@educare.org");

		// when / then
		assertThatThrownBy(() -> converter.convert(jwt))
			.isInstanceOf(InvalidBearerTokenException.class)
			.hasMessage("Invalid token");
		verifyNoInteractions(lookup);
	}

	@Test
	void shouldRejectTokenWhenSubjectIsMissing() {
		// given
		Jwt jwt = jwt(null);

		// when / then
		assertThatThrownBy(() -> converter.convert(jwt))
			.isInstanceOf(InvalidBearerTokenException.class)
			.hasMessage("Invalid token");
		verifyNoInteractions(lookup);
	}

	@Test
	void shouldRejectTokenWhenUserDoesNotExist() {
		// given
		when(lookup.findById(USER_ID)).thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> converter.convert(jwt(USER_ID.toString())))
			.isInstanceOf(InvalidBearerTokenException.class)
			.hasMessage("Invalid token");
	}

	@Test
	void shouldAuthenticateUserWithRoleAuthorityWhenUserExists() {
		// given
		AuthenticatedUser user = new AuthenticatedUser(USER_ID, "ADMIN");
		when(lookup.findById(USER_ID)).thenReturn(Optional.of(user));

		// when
		AbstractAuthenticationToken authentication = converter.convert(jwt(USER_ID.toString()));

		// then
		assertThat(authentication).isInstanceOf(AuthenticatedUserAuthentication.class);
		assertThat(authentication.isAuthenticated()).isTrue();
		assertThat(authentication.getPrincipal()).isEqualTo(user);
		assertThat(authentication.getName()).isEqualTo(USER_ID.toString());
		assertThat(authentication.getCredentials()).isNull();
		assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.containsExactly("ROLE_ADMIN");
	}

}
