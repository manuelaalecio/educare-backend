package com.manuelaalecio.educare_backend.user.api;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.error.InvalidSortPropertyException;
import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUser;
import com.manuelaalecio.educare_backend.user.api.dto.ChangePasswordRequest;
import com.manuelaalecio.educare_backend.user.api.dto.CreateUserRequest;
import com.manuelaalecio.educare_backend.user.api.dto.UpdateUserRequest;
import com.manuelaalecio.educare_backend.user.api.dto.UserResponse;
import com.manuelaalecio.educare_backend.user.application.UserService;
import com.manuelaalecio.educare_backend.user.domain.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping(UserController.BASE_PATH)
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

	static final String BASE_PATH = "/api/v1/users";

	/**
	 * Properties a listing may be sorted by. Anything else (e.g. {@code passwordHash}) is refused, so the
	 * API never exposes an ordering by internal columns.
	 */
	static final Set<String> SORTABLE_PROPERTIES = Set.of("name", "login", "createdAt");

	private final UserService userService;
	private final UserMapper userMapper;

	@PostMapping
	public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
		User user = userService.create(request.name(), request.login(), request.password(),
				userMapper.toRole(request.role()));
		URI location = UriComponentsBuilder.fromPath(BASE_PATH).path("/{id}").buildAndExpand(user.getId()).toUri();
		return ResponseEntity.created(location).body(userMapper.toResponse(user));
	}

	@GetMapping
	public PagedModel<UserResponse> list(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
		for (Sort.Order order : pageable.getSort()) {
			if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
				throw new InvalidSortPropertyException(order.getProperty());
			}
		}
		return new PagedModel<>(userService.list(pageable).map(userMapper::toResponse));
	}

	@GetMapping("/{id}")
	public UserResponse findById(@PathVariable UUID id) {
		return userMapper.toResponse(userService.findById(id));
	}

	@PutMapping("/{id}")
	public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request,
			@AuthenticationPrincipal AuthenticatedUser actor) {
		User user = userService.update(id, request.name(), request.login(), userMapper.toRole(request.role()),
				actor.id());
		return userMapper.toResponse(user);
	}

	@PutMapping("/{id}/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@PathVariable UUID id, @Valid @RequestBody ChangePasswordRequest request) {
		userService.changePassword(id, request.password());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
		userService.delete(id, actor.id());
	}

}
