package com.manuelaalecio.educare_backend.guardian.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * The CPF lookups compare the normalized value, as stored by {@link Guardian}. The unique index below is reported by
 * the database when a concurrent save repeats a CPF, so the use cases can still turn it into a conflict.
 */
public interface GuardianRepository extends JpaRepository<Guardian, UUID>, JpaSpecificationExecutor<Guardian> {

	/**
	 * Partial unique index on the CPF: several guardians may have none.
	 */
	String CPF_UNIQUE_CONSTRAINT = "guardians_cpf_key";

	boolean existsByCpf(String cpf);

	boolean existsByCpfAndIdNot(String cpf, UUID id);

}
