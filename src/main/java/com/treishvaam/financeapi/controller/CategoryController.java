package com.treishvaam.financeapi.controller;

import com.treishvaam.financeapi.model.Category;
import com.treishvaam.financeapi.repository.CategoryRepository;
import com.treishvaam.financeapi.service.BlogPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    @Autowired
    private CategoryRepository categoryRepository;
    
    @Autowired
    private BlogPostService blogPostService; 

    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Category> createCategory(@RequestBody Map<String, String> payload) {
        String categoryName = payload.get("name");
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Category newCategory = new Category();
        newCategory.setName(categoryName);
        newCategory.setSlug(blogPostService.generateUserFriendlySlug(categoryName)); 
        Category savedCategory = categoryRepository.save(newCategory);
        return ResponseEntity.ok(savedCategory);
    }

    @PostMapping("/admin/backfill-slugs")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, String>> backfillCategorySlugs() {
        List<Category> categories = categoryRepository.findAll();
        int count = 0;
        for (Category category : categories) {
            if (category.getSlug() == null || category.getSlug().isEmpty()) {
                category.setSlug(blogPostService.generateUserFriendlySlug(category.getName()));
                categoryRepository.save(category);
                count++;
            }
        }
        return ResponseEntity.ok(Map.of("message", "Successfully updated " + count + " categories with new slugs."));
    }
}