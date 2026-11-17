package com.manuelaalecio.educare_backend.child.application;

import java.util.UUID;

import com.manuelaalecio.educare_backend.child.domain.Relationship;
import com.manuelaalecio.educare_backend.guardian.application.GuardianInput;

/**
 * One guardian of the child as received for creation and update: either a guardian already registered
 * ({@code guardianId}) or the data of a new one ({@code newGuardian}), exactly one of them, always with the
 * relationship. The request validation ensures the "exactly one".
 */
public record ChildGuardianCommand(UUID guardianId, GuardianInput newGuardian, Relationship relationship) {

	public static ChildGuardianCommand existing(UUID guardianId, Relationship relationship) {
		return new ChildGuardianCommand(guardianId, null, relationship);
	}

	public static ChildGuardianCommand newGuardian(GuardianInput guardian, Relationship relationship) {
		return new ChildGuardianCommand(null, guardian, relationship);
	}

}
