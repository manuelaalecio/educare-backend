package com.manuelaalecio.educare_backend.child.domain;

/**
 * Relationship of a guardian with one child, an attribute of the link: the same guardian may be the mother of one
 * child and the stepmother of another.
 */
public enum Relationship {
	MOTHER, FATHER, GRANDMOTHER, GRANDFATHER, AUNT, UNCLE, SISTER, BROTHER, STEPMOTHER, STEPFATHER, LEGAL_GUARDIAN,
	OTHER
}
