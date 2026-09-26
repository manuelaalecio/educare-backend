package com.manuelaalecio.educare_backend.auth.api.dto;

/**
 * Result of a successful login, as in OAuth2.
 *
 * @param tokenType always {@code Bearer}
 * @param expiresIn validity of the token, in seconds
 */
public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {
}
