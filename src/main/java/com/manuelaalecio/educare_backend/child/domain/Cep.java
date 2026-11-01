package com.manuelaalecio.educare_backend.child.domain;

import java.util.regex.Pattern;

/**
 * Brazilian postal code (CEP): 8 digits, accepted with or without the hyphen after the fifth digit
 * ({@code 01310-100} or {@code 01310100}) and stored with digits only. Whether the CEP exists is not checked.
 */
public final class Cep {

	private static final Pattern FORMAT = Pattern.compile("^[0-9]{5}-?[0-9]{3}$");

	private Cep() {
	}

	/**
	 * @return the CEP with digits only, or {@code null} for {@code null}
	 */
	public static String normalize(String cep) {
		return cep == null ? null : cep.replace("-", "");
	}

	/**
	 * @return whether the CEP has one of the accepted formats; {@code false} for {@code null}
	 */
	public static boolean isValid(String cep) {
		return cep != null && FORMAT.matcher(cep).matches();
	}

}
