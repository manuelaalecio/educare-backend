package com.manuelaalecio.educare_backend.auth.api;

import com.manuelaalecio.educare_backend.auth.api.dto.AccessTokenResponse;
import com.manuelaalecio.educare_backend.auth.api.dto.ChangeOwnPasswordRequest;
import com.manuelaalecio.educare_backend.auth.api.dto.LoginRequest;
import com.manuelaalecio.educare_backend.auth.api.dto.MeResponse;
import com.manuelaalecio.educare_backend.auth.application.AuthService;
import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login and self-service of the authenticated user. Only the login is public (see
 * {@code shared/security/SecurityConfiguration}); the other endpoints accept any role.
 */
@RestController
@RequestMapping(AuthController.BASE_PATH)
@RequiredArgsConstructor
public class AuthController {

	static final String BASE_PATH = "/api/v1/auth";

	private final AuthService authService;
	private final AuthMapper authMapper;

	@PostMapping("/login")
	// public: overrides the bearer token declared for the whole API in the OpenAPI document, so that API clients
	// generated from it do not send an empty Authorization header, which the resource server refuses with 401
	@SecurityRequirements
	public AccessTokenResponse login(@Valid @RequestBody LoginRequest request) {
		return authMapper.toResponse(authService.login(request.login(), request.password()));
	}

	@GetMapping("/me")
	public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
		return authMapper.toResponse(authService.me(user.id()));
	}

	@PutMapping("/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changeOwnPassword(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody ChangeOwnPasswordRequest request) {
		authService.changeOwnPassword(user.id(), request.currentPassword(), request.newPassword());
	}

}
