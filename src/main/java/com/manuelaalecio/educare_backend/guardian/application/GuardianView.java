package com.manuelaalecio.educare_backend.guardian.application;

import java.util.List;

import com.manuelaalecio.educare_backend.guardian.domain.Guardian;

/**
 * A guardian with its linked children (by name), as the use cases that return one guardian give it.
 */
public record GuardianView(Guardian guardian, List<GuardianChildRef> children) {
}
