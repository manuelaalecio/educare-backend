package com.manuelaalecio.educare_backend.child.domain;

import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A guardian of a child, with the relationship between them. The database keeps a single link per pair
 * ({@code child_guardians_child_guardian_key}), deletes the links with the child and keeps a linked guardian from
 * being deleted. The guardian is referenced by id only: it belongs to another module.
 */
@Entity
@Table(name = "child_guardians")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildGuardian extends BaseEntity {

	@Column(name = "child_id", nullable = false, updatable = false)
	private UUID childId;

	@Column(name = "guardian_id", nullable = false, updatable = false)
	private UUID guardianId;

	@Enumerated(EnumType.STRING)
	@Column(name = "relationship", nullable = false, length = 20)
	private Relationship relationship;

	private ChildGuardian(UUID childId, UUID guardianId, Relationship relationship) {
		this.childId = childId;
		this.guardianId = guardianId;
		this.relationship = relationship;
	}

	/**
	 * Links the guardian to the child.
	 */
	public static ChildGuardian link(UUID childId, UUID guardianId, Relationship relationship) {
		if (childId == null || guardianId == null || relationship == null) {
			throw new IllegalArgumentException("Criança, responsável e parentesco do vínculo são obrigatórios");
		}
		return new ChildGuardian(childId, guardianId, relationship);
	}

	public void changeRelationship(Relationship relationship) {
		if (relationship == null) {
			throw new IllegalArgumentException("Parentesco do vínculo é obrigatório");
		}
		this.relationship = relationship;
	}

}
