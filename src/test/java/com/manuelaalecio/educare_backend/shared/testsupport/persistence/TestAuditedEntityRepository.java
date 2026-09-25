package com.manuelaalecio.educare_backend.shared.testsupport.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestAuditedEntityRepository extends JpaRepository<TestAuditedEntity, UUID> {
}
