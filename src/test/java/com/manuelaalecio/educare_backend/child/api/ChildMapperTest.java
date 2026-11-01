package com.manuelaalecio.educare_backend.child.api;

import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.BIRTH_CERTIFICATE_NUMBER;
import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.aChild;
import static com.manuelaalecio.educare_backend.child.ChildDetailsBuilder.aCompleteChild;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.manuelaalecio.educare_backend.child.api.dto.ChildRequest;
import com.manuelaalecio.educare_backend.child.api.dto.ChildResponse;
import com.manuelaalecio.educare_backend.child.api.dto.ChildSummaryResponse;
import com.manuelaalecio.educare_backend.child.domain.BrazilianState;
import com.manuelaalecio.educare_backend.child.domain.Child;
import com.manuelaalecio.educare_backend.child.domain.ChildDetails;
import com.manuelaalecio.educare_backend.child.domain.Gender;
import com.manuelaalecio.educare_backend.child.domain.MaritalStatus;
import com.manuelaalecio.educare_backend.child.domain.Race;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ChildMapperTest {

	private static final UUID ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
	private static final Instant CREATED_AT = Instant.parse("2026-03-01T10:00:00Z");
	private static final Instant UPDATED_AT = Instant.parse("2026-03-02T09:00:00Z");

	private final ChildMapper mapper = new ChildMapper();

	@Test
	void shouldConvertEveryFieldWhenMappingCompleteRequest() {
		// given
		var request = new ChildRequest("Maria", LocalDate.of(2018, 5, 10), "01310-100", "Avenida Paulista",
				"Bela Vista", "1000", "Apto 12", "São Paulo", "SP", "São Paulo - SP", "BROWN", "FEMALE",
				"529.982.247-25", "12.345.678-9", "Brasileira", "STABLE_UNION", "Católica", "Paróquia", "Asma",
				"Salbutamol", "Poeira", BIRTH_CERTIFICATE_NUMBER);

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
				null, "Cidade", null, null, null, null, null, null, null, null, null, null, null, null, null, null);

		// when
		ChildDetails details = mapper.toDetails(request);

		// then
		assertThat(details.state()).isNull();
		assertThat(details.race()).isNull();
		assertThat(details.gender()).isNull();
		assertThat(details.maritalStatus()).isNull();
	}

	@Test
	void shouldConvertEveryFieldWhenMappingCompleteChild() {
		// given
		Child child = stored(new Child(aCompleteChild().build()));

		// when
		ChildResponse response = mapper.toResponse(child);

		// then
		assertThat(response).isEqualTo(new ChildResponse(ID, "Maria Clara Souza", LocalDate.of(2018, 5, 10),
				"01310100", "Avenida Paulista", "Bela Vista", "1000", "Apto 12", "São Paulo", "SP", "São Paulo - SP",
				"BROWN", "FEMALE", "52998224725", "123456789", "Brasileira", "SINGLE", "Católica",
				"Paróquia São José", "Asma leve", "Salbutamol em crises", "Evitar poeira", BIRTH_CERTIFICATE_NUMBER,
				CREATED_AT, UPDATED_AT));
	}

	@Test
	void shouldReturnNullOptionalsWhenMappingChildWithRequiredDataOnly() {
		// when
		ChildResponse response = mapper.toResponse(stored(new Child(aChild().build())));

		// then
		assertThat(response.race()).isNull();
		assertThat(response.gender()).isNull();
		assertThat(response.maritalStatus()).isNull();
		assertThat(response.cpf()).isNull();
		assertThat(response.birthCertificateNumber()).isNull();
		assertThat(response.state()).isEqualTo("SP");
	}

	@Test
	void shouldKeepOnlyListFieldsWhenMappingSummary() {
		// when
		ChildSummaryResponse summary = mapper.toSummary(stored(new Child(aCompleteChild().build())));

		// then
		assertThat(summary).isEqualTo(new ChildSummaryResponse(ID, "Maria Clara Souza", LocalDate.of(2018, 5, 10),
				CREATED_AT, UPDATED_AT));
	}

	private static Child stored(Child child) {
		ReflectionTestUtils.setField(child, "id", ID);
		ReflectionTestUtils.setField(child, "createdAt", CREATED_AT);
		ReflectionTestUtils.setField(child, "updatedAt", UPDATED_AT);
		return child;
	}

}
