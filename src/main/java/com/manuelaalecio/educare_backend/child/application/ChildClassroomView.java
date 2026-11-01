package com.manuelaalecio.educare_backend.child.application;

import com.manuelaalecio.educare_backend.child.domain.ChildClassroom;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomRef;

/**
 * A period of the child in a class, with the current name of the class.
 */
public record ChildClassroomView(ChildClassroom link, ClassroomRef classroom) {
}
