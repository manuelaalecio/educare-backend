package com.manuelaalecio.educare_backend.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Declares the {@code 429} of every operation that requires a token: the limit of requests per user applies to all of
 * them, from an MVC interceptor out of springdoc's reach. The public operations (as the login) do not get it. Uses
 * the same criterion as the {@link UnauthorizedResponseCustomizer}.
 */
public class TooManyRequestsResponseCustomizer implements OpenApiCustomizer {

	static final String TOO_MANY_REQUESTS = "429";

	@Override
	public void customise(OpenAPI openApi) {
		if (openApi.getPaths() == null) {
			return;
		}
		for (PathItem pathItem : openApi.getPaths().values()) {
			for (Operation operation : pathItem.readOperations()) {
				if (ProblemResponses.requiresToken(openApi, operation)
						&& !operation.getResponses().containsKey(TOO_MANY_REQUESTS)) {
					operation.getResponses().addApiResponse(TOO_MANY_REQUESTS,
							ProblemResponses.problemResponse("Too Many Requests"));
				}
			}
		}
	}

}
