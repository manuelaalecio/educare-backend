package com.manuelaalecio.educare_backend.child.domain;

import java.util.Locale;

/**
 * Brazilian identity card number (RG). Its format varies by state, so it is only normalized: any punctuation is
 * accepted, and it is stored with only the (ASCII) letters and digits, letters in upper case
 * ({@code 12.345.678-x} becomes {@code 12345678X}).
 */
public final class Rg {

	private Rg() {
	}

	/**
	 * @return the RG with only letters and digits in upper case (empty when it has none), or {@code null} for
	 * {@code null}
	 */
	public static String normalize(String rg) {
		return rg == null ? null : rg.replaceAll("[^0-9A-Za-z]", "").toUpperCase(Locale.ROOT);
	}

}
