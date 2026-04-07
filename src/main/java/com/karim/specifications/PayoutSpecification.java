package com.karim.specifications;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.karim.dto.PayoutFilter;
import com.karim.entity.AgentPayout;

import jakarta.persistence.criteria.Predicate;

public class PayoutSpecification {

    public static Specification<AgentPayout> getPayouts(UUID agentId, PayoutFilter filter) {
        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            // ✅ Mandatory: agent filter
            predicates.add(cb.equal(root.get("agentId"), agentId));

            // ✅ Optional: status filter
            if (filter != null && filter.getStatus() != null && !filter.getStatus().isEmpty()) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            // ✅ Optional: fromDate filter
            if (filter != null && filter.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        filter.getFromDate()
                ));
            }

            // ✅ Optional: toDate filter
            if (filter != null && filter.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("createdAt"),
                        filter.getToDate()
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}