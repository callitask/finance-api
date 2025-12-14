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

### Key Operations
- **Get User**: `GET /api/v1/auth/me`
  - Returns: `{ "username": string, "email": string }` if authenticated, or 401 if not.

## Access Control
- All admin and draft operations require `ROLE_ADMIN` (checked via `@PreAuthorize`).
- Public endpoints do not require authentication.
- The security configuration enforces RBAC for sensitive operations.

---
This API reference covers the main endpoints and access rules for blog post and authentication operations.