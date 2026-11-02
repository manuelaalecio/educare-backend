package com.manuelaalecio.educare_backend.child.domain;

import java.util.regex.Pattern;

/**
 * Registration number (matrícula) of a birth certificate: 32 digits, accepted with dots, spaces and hyphens between
 * them, and stored with digits only. Only the length is required: check digits, book type and registration year are
 * not verified.
 */
public final class BirthRegistrationNumber {

	public static final int LENGTH = 32;

	private static final Pattern SEPARATORS = Pattern.compile("[.\\s-]");

	private static final Pattern DIGITS = Pattern.compile("^[0-9]{" + LENGTH + "}$");

	private BirthRegistrationNumber() {
	}

	/**
	 * @return the number without dots, spaces and hyphens (other characters are kept), or {@code null} for
	 * {@code null}
	 */
	public static String normalize(String number) {
		return number == null ? null : SEPARATORS.matcher(number).replaceAll("");
	}

	/**
	 * @return whether the number has exactly 32 digits once the separators are removed; {@code false} for
	 * {@code null}
	 */
	public static boolean isValid(String number) {
		return number != null && DIGITS.matcher(normalize(number)).matches();
	}

}
