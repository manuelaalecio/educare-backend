package com.manuelaalecio.educare_backend.child.api;

import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.BIRTH_CERTIFICATE_NUMBER;
import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.aChild;
import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.aCompleteChild;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.manuelaalecio.educare_backend.child.api.dto.ChildClassroomResponse;
import com.manuelaalecio.educare_backend.child.api.dto.ChildGuardianRequest;
import com.manuelaalecio.educare_backend.child.api.dto.ChildGuardianResponse;
import com.manuelaalecio.educare_backend.child.api.dto.ChildRequest;
import com.manuelaalecio.educare_backend.child.api.dto.ChildResponse;
import com.manuelaalecio.educare_backend.child.api.dto.ChildSummaryResponse;
import com.manuelaalecio.educare_backend.child.api.dto.ClassroomRefResponse;
import com.manuelaalecio.educare_backend.child.api.dto.NewGuardianRequest;
import com.manuelaalecio.educare_backend.child.application.ChildClassroomView;
import com.manuelaalecio.educare_backend.child.application.ChildGuardianCommand;
import com.manuelaalecio.educare_backend.child.application.ChildGuardianView;
import com.manuelaalecio.educare_backend.child.application.ChildSummaryView;
import com.manuelaalecio.educare_backend.child.application.ChildView;
import com.manuelaalecio.educare_backend.child.domain.BrazilianState;
import com.manuelaalecio.educare_backend.child.domain.Child;
import com.manuelaalecio.educare_backend.child.domain.ChildClassroom;
import com.manuelaalecio.educare_backend.child.domain.ChildDetails;
import com.manuelaalecio.educare_backend.child.domain.Gender;
import com.manuelaalecio.educare_backend.child.domain.MaritalStatus;
import com.manuelaalecio.educare_backend.child.domain.Race;
import com.manuelaalecio.educare_backend.child.domain.Relationship;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomRef;
import com.manuelaalecio.educare_backend.guardian.application.GuardianContact;
import com.manuelaalecio.educare_backend.guardian.application.GuardianInput;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ChildMapperTest {

	private static final UUID ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
	private static final UUID TURMA_1 = UUID.fromString("0190f4a2-0000-7000-8000-0000000000c1");
	private static final ClassroomRef TURMA_1_REF = new ClassroomRef(TURMA_1, "Turma 1");
	private static final ClassroomRefResponse TURMA_1_RESPONSE = new ClassroomRefResponse(TURMA_1, "Turma 1");
	private static final Instant CREATED_AT = Instant.parse("2026-03-01T10:00:00Z");
	private static final Instant UPDATED_AT = Instant.parse("2026-03-02T09:00:00Z");
	private static final UUID MARIA_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d1");
	private static final UUID CARLOS_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d3");
	private static final GuardianContact MARIA = new GuardianContact(MARIA_ID, "Maria Souza", "11912345678", false,
			"Padaria Central", "1133334444");
	private static final GuardianContact CARLOS = new GuardianContact(CARLOS_ID, "Carlos Souza", "11966665555", true,
			null, null);

	private final ChildMapper mapper = new ChildMapper();

	@Test
	void shouldConvertEveryFieldWhenMappingCompleteRequest() {
		// given
		var request = new ChildRequest("Maria", LocalDate.of(2018, 5, 10), "01310-100", "Avenida Paulista",
				"Bela Vista", "1000", "Apto 12", "São Paulo", "SP", "São Paulo - SP", "BROWN", "FEMALE",
				"529.982.247-25", "12.345.678-9", "Brasileira", "STABLE_UNION", "Católica", "Paróquia", "Asma",
				"Salbutamol", "Poeira", BIRTH_CERTIFICATE_NUMBER, TURMA_1, List.of());

		// when
		ChildDetails details = mapper.toDetails(request);

		// then
		assertThat(details).isEqualTo(new ChildDetails("Maria", LocalDate.of(2018, 5, 10), "01310-100",
				"Avenida Paulista", "Bela Vista", "1000", "Apto 12", "São Paulo", BrazilianState.SP, "São Paulo - SP",
				Race.BROWN, Gender.FEMALE, "529.982.247-25", "12.345.678-9", "Brasileira",
				MaritalStatus.STABLE_UNION, "Católica", "Paróquia", "Asma", "Salbutamol", "Poeira",
				BIRTH_CERTIFICATE_NUMBER));
	}

	@Test
	void shouldKeepMissingEnumsAsNullWhenMappingRequest() {
		// given
		var request = new ChildRequest("Maria", LocalDate.of(2018, 5, 10), "01310100", "Avenida", "Bairro", "1",
				null, "Cidade", null, null, null, null, null, null, null, null, null, null, null, null, null, null,
				TURMA_1, List.of());

		// when
		ChildDetails details = mapper.toDetails(request);

		// then
		assertThat(details.state()).isNull();
		assertThat(details.race()).isNull();
		assertThat(details.gender()).isNull();
		assertThat(details.maritalStatus()).isNull();
	}

	@Test
	void shouldConvertEveryFieldAndClassroomWhenMappingCompleteChild() {
		// given
		Child child = stored(new Child(aCompleteChild().build()));

		// when
		ChildResponse response = mapper.toResponse(new ChildView(child, TURMA_1_REF,
				List.of(new ChildGuardianView(CARLOS, Relationship.GRANDFATHER),
						new ChildGuardianView(MARIA, Relationship.MOTHER))));

		// then
		assertThat(response).isEqualTo(new ChildResponse(ID, "Maria Clara Souza", LocalDate.of(2018, 5, 10),
				TURMA_1_RESPONSE,
				List.of(new ChildGuardianResponse(CARLOS_ID, "Carlos Souza", "GRANDFATHER", "11966665555", true, null,
						null),
						new ChildGuardianResponse(MARIA_ID, "Maria Souza", "MOTHER", "11912345678", false,
								"Padaria Central", "1133334444")),
				"01310100", "Avenida Paulista", "Bela Vista", "1000", "Apto 12", "São Paulo", "SP",
				"São Paulo - SP", "BROWN", "FEMALE", "52998224725", "123456789", "Brasileira", "SINGLE", "Católica",
				"Paróquia São José", "Asma leve", "Salbutamol em crises", "Evitar poeira", BIRTH_CERTIFICATE_NUMBER,
				CREATED_AT, UPDATED_AT));
	}

	@Test
	void shouldReturnNullOptionalsWhenMappingChildWithRequiredDataOnly() {
		// when
		ChildResponse response = mapper
			.toResponse(new ChildView(stored(new Child(aChild().build())), TURMA_1_REF, List.of()));

		// then
		assertThat(response.race()).isNull();
		assertThat(response.gender()).isNull();
		assertThat(response.maritalStatus()).isNull();
		assertThat(response.cpf()).isNull();
		assertThat(response.birthCertificateNumber()).isNull();
		assertThat(response.state()).isEqualTo("SP");
		assertThat(response.guardians()).isEmpty();
	}

	@Test
	void shouldConvertGuardiansInOrderWhenMappingRequest() {
		// given
		var joao = new NewGuardianRequest("João Souza", "MARRIED", LocalDate.of(1985, 1, 2), "52998224725", "123",
				"11955554444", null, "Oficina", "Rua B, 2", "1144445555");
		var request = new ChildRequest("Maria", LocalDate.of(2018, 5, 10), "01310100", "Avenida", "Bairro", "1",
				null, "Cidade", "SP", null, null, null, null, null, null, null, null, null, null, null, null, null,
				TURMA_1, List.of(new ChildGuardianRequest(MARIA_ID, null, "MOTHER"),
						new ChildGuardianRequest(null, joao, "FATHER")));

		// when
		List<ChildGuardianCommand> commands = mapper.toGuardianCommands(request);

		// then
		assertThat(commands).containsExactly(ChildGuardianCommand.existing(MARIA_ID, Relationship.MOTHER),
				ChildGuardianCommand.newGuardian(new GuardianInput("João Souza", "MARRIED", LocalDate.of(1985, 1, 2),
						"52998224725", "123", "11955554444", null, "Oficina", "Rua B, 2", "1144445555"),
						Relationship.FATHER));
	}

	@Test
	void shouldReturnNoGuardiansWhenRequestHasNone() {
		// given
		var request = new ChildRequest("Maria", LocalDate.of(2018, 5, 10), "01310100", "Avenida", "Bairro", "1",
				null, "Cidade", "SP", null, null, null, null, null, null, null, null, null, null, null, null, null,
				TURMA_1, null);

		// when / then
		assertThat(mapper.toGuardianCommands(request)).isEmpty();
	}

	@Test
	void shouldKeepOnlyListFieldsAndClassroomWhenMappingSummary() {
		// when
		ChildSummaryResponse summary = mapper
			.toSummary(new ChildSummaryView(stored(new Child(aCompleteChild().build())), TURMA_1_REF));

		// then
		assertThat(summary).isEqualTo(new ChildSummaryResponse(ID, "Maria Clara Souza", LocalDate.of(2018, 5, 10),
				TURMA_1_RESPONSE, CREATED_AT, UPDATED_AT));
	}

	@Test
	void shouldMapCurrentClassroomWithoutEndDateWhenMappingHistory() {
		// given
		ChildClassroom current = ChildClassroom.open(ID, TURMA_1, LocalDate.of(2026, 11, 1));

		// when
		ChildClassroomResponse item = mapper.toHistoryItem(new ChildClassroomView(current, TURMA_1_REF));

		// then
		assertThat(item).isEqualTo(new ChildClassroomResponse(TURMA_1, "Turma 1", LocalDate.of(2026, 11, 1), null));
	}

	@Test
	void shouldMapEndDateWhenMappingEndedClassroom() {
		// given
		ChildClassroom ended = ChildClassroom.open(ID, TURMA_1, LocalDate.of(2026, 10, 1));
		ended.close(LocalDate.of(2026, 11, 1));

		// when
		ChildClassroomResponse item = mapper.toHistoryItem(new ChildClassroomView(ended, TURMA_1_REF));

		// then
		assertThat(item).isEqualTo(new ChildClassroomResponse(TURMA_1, "Turma 1", LocalDate.of(2026, 10, 1),
				LocalDate.of(2026, 11, 1)));
	}

	private static Child stored(Child child) {
		ReflectionTestUtils.setField(child, "id", ID);
		ReflectionTestUtils.setField(child, "createdAt", CREATED_AT);
		ReflectionTestUtils.setField(child, "updatedAt", UPDATED_AT);
		return child;
	}

}
