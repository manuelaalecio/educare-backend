package com.manuelaalecio.educare_backend.child.domain;

import java.time.LocalDate;
import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A period of a child in a class: active while it has no end date. The database keeps at most one active link per
 * child ({@code child_classrooms_active_key}) and the end date not before the start date
 * ({@code child_classrooms_dates_check}). The class is referenced by id only: it belongs to another module.
 */
@Entity
@Table(name = "child_classrooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChildClassroom extends BaseEntity {

	@Column(name = "child_id", nullable = false, updatable = false)
	private UUID childId;

	@Column(name = "classroom_id", nullable = false, updatable = false)
	private UUID classroomId;

	@Column(name = "start_date", nullable = false, updatable = false)
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	private ChildClassroom(UUID childId, UUID classroomId, LocalDate startDate) {
		this.childId = childId;
		this.classroomId = classroomId;
		this.startDate = startDate;
	}

	/**
	 * Opens an active link of the child with the class, starting on the given day.
	 */
	public static ChildClassroom open(UUID childId, UUID classroomId, LocalDate startDate) {
		if (childId == null || classroomId == null || startDate == null) {
			throw new IllegalArgumentException("Criança, turma e data de início do vínculo são obrigatórias");
		}
		return new ChildClassroom(childId, classroomId, startDate);
	}

	/**
	 * Ends the link on the given day, which may be the start day itself (two changes on the same day).
	 */
	public void close(LocalDate endDate) {
		if (endDate == null) {
			throw new IllegalArgumentException("Data de fim do vínculo é obrigatória");
		}
		if (!isActive()) {
			throw new IllegalArgumentException("Vínculo já encerrado");
		}
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("Data de fim do vínculo não pode ser anterior à de início");
		}
		this.endDate = endDate;
	}

	public boolean isActive() {
		return endDate == null;
	}

}
