package com.manuelaalecio.educare_backend.child.application;

import java.util.List;

import com.manuelaalecio.educare_backend.child.domain.Child;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomRef;

/**
 * A child together with its current class and its guardians (by name, then id), as the use cases that return one
 * child give it. A child saved before the guardians were required may have none.
 */
public record ChildView(Child child, ClassroomRef classroom, List<ChildGuardianView> guardians) {
}
