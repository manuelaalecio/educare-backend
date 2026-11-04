package com.manuelaalecio.educare_backend.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies the {@link RequestRateLimiter} to the requests of an authenticated user, before {@code @PreAuthorize} and
 * the controller run. A refusal is thrown as {@link RequestRateLimitExceededException}, which the
 * {@code GlobalExceptionHandler} turns into the {@code 429}. Requests without an {@link AuthenticatedUser} pass: the
 * public routes, and the refused tokens already got a {@code 401} from the security filter chain.
 */
@RequiredArgsConstructor
public class RequestRateLimitInterceptor implements HandlerInterceptor {

	private final RequestRateLimiter limiter;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
			limiter.acquire(user.id());
		}
		return true;
	}

}
