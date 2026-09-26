package com.manuelaalecio.educare_backend.user.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

	/**
	 * @param login already normalized (see {@link User#normalizeLogin(String)})
	 */
	boolean existsByLogin(String login);

	/**
	 * @param login already normalized (see {@link User#normalizeLogin(String)})
	 */
	boolean existsByLoginAndIdNot(String login, UUID id);

	/**
	 * @param login already normalized (see {@link User#normalizeLogin(String)})
	 */
	Optional<User> findByLogin(String login);

	/**
	 * Loads the users with the role and locks their rows ({@code SELECT ... FOR UPDATE}) until the end of the
	 * transaction, so concurrent demotions or deletions of the last users with a role are serialized.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT u FROM User u WHERE u.role = :role")
	List<User> findAllByRoleForUpdate(Role role);

}
