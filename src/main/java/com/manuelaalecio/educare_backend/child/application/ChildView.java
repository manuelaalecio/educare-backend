package com.manuelaalecio.educare_backend.child.application;

import com.manuelaalecio.educare_backend.child.domain.Child;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomRef;

/**
 * A child together with its current class, as the use cases that return one child give it.
 */
public record ChildView(Child child, ClassroomRef classroom) {
}
