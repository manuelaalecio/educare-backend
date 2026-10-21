package com.manuelaalecio.educare_backend.shared.config;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;

/**
 * Marks as {@code required} every property of the schemas used only in responses, except the record components
 * annotated with {@link Nullable}: a response field is always filled unless its DTO says otherwise, so a forgotten
 * annotation yields a stricter type in the client, never a silently optional one. The schemas used in requests keep
 * the {@code required} that springdoc derives from Bean Validation, and the schemas written by hand in
 * {@link OpenApiConfiguration} keep their own.
 * <p>
 * The Java class of a schema is found by its simple name among the records of the application packages (as springdoc
 * names them); a schema without a matching record (e.g. {@code PagedModelUserResponse}) has every property required.
 */
public class RequiredResponsePropertiesCustomizer implements OpenApiCustomizer {

	private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

	private static final Set<String> HAND_WRITTEN_SCHEMAS = Set.of(ProblemResponses.PROBLEM_DETAIL_SCHEMA,
			ProblemResponses.FIELD_ERROR_SCHEMA);

	private final Collection<String> basePackages;

	public RequiredResponsePropertiesCustomizer(Collection<String> basePackages) {
		this.basePackages = List.copyOf(basePackages);
	}

	@Override
	@SuppressWarnings("rawtypes")
	public void customise(OpenAPI openApi) {
		Map<String, Schema> schemas = openApi.getComponents() != null ? openApi.getComponents().getSchemas() : null;
		if (schemas == null || openApi.getPaths() == null) {
			return;
		}
		Set<String> requestSchemas = new HashSet<>();
		Set<String> responseSchemas = new HashSet<>();
		for (PathItem pathItem : openApi.getPaths().values()) {
			for (Operation operation : pathItem.readOperations()) {
				if (operation.getRequestBody() != null) {
					collect(operation.getRequestBody().getContent(), schemas, requestSchemas);
				}
				if (operation.getParameters() != null) {
					for (Parameter parameter : operation.getParameters()) {
						collect(parameter.getSchema(), schemas, requestSchemas);
						collect(parameter.getContent(), schemas, requestSchemas);
					}
				}
				if (operation.getResponses() != null) {
					for (ApiResponse response : operation.getResponses().values()) {
						collect(response.getContent(), schemas, responseSchemas);
					}
				}
			}
		}
		responseSchemas.removeAll(requestSchemas);
		responseSchemas.removeAll(HAND_WRITTEN_SCHEMAS);
		if (responseSchemas.isEmpty()) {
			return;
		}
		Map<String, Class<?>> records = applicationRecords();
		for (String name : responseSchemas) {
			markRequired(schemas.get(name), nullableComponents(records.get(name)));
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void markRequired(Schema schema, Set<String> nullable) {
		if (schema == null || schema.getProperties() == null) {
			return;
		}
		List<String> current = schema.getRequired() != null ? schema.getRequired() : List.of();
		List<String> required = ((Set<String>) schema.getProperties().keySet()).stream()
			.filter(property -> current.contains(property) || !nullable.contains(property))
			.toList();
		schema.setRequired(required.isEmpty() ? null : required);
	}

	private static Set<String> nullableComponents(Class<?> type) {
		if (type == null || !type.isRecord()) {
			return Set.of();
		}
		return Arrays.stream(type.getRecordComponents())
			.filter(component -> component.getAnnotatedType().isAnnotationPresent(Nullable.class))
			.map(RecordComponent::getName)
			.collect(Collectors.toSet());
	}

	@SuppressWarnings("rawtypes")
	private static void collect(Content content, Map<String, Schema> schemas, Set<String> found) {
		if (content != null) {
			content.values().forEach(mediaType -> collect(mediaType.getSchema(), schemas, found));
		}
	}

	/** Adds to {@code found} every component schema referenced, directly or transitively, by {@code schema}. */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void collect(Schema<?> schema, Map<String, Schema> schemas, Set<String> found) {
		if (schema == null) {
			return;
		}
		String ref = schema.get$ref();
		if (ref != null) {
			if (ref.startsWith(SCHEMA_REF_PREFIX)) {
				String name = ref.substring(SCHEMA_REF_PREFIX.length());
				if (found.add(name)) {
					collect(schemas.get(name), schemas, found);
				}
			}
			return;
		}
		if (schema.getProperties() != null) {
			schema.getProperties().values().forEach(property -> collect(property, schemas, found));
		}
		collect(schema.getItems(), schemas, found);
		collect(schema.getNot(), schemas, found);
		if (schema.getAdditionalProperties() instanceof Schema additional) {
			collect(additional, schemas, found);
		}
		for (List<Schema> composed : Arrays.asList(schema.getAllOf(), schema.getAnyOf(), schema.getOneOf())) {
			if (composed != null) {
				composed.forEach(part -> collect(part, schemas, found));
			}
		}
	}

	/** The records of the application packages by simple name, which is how springdoc names their schemas. */
	private Map<String, Class<?>> applicationRecords() {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter((reader, factory) -> Record.class.getName()
			.equals(reader.getClassMetadata().getSuperClassName()));
		Map<String, Class<?>> records = new HashMap<>();
		ClassLoader classLoader = getClass().getClassLoader();
		for (String basePackage : basePackages) {
			scanner.findCandidateComponents(basePackage).forEach(candidate -> {
				Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), classLoader);
				records.put(type.getSimpleName(), type);
			});
		}
		return records;
	}

}
