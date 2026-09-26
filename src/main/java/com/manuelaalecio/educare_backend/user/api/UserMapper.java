package com.manuelaalecio.educare_backend.user.api;

import com.manuelaalecio.educare_backend.user.api.dto.UserResponse;
import com.manuelaalecio.educare_backend.user.domain.Role;
import com.manuelaalecio.educare_backend.user.domain.User;
import org.springframework.stereotype.Component;

/**
 * Converts between the {@link User} entity and the API representation, in plain Java.
 */
@Component
public class UserMapper {

	public UserResponse toResponse(User user) {
		return new UserResponse(user.getId(), user.getName(), user.getLogin(), user.getRole().name(),
				user.getCreatedAt(), user.getUpdatedAt());
	}

	/**
	 * Converts a role already validated by the request DTO. {@code null} stays {@code null}, meaning "not informed".
	 */
	public Role toRole(String role) {
		return role == null ? null : Role.valueOf(role);
	}

}
