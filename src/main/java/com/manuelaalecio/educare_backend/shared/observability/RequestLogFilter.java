package com.manuelaalecio.educare_backend.shared.observability;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Logs one line per request, for measuring how many calls each route gets and how long they take (spec
 * {@code observability}): {@code http method=GET route=/api/v1/children/{id} status=200 durationMs=12}.
 * <p>
 * The line has only values set by the server: the route is the pattern of the mapping that served the request, never
 * the real path, so no id, query string, body, header or user ever reaches the log. A request that did not reach a
 * route (refused by the security filter chain, or a missing route served by the static resource handler) is logged
 * as {@value #UNMATCHED}. Runs before the security filter chain, so the duration includes authentication and the rate
 * limit. The Actuator is left out (the Kubernetes probes run every few seconds). Turned off by setting the level of
 * this logger above {@code INFO}.
 */
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1)
public class RequestLogFilter extends OncePerRequestFilter {

	static final String UNMATCHED = "unmatched";

	/** Pattern of the static resource handler, which answers the routes that do not exist. */
	private static final String RESOURCE_HANDLER_PATTERN = "/**";

	private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return path.equals("/actuator") || path.startsWith("/actuator/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!log.isInfoEnabled()) {
			chain.doFilter(request, response);
			return;
		}
		long start = System.nanoTime();
		boolean failed = true;
		try {
			chain.doFilter(request, response);
			failed = false;
		}
		finally {
			long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			// an exception that leaves the chain becomes a 500 in the container
			int status = failed ? HttpStatus.INTERNAL_SERVER_ERROR.value() : response.getStatus();
			log.info("http method={} route={} status={} durationMs={}", request.getMethod(), routeOf(request), status,
					durationMs);
		}
	}

	private static String routeOf(HttpServletRequest request) {
		Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		if (!(pattern instanceof String route) || route.equals(RESOURCE_HANDLER_PATTERN)) {
			return UNMATCHED;
		}
		return route;
	}

}
