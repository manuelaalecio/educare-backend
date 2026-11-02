package com.manuelaalecio.educare_backend.classroom.application;

import java.util.UUID;

/**
 * Id and current name of a class, as other modules see it.
 */
public record ClassroomRef(UUID id, String name) {
}
