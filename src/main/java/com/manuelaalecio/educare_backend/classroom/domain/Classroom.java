package com.manuelaalecio.educare_backend.classroom.domain;

import com.manuelaalecio.educare_backend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A class (group of children) of the institution. The name is unique ignoring case and accents, which the database
 * guarantees with the {@code classrooms_name_key} index.
 */
@Entity
@Table(name = "classrooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Classroom extends BaseEntity {

	public static final int NAME_MAX_LENGTH = 60;

	@Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	public Classroom(String name) {
		rename(name);
	}

	/**
	 * Replaces the name, stored without leading and trailing spaces. The students of the class do not change.
	 */
	public void rename(String name) {
		this.name = normalizeName(name);
	}

	/**
	 * Validates a name and returns it as it would be stored (without leading and trailing spaces), so it can be
	 * compared with the stored names before changing anything.
	 *
	 * @throws IllegalArgumentException when the name is blank or too long
	 */
	public static String normalizeName(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Nome da turma é obrigatório");
		}
		String stripped = name.strip();
		if (stripped.length() > NAME_MAX_LENGTH) {
			throw new IllegalArgumentException("Nome da turma deve ter no máximo " + NAME_MAX_LENGTH + " caracteres");
		}
		return stripped;
	}

}
