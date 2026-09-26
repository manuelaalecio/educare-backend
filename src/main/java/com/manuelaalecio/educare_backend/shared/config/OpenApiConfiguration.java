package com.manuelaalecio.educare_backend.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata of the OpenAPI document, published only where springdoc is enabled (the {@code dev} profile). Declares
 * the bearer token so that the Swagger UI can send it in the protected routes.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

	private static final String BEARER_SCHEME = "bearer-jwt";

	@Bean
	OpenAPI openApi() {
		return new OpenAPI()
			.info(new Info().title("Educare API").version("v1"))
			.components(new Components().addSecuritySchemes(BEARER_SCHEME,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
	}

}
