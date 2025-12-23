# 05-DATABASE-SCHEMA.md

## 1. Schema Management
This project uses **Liquibase** for database version control and schema migrations. All changes are tracked in XML changelog files, ensuring consistent schema updates across environments.

## 2. Key Tables
Based on the included changelogs, the primary tables in the schema are:
- `users`
- `roles`
- `user_roles` (join table for users and roles)
- `blog_posts` (includes editorial fields: `editor_notes`, `review_status`, `archived`, etc.)
- `categories`
- `market_data`
- `audit_logs`
- `historical_data_cache`
- `contact_message`
- `audience_visits`
- `quote_data`
- `page_content`
- `news` (with `archived` and `description` fields)

## 3. Recent Changes
- **V36__add_audit_log_table**: Introduced the `audit_logs` table to track important system or user actions for compliance and monitoring.
- **V38__add_archived_flag_to_news**: Added an `archived` flag to the news table, allowing news items to be marked as archived without deletion.
- **V35__add_editorial_fields**: Added editorial fields (e.g., `editor_notes`, `review_status`) to `blog_posts` for workflow management.
- **V39__add_description_to_news**: Added a `description` field to the news table for richer metadata.

## 4. Entity Relationships
- **Users & Roles**: Many-to-Many relationship via the `user_roles` join table. Each user can have multiple roles, and each role can be assigned to multiple users.
- **Blog Posts & Categories**: Each `blog_post` belongs to a `category`, and categories can have multiple posts (One-to-Many).

---
This document summarizes the database schema and recent changes as managed by Liquibase changelogs, including editorial and news-related updates.