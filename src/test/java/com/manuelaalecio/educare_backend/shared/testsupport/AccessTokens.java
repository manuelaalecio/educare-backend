package com.manuelaalecio.educare_backend.shared.testsupport;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import com.manuelaalecio.educare_backend.shared.security.AccessTokenIssuer;
import com.manuelaalecio.educare_backend.shared.security.SecurityProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Real access tokens for tests, issued by the {@link AccessTokenIssuer} of the context, so requests go through the
 * actual bearer token filter (design D10 of the add-authentication change).
 * <p>
 * In a {@code @WebMvcTest}, import it with the security configuration and a {@link Clock}, and mock the lookup for
 * the ids used:
 *
 * <pre>
 * &#64;WebMvcTest(SomeController.class)
 * &#64;Import({SecurityConfiguration.class, JwtConfiguration.class, ClockConfiguration.class, AccessTokens.class, ...})
 * class SomeControllerTest {
 *
 * 	&#64;MockitoBean
 * 	AuthenticatedUserLookup lookup;
 *
 * 	&#64;Autowired
 * 	AccessTokens accessTokens;
 *
 * 	// given(lookup.findById(ID)).willReturn(Optional.of(new AuthenticatedUser(ID, "ADMIN")));
 * 	// mockMvc.perform(get("/api/v1/...").header(HttpHeaders.AUTHORIZATION, accessTokens.bearer(ID)))
 * }
 * </pre>
 *
 * In a {@code @SpringBootTest}, {@code @Import(AccessTokens.class)} is enough. Tokens follow the context
 * {@link Clock}, so with {@code MutableClockConfiguration} they are issued at the instant the test sets.
 */
@TestComponent
public class AccessTokens {

	/** A valid HS256 key that is not the one configured in the application. */
	private static final String OTHER_SECRET = "another-key-not-configured-in-the-application";

	private final AccessTokenIssuer issuer;
	private final AccessTokenIssuer otherKeyIssuer;

	public AccessTokens(AccessTokenIssuer issuer, SecurityProperties properties, Clock clock) {
		this.issuer = issuer;
		NimbusJwtEncoder otherKeyEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(
			new SecretKeySpec(OTHER_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
		this.otherKeyIssuer = new AccessTokenIssuer(otherKeyEncoder, properties.jwt().expiration(), clock);
	}

	/** The raw token of the user, as returned by the login. */
	public String forUser(UUID userId) {
		return issuer.issue(userId).value();
	}

	/** The {@code Authorization} header value for the user: {@code Bearer <token>}. */
	public String bearer(UUID userId) {
		return "Bearer " + forUser(userId);
	}

	/** A token valid in every way (issuer, subject, dates) except that it is signed with another key. */
	public String signedWithAnotherKey(UUID userId) {
		return otherKeyIssuer.issue(userId).value();
	}

}
