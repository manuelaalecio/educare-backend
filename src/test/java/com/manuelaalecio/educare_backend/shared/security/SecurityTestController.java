package com.manuelaalecio.educare_backend.shared.security;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for {@link SecurityConfigurationTest}, standing in for the public and protected routes of the modules.
 * A {@link TestComponent}, so that component scanning does not add it to other test contexts.
 */
@TestComponent
@RestController
public class SecurityTestController {

	static final String CREATED_LOCATION = "/api/v1/test-resources/42";

	@PostMapping("/api/v1/auth/login")
	public Map<String, String> login() {
		return Map.of("result", "login");
	}

	@GetMapping("/actuator/health")
	public Map<String, String> health() {
		return Map.of("status", "UP");
	}

	@GetMapping("/api/v1/test-resources/me")
	public Map<String, UUID> me(@AuthenticationPrincipal AuthenticatedUser user) {
		return Map.of("id", user.id());
	}

	@PostMapping("/api/v1/test-resources")
	public ResponseEntity<Void> create() {
		return ResponseEntity.created(URI.create(CREATED_LOCATION)).build();
	}

	@PreAuthorize("hasRole('ADMIN')")
	@GetMapping("/api/v1/test-resources/admin")
	public Map<String, String> adminOnly() {
		return Map.of("result", "admin");
	}

}
