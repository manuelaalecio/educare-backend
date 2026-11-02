package com.manuelaalecio.educare_backend.child.api;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.manuelaalecio.educare_backend.child.api.dto.ChildClassroomResponse;
import com.manuelaalecio.educare_backend.child.api.dto.ChildRequest;
import com.manuelaalecio.educare_backend.child.api.dto.ChildResponse;
import com.manuelaalecio.educare_backend.child.api.dto.ChildSummaryResponse;
import com.manuelaalecio.educare_backend.child.application.ChildService;
import com.manuelaalecio.educare_backend.shared.config.BadRequestResponse;
import com.manuelaalecio.educare_backend.shared.config.ConflictResponse;
import com.manuelaalecio.educare_backend.shared.config.ForbiddenResponse;
import com.manuelaalecio.educare_backend.shared.config.NotFoundResponse;
import com.manuelaalecio.educare_backend.shared.error.InvalidSortPropertyException;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Registry of the children served by the institution. Every authenticated user creates, reads, lists and updates
 * children (the token is required by the filter chain); only {@code COORDENADOR} deletes.
 */
@RestController
@RequestMapping(ChildController.BASE_PATH)
@RequiredArgsConstructor
public class ChildController {

	static final String BASE_PATH = "/api/v1/children";

	static final Set<String> SORTABLE_PROPERTIES = Set.of("name", "birthDate", "createdAt");

	private final ChildService childService;
	private final ChildMapper childMapper;

	@PostMapping
	@ApiResponse(responseCode = "201", description = "Created", headers = @Header(name = "Location",
			description = "Path of the created child", schema = @Schema(type = "string", format = "uri")))
	@BadRequestResponse
	@ConflictResponse
	public ResponseEntity<ChildResponse> create(@Valid @RequestBody ChildRequest request) {
		ChildResponse created = childMapper
			.toResponse(childService.create(childMapper.toDetails(request), request.classroomId()));
		URI location = UriComponentsBuilder.fromPath(BASE_PATH).path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	/**
	 * A page of children, each with only the list fields and the current class.
	 *
	 * @param name part of the name, compared ignoring case and accents; ignored when blank
	 * @param classroomId only the children currently in this class
	 */
	@GetMapping
	// runtime no-op, so that springdoc documents the 200 next to the declared errors (see AuthController.login)
	@ResponseStatus(HttpStatus.OK)
	@BadRequestResponse
	public PagedModel<ChildSummaryResponse> list(@RequestParam(required = false) String name,
			@RequestParam(required = false) UUID classroomId, @PageableDefault(size = 20, sort = "name") Pageable pageable) {
		for (Sort.Order order : pageable.getSort()) {
			if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
				throw new InvalidSortPropertyException(order.getProperty());
			}
		}
		return new PagedModel<>(childService.list(name, classroomId, pageable).map(childMapper::toSummary));
	}

	@GetMapping("/{id}")
	@ResponseStatus(HttpStatus.OK)
	@BadRequestResponse
	@NotFoundResponse
	public ChildResponse findById(@PathVariable UUID id) {
		return childMapper.toResponse(childService.findById(id));
	}

	/**
	 * Every class the child has been in, the most recent first.
	 */
	@GetMapping("/{id}/classrooms")
	@ResponseStatus(HttpStatus.OK)
	@BadRequestResponse
	@NotFoundResponse
	public List<ChildClassroomResponse> history(@PathVariable UUID id) {
		return childService.history(id).stream().map(childMapper::toHistoryItem).toList();
	}

	@PutMapping("/{id}")
	@ResponseStatus(HttpStatus.OK)
	@BadRequestResponse
	@NotFoundResponse
	@ConflictResponse
	public ChildResponse update(@PathVariable UUID id, @Valid @RequestBody ChildRequest request) {
		return childMapper.toResponse(childService.update(id, childMapper.toDetails(request), request.classroomId()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('COORDENADOR')")
	@ForbiddenResponse
	@BadRequestResponse
	@NotFoundResponse
	public void delete(@PathVariable UUID id) {
		childService.delete(id);
	}

}
