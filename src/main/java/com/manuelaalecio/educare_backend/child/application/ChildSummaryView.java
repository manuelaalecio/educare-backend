package com.manuelaalecio.educare_backend.child.application;

import com.manuelaalecio.educare_backend.child.domain.Child;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomRef;

/**
 * A listed child together with its current class.
 */
public record ChildSummaryView(Child child, ClassroomRef classroom) {
}
