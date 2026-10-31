package com.manuelaalecio.educare_backend.classroom.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.manuelaalecio.educare_backend.classroom.api.dto.ClassroomResponse;
import com.manuelaalecio.educare_backend.classroom.api.dto.CreateClassroomRequest;
import com.manuelaalecio.educare_backend.classroom.api.dto.UpdateClassroomRequest;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomService;
import com.manuelaalecio.educare_backend.shared.config.BadRequestResponse;
import com.manuelaalecio.educare_backend.shared.config.ConflictResponse;
import com.manuelaalecio.educare_backend.shared.config.ForbiddenResponse;
import com.manuelaalecio.educare_backend.shared.config.NotFoundResponse;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping(ClassroomController.BASE_PATH)
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@ForbiddenResponse
public class ClassroomController {

	static final String BASE_PATH = "/api/v1/classrooms";

	private final ClassroomService classroomService;
	private final ClassroomMapper classroomMapper;

	@PostMapping
	@ApiResponse(responseCode = "201", description = "Created", headers = @Header(name = "Location",
			description = "Path of the created class", schema = @Schema(type = "string", format = "uri")))
	@BadRequestResponse
	@ConflictResponse
	public ResponseEntity<ClassroomResponse> create(@Valid @RequestBody CreateClassroomRequest request) {
		ClassroomResponse created = classroomMapper.toResponse(classroomService.create(request.name()));
		URI location = UriComponentsBuilder.fromPath(BASE_PATH).path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	/**
	 * Every class, in alphabetical order, without pagination: the institution has few classes.
	 */
	@GetMapping
	// runtime no-op, so that springdoc documents the 200 next to the declared errors (see AuthController.login)
	@ResponseStatus(HttpStatus.OK)
	public List<ClassroomResponse> list() {
		return classroomService.list().stream().map(classroomMapper::toResponse).toList();
	}

	@GetMapping("/{id}")
	@ResponseStatus(HttpStatus.OK)
	@BadRequestResponse
	@NotFoundResponse
	public ClassroomResponse findById(@PathVariable UUID id) {
		return classroomMapper.toResponse(classroomService.findById(id));
	}

	@PutMapping("/{id}")
	@ResponseStatus(HttpStatus.OK)
	@BadRequestResponse
	@NotFoundResponse
	@ConflictResponse
	public ClassroomResponse rename(@PathVariable UUID id, @Valid @RequestBody UpdateClassroomRequest request) {
		return classroomMapper.toResponse(classroomService.rename(id, request.name()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@BadRequestResponse
	@NotFoundResponse
	@ConflictResponse
	public void delete(@PathVariable UUID id) {
		classroomService.delete(id);
	}

}
