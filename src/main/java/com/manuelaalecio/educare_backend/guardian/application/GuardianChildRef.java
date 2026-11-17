package com.manuelaalecio.educare_backend.guardian.application;

import java.util.UUID;

/**
 * A child linked to a guardian, as the {@code guardian} module sees it: id, current name and the relationship of
 * that link (the name of a constant owned by the {@code child} module, such as {@code MOTHER}).
 */
public record GuardianChildRef(UUID childId, String childName, String relationship) {
}
