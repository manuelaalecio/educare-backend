package com.manuelaalecio.educare_backend.shared.security;

/**
 * An access token just issued.
 *
 * @param value the signed JWT
 * @param expiresIn seconds until it expires, as in OAuth2
 */
public record IssuedAccessToken(String value, long expiresIn) {
}
