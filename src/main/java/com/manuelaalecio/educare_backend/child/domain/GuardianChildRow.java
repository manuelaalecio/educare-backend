package com.manuelaalecio.educare_backend.child.domain;

import java.util.UUID;

/**
 * Projection of a child linked to a guardian: the guardian, the child's id and current name, and the relationship.
 */
public interface GuardianChildRow {

	UUID getGuardianId();

	UUID getChildId();

	String getChildName();

	Relationship getRelationship();

}
