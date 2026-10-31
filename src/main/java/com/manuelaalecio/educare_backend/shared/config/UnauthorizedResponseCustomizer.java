package com.manuelaalecio.educare_backend.shared.config;

import java.util.List;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Declares the {@code 401} of every operation that requires a token: the refusal comes from the security filter
 * chain, out of springdoc's reach. An operation without its own {@code security} inherits the document's one; an
 * empty list (as in the login) makes it public.
 */
public class UnauthorizedResponseCustomizer implements OpenApiCustomizer {

	static final String UNAUTHORIZED = "401";

	@Override
	public void customise(OpenAPI openApi) {
		if (openApi.getPaths() == null) {
			return;
		}
		for (PathItem pathItem : openApi.getPaths().values()) {
			for (Operation operation : pathItem.readOperations()) {
				if (requiresToken(openApi, operation) && !operation.getResponses().containsKey(UNAUTHORIZED)) {
					operation.getResponses().addApiResponse(UNAUTHORIZED,
							ProblemResponses.problemResponse("Unauthorized"));
				}
			}
		}
	}

	private static boolean requiresToken(OpenAPI openApi, Operation operation) {
		List<SecurityRequirement> security = operation.getSecurity() != null ? operation.getSecurity()
				: openApi.getSecurity();
		return security != null && !security.isEmpty();
	}

}
