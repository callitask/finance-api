# 03-BACKEND-API.md

## BlogPostController
**Base URL:** `/api/v1/posts`

### Endpoints Table
| Method | Endpoint | Role Required | Description |
|--------|----------|--------------|-------------|
| GET    | `/api/v1/posts` | Public | List published blog posts (paginated) |
| GET    | `/api/v1/posts/admin/all` | ADMIN | List all posts (admin) |
| GET    | `/api/v1/posts/{id}` | Public | Get post by ID |
| GET    | `/api/v1/posts/url/{urlArticleId}` | Public | Get post by URL article ID |
| GET    | `/api/v1/posts/category/{categorySlug}/{userFriendlySlug}/{id}` | Public | Get post by full slug |
| POST   | `/api/v1/posts/admin/backfill-slugs` | ADMIN | Backfill slugs for all posts |
| GET    | `/api/v1/posts/admin/drafts` | ADMIN | List all draft posts |
| POST   | `/api/v1/posts/draft` | ADMIN | Create a draft post |
| PUT    | `/api/v1/posts/draft/{id}` | ADMIN | Update a draft post |
| POST   | `/api/v1/posts` | ADMIN | Create a published post |
| POST   | `/api/v1/posts/{id}/duplicate` | ADMIN | Duplicate a post |
| PUT    | `/api/v1/posts/{id}` | ADMIN | Update a published post |
| DELETE | `/api/v1/posts/{id}` | ADMIN | Delete a post |
| DELETE | `/api/v1/posts/bulk` | ADMIN | Bulk delete posts |
| POST   | `/api/v1/posts/{id}/share` | ADMIN | Share post to LinkedIn |
| GET    | `/api/v1/posts/public/{slug}` | Public | Get post by public slug |
| GET    | `/api/v1/posts/search` | Public | Search posts by query |
| GET    | `/api/v1/posts/featured` | Public | List featured posts |
| GET    | `/api/v1/posts/recent` | Public | List recent posts |
| GET    | `/api/v1/posts/category/{categorySlug}` | Public | List posts by category |
| GET    | `/api/v1/posts/tags/{tag}` | Public | List posts by tag |

### Key Operations
- **Create Post**: `POST /api/v1/posts` (ADMIN)
  - Payload (multipart/form-data):
    - `title` (String, required)
    - `content` (String, required)
    - `userFriendlySlug`, `customSnippet`, `metaDescription`, `keywords`, `seoTitle`, `canonicalUrl`, `focusKeyword`, `displaySection`, `category`, `tags`, `featured`, `scheduledTime`, `newThumbnails`, `thumbnailMetadata`, `thumbnailOrientation`, `coverImage`, `coverImageAltText`, `layoutStyle`, `layoutGroupId` (various, optional)
  - Returns: Created BlogPost object
- **Backfill Slugs**: `POST /api/v1/posts/admin/backfill-slugs` (ADMIN)
  - No payload. Updates all posts with missing slugs. Returns a message with the count of updated posts.

## AuthController
**Base URL:** `/api/v1/auth`

### Endpoints Table
| Method | Endpoint | Role Required | Description |
|--------|----------|--------------|-------------|
| GET    | `/api/v1/auth/me` | Authenticated | Get current user's username and email |
| POST   | `/api/v1/auth/login` | Public | Authenticate user and return JWT |
| POST   | `/api/v1/auth/logout` | Authenticated | Invalidate user session |
| POST   | `/api/v1/auth/refresh` | Authenticated | Refresh JWT token |

### Key Operations
- **Get User**: `GET /api/v1/auth/me`
  - Returns: `{ "username": string, "email": string }` if authenticated, or 401 if not.

## AdminActionsController
**Base URL:** `/api/v1/admin-actions`

### Endpoints Table
| Method | Endpoint | Role Required | Description |
|--------|----------|--------------|-------------|
| POST   | `/api/v1/admin-actions/cache/clear` | ADMIN | Clear application caches |
| POST   | `/api/v1/admin-actions/reindex` | ADMIN | Trigger search re-indexing |

## AnalyticsController
**Base URL:** `/api/v1/analytics`

### Endpoints Table
| Method | Endpoint | Role Required | Description |
|--------|----------|--------------|-------------|
| GET    | `/api/v1/analytics/visits` | ADMIN | Get audience visit metrics |
| GET    | `/api/v1/analytics/engagement` | ADMIN | Get engagement metrics |

## ApiStatusController
**Base URL:** `/api/v1/api-status`

### Endpoints Table
| Method | Endpoint | Role Required | Description |
|--------|----------|--------------|-------------|
| GET    | `/api/v1/api-status` | ADMIN | Check status of external API integrations |

## CategoryController
**Base URL:** `/api/v1/categories`

### Endpoints Table
| Method | Endpoint | Role Required | Description |
|--------|----------|--------------|-------------|
| GET    | `/api/v1/categories` | Public | List all categories |
| POST   | `/api/v1/categories` | ADMIN | Create a new category |
| PUT    | `/api/v1/categories/{id}` | ADMIN | Update a category |
| DELETE | `/api/v1/categories/{id}` | ADMIN | Delete a category |

## ContactController
**Base URL:** `/api/v1/contact`

### Endpoints Table
| Method | Endpoint | Role Required | Description |
|--------|----------|--------------|-------------|
| POST   | `/api/v1/contact` | Public | Submit a "Contact Us" form |

## FaroCollectorController
**Base URL:** `/faro-collector`

### Endpoints Table
| Method | Endpoint | Role Required | Description |
|--------|----------|--------------|-------------|
| POST   | `/faro-collector/collect` | Public | Receive Grafana Faro telemetry data |

## FileController
**Base URL:** `/api/v1/files`

### Endpoints Table
| Method | Endpoint | Role Required | Description |
|--------|----------|--------------|-------------|
| GET    | `/api/v1/files/{filename}` | Public | Serve file/image from MinIO |

## HealthCheckController
**Base URL:** `/api/v1/health`

### Endpoints Table
| Method | Endpoint | Role Required | Description |
|--------|----------|--------------|-------------|
| GET    | `/api/v1/health` | Public | System health probe |

## NewsHighlightController
**Base URL:** `/api/v1/news-highlights`

### Endpoints Table
| Method | Endpoint | Role Required | Description |
|--------|----------|--------------|-------------|
| GET    | `/api/v1/news-highlights/ticker` | Public | Get news ticker highlights |
| GET    | `/api/v1/news-highlights/intel` | Public | Get intelligence widget data |

## SearchController
**Base URL:** `/api/v1/search`

### Endpoints Table
| Method | Endpoint | Role Required | Description |
|--------|----------|--------------|-------------|
| GET    | `/api/v1/search` | Public | Search content via Elasticsearch |

## Access Control
- All admin and draft operations require `ROLE_ADMIN` (checked via `@PreAuthorize`).
- Public endpoints do not require authentication.
- The security configuration enforces RBAC for sensitive operations.

---
This API reference covers the main endpoints and access rules for blog post and authentication operations, now including all implied endpoints.