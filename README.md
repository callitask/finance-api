# Treishvaam Finance - Enterprise Frontend

## Project Overview

This is the React-based frontend for the Treishvaam Finance Platform. It is designed to be served via **Cloudflare Pages** and utilizes a **Zero-Trust Security Architecture**.

Unlike traditional SPAs, this application **does not** hold any API keys or secrets. It relies entirely on a "Backend-for-Frontend" (BFF) pattern, where the Spring Boot backend acts as a secure proxy for all external data providers (NewsAPI, AlphaVantage, etc.).

## Security & Configuration (Zero Trust)

**Fort Knox Security Suite: ENABLED**
This project strictly follows the **12-Factor App** configuration methodology. All production URLs are hidden from the codebase and injected strictly at runtime.

### 1. Environment Variables
The application requires two key variables to function. These tell the frontend where to find the secure API and Authentication server.

| Variable Name | Description | Local Dev Value | Production Value |
| :--- | :--- | :--- | :--- |
| `REACT_APP_API_URL` | The URL of the Spring Boot Backend. | `http://localhost:8080` | `[REDACTED - INJECTED VIA CLOUDFLARE]` |
| `REACT_APP_AUTH_URL` | The URL of the Keycloak Server. | `http://localhost:8080/auth` | `[REDACTED - INJECTED VIA CLOUDFLARE]` |

### 2. Local Development Setup
To run this project locally, you must create a `.env` file in the root directory (this file is git-ignored for security).

1.  **Copy the template**:
    ```bash
    cp .env.example .env
    ```
2.  **Edit `.env`**:
    ```env
    REACT_APP_API_URL=http://localhost:8080
    REACT_APP_AUTH_URL=http://localhost:8080/auth
    ```
3.  **Start the App**:
    ```bash
    npm start
    ```

### 3. Production Deployment (Cloudflare)
We use **Cloudflare Pages** for hosting.
1.  **Push** your code to the `main` branch.
2.  **Go to Cloudflare Dashboard** -> Pages -> Settings -> Environment Variables.
3.  **Add the Production Variables** (Mark them as "Encrypted" / "Secret"):
    * `REACT_APP_API_URL`: `https://<HIDDEN_DOMAIN_OR_TUNNEL>`
    * `REACT_APP_AUTH_URL`: `https://<HIDDEN_DOMAIN_OR_TUNNEL>/auth`
4.  **Redeploy** to apply changes.

## Architecture Highlights

* **Edge-Side Hydration (Zero Latency)**: When accessed via Cloudflare, the Cloudflare Worker pre-fetches API data and injects it into the HTML head as `window.__PRELOADED_STATE__`. The React app detects this on mount and **skips the initial API call**, rendering Blog Posts and Market Data instantly.
* **Lossless Image Pipeline**: The `ImageCropUploader` sends raw, uncompressed PNG data to the backend. We intentionally disable client-side compression to prevent "generation loss". All resizing and WebP conversion is handled server-side by **Java 21 Virtual Threads** for maximum quality.
* **Secure API Client**: All HTTP requests are handled by `src/apiConfig.js`. This client automatically attaches the Keycloak JWT (Bearer Token) to every request and handles 401/403 errors globally.
* **Silent Single Sign-On (SSO)**: The application uses a hidden iframe strategy (`silent-check-sso.html`) to renew tokens in the background. The Backend strictly whitelists this frontend domain via **Content Security Policy (CSP)** to allow this flow while blocking all other framing attempts.
* **Real User Monitoring (RUM)**: Grafana Faro is initialized in `src/faroConfig.js` to track frontend performance and errors in real-time.

## Key Directories

* `src/apiConfig.js`: **Critical.** Central configuration for API endpoints.
* `src/context/AuthContext.js`: Handles Keycloak login/logout and session state.
* `public/silent-check-sso.html`: **Critical.** A static file loaded by Keycloak in an iframe to enable silent token renewal without page reloads.
* `src/components/ImageCropUploader.js`: Handles image cropping and raw PNG upload logic.
* `src/pages`: Lazy-loaded route components (Dashboard, Blog, Market).

## Commands

| Command | Description |
| :--- | :--- |
| `npm start` | Runs the app in development mode at `http://localhost:3000`. |
| `npm run build` | Builds the app for production to the `build` folder. |
| `npm test` | Launches the test runner. |

## License
Proprietary software. All rights reserved by Treishvaam Group.