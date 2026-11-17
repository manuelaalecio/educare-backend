package com.manuelaalecio.educare_backend.child.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChildGuardianRepository extends JpaRepository<ChildGuardian, UUID> {

	/**
	 * Every guardian link of the child.
	 */
	List<ChildGuardian> findByChildId(UUID childId);

	/**
	 * The children of several guardians, in a single query, by the child's name and then id.
	 */
	@Query("""
			select l.guardianId as guardianId, c.id as childId, c.name as childName, l.relationship as relationship
			from ChildGuardian l join Child c on c.id = l.childId
			where l.guardianId in :guardianIds
			order by c.name, c.id
			""")
	List<GuardianChildRow> findChildrenOfGuardians(Collection<UUID> guardianIds);

}
