package com.manuelaalecio.educare_backend.user.domain;

import java.util.Locale;
import java.util.regex.Pattern;

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
 * A staff member who uses the system. The login is an email, stored in lower case; only the password hash is kept.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

	public static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
	public static final int NAME_MAX_LENGTH = 150;
	public static final int LOGIN_MAX_LENGTH = 254;

	private static final Pattern EMAIL = Pattern.compile(EMAIL_PATTERN);

	@Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	@Column(name = "login", nullable = false, length = LOGIN_MAX_LENGTH)
	private String login;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private Role role;

	public User(String name, String login, String passwordHash) {
		this(name, login, passwordHash, Role.USER);
	}

	public User(String name, String login, String passwordHash, Role role) {
		rename(name);
		changeLogin(login);
		changePasswordHash(passwordHash);
		changeRole(role);
	}

	/**
	 * Normalizes a login the same way it is stored, so it can be compared with stored logins.
	 */
	public static String normalizeLogin(String login) {
		return login == null ? null : login.toLowerCase(Locale.ROOT);
	}

	public void rename(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Nome é obrigatório");
		}
		String stripped = name.strip();
		if (stripped.length() > NAME_MAX_LENGTH) {
			throw new IllegalArgumentException("Nome deve ter no máximo " + NAME_MAX_LENGTH + " caracteres");
		}
		this.name = stripped;
	}

	public void changeLogin(String login) {
		String normalized = normalizeLogin(login);
		if (normalized == null || !EMAIL.matcher(normalized).matches()) {
			throw new IllegalArgumentException("Login deve ser um email válido");
		}
		if (normalized.length() > LOGIN_MAX_LENGTH) {
			throw new IllegalArgumentException("Login deve ter no máximo " + LOGIN_MAX_LENGTH + " caracteres");
		}
		this.login = normalized;
	}

	public void changePasswordHash(String passwordHash) {
		if (passwordHash == null || passwordHash.isBlank()) {
			throw new IllegalArgumentException("Hash da senha é obrigatório");
		}
		this.passwordHash = passwordHash;
	}

	public void changeRole(Role role) {
		if (role == null) {
			throw new IllegalArgumentException("Role é obrigatória");
		}
		this.role = role;
	}

}
