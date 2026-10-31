package com.manuelaalecio.educare_backend.classroom.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * The name comparisons and the ordering use {@code lower(immutable_unaccent(name))}, the same expression as the
 * unique index {@code classrooms_name_key}, so the application and the database agree on which names are equal.
 */
public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {

	/**
	 * Name of the unique index on the normalized name, reported by the database when it is violated.
	 */
	String NAME_UNIQUE_CONSTRAINT = "classrooms_name_key";

	/**
	 * @param name compared ignoring case and accents; the caller strips it beforehand
	 */
	@Query(value = """
			SELECT EXISTS (
			    SELECT 1 FROM classrooms
			    WHERE lower(immutable_unaccent(name)) = lower(immutable_unaccent(:name)))
			""", nativeQuery = true)
	boolean existsByNormalizedName(String name);

	/**
	 * Same as {@link #existsByNormalizedName(String)}, ignoring the class with the given id.
	 */
	@Query(value = """
			SELECT EXISTS (
			    SELECT 1 FROM classrooms
			    WHERE lower(immutable_unaccent(name)) = lower(immutable_unaccent(:name)) AND id <> :id)
			""", nativeQuery = true)
	boolean existsByNormalizedNameAndIdNot(String name, UUID id);

	/**
	 * Every class in alphabetical order ignoring case and accents; name and id only make the order deterministic.
	 */
	@Query(value = "SELECT * FROM classrooms ORDER BY lower(immutable_unaccent(name)), name, id", nativeQuery = true)
	List<Classroom> findAllOrderedByName();

}
