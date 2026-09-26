package com.manuelaalecio.educare_backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderConfigurationTest {

	@Test
	void shouldMatchRawPasswordWhenEncodedByProvidedEncoder() {
		// given
		PasswordEncoder encoder = new PasswordEncoderConfiguration().passwordEncoder();

		// when
		String hash = encoder.encode("segredo123");

		// then
		assertThat(hash).isNotEqualTo("segredo123").startsWith("$2a$10$");
		assertThat(encoder.matches("segredo123", hash)).isTrue();
		assertThat(encoder.matches("outraSenha", hash)).isFalse();
	}

}
