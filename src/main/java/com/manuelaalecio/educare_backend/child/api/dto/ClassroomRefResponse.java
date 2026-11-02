package com.manuelaalecio.educare_backend.child.api.dto;

import java.util.UUID;

/**
 * The current class of a child: id and current name.
 */
public record ClassroomRefResponse(UUID id, String name) {
}
