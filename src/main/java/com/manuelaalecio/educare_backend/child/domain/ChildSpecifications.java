package com.manuelaalecio.educare_backend.child.domain;

import java.util.UUID;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filters of the children listing, combined by the caller.
 */
public final class ChildSpecifications {

	private ChildSpecifications() {
	}

	/**
	 * Children whose name matches the pattern ignoring case and accents, with {@code \} as the escape character.
	 * {@code immutable_unaccent} is the function created by the classrooms migration (V6).
	 *
	 * @param pattern a {@code LIKE} pattern built by the caller (already wrapped in {@code %} and escaped)
	 */
	public static Specification<Child> nameMatches(String pattern) {
		return (root, query, cb) -> cb.like(
				cb.lower(cb.function("immutable_unaccent", String.class, root.get("name"))),
				cb.lower(cb.function("immutable_unaccent", String.class, cb.literal(pattern))), '\\');
	}

	/**
	 * Children whose current class (the active link) is the given one; children that left it do not match.
	 */
	public static Specification<Child> inClassroom(UUID classroomId) {
		return (root, query, cb) -> {
			Subquery<Integer> link = query.subquery(Integer.class);
			Root<ChildClassroom> linkRoot = link.from(ChildClassroom.class);
			link.select(cb.literal(1))
				.where(cb.equal(linkRoot.get("childId"), root.get("id")),
						cb.equal(linkRoot.get("classroomId"), classroomId), cb.isNull(linkRoot.get("endDate")));
			return cb.exists(link);
		};
	}

}
