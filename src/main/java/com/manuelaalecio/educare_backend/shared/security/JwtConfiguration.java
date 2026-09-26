package com.manuelaalecio.educare_backend.shared.security;

import java.time.Clock;
import java.time.Duration;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * HS256 tokens signed and validated with the key of {@link SecurityProperties} (design D3 of the add-authentication
 * change). Dates are checked against the application {@link Clock} with no clock skew: there is a single server,
 * and tests control the time.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
public class JwtConfiguration {

	@Bean
	JwtEncoder jwtEncoder(SecurityProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(properties.jwt().secretKey()));
	}

	@Bean
	JwtDecoder jwtDecoder(SecurityProperties properties, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.jwt().secretKey())
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
		timestampValidator.setClock(clock);
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestampValidator,
			new JwtIssuerValidator(AccessTokenIssuer.ISSUER)));
		return decoder;
	}

	@Bean
	AccessTokenIssuer accessTokenIssuer(JwtEncoder jwtEncoder, SecurityProperties properties, Clock clock) {
		return new AccessTokenIssuer(jwtEncoder, properties.jwt().expiration(), clock);
	}

}
