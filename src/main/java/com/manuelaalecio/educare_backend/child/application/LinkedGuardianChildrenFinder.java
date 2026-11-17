package com.manuelaalecio.educare_backend.child.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.manuelaalecio.educare_backend.child.domain.ChildGuardianRepository;
import com.manuelaalecio.educare_backend.child.domain.GuardianChildRow;
import com.manuelaalecio.educare_backend.guardian.application.GuardianChildRef;
import com.manuelaalecio.educare_backend.guardian.application.GuardianChildrenFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The children of the guardians from the links. Lives in {@code child}, which owns the links, and implements the
 * port declared by {@code guardian}.
 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LinkedGuardianChildrenFinder implements GuardianChildrenFinder {

	private final ChildGuardianRepository linkRepository;

	/**
	 * Every guardian in a single query; the order of the query (child's name, then id) is kept in each list.
	 */
	@Override
	public Map<UUID, List<GuardianChildRef>> findChildren(Collection<UUID> guardianIds) {
		if (guardianIds.isEmpty()) {
			return Map.of();
		}
		return linkRepository.findChildrenOfGuardians(guardianIds)
			.stream()
			.collect(Collectors.groupingBy(GuardianChildRow::getGuardianId, LinkedHashMap::new,
					Collectors.mapping(row -> new GuardianChildRef(row.getChildId(), row.getChildName(),
							row.getRelationship().name()), Collectors.toList())));
	}

}
