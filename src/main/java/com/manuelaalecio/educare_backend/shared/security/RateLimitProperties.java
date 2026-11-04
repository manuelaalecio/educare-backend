package com.manuelaalecio.educare_backend.shared.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limit of requests per authenticated user (spec {@code security}, "Limite de requisições por usuário autenticado"),
 * validated when bound: an invalid value fails the startup. The defaults (120 requests per minute) are in the
 * {@code application.yaml}.
 *
 * @param maxRequests requests a user can make within the window
 * @param window length of the sliding window
 */
@ConfigurationProperties("educare.rate-limit")
public record RateLimitProperties(int maxRequests, Duration window) {

	public RateLimitProperties {
		if (maxRequests <= 0) {
			throw new IllegalArgumentException("educare.rate-limit.max-requests deve ser positivo");
		}
		if (window == null || window.isNegative() || window.isZero()) {
			throw new IllegalArgumentException("educare.rate-limit.window deve ser positivo");
		}
	}

}
