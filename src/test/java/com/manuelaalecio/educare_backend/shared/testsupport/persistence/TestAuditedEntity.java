package com.manuelaalecio.educare_backend.shared.testsupport.persistence;

import com.manuelaalecio.educare_backend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "test_audited_entity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestAuditedEntity extends BaseEntity {

	@Column(name = "name", nullable = false)
	private String name;

	public TestAuditedEntity(String name) {
		this.name = name;
	}

	public void rename(String name) {
		this.name = name;
	}

}
