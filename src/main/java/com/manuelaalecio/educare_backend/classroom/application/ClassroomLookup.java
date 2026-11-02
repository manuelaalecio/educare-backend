package com.manuelaalecio.educare_backend.classroom.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.manuelaalecio.educare_backend.classroom.domain.Classroom;
import com.manuelaalecio.educare_backend.classroom.domain.ClassroomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access to the classes for other modules (e.g. {@code child}, to check that a class exists and to show its
 * name), without the children counts of {@link ClassroomService} and without exposing the repository.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClassroomLookup {

	private final ClassroomRepository classroomRepository;

	public Optional<ClassroomRef> find(UUID id) {
		return classroomRepository.findById(id).map(ClassroomLookup::toRef);
	}

	/**
	 * Looks up several classes in a single query.
	 *
	 * @return the classes found, by id; ids of classes that do not exist are missing from the map
	 */
	public Map<UUID, ClassroomRef> findAll(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return classroomRepository.findAllById(ids)
			.stream()
			.map(ClassroomLookup::toRef)
			.collect(Collectors.toMap(ClassroomRef::id, Function.identity()));
	}

	private static ClassroomRef toRef(Classroom classroom) {
		return new ClassroomRef(classroom.getId(), classroom.getName());
	}

}
