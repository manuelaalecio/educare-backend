package com.manuelaalecio.educare_backend.user.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.user.domain.InvalidCurrentPasswordException;
import com.manuelaalecio.educare_backend.user.domain.LastCoordinatorException;
import com.manuelaalecio.educare_backend.user.domain.LoginAlreadyInUseException;
import com.manuelaalecio.educare_backend.user.domain.NewPasswordSameAsCurrentException;
import com.manuelaalecio.educare_backend.user.domain.NewPasswordSameAsLoginException;
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
	private final Clock clock;
	private final PasswordAttemptLimiter passwordAttemptLimiter;

	/**
	 * @param role {@code null} creates the user with the default role ({@link Role#EDUCADOR})
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
	 * @param actorId the COORDENADOR who makes the change
	 */
	public User update(UUID id, String name, String login, Role role, UUID actorId) {
		User user = findById(id);
		if (userRepository.existsByLoginAndIdNot(User.normalizeLogin(login), id)) {
			throw new LoginAlreadyInUseException();
		}
		if (role != Role.COORDENADOR) {
			ensureAnotherCoordinatorRemainsWhenLosingCoordinator(user);
		}
		user.rename(name);
		user.changeLogin(login);
		user.changeRole(role);
		return saveAndFlush(user);
	}

	/**
	 * Password reset by an COORDENADOR. Also disconnects the user: their tokens issued before now are refused.
	 */
	public void changePassword(UUID id, String password) {
		User user = findById(id);
		user.changePassword(passwordEncoder.encode(password), clock.instant());
		userRepository.saveAndFlush(user);
	}

	/**
	 * @param actorId the COORDENADOR who makes the change, who cannot delete themselves
	 */
	public void delete(UUID id, UUID actorId) {
		if (id.equals(actorId)) {
			throw new SelfDeletionException();
		}
		User user = findById(id);
		ensureAnotherCoordinatorRemainsWhenLosingCoordinator(user);
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

	/**
	 * Changes the password of the user themselves, which disconnects their other sessions (tokens issued before now
	 * are refused). Only a wrong current password counts towards the {@link PasswordAttemptLimiter}; a blocked user
	 * is refused before the password is even checked.
	 */
	public void changeOwnPassword(UUID id, String currentPassword, String newPassword) {
		passwordAttemptLimiter.ensureAllowed(id);
		User user = findById(id);
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			passwordAttemptLimiter.recordFailure(id);
			throw new InvalidCurrentPasswordException();
		}
		// the current password was just checked, so comparing the strings is enough (and spares a BCrypt round)
		if (newPassword.equals(currentPassword)) {
			throw new NewPasswordSameAsCurrentException();
		}
		if (User.normalizeLogin(newPassword).equals(user.getLogin())) {
			throw new NewPasswordSameAsLoginException();
		}
		user.changePassword(passwordEncoder.encode(newPassword), clock.instant());
		userRepository.saveAndFlush(user);
		passwordAttemptLimiter.reset(id);
	}

	/**
	 * Called when the user is about to stop being COORDENADOR (demotion or deletion). Locks the COORDENADOR rows, so two
	 * concurrent demotions are serialized and the second one no longer counts the first demoted user.
	 */
	private void ensureAnotherCoordinatorRemainsWhenLosingCoordinator(User user) {
		if (user.getRole() == Role.COORDENADOR && userRepository.findAllByRoleForUpdate(Role.COORDENADOR).size() < 2) {
			throw new LastCoordinatorException();
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
