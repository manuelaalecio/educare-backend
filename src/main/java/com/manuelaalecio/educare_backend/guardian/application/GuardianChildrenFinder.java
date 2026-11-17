package com.manuelaalecio.educare_backend.guardian.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Finds the children linked to guardians. The {@code child} module, which owns the links, implements it, so
 * {@code guardian} never depends on that module.
 */
public interface GuardianChildrenFinder {

	/**
	 * The children of several guardians at once, in a single query in a real implementation.
	 *
	 * @return the children by guardian id, each list by the child's name and then id; a guardian missing from the
	 * map has no children
	 */
	Map<UUID, List<GuardianChildRef>> findChildren(Collection<UUID> guardianIds);

}
