package com.karim.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karim.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

	// ✅ findBySlug
	Optional<Category> findBySlug(String slug);

	// ✅ findAllActive
	@Query("""
			    SELECT c FROM Category c
			    WHERE c.isActive = true
			    AND c.isDeleted = false
			""")
	List<Category> findAllActive();

	// ✅ findAllAdmin (no soft delete filter)
	@Query("""
			    SELECT c FROM Category c
			""")
	List<Category> findAllAdmin();

	// ✅ findChildren
	@Query("""
			    SELECT c FROM Category c
			    WHERE c.parent.id = :parentId
			""")
	List<Category> findChildren(@Param("parentId") UUID parentId);

	// ✅ bulkUpdateSortOrder
	@Modifying
	@Query("""
			    UPDATE Category c
			    SET c.sortOrder = :sortOrder
			    WHERE c.id = :id
			""")
	void updateSortOrder(@Param("id") UUID id, @Param("sortOrder") int sortOrder);

	// (Used in service loop for bulk updates)

	// ✅ hasActiveProducts
	@Query("""
			    SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
			    FROM Product p
			    WHERE p.categoryId = :categoryId
			    AND p.isActive = true
			    AND p.isDeleted = false
			""")
	boolean hasActiveProducts(@Param("categoryId") UUID categoryId);

	@Query("""
			    SELECT c FROM Category c
			    WHERE (:name IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%')))
			    AND (:isActive IS NULL OR c.isActive = :isActive)
			    AND (:isDeleted IS NULL OR c.isDeleted = :isDeleted)
			    AND (:parentId IS NULL OR c.parent.id = :parentId)
			""")
	List<Category> findCategoriesAdmin(@Param("name") String name, @Param("isActive") Boolean isActive,
			@Param("isDeleted") Boolean isDeleted, @Param("parentId") UUID parentId);
}
