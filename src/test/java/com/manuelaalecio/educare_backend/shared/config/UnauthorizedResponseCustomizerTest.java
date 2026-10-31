package com.manuelaalecio.educare_backend.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.junit.jupiter.api.Test;

class UnauthorizedResponseCustomizerTest {

	private final UnauthorizedResponseCustomizer customizer = new UnauthorizedResponseCustomizer();

	private final Operation protectedOperation = new Operation().responses(ok());

	private final Operation publicOperation = new Operation().security(List.of()).responses(ok());

	@Test
	void shouldAddProblemDetailUnauthorizedWhenOperationInheritsDocumentSecurity() {
		// given
		OpenAPI openApi = document().addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));

		// when
		customizer.customise(openApi);

		// then
		ApiResponse unauthorized = protectedOperation.getResponses().get("401");
		assertThat(unauthorized).isNotNull();
		assertThat(unauthorized.getContent()).containsOnlyKeys("application/problem+json");
		assertThat(unauthorized.getContent().get("application/problem+json").getSchema().get$ref())
			.isEqualTo("#/components/schemas/ProblemDetail");
	}

	@Test
	void shouldNotAddUnauthorizedWhenOperationIsPublic() {
		// given
		OpenAPI openApi = document().addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));

		// when
		customizer.customise(openApi);

		// then
		assertThat(publicOperation.getResponses()).doesNotContainKey("401");
	}

	@Test
	void shouldAddUnauthorizedWhenOperationDeclaresItsOwnSecurity() {
		// given
		Operation operation = new Operation().addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
			.responses(ok());
		OpenAPI openApi = new OpenAPI().paths(new Paths().addPathItem("/own", new PathItem().get(operation)));

		// when
		customizer.customise(openApi);

		// then
		assertThat(operation.getResponses()).containsKey("401");
	}

	@Test
	void shouldNotAddUnauthorizedWhenDocumentHasNoSecurity() {
		// given
		OpenAPI openApi = document();

		// when
		customizer.customise(openApi);

		// then
		assertThat(protectedOperation.getResponses()).doesNotContainKey("401");
	}

	@Test
	void shouldKeepDeclaredUnauthorizedWhenOperationAlreadyHasOne() {
		// given
		ApiResponse declared = new ApiResponse().description("Invalid credentials");
		protectedOperation.getResponses().addApiResponse("401", declared);
		OpenAPI openApi = document().addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));

		// when
		customizer.customise(openApi);

		// then
		assertThat(protectedOperation.getResponses().get("401")).isSameAs(declared);
	}

	@Test
	void shouldDoNothingWhenDocumentHasNoPaths() {
		// given
		OpenAPI openApi = new OpenAPI().addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));

		// when
		customizer.customise(openApi);

		// then
		assertThat(openApi.getPaths()).isNull();
	}

	private OpenAPI document() {
		return new OpenAPI().paths(new Paths()
			.addPathItem("/protected", new PathItem().get(protectedOperation))
			.addPathItem("/public", new PathItem().post(publicOperation)));
	}

	private static ApiResponses ok() {
		return new ApiResponses().addApiResponse("200", new ApiResponse().description("OK"));
	}

}
