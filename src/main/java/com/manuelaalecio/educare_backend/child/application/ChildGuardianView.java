package com.manuelaalecio.educare_backend.child.application;

import com.manuelaalecio.educare_backend.child.domain.Relationship;
import com.manuelaalecio.educare_backend.guardian.application.GuardianContact;

/**
 * A guardian of the child: the current contact data of the guardian and the relationship of this link.
 */
public record ChildGuardianView(GuardianContact contact, Relationship relationship) {
}
