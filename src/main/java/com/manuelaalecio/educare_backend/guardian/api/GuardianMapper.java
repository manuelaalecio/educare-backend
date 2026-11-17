package com.manuelaalecio.educare_backend.guardian.api;

import java.util.List;

import com.manuelaalecio.educare_backend.guardian.api.dto.GuardianChildResponse;
import com.manuelaalecio.educare_backend.guardian.api.dto.GuardianRequest;
import com.manuelaalecio.educare_backend.guardian.api.dto.GuardianResponse;
import com.manuelaalecio.educare_backend.guardian.api.dto.GuardianSummaryResponse;
import com.manuelaalecio.educare_backend.guardian.application.GuardianChildRef;
import com.manuelaalecio.educare_backend.guardian.application.GuardianInput;
import com.manuelaalecio.educare_backend.guardian.application.GuardianSummaryView;
import com.manuelaalecio.educare_backend.guardian.application.GuardianView;
import com.manuelaalecio.educare_backend.guardian.domain.Guardian;
import org.springframework.stereotype.Component;

/**
 * Converts between the use-case views and the API representation, in plain Java.
 */
@Component
public class GuardianMapper {

	public GuardianInput toInput(GuardianRequest request) {
		return new GuardianInput(request.name(), request.maritalStatus(), request.birthDate(), request.cpf(),
				request.rg(), request.phone(), request.notWorking(), request.workplace(), request.workAddress(),
				request.workPhone());
	}

	public GuardianResponse toResponse(GuardianView view) {
		Guardian guardian = view.guardian();
		return new GuardianResponse(guardian.getId(), guardian.getName(),
				guardian.getMaritalStatus() == null ? null : guardian.getMaritalStatus().name(),
				guardian.getBirthDate(), guardian.getCpf(), guardian.getRg(), guardian.getPhone(),
				guardian.isNotWorking(), guardian.getWorkplace(), guardian.getWorkAddress(), guardian.getWorkPhone(),
				toResponse(view.children()), guardian.getCreatedAt(), guardian.getUpdatedAt());
	}

	public GuardianSummaryResponse toSummary(GuardianSummaryView view) {
		Guardian guardian = view.guardian();
		return new GuardianSummaryResponse(guardian.getId(), guardian.getName(), guardian.getPhone(),
				toResponse(view.children()));
	}

	private static List<GuardianChildResponse> toResponse(List<GuardianChildRef> children) {
		return children.stream()
			.map(child -> new GuardianChildResponse(child.childId(), child.childName(), child.relationship()))
			.toList();
	}

}
