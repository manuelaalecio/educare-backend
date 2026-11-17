package com.manuelaalecio.educare_backend.guardian.domain;

import org.springframework.data.jpa.domain.Specification;

/**
 * Filters of the guardians listing.
 */
public final class GuardianSpecifications {

	private GuardianSpecifications() {
	}

	/**
	 * Guardians whose name matches the pattern ignoring case and accents, with {@code \} as the escape character.
	 * {@code immutable_unaccent} is the function created by the classrooms migration (V6).
	 *
	 * @param pattern a {@code LIKE} pattern built by the caller (already wrapped in {@code %} and escaped)
	 */
	public static Specification<Guardian> nameMatches(String pattern) {
		return (root, query, cb) -> cb.like(
				cb.lower(cb.function("immutable_unaccent", String.class, root.get("name"))),
				cb.lower(cb.function("immutable_unaccent", String.class, cb.literal(pattern))), '\\');
	}

	/**
	 * Guardians with exactly this CPF (digits only, as stored).
	 */
	public static Specification<Guardian> cpfEquals(String cpf) {
		return (root, query, cb) -> cb.equal(root.get("cpf"), cpf);
	}

}
