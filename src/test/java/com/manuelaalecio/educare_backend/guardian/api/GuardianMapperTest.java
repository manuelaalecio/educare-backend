package com.manuelaalecio.educare_backend.guardian.api;

import static com.manuelaalecio.educare_backend.guardian.GuardianDetailsBuilder.aCompleteGuardian;
import static com.manuelaalecio.educare_backend.guardian.GuardianDetailsBuilder.aGuardian;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.manuelaalecio.educare_backend.guardian.api.dto.GuardianChildResponse;
import com.manuelaalecio.educare_backend.guardian.api.dto.GuardianRequest;
import com.manuelaalecio.educare_backend.guardian.api.dto.GuardianResponse;
import com.manuelaalecio.educare_backend.guardian.api.dto.GuardianSummaryResponse;
import com.manuelaalecio.educare_backend.guardian.application.GuardianChildRef;
import com.manuelaalecio.educare_backend.guardian.application.GuardianInput;
import com.manuelaalecio.educare_backend.guardian.application.GuardianSummaryView;
import com.manuelaalecio.educare_backend.guardian.application.GuardianView;
import com.manuelaalecio.educare_backend.guardian.domain.Guardian;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GuardianMapperTest {

	private static final UUID ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d1");
	private static final UUID ANA_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000c1");
	private static final Instant CREATED_AT = Instant.parse("2026-03-01T10:00:00Z");
	private static final Instant UPDATED_AT = Instant.parse("2026-03-02T09:00:00Z");
	private static final GuardianChildRef ANA = new GuardianChildRef(ANA_ID, "Ana Souza", "MOTHER");

	private final GuardianMapper mapper = new GuardianMapper();

	@Test
	void shouldCopyEveryFieldWhenConvertingRequestToInput() {
		// given
		var request = new GuardianRequest("Maria Souza", "MARRIED", LocalDate.of(1990, 5, 10), "52998224725",
				"12345678X", "11912345678", false, "Padaria", "Rua A, 1", "1133334444");

		// when
		GuardianInput input = mapper.toInput(request);

		// then
		assertThat(input).isEqualTo(new GuardianInput("Maria Souza", "MARRIED", LocalDate.of(1990, 5, 10),
				"52998224725", "12345678X", "11912345678", false, "Padaria", "Rua A, 1", "1133334444"));
	}

	@Test
	void shouldReturnEveryFieldAndChildrenWhenConvertingFullGuardian() {
		// when
		GuardianResponse response = mapper.toResponse(new GuardianView(stored(new Guardian(aCompleteGuardian()
			.build())), List.of(ANA)));

		// then
		assertThat(response).isEqualTo(new GuardianResponse(ID, "Maria Souza", "MARRIED", LocalDate.of(1990, 5, 10),
				"52998224725", "12345678X", "11912345678", false, "Padaria Central", "Rua das Flores, 100",
				"1133334444", List.of(new GuardianChildResponse(ANA_ID, "Ana Souza", "MOTHER")), CREATED_AT,
				UPDATED_AT));
	}

	@Test
	void shouldReturnNullOptionalsWhenGuardianHasOnlyRequiredData() {
		// when
		GuardianResponse response = mapper.toResponse(new GuardianView(stored(new Guardian(aGuardian().notWorking(true)
			.build())), List.of()));

		// then
		assertThat(response).extracting(GuardianResponse::maritalStatus, GuardianResponse::birthDate,
				GuardianResponse::cpf, GuardianResponse::rg, GuardianResponse::workplace, GuardianResponse::workAddress,
				GuardianResponse::workPhone)
			.containsOnlyNulls();
		assertThat(response.notWorking()).isTrue();
		assertThat(response.children()).isEmpty();
	}

	@Test
	void shouldReturnOnlySummaryFieldsWhenConvertingListedGuardian() {
		// when
		GuardianSummaryResponse summary = mapper.toSummary(new GuardianSummaryView(stored(new Guardian(
				aCompleteGuardian().build())), List.of(ANA)));

		// then
		assertThat(summary).isEqualTo(new GuardianSummaryResponse(ID, "Maria Souza", "11912345678",
				List.of(new GuardianChildResponse(ANA_ID, "Ana Souza", "MOTHER"))));
	}

	private static Guardian stored(Guardian guardian) {
		ReflectionTestUtils.setField(guardian, "id", ID);
		ReflectionTestUtils.setField(guardian, "createdAt", CREATED_AT);
		ReflectionTestUtils.setField(guardian, "updatedAt", UPDATED_AT);
		return guardian;
	}

}
