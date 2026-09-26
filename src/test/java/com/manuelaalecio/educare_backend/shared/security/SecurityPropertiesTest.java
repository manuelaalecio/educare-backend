package com.manuelaalecio.educare_backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.List;

import com.manuelaalecio.educare_backend.shared.security.SecurityProperties.Cors;
import com.manuelaalecio.educare_backend.shared.security.SecurityProperties.Jwt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SecurityPropertiesTest {

	private static final String SECRET_32_BYTES = "0123456789abcdef0123456789abcdef";
	private static final Duration EIGHT_HOURS = Duration.ofHours(8);
	private static final List<String> ORIGINS = List.of("http://localhost:5173");

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void shouldRejectSecretWhenMissingOrBlank(String secret) {
		assertThatIllegalArgumentException().isThrownBy(() -> new Jwt(secret, EIGHT_HOURS))
			.withMessageContaining("educare.security.jwt.secret é obrigatório");
	}

	@ParameterizedTest
	@ValueSource(strings = {"${EDUCARE_JWT_SECRET}", "prefix-${EDUCARE_JWT_SECRET}-0123456789abcdef0123456789"})
	void shouldRejectSecretWhenPlaceholderIsUnresolved(String secret) {
		assertThatIllegalArgumentException().isThrownBy(() -> new Jwt(secret, EIGHT_HOURS))
			.withMessageContaining("EDUCARE_JWT_SECRET")
			.withMessageNotContaining(secret);
	}

	@Test
	void shouldRejectSecretWhenShorterThan32Bytes() {
		// given
		String secret = SECRET_32_BYTES.substring(1);

		// when / then
		assertThatIllegalArgumentException().isThrownBy(() -> new Jwt(secret, EIGHT_HOURS))
			.withMessageContaining("no mínimo 32 bytes")
			.withMessageNotContaining(secret);
	}

	@Test
	void shouldCountUtf8BytesWhenCheckingSecretLength() {
		// given: 16 characters, 32 bytes in UTF-8
		String secret = "çççççççççççççççç";

		// when
		Jwt jwt = new Jwt(secret, EIGHT_HOURS);

		// then
		assertThat(jwt.secretKey().getEncoded()).hasSize(32);
	}

	@Test
	void shouldAcceptSecretWhenItHas32Bytes() {
		// when
		Jwt jwt = new Jwt(SECRET_32_BYTES, EIGHT_HOURS);

		// then
		assertThat(jwt.secret()).isEqualTo(SECRET_32_BYTES);
		assertThat(jwt.expiration()).isEqualTo(EIGHT_HOURS);
		assertThat(jwt.secretKey().getAlgorithm()).isEqualTo("HmacSHA256");
		assertThat(jwt.secretKey().getEncoded()).isEqualTo(SECRET_32_BYTES.getBytes());
	}

	@Test
	void shouldRejectExpirationWhenMissing() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Jwt(SECRET_32_BYTES, null))
			.withMessageContaining("educare.security.jwt.expiration");
	}

	@ParameterizedTest
	@ValueSource(strings = {"PT0S", "-PT1H"})
	void shouldRejectExpirationWhenNotPositive(String expiration) {
		assertThatIllegalArgumentException().isThrownBy(() -> new Jwt(SECRET_32_BYTES, Duration.parse(expiration)))
			.withMessageContaining("educare.security.jwt.expiration");
	}

	@Test
	void shouldHideSecretWhenConvertedToString() {
		// given
		SecurityProperties properties = new SecurityProperties(new Jwt(SECRET_32_BYTES, EIGHT_HOURS), new Cors(ORIGINS));

		// when
		String text = properties.toString();

		// then
		assertThat(text).doesNotContain(SECRET_32_BYTES).contains("secret=***", "PT8H", "http://localhost:5173");
	}

	@ParameterizedTest
	@NullAndEmptySource
	void shouldRejectOriginsWhenMissingOrEmpty(List<String> origins) {
		assertThatIllegalArgumentException().isThrownBy(() -> new Cors(origins))
			.withMessageContaining("educare.security.cors.allowed-origins é obrigatório");
	}

	@Test
	void shouldRejectOriginsWhenWildcard() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Cors(List.of("http://localhost:5173", "*")))
			.withMessageContaining("não aceita '*'");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"https://a.org/app",
		"https://a.org/",
		"a.org",
		"ftp://a.org",
		"https://",
		"https://a.org?x=1",
		"https://a.org#top",
		"https://user@a.org",
		"https://*.a.org",
		"${EDUCARE_CORS_ALLOWED_ORIGINS}",
		"",
	})
	void shouldRejectOriginWhenNotSchemeHostAndPort(String origin) {
		assertThatIllegalArgumentException().isThrownBy(() -> new Cors(List.of(origin)))
			.withMessageContaining("origem inválida");
	}

	@Test
	void shouldAcceptOriginsWhenAllAreHttpOrHttpsWithHost() {
		// given
		List<String> origins = List.of("https://a.org", "http://localhost:5173");

		// when
		Cors cors = new Cors(origins);

		// then
		assertThat(cors.allowedOrigins()).containsExactly("https://a.org", "http://localhost:5173");
	}

	@Test
	void shouldValidateEachPartWhenBlocksAreMissing() {
		// given: a block missing from the configuration is bound as null
		assertThatIllegalArgumentException().isThrownBy(() -> new SecurityProperties(null, new Cors(ORIGINS)))
			.withMessageContaining("educare.security.jwt.secret");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new SecurityProperties(new Jwt(SECRET_32_BYTES, EIGHT_HOURS), null))
			.withMessageContaining("educare.security.cors.allowed-origins");
	}

}
