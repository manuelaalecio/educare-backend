package com.manuelaalecio.educare_backend.child.domain;

import java.util.regex.Pattern;

/**
 * Brazilian individual taxpayer number (CPF): 11 digits, accepted without mask ({@code 52998224725}) or with the
 * full mask ({@code 529.982.247-25}), and stored with digits only.
 */
public final class Cpf {

	private static final Pattern FORMAT = Pattern.compile("^([0-9]{11}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2})$");

	private Cpf() {
	}

	/**
	 * @return the CPF with digits only, or {@code null} for {@code null}
	 */
	public static String normalize(String cpf) {
		return cpf == null ? null : cpf.replace(".", "").replace("-", "");
	}

	/**
	 * @return whether the CPF has one of the accepted formats, is not made of a single repeated digit and has
	 * both check digits right; {@code false} for {@code null}
	 */
	public static boolean isValid(String cpf) {
		if (cpf == null || !FORMAT.matcher(cpf).matches()) {
			return false;
		}
		String digits = normalize(cpf);
		if (digits.chars().distinct().count() == 1) {
			return false;
		}
		return checkDigit(digits, 9) == digits.charAt(9) - '0' && checkDigit(digits, 10) == digits.charAt(10) - '0';
	}

	/**
	 * Modulo 11 check digit of the first {@code length} digits, with weights from {@code length + 1} down to 2.
	 */
	private static int checkDigit(String digits, int length) {
		int sum = 0;
		for (int i = 0; i < length; i++) {
			sum += (digits.charAt(i) - '0') * (length + 1 - i);
		}
		int remainder = sum % 11;
		return remainder < 2 ? 0 : 11 - remainder;
	}

}
