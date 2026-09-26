package com.manuelaalecio.educare_backend.user.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.manuelaalecio.educare_backend.user.api.dto.UserResponse;
import com.manuelaalecio.educare_backend.user.domain.Role;
import com.manuelaalecio.educare_backend.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserMapperTest {

	private final UserMapper mapper = new UserMapper();

	@Test
	void shouldCopyEveryFieldWhenMappingUserToResponse() {
		// given
		UUID id = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
		Instant createdAt = Instant.parse("2026-03-01T10:00:00Z");
		Instant updatedAt = Instant.parse("2026-03-02T09:00:00Z");
		User user = new User("Ana Souza", "ana.souza@educare.org", "hash", Role.ADMIN);
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "createdAt", createdAt);
		ReflectionTestUtils.setField(user, "updatedAt", updatedAt);

		// when
		UserResponse response = mapper.toResponse(user);

		// then
		assertThat(response).isEqualTo(
				new UserResponse(id, "Ana Souza", "ana.souza@educare.org", "ADMIN", createdAt, updatedAt));
	}

	@Test
	void shouldMapUserRoleNameWhenUserIsNotAdmin() {
		// given
		User user = new User("Bruno Lima", "bruno.lima@educare.org", "hash");

		// when
		UserResponse response = mapper.toResponse(user);

		// then
		assertThat(response.role()).isEqualTo("USER");
	}

	@Test
	void shouldReturnNullRoleWhenRoleIsNotInformed() {
		// when
		Role role = mapper.toRole(null);

		// then
		assertThat(role).isNull();
	}

	@Test
	void shouldReturnAdminWhenRoleIsAdmin() {
		// when
		Role role = mapper.toRole("ADMIN");

		// then
		assertThat(role).isEqualTo(Role.ADMIN);
	}

	@Test
	void shouldReturnUserWhenRoleIsUser() {
		// when
		Role role = mapper.toRole("USER");

		// then
		assertThat(role).isEqualTo(Role.USER);
	}

}
