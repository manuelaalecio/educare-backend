package com.manuelaalecio.educare_backend.shared.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.config.ClockConfiguration;
import com.manuelaalecio.educare_backend.shared.security.JwtConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Checks the helper against the dev configuration the tests run with: its tokens are accepted by the application
 * decoder, except the one signed with another key.
 */
class AccessTokensTest {

	private static final UUID USER_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(JwtConfiguration.class, ClockConfiguration.class, AccessTokens.class)
		.withPropertyValues(
			"educare.security.jwt.secret=access-tokens-test-secret-0123456789abcdef",
			"educare.security.jwt.expiration=8h",
			"educare.security.cors.allowed-origins=http://localhost:5173");

	@Test
	void shouldIssueTokensAcceptedByDecoderWhenUsingApplicationKey() {
		runner.run(context -> {
			// given
			AccessTokens accessTokens = context.getBean(AccessTokens.class);
			JwtDecoder decoder = context.getBean(JwtDecoder.class);

			// when
			String token = accessTokens.forUser(USER_ID);

			// then
			assertThat(decoder.decode(token).getSubject()).isEqualTo(USER_ID.toString());
			assertThat(accessTokens.bearer(USER_ID)).startsWith("Bearer ");
			assertThat(decoder.decode(accessTokens.bearer(USER_ID).substring("Bearer ".length())).getSubject())
				.isEqualTo(USER_ID.toString());
		});
	}

	@Test
	void shouldIssueTokenRejectedByDecoderWhenSignedWithAnotherKey() {
		runner.run(context -> {
			// given
			AccessTokens accessTokens = context.getBean(AccessTokens.class);
			JwtDecoder decoder = context.getBean(JwtDecoder.class);

			// when
			String token = accessTokens.signedWithAnotherKey(USER_ID);

			// then
			assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
		});
	}

}
