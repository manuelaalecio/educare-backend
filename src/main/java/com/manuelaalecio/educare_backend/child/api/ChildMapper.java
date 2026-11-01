package com.manuelaalecio.educare_backend.child.api;

import com.manuelaalecio.educare_backend.child.api.dto.ChildRequest;
import com.manuelaalecio.educare_backend.child.api.dto.ChildResponse;
import com.manuelaalecio.educare_backend.child.api.dto.ChildSummaryResponse;
import com.manuelaalecio.educare_backend.child.domain.BrazilianState;
import com.manuelaalecio.educare_backend.child.domain.Child;
import com.manuelaalecio.educare_backend.child.domain.ChildDetails;
import com.manuelaalecio.educare_backend.child.domain.Gender;
import com.manuelaalecio.educare_backend.child.domain.MaritalStatus;
import com.manuelaalecio.educare_backend.child.domain.Race;
import org.springframework.stereotype.Component;

/**
 * Converts between the {@link Child} entity and the API representation, in plain Java. The enums arrive as
 * strings already validated by the request.
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

	public ChildResponse toResponse(Child child) {
		return new ChildResponse(child.getId(), child.getName(), child.getBirthDate(), child.getCep(),
				child.getAddress(), child.getNeighborhood(), child.getAddressNumber(), child.getAddressComplement(),
				child.getCity(), child.getState().name(), child.getBirthplace(), name(child.getRace()),
				name(child.getGender()), child.getCpf(), child.getRg(), child.getNationality(),
				name(child.getMaritalStatus()), child.getReligion(), child.getChurch(), child.getHealthReport(),
				child.getMedication(), child.getMedicalObservation(), child.getBirthCertificateNumber(),
				child.getCreatedAt(), child.getUpdatedAt());
	}

	public ChildSummaryResponse toSummary(Child child) {
		return new ChildSummaryResponse(child.getId(), child.getName(), child.getBirthDate(), child.getCreatedAt(),
				child.getUpdatedAt());
	}

	private static String name(Enum<?> value) {
		return value == null ? null : value.name();
	}

}
