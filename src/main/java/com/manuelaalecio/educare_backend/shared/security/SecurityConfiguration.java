package com.manuelaalecio.educare_backend.shared.security;

import java.time.Duration;
import java.util.List;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless bearer token authentication for the whole API (designs D5 and D6 of the add-authentication change).
 * Only the login and the health are public; roles are checked with {@code @PreAuthorize} in each module. The
 * {@code 401} and {@code 403} responses come from {@link ProblemDetailSecurityHandlers}, also for refusals raised
 * after the filter chain (e.g. by {@code @PreAuthorize}).
 * <p>
 * Only in a servlet application, where {@link HttpSecurity} exists: a context started without a web server (e.g.
 * to check the migrations at startup) has no requests to protect.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableMethodSecurity
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource,
			AuthenticatedUserConverter authenticatedUserConverter, ProblemDetailSecurityHandlers handlers) {
		return http
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			// answers the preflight before the authentication
			.cors(cors -> cors.configurationSource(corsConfigurationSource))
			.authorizeHttpRequests(requests -> requests
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				// so that the error page of an authenticated request (e.g. a 404) does not turn into a 401
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				// a missing route also gets a 401, without revealing whether it exists
				.anyRequest().authenticated())
			.oauth2ResourceServer(resourceServer -> resourceServer
				.jwt(jwt -> jwt.jwtAuthenticationConverter(authenticatedUserConverter))
				.authenticationEntryPoint(handlers)
				.accessDeniedHandler(handlers))
			// the default entry point would tell the refusal reason in WWW-Authenticate (error_description)
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(handlers)
				.accessDeniedHandler(handlers))
			.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.cors().allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
		// the frontend reads the id of a created resource from Location
		configuration.setExposedHeaders(List.of(HttpHeaders.LOCATION));
		// the token is sent in a header, not in a cookie
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(Duration.ofHours(1));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

}
