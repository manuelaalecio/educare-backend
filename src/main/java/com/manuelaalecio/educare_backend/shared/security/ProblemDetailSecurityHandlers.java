package com.manuelaalecio.educare_backend.shared.security;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the {@code 401} and {@code 403} responses of the security filter chain as a {@link ProblemDetail}, in the
 * same format as the {@code GlobalExceptionHandler}. The {@code 401} never tells why the request was refused
 * (missing, malformed, expired token etc.), neither in the body nor in {@code WWW-Authenticate}.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

	static final String UNAUTHORIZED_DETAIL = "Autenticação necessária";

	static final String FORBIDDEN_DETAIL = "Acesso negado";

	private final JsonMapper jsonMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		write(request, response, HttpStatus.UNAUTHORIZED, UNAUTHORIZED_DETAIL);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(request, response, HttpStatus.FORBIDDEN, FORBIDDEN_DETAIL);
	}

	private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
			throws IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setInstance(instanceOf(request));
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		jsonMapper.writeValue(response.getOutputStream(), problem);
	}

	/**
	 * The request path, as MVC fills {@code instance}; left out when the path is not a valid URI, so that the
	 * refusal does not turn into a {@code 500}.
	 */
	private static URI instanceOf(HttpServletRequest request) {
		try {
			return URI.create(request.getRequestURI());
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
