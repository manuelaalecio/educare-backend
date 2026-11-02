package com.manuelaalecio.educare_backend.child.domain;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * The document lookups compare the normalized values, as stored by {@link Child}. The unique constraints below are
 * reported by the database when a concurrent save repeats a document, so the service can still turn them into
 * conflicts.
 */
public interface ChildRepository extends JpaRepository<Child, UUID>, JpaSpecificationExecutor<Child> {

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
	 * Loads the child locking its row until the end of the transaction, so concurrent updates of the same child
	 * (and of its class) run one after the other.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Child c where c.id = :id")
	Optional<Child> findByIdForUpdate(UUID id);

}
