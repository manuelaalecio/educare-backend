package com.manuelaalecio.educare_backend.child.domain;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * The document lookups compare the normalized values, as stored by {@link Child}. The unique constraints below are
 * reported by the database when a concurrent save repeats a document, so the service can still turn them into
 * conflicts.
 */
public interface ChildRepository extends JpaRepository<Child, UUID> {

	String CPF_UNIQUE_CONSTRAINT = "children_cpf_key";

	String RG_UNIQUE_CONSTRAINT = "children_rg_key";

	String BIRTH_CERTIFICATE_NUMBER_UNIQUE_CONSTRAINT = "children_birth_certificate_number_key";

	boolean existsByCpf(String cpf);

	boolean existsByCpfAndIdNot(String cpf, UUID id);

	boolean existsByRg(String rg);

	boolean existsByRgAndIdNot(String rg, UUID id);

	boolean existsByBirthCertificateNumber(String birthCertificateNumber);

	boolean existsByBirthCertificateNumberAndIdNot(String birthCertificateNumber, UUID id);

	/**
	 * Children whose name matches the pattern ignoring case and accents, with {@code \} as the escape character.
	 * {@code immutable_unaccent} is the function created by the classrooms migration (V6).
	 *
	 * @param pattern a {@code LIKE} pattern built by the caller (already wrapped in {@code %} and escaped)
	 */
	@Query("""
			select c from Child c
			where lower(function('immutable_unaccent', c.name)) like lower(function('immutable_unaccent', :pattern))
			escape '\\'
			""")
	Page<Child> searchByName(String pattern, Pageable pageable);

}
