package com.manuelaalecio.educare_backend.guardian.api;

import java.util.Set;
import java.util.UUID;

import com.manuelaalecio.educare_backend.guardian.api.dto.GuardianRequest;
import com.manuelaalecio.educare_backend.guardian.api.dto.GuardianResponse;
import com.manuelaalecio.educare_backend.guardian.api.dto.GuardianSummaryResponse;
import com.manuelaalecio.educare_backend.guardian.application.GuardianService;
import com.manuelaalecio.educare_backend.shared.config.BadRequestResponse;
import com.manuelaalecio.educare_backend.shared.config.ConflictResponse;
import com.manuelaalecio.educare_backend.shared.config.NotFoundResponse;
import com.manuelaalecio.educare_backend.shared.error.InvalidSortPropertyException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registry of the guardians of the children. Every authenticated user lists, reads and updates guardians (the token
 * is required by the filter chain). Guardians are created and linked in the body of a child.
 */
@RestController
@RequestMapping(GuardianController.BASE_PATH)
@RequiredArgsConstructor
public class GuardianController {

	static final String BASE_PATH = "/api/v1/guardians";

	static final Set<String> SORTABLE_PROPERTIES = Set.of("name", "createdAt");

	private final GuardianService guardianService;
	private final GuardianMapper guardianMapper;

	/**
	 * A page of guardians, each with only the name, the phone and the linked children.
	 *
	 * @param q a full CPF (11 digits, matched exactly) or part of the name (ignoring case and accents); ignored when
	 * blank
	 */
	@GetMapping
	// runtime no-op, so that springdoc documents the 200 next to the declared errors (see AuthController.login)
	@ResponseStatus(HttpStatus.OK)
	@BadRequestResponse
	public PagedModel<GuardianSummaryResponse> list(@RequestParam(required = false) String q,
			@PageableDefault(size = 20, sort = "name") Pageable pageable) {
		for (Sort.Order order : pageable.getSort()) {
			if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
				throw new InvalidSortPropertyException(order.getProperty());
			}
		}
		return new PagedModel<>(guardianService.list(q, pageable).map(guardianMapper::toSummary));
	}

	@GetMapping("/{id}")
	@ResponseStatus(HttpStatus.OK)
	@BadRequestResponse
	@NotFoundResponse
	public GuardianResponse findById(@PathVariable UUID id) {
		return guardianMapper.toResponse(guardianService.findById(id));
	}

	/**
	 * Replaces every field of the guardian; the links with children do not change.
	 */
	@PutMapping("/{id}")
	@ResponseStatus(HttpStatus.OK)
	@BadRequestResponse
	@NotFoundResponse
	@ConflictResponse
	public GuardianResponse update(@PathVariable UUID id, @Valid @RequestBody GuardianRequest request) {
		return guardianMapper.toResponse(guardianService.update(id, guardianMapper.toInput(request)));
	}

}
