package com.manuelaalecio.educare_backend.guardian.application;

import java.util.List;

import com.manuelaalecio.educare_backend.guardian.domain.Guardian;

/**
 * A listed guardian with its linked children (by name).
 */
public record GuardianSummaryView(Guardian guardian, List<GuardianChildRef> children) {
}
