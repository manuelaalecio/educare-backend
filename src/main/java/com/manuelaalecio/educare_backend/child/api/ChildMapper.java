package com.manuelaalecio.educare_backend.child.api;

import com.manuelaalecio.educare_backend.child.api.dto.ChildClassroomResponse;
import com.manuelaalecio.educare_backend.child.api.dto.ChildRequest;
import com.manuelaalecio.educare_backend.child.api.dto.ChildResponse;
import com.manuelaalecio.educare_backend.child.api.dto.ChildSummaryResponse;
import com.manuelaalecio.educare_backend.child.api.dto.ClassroomRefResponse;
import com.manuelaalecio.educare_backend.child.application.ChildClassroomView;
import com.manuelaalecio.educare_backend.child.application.ChildSummaryView;
import com.manuelaalecio.educare_backend.child.application.ChildView;
import com.manuelaalecio.educare_backend.child.domain.BrazilianState;
import com.manuelaalecio.educare_backend.child.domain.Child;
import com.manuelaalecio.educare_backend.child.domain.ChildDetails;
import com.manuelaalecio.educare_backend.child.domain.Gender;
import com.manuelaalecio.educare_backend.child.domain.MaritalStatus;
import com.manuelaalecio.educare_backend.child.domain.Race;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomRef;
import org.springframework.stereotype.Component;

/**
 * Converts between the use-case views and the API representation, in plain Java. The enums arrive as strings
 * already validated by the request.
 */
@Component
public class ChildMapper {

	public ChildDetails toDetails(ChildRequest request) {
		return new ChildDetails(request.name(), request.birthDate(), request.cep(), request.address(),
				request.neighborhood(), request.addressNumber(), request.addressComplement(), request.city(),
				request.state() == null ? null : BrazilianState.valueOf(request.state()), request.birthplace(),
				request.race() == null ? null : Race.valueOf(request.race()),
				request.gender() == null ? null : Gender.valueOf(request.gender()), request.cpf(), request.rg(),
				request.nationality(),
				request.maritalStatus() == null ? null : MaritalStatus.valueOf(request.maritalStatus()),
				request.religion(), request.church(), request.healthReport(), request.medication(),
				request.medicalObservation(), request.birthCertificateNumber());
	}

	public ChildResponse toResponse(ChildView view) {
		Child child = view.child();
		return new ChildResponse(child.getId(), child.getName(), child.getBirthDate(), toResponse(view.classroom()),
				child.getCep(), child.getAddress(), child.getNeighborhood(), child.getAddressNumber(),
				child.getAddressComplement(), child.getCity(), child.getState().name(), child.getBirthplace(),
				name(child.getRace()), name(child.getGender()), child.getCpf(), child.getRg(), child.getNationality(),
				name(child.getMaritalStatus()), child.getReligion(), child.getChurch(), child.getHealthReport(),
				child.getMedication(), child.getMedicalObservation(), child.getBirthCertificateNumber(),
				child.getCreatedAt(), child.getUpdatedAt());
	}

	public ChildSummaryResponse toSummary(ChildSummaryView view) {
		Child child = view.child();
		return new ChildSummaryResponse(child.getId(), child.getName(), child.getBirthDate(),
				toResponse(view.classroom()), child.getCreatedAt(), child.getUpdatedAt());
	}

	public ChildClassroomResponse toHistoryItem(ChildClassroomView view) {
		return new ChildClassroomResponse(view.classroom().id(), view.classroom().name(), view.link().getStartDate(),
				view.link().getEndDate());
	}

	private static ClassroomRefResponse toResponse(ClassroomRef classroom) {
		return new ClassroomRefResponse(classroom.id(), classroom.name());
	}

	private static String name(Enum<?> value) {
		return value == null ? null : value.name();
	}

}
