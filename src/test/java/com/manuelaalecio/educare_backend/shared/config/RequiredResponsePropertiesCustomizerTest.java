package com.manuelaalecio.educare_backend.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequiredResponsePropertiesCustomizerTest {

	private final RequiredResponsePropertiesCustomizer customizer = new RequiredResponsePropertiesCustomizer(
			List.of(RequiredResponsePropertiesCustomizerTest.class.getPackageName()));

	private OpenAPI openApi;

	@BeforeEach
	void setUp() {
		openApi = new OpenAPI().components(new Components()
			.addSchemas("SampleResponse", new ObjectSchema()
				.addProperty("id", new StringSchema().format("uuid"))
				.addProperty("name", new StringSchema())
				.addProperty("nickname", new StringSchema())
				.addProperty("items", new ArraySchema().items(ref("SampleItem"))))
			.addSchemas("SampleItem", new ObjectSchema().addProperty("label", new StringSchema()))
			.addSchemas("SampleRequest", new ObjectSchema()
				.addProperty("name", new StringSchema())
				.addProperty("nickname", new StringSchema())
				.required(List.of("name")))
			.addSchemas("SharedPayload", new ObjectSchema().addProperty("value", new StringSchema()))
			.addSchemas("PagedModelSample", new ObjectSchema()
				.addProperty("content", new ArraySchema().items(ref("SampleResponse")))
				.addProperty("page", new ObjectSchema()))
			.addSchemas(ProblemResponses.PROBLEM_DETAIL_SCHEMA, new ObjectSchema()
				.addProperty("title", new StringSchema())
				.addProperty("detail", new StringSchema())
				.required(List.of("title"))))
			.paths(new Paths()
				.addPathItem("/samples", new PathItem()
					.get(new Operation().responses(responses("PagedModelSample")))
					.post(new Operation()
						.requestBody(new RequestBody().content(content("SampleRequest")))
						.responses(responses("SampleResponse"))))
				.addPathItem("/shared", new PathItem()
					.put(new Operation()
						.requestBody(new RequestBody().content(content("SharedPayload")))
						.responses(responses("SharedPayload")))));
	}

	@Test
	void shouldRequireEveryPropertyExceptNullableWhenSchemaIsOnlyUsedInResponses() {
		// when
		customizer.customise(openApi);

		// then
		assertThat(schema("SampleResponse").getRequired()).containsExactlyInAnyOrder("id", "name", "items");
	}

	@Test
	void shouldRequireNestedSchemasWhenReachedOnlyFromResponses() {
		// when
		customizer.customise(openApi);

		// then
		assertThat(schema("SampleItem").getRequired()).containsExactly("label");
	}

	@Test
	void shouldRequireEveryPropertyWhenSchemaHasNoMatchingRecord() {
		// when
		customizer.customise(openApi);

		// then
		assertThat(schema("PagedModelSample").getRequired()).containsExactlyInAnyOrder("content", "page");
	}

	@Test
	void shouldKeepRequestSchemaUntouchedWhenUsedInRequestBody() {
		// when
		customizer.customise(openApi);

		// then
		assertThat(schema("SampleRequest").getRequired()).containsExactly("name");
	}

	@Test
	void shouldKeepSchemaUntouchedWhenUsedInBothRequestAndResponse() {
		// when
		customizer.customise(openApi);

		// then
		assertThat(schema("SharedPayload").getRequired()).isNull();
	}

	@Test
	void shouldKeepHandWrittenProblemDetailUntouchedWhenUsedInResponses() {
		// given
		openApi.getPaths().get("/samples").getGet().getResponses()
			.addApiResponse("400", new ApiResponse().content(content(ProblemResponses.PROBLEM_DETAIL_SCHEMA)));

		// when
		customizer.customise(openApi);

		// then
		assertThat(schema(ProblemResponses.PROBLEM_DETAIL_SCHEMA).getRequired()).containsExactly("title");
	}

	@Test
	void shouldDoNothingWhenDocumentHasNoPaths() {
		// given
		OpenAPI empty = new OpenAPI().components(new Components()
			.addSchemas("SampleResponse", new ObjectSchema().addProperty("id", new StringSchema())));

		// when
		customizer.customise(empty);

		// then
		assertThat(empty.getComponents().getSchemas().get("SampleResponse").getRequired()).isNull();
	}

	@SuppressWarnings("rawtypes")
	private Schema schema(String name) {
		return openApi.getComponents().getSchemas().get(name);
	}

	private static Schema<?> ref(String name) {
		return new Schema<>().$ref("#/components/schemas/" + name);
	}

	private static Content content(String schema) {
		return new Content().addMediaType("application/json", new MediaType().schema(ref(schema)));
	}

	private static ApiResponses responses(String schema) {
		return new ApiResponses().addApiResponse("200", new ApiResponse().content(content(schema)));
	}

	/** Matched by name with the {@code SampleResponse} schema; {@code nickname} may be absent. */
	record SampleResponse(UUID id, String name, @Nullable String nickname, List<SampleItem> items) {
	}

	record SampleItem(String label) {
	}

}
