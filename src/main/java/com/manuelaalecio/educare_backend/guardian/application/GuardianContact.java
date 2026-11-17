package com.manuelaalecio.educare_backend.guardian.application;

import java.util.UUID;

/**
 * The contact data of a guardian shown with a child: no documents, birth date, marital status nor work address.
 * {@code workplace} and {@code workPhone} are {@code null} when {@code notWorking} is {@code true}.
 */
public record GuardianContact(UUID id, String name, String phone, boolean notWorking, String workplace,
		String workPhone) {
}
