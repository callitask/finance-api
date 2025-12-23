package com.treishvaam.financeapi.repository;

import com.treishvaam.financeapi.model.PageContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PageContentRepository extends JpaRepository<PageContent, String> {}
