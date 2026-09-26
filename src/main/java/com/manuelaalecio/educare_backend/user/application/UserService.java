package com.manuelaalecio.educare_backend.user.application;

import java.util.UUID;

import com.manuelaalecio.educare_backend.user.domain.LoginAlreadyInUseException;
import com.manuelaalecio.educare_backend.user.domain.Role;
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

	public User update(UUID id, String name, String login, Role role) {
		User user = findById(id);
		if (userRepository.existsByLoginAndIdNot(User.normalizeLogin(login), id)) {
			throw new LoginAlreadyInUseException();
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

	public void delete(UUID id) {
		userRepository.delete(findById(id));
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
