package com.manuelaalecio.educare_backend.guardian.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * A guardian as listed: only the name, the phone and the linked children, without documents, birth date, marital
 * status nor work data (data minimization, LGPD). Never carries the authors of the record.
 */
public record GuardianSummaryResponse(UUID id, String name, String phone, List<GuardianChildResponse> children) {
}
