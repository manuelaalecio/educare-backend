package com.manuelaalecio.educare_backend.user.application;

import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.user.domain.InvalidCurrentPasswordException;
import com.manuelaalecio.educare_backend.user.domain.LastAdminException;
import com.manuelaalecio.educare_backend.user.domain.LoginAlreadyInUseException;
import com.manuelaalecio.educare_backend.user.domain.Role;
import com.manuelaalecio.educare_backend.user.domain.SelfDeletionException;
import com.manuelaalecio.educare_backend.user.domain.User;
import com.manuelaalecio.educare_backend.user.domain.UserNotFoundException;
import com.manuelaalecio.educare_backend.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the user registry. Passwords arrive in plain text and only their hash reaches the domain.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

	/**
	 * BCrypt hash, with the cost of the {@link PasswordEncoder} (10), of a password nobody knows. Compared when the
	 * login does not exist, so the response time does not reveal whether an email is registered.
	 */
	static final String UNKNOWN_USER_PASSWORD_HASH = "$2a$10$VqtlB.ZTZmMcQKixy5mJHOq5y25A4wjhYUcvZXq.E3GZOAwRg8PVy";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	/**
	 * @param role {@code null} creates the user with the default role ({@link Role#USER})
	 */
	public User create(String name, String login, String password, Role role) {
		String passwordHash = passwordEncoder.encode(password);
		User user = role == null
				? new User(name, login, passwordHash)
				: new User(name, login, passwordHash, role);
		if (userRepository.existsByLogin(user.getLogin())) {
			throw new LoginAlreadyInUseException();
		}
		return saveAndFlush(user);
	}

	@Transactional(readOnly = true)
	public User findById(UUID id) {
		return userRepository.findById(id).orElseThrow(UserNotFoundException::new);
	}

	@Transactional(readOnly = true)
	public Page<User> list(Pageable pageable) {
		return userRepository.findAll(pageable);
	}

	/**
	 * @param actorId the ADMIN who makes the change
	 */
	public User update(UUID id, String name, String login, Role role, UUID actorId) {
		User user = findById(id);
		if (userRepository.existsByLoginAndIdNot(User.normalizeLogin(login), id)) {
			throw new LoginAlreadyInUseException();
		}
		if (role != Role.ADMIN) {
			ensureAnotherAdminRemainsWhenLosingAdmin(user);
		}
		user.rename(name);
		user.changeLogin(login);
		user.changeRole(role);
		return saveAndFlush(user);
	}

	public void changePassword(UUID id, String password) {
		User user = findById(id);
		user.changePasswordHash(passwordEncoder.encode(password));
		userRepository.saveAndFlush(user);
	}

	/**
	 * @param actorId the ADMIN who makes the change, who cannot delete themselves
	 */
	public void delete(UUID id, UUID actorId) {
		if (id.equals(actorId)) {
			throw new SelfDeletionException();
		}
		User user = findById(id);
		ensureAnotherAdminRemainsWhenLosingAdmin(user);
		userRepository.delete(user);
	}

	/**
	 * Never logs the login: it is personal data, and a failed attempt could reveal someone else's email.
	 *
	 * @return empty when the login does not exist or the password does not match
	 */
	@Transactional(readOnly = true)
	public Optional<UserAccount> authenticate(String login, String password) {
		Optional<User> user = userRepository.findByLogin(User.normalizeLogin(login));
		if (user.isEmpty()) {
			passwordEncoder.matches(password, UNKNOWN_USER_PASSWORD_HASH);
			return Optional.empty();
		}
		return user.filter(found -> passwordEncoder.matches(password, found.getPasswordHash()))
				.map(UserService::toAccount);
	}

	@Transactional(readOnly = true)
	public UserAccount findAccount(UUID id) {
		return toAccount(findById(id));
	}

	public void changeOwnPassword(UUID id, String currentPassword, String newPassword) {
		User user = findById(id);
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new InvalidCurrentPasswordException();
		}
		user.changePasswordHash(passwordEncoder.encode(newPassword));
		userRepository.saveAndFlush(user);
	}

	/**
	 * Called when the user is about to stop being ADMIN (demotion or deletion). Locks the ADMIN rows, so two
	 * concurrent demotions are serialized and the second one no longer counts the first demoted user.
	 */
	private void ensureAnotherAdminRemainsWhenLosingAdmin(User user) {
		if (user.getRole() == Role.ADMIN && userRepository.findAllByRoleForUpdate(Role.ADMIN).size() < 2) {
			throw new LastAdminException();
		}
	}

	private static UserAccount toAccount(User user) {
		return new UserAccount(user.getId(), user.getName(), user.getLogin(), user.getRole().name(),
				user.getCreatedAt(), user.getUpdatedAt());
	}

	/**
	 * Flushes so a concurrent insert of the same login violates {@code users_login_key} here, where it can
	 * still become a conflict, instead of at commit time.
	 */
	private User saveAndFlush(User user) {
		try {
			return userRepository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			throw new LoginAlreadyInUseException();
		}
	}

}
