package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.model.Category;
import com.treishvaam.financeapi.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @Autowired
    private CategoryRepository categoryRepository;

    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }

    // --- FIX: This method now correctly reads the "name" field from the incoming JSON. ---
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Category> createCategory(@RequestBody Map<String, String> payload) {
        String categoryName = payload.get("name");
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Category newCategory = new Category();
        newCategory.setName(categoryName);
        Category savedCategory = categoryRepository.save(newCategory);
        return ResponseEntity.ok(savedCategory);
    }
}