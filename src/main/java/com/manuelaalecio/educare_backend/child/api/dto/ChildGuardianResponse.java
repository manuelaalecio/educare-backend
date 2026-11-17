package com.manuelaalecio.educare_backend.child.api.dto;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A guardian of the child: the contact data and the relationship with this child, without documents, birth date,
 * marital status nor work address. {@code workplace} and {@code workPhone} are {@code null} when {@code notWorking}
 * is {@code true}.
 */
public record ChildGuardianResponse(UUID id, String name, String relationship, String phone, boolean notWorking,
		@Nullable String workplace, @Nullable String workPhone) {
}
