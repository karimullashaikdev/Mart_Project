package com.karim.specifications;

import org.springframework.data.jpa.domain.Specification;

import com.karim.dto.UserFilterDto;
import com.karim.entity.User;

import jakarta.persistence.criteria.Predicate;

public class UserSpecification {

	public static Specification<User> filter(UserFilterDto filter) {
		return (root, query, cb) -> {

			Predicate predicate = cb.conjunction();

			if (filter.getRole() != null) {
				predicate = cb.and(predicate, cb.equal(root.get("role"), filter.getRole()));
			}

			if (filter.getIsActive() != null) {
				predicate = cb.and(predicate, cb.equal(root.get("isActive"), filter.getIsActive()));
			}

			if (filter.getSearch() != null && !filter.getSearch().isEmpty()) {
				String like = "%" + filter.getSearch().toLowerCase() + "%";

				predicate = cb.and(predicate, cb.or(cb.like(cb.lower(root.get("fullName")), like),
						cb.like(cb.lower(root.get("email")), like)));
			}

			return predicate;
		};
	}
}