package com.treishvaam.financeapi.repository;

import com.treishvaam.financeapi.model.Category;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
  Optional<Category> findBySlug(String slug);

  Optional<Category> findByName(String name);
}
