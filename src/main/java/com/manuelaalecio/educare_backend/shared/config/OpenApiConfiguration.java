package com.manuelaalecio.educare_backend.shared.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata of the OpenAPI document, published only where springdoc is enabled (the {@code dev} profile) and
 * versioned as the API contract in {@code openapi/educare-api.json}. Declares the bearer token so that the Swagger UI
 * can send it in the protected routes, a relative server so that the document carries no address of any environment,
 * and the {@code ProblemDetail} schema of every error response.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

	private static final String BEARER_SCHEME = "bearer-jwt";

	@Bean
	OpenAPI openApi() {
		return new OpenAPI()
			.info(new Info().title("Educare API").version("v1"))
			.servers(List.of(new Server().url("/")))
			.components(new Components()
				.addSecuritySchemes(BEARER_SCHEME,
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
				.addSchemas(ProblemResponses.PROBLEM_DETAIL_SCHEMA, problemDetailSchema())
				.addSchemas(ProblemResponses.FIELD_ERROR_SCHEMA, fieldErrorSchema()))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
	}

	@Bean
	UnauthorizedResponseCustomizer unauthorizedResponseCustomizer() {
		return new UnauthorizedResponseCustomizer();
	}

	@Bean
	RequiredResponsePropertiesCustomizer requiredResponsePropertiesCustomizer(BeanFactory beanFactory) {
		return new RequiredResponsePropertiesCustomizer(AutoConfigurationPackages.get(beanFactory));
	}

	/**
	 * RFC 9457 body of every error, as written by the {@code GlobalExceptionHandler} and the security handlers.
	 * Only {@code title} and {@code status} always come: {@code type} is left out when it is {@code about:blank}
	 * (its meaning when absent, RFC 9457), {@code detail} and {@code instance} may be absent, and {@code errors}
	 * comes only with invalid fields.
	 */
	private static Schema<?> problemDetailSchema() {
		return new ObjectSchema()
			.addProperty("type", new StringSchema().format("uri"))
			.addProperty("title", new StringSchema())
			.addProperty("status", new IntegerSchema())
			.addProperty("detail", new StringSchema())
			.addProperty("instance", new StringSchema().format("uri"))
			.addProperty("errors", new ArraySchema().items(new Schema<>().$ref(ProblemResponses.FIELD_ERROR_REF)))
			.required(List.of("title", "status"));
	}

	private static Schema<?> fieldErrorSchema() {
		return new ObjectSchema()
			.addProperty("field", new StringSchema())
			.addProperty("message", new StringSchema())
			.required(List.of("field", "message"));
	}

}
