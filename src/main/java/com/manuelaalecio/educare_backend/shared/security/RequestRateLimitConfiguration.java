package com.manuelaalecio.educare_backend.shared.security;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Limit of requests per authenticated user (spec {@code security}). An interceptor rather than a filter, so that the
 * refusal goes through the {@code GlobalExceptionHandler} like every other error and the security filter chain keeps
 * only the authentication. Self-contained (needs only the {@link Clock}), so the {@code @WebMvcTest}s get it without
 * importing it.
 * <p>
 * The public routes are left out explicitly: the resource server authenticates any request with a valid Bearer token,
 * also on a public route such as the login.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RequestRateLimitConfiguration implements WebMvcConfigurer {

	static final String[] PUBLIC_PATHS = {"/api/v1/auth/login", "/actuator", "/actuator/**", "/v3/api-docs",
			"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

	private final RequestRateLimiter limiter;

	public RequestRateLimitConfiguration(RateLimitProperties properties, Clock clock) {
		this.limiter = new RequestRateLimiter(properties, clock);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new RequestRateLimitInterceptor(limiter)).excludePathPatterns(PUBLIC_PATHS);
	}

}
