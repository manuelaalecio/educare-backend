package com.manuelaalecio.educare_backend.user.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

	/**
	 * @param login already normalized (see {@link User#normalizeLogin(String)})
	 */
	boolean existsByLogin(String login);

	/**
	 * @param login already normalized (see {@link User#normalizeLogin(String)})
	 */
	boolean existsByLoginAndIdNot(String login, UUID id);

}
