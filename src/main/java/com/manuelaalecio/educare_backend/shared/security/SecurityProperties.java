package com.manuelaalecio.educare_backend.shared.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security settings, validated when bound: an invalid value fails the startup (design D4 of the add-authentication
 * change). A missing environment variable reaches here as an empty string (Compose) or as the unresolved
 * placeholder itself, and both are refused.
 */
@ConfigurationProperties("educare.security")
public record SecurityProperties(Jwt jwt, Cors cors) {

	private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\$\\{[^}]*}");

	public SecurityProperties {
		// A block missing from the configuration is bound as null.
		if (jwt == null) {
			throw new IllegalArgumentException(Jwt.SECRET_REQUIRED);
		}
		if (cors == null) {
			throw new IllegalArgumentException(Cors.ORIGINS_REQUIRED);
		}
	}

	/**
	 * @param secret HS256 signing key, at least 32 bytes in UTF-8
	 * @param expiration validity of the access tokens
	 */
	public record Jwt(String secret, Duration expiration) {

		/** 256 bits, the minimum key size for HS256. */
		static final int MIN_SECRET_BYTES = 32;

		private static final String SECRET_REQUIRED = "educare.security.jwt.secret é obrigatório";

		public Jwt {
			if (secret == null || secret.isBlank()) {
				throw new IllegalArgumentException(SECRET_REQUIRED);
			}
			if (UNRESOLVED_PLACEHOLDER.matcher(secret).find()) {
				throw new IllegalArgumentException(
					"educare.security.jwt.secret contém uma variável não resolvida; configure EDUCARE_JWT_SECRET");
			}
			if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
				throw new IllegalArgumentException(
					"educare.security.jwt.secret deve ter no mínimo " + MIN_SECRET_BYTES + " bytes");
			}
			if (expiration == null || expiration.isNegative() || expiration.isZero()) {
				throw new IllegalArgumentException("educare.security.jwt.expiration deve ser positivo");
			}
		}

		public SecretKey secretKey() {
			return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		}

		/** Keeps the secret out of logs and error messages. */
		@Override
		public String toString() {
			return "Jwt[secret=***, expiration=" + expiration + "]";
		}
	}

	/**
	 * @param allowedOrigins exact origins of the frontend ({@code http} or {@code https}, host and optional port)
	 */
	public record Cors(List<String> allowedOrigins) {

		private static final String ORIGINS_REQUIRED = "educare.security.cors.allowed-origins é obrigatório";

		public Cors {
			if (allowedOrigins == null || allowedOrigins.isEmpty()) {
				throw new IllegalArgumentException(ORIGINS_REQUIRED);
			}
			allowedOrigins.forEach(Cors::validateOrigin);
			allowedOrigins = List.copyOf(allowedOrigins);
		}

		private static void validateOrigin(String origin) {
			if ("*".equals(origin)) {
				throw new IllegalArgumentException(
					"educare.security.cors.allowed-origins não aceita '*'; informe as origens do frontend");
			}
			if (!isValidOrigin(origin)) {
				throw new IllegalArgumentException("educare.security.cors.allowed-origins contém uma origem inválida"
					+ " (esperado http(s)://host[:porta], sem caminho): " + origin);
			}
		}

		private static boolean isValidOrigin(String origin) {
			URI uri;
			try {
				uri = new URI(origin);
			}
			catch (URISyntaxException ex) {
				return false;
			}
			return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
				// checked first: the path is null only for opaque URIs, which have no host
				&& uri.getHost() != null
				&& uri.getRawUserInfo() == null
				&& uri.getRawPath().isEmpty()
				&& uri.getRawQuery() == null
				&& uri.getRawFragment() == null;
		}
	}

}
