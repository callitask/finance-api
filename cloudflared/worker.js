/**
 * AI-CONTEXT:
 *
 * Purpose:
 * - Enterprise Edge Controller for Treishvaam Finance.
 * - Handles: Zero-Trust Security, SEO Edge Hydration, Flattened Sitemap Aggregation, and High-Availability Fallback.
 *
 * Scope:
 * - Intercepts all traffic to treishfin.treishvaamgroup.com
 * - Manages routing between Static Frontend (Pages) and Dynamic Backend (API).
 * - Enforces security headers and bot protection.
 *
 * Critical Dependencies:
 * - Backend: https://api.treishvaamgroup.com (via env.BACKEND_URL)
 * - Frontend: Cloudflare Pages (Static Assets)
 * - Sitemap: Aggregates Internal Static XML + /sitemap-dynamic/* (Backend)
 *
 * Security Constraints:
 * - Strict Content-Security-Policy (CSP).
 * - HSTS enforcement.
 * - No hardcoded secrets.
 *
 * Non-Negotiables:
 * - SITEMAP: Must always serve Static Sitemap if Backend is down.
 * - SEO: Must hydrate HTML for Bots to prevent "blank page" indexing.
 * - AVAILABILITY: Must serve cached content on 5xx errors.
 * - FREE TIER: Must use standard Cache API.
 *
 * IMMUTABLE CHANGE HISTORY:
 * - ADDED: Hybrid Sitemap Aggregation (Static + Dynamic).
 * - EDITED: Consolidated all previous logic (SEO, Security, Fallback) into single file.
 * - EDITED: Enhanced fallback logic for dynamic sitemap segments.
 * - EDITED:
 * • FLATTENED Sitemap Index (No nested indices).
 * • Added Aggressive Image Caching (Free Tier compatible).
 * • Added Offline Resilience for Sitemap Metadata.
 * • Phase 3 - Flattening & Speed.
 * - EDITED:
 * • ADDED INTERNAL STATIC SITEMAP GENERATION (Zero-Dependency).
 * • Worker now serves /sitemap-static.xml directly from memory.
 * • Fixes "0 Discovered Pages" by guaranteeing valid XML.
 */

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // =================================================================================
    // 0. CONFIGURATION (Zero Trust / Environment Agnostic)
    // =================================================================================
    const BACKEND_URL = env.BACKEND_URL || "https://api.treishvaamgroup.com";
    const FRONTEND_URL = env.FRONTEND_URL || "https://treishfin.treishvaamgroup.com";
    const PARENT_ORG_URL = "https://treishvaamgroup.com";

    const backendConfig = new URL(BACKEND_URL);

    // =================================================================================
    // 1. UNIVERSAL HEADER INJECTION
    // =================================================================================
    const cf = request.cf || {};
    const enhancedHeaders = new Headers(request.headers);
    
    // Geo Intelligence
    enhancedHeaders.set("X-Visitor-City", cf.city || "Unknown");
    enhancedHeaders.set("X-Visitor-Region", cf.region || "Unknown");
    enhancedHeaders.set("X-Visitor-Country", cf.country || "Unknown");
    enhancedHeaders.set("X-Visitor-Continent", cf.continent || "Unknown");
    enhancedHeaders.set("X-Visitor-Timezone", cf.timezone || "UTC");
    enhancedHeaders.set("X-Visitor-Lat", cf.latitude || "0");
    enhancedHeaders.set("X-Visitor-Lon", cf.longitude || "0");
    enhancedHeaders.set("X-Visitor-Device-Colo", cf.colo || "Unknown");

    // Base Request for internal fetching
    const baseEnhancedRequest = new Request(request.url, {
      headers: enhancedHeaders,
      method: request.method,
      body: request.body,
      redirect: request.redirect
    });

    // =================================================================================
    // HELPER: ADD SECURITY HEADERS (The Enterprise Shield)
    // =================================================================================
    const addSecurityHeaders = (response) => {
      if (!response) return response;
      const newHeaders = new Headers(response.headers);
      
      // 1. HSTS: Force HTTPS for 2 years
      newHeaders.set("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
      
      // 2. Anti-MIME Sniffing
      newHeaders.set("X-Content-Type-Options", "nosniff");
      
      // 3. Clickjacking Protection (CSP - Modern Standard)
      newHeaders.set("Content-Security-Policy", "frame-ancestors 'self';");
      
      // 4. XSS Protection (Legacy browsers)
      newHeaders.set("X-XSS-Protection", "1; mode=block");
      
      // 5. Privacy: Only send origin when cross-origin
      newHeaders.set("Referrer-Policy", "strict-origin-when-cross-origin");
      
      // 6. Permissions Policy: Block invasive features by default
      newHeaders.set("Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=()");

      return new Response(response.body, {
        status: response.status,
        statusText: response.statusText,
        headers: newHeaders
      });
    };

    // =================================================================================
    // HELPER: SAFE JSON INJECTION (Prevents XSS in Preloaded State)
    // =================================================================================
    const safeStringify = (data) => {
      if (data === undefined || data === null) return 'null';
      return JSON.stringify(data).replace(/</g, '\\u003c').replace(/>/g, '\\u003e').replace(/&/g, '\\u0026');
    };

    // ----------------------------------------------
    // 2. HIGH AVAILABILITY ROBOTS.TXT (Edge-Served)
    // ----------------------------------------------
    if (url.pathname === "/robots.txt") {
      const robotsTxt = `User-agent: *
Allow: /

# --- ENTERPRISE SEO: Allow Googlebot to fetch API data for rendering ---
Allow: /api/posts
Allow: /api/categories
Allow: /api/market
Allow: /api/news

# Disallow crawlers from indexing Auth, Admin, and internal search paths
Disallow: /api/auth/
Disallow: /api/contact/
Disallow: /api/admin/
Disallow: /dashboard/
Disallow: /?q=*
Disallow: /silent-check-sso.html
Disallow: /login

# Sitemap Index
Sitemap: ${FRONTEND_URL}/sitemap.xml`;

      return new Response(robotsTxt, {
        headers: {
          "Content-Type": "text/plain; charset=utf-8",
          "Cache-Control": "public, max-age=86400"
        }
      });
    }

    // ----------------------------------------------
    // 3. FLATTENED SITEMAP AGGREGATOR (NEW LOGIC)
    // ----------------------------------------------
    
    // A. ROOT SITEMAP INDEX (/sitemap.xml)
    if (url.pathname === '/sitemap.xml') {
        return handleFlattenedSitemapIndex(env, FRONTEND_URL, BACKEND_URL, ctx);
    }

    // B. [NEW] STATIC SITEMAP GENERATOR (/sitemap-static.xml)
    // Generated internally to guarantee existence.
    if (url.pathname === '/sitemap-static.xml') {
        return handleStaticSitemap(FRONTEND_URL);
    }

    // C. DYNAMIC CHILD SITEMAPS PROXY
    // Matches: /sitemap-dynamic/blog/0.xml
    if (url.pathname.startsWith('/sitemap-dynamic/')) {
        const parts = url.pathname.split('/');
        // Expected: /sitemap-dynamic/{type}/{file}
        if (parts.length >= 4) {
            const type = parts[2];
            const filename = parts[3];
            const backendPath = `/api/public/sitemap/${type}/${filename}`;
            return fetchBackendWithCache(BACKEND_URL + backendPath, ctx);
        }
    }

    // ----------------------------------------------
    // 4. API PROXY + IMAGE ACCELERATION (NEW LOGIC)
    // ----------------------------------------------
    if (url.pathname.startsWith("/api")) {
      const targetUrl = new URL(request.url);
      targetUrl.hostname = backendConfig.hostname;
      targetUrl.protocol = backendConfig.protocol;

      const proxyReq = new Request(targetUrl.toString(), {
        headers: enhancedHeaders, 
        method: request.method,
        body: request.body,
        redirect: request.redirect
      });

      // --- [NEW] IMAGE ACCELERATION (FREE TIER CACHE) ---
      // Caches API images at the edge to fix "loading too slow"
      if (url.pathname.match(/\.(jpg|jpeg|png|gif|webp)$/) || url.pathname.includes("/uploads/")) {
        const cache = caches.default;
        const cacheKey = new Request(url.toString(), request);
        
        // 1. Check Cache
        let cachedResponse = await cache.match(cacheKey);
        if (cachedResponse) {
             const cachedRes = new Response(cachedResponse.body, cachedResponse);
             cachedRes.headers.set("X-Cache-Status", "HIT");
             return addSecurityHeaders(cachedRes);
        }

        // 2. Fetch from Backend
        const apiResp = await fetch(proxyReq);
        
        // 3. Store in Cache (if success)
        if (apiResp.ok) {
             const responseToCache = new Response(apiResp.body, apiResp);
             // Cache for 1 year (Immutable)
             responseToCache.headers.set("Cache-Control", "public, max-age=31536000, immutable");
             responseToCache.headers.set("X-Cache-Status", "MISS");
             
             ctx.waitUntil(cache.put(cacheKey, responseToCache.clone()));
             return addSecurityHeaders(responseToCache);
        }
        return apiResp;
      }
      // --- END IMAGE ACCELERATION ---

      const apiResp = await fetch(proxyReq);
      return apiResp;
    }

    // ----------------------------------------------
    // 5. STATIC ASSETS
    // ----------------------------------------------
    if (url.pathname.match(/\.(jpg|jpeg|png|gif|webp|css|js|json|ico|xml)$/)) {
      const assetResp = await fetch(baseEnhancedRequest);
      return addSecurityHeaders(assetResp);
    }

    // ----------------------------------------------
    // 6. FETCH HTML SHELL WITH CACHING
    // ----------------------------------------------
    let response;
    const cacheKey = new Request(url.origin + "/", request);
    const cache = caches.default;

    try {
      response = await fetch(baseEnhancedRequest);

      if (response.ok) {
        const clone = response.clone();
        const cacheHeaders = new Headers(clone.headers);
        cacheHeaders.set("Cache-Control", "public, max-age=3600");
        
        const responseToCache = new Response(clone.body, {
          status: clone.status,
          statusText: clone.statusText,
          headers: cacheHeaders
        });
        ctx.waitUntil(cache.put(cacheKey, responseToCache));
      }
    } catch (e) {
      response = null;
    }

    if (!response || response.status >= 500) {
      const cachedResponse = await cache.match(cacheKey);
      if (cachedResponse) {
        response = new Response(cachedResponse.body, cachedResponse);
        response.headers.set("X-Fallback-Source", "Worker-Cache");
      } else {
        if (!response) return new Response("Service Unavailable", { status: 503 });
      }
    }

    // =================================================================================
    // 7. SEO INTELLIGENCE & EDGE HYDRATION (ORIGINAL LOGIC RESTORED)
    // =================================================================================
    
    // SCENARIO A: HOMEPAGE
    if (url.pathname === "/" || url.pathname === "") {
      const pageTitle = "Treishvaam Finance (TreishFin) | Global Financial Analysis & News";
      const pageDesc = "Treishvaam Finance (TreishFin) provides real-time market data, financial news, and expert analysis. A subsidiary of Treishvaam Group.";
      
      const homeSchema = {
        "@context": "https://schema.org",
        "@type": "FinancialService",
        "name": "Treishvaam Finance",
        "alternateName": "TreishFin",
        "url": FRONTEND_URL + "/",
        "logo": "https://treishvaamgroup.com/logo512.webp",
        "image": "https://treishvaamgroup.com/logo512.webp",
        "description": pageDesc,
        "priceRange": "$$",
        "telephone": "+91 81785 29633",
        "email": "treishfin.treishvaamgroup@gmail.com",
        "address": {
          "@type": "PostalAddress",
          "streetAddress": "Electronic City",
          "addressLocality": "Bangalore",
          "addressRegion": "Karnataka",
          "postalCode": "560100",
          "addressCountry": "IN"
        },
        "sameAs": [
            "https://www.linkedin.com/company/treishvaamfinance",
            "https://twitter.com/treishvaamfinance",
            "https://x.com/treishvaamfinance",
            "https://www.instagram.com/treishvaamfinance"
        ],
        "contactPoint": {
          "@type": "ContactPoint",
          "contactType": "customer service",
          "telephone": "+91 81785 29633",
          "email": "treishfin.treishvaamgroup@gmail.com",
          "areaServed": "Global",
          "availableLanguage": "English"
        },
        "parentOrganization": {
          "@type": "Corporation",
          "name": "Treishvaam Group",
          "url": PARENT_ORG_URL,
          "email": "treishvaamgroup@gmail.com",
          "telephone": "+91 81785 29633",
          "logo": "https://treishvaamgroup.com/logo512.webp",
          "image": "https://treishvaamgroup.com/logo512.webp", 
          "sameAs": [
            "https://www.linkedin.com/company/treishvaamgroup",
            "https://twitter.com/treishvaamgroup",
            "https://x.com/treishvaamgroup",
            "https://www.instagram.com/treishvaamgroup"
          ],
          "address": {
            "@type": "PostalAddress",
            "streetAddress": "Electronic City",
            "addressLocality": "Bangalore",
            "addressRegion": "Karnataka",
            "postalCode": "560100",
            "addressCountry": "IN"
          }
        },
        "founder": {
          "@type": "Person",
          "name": "Amitsagar Kandpal",
          "jobTitle": "Founder & Chairman",
          "email": "callitask@gmail.com",
          "telephone": "+91 81785 29633",
          "url": "https://treishvaamgroup.com/",
          "sameAs": [
              "https://www.linkedin.com/in/amitsagarkandpal",
              "https://twitter.com/treishvaam",
              "https://x.com/treishvaam",
              "https://www.instagram.com/treishvaam"
          ]
        },
        "potentialAction": {
          "@type": "SearchAction",
          "target": `${FRONTEND_URL}/?q={search_term_string}`,
          "query-input": "required name=search_term_string"
        }
      };

      const rewritten = new HTMLRewriter()
        .on("title", { element(e) { e.setInnerContent(pageTitle); } })
        .on('meta[name="description"]', { element(e) { e.setAttribute("content", pageDesc); } })
        .on('meta[property="og:title"]', { element(e) { e.setAttribute("content", pageTitle); } })
        .on('meta[property="og:description"]', { element(e) { e.setAttribute("content", pageDesc); } })
        .on("head", {
          element(e) {
            e.append(`<script type="application/ld+json">${JSON.stringify(homeSchema)}</script>`, { html: true });
          }
        })
        .transform(response);
        
      return addSecurityHeaders(rewritten);
    }

    // SCENARIO B: STATIC PAGES
    const staticPages = {
      "/about": {
        title: "About Us | Treishfin",
        description: "Learn about Treishvaam Finance, our mission to democratize financial literacy, and our founder Amitsagar Kandpal.",
        image: `${FRONTEND_URL}/logo.webp`
      },
      "/vision": {
        title: "Treishfin · Our Vision",
        description: "To build a world where financial literacy is a universal skill. Explore the philosophy and roadmap driving Treishvaam Finance.",
        image: `${FRONTEND_URL}/logo.webp`
      },
      "/contact": {
        title: "Treishfin · Contact Us",
        description: "Have questions about financial markets or our platform? Get in touch with the Treishvaam Finance team today.",
        image: `${FRONTEND_URL}/logo.webp`
      }
    };

    if (staticPages[url.pathname]) {
      const pageData = staticPages[url.pathname];
      const pageSchema = {
        "@context": "https://schema.org",
        "@type": "WebPage",
        "name": pageData.title,
        "description": pageData.description,
        "url": FRONTEND_URL + url.pathname,
        "publisher": {
          "@type": "Organization",
          "name": "Treishvaam Finance",
          "parentOrganization": {
            "@type": "Corporation",
            "name": "Treishvaam Group"
          },
          "logo": { "@type": "ImageObject", "url": `${FRONTEND_URL}/logo.webp` }
        }
      };

      const rewritten = new HTMLRewriter()
        .on("title", { element(e) { e.setInnerContent(pageData.title); } })
        .on('meta[name="description"]', { element(e) { e.setAttribute("content", pageData.description); } })
        .on('meta[property="og:title"]', { element(e) { e.setAttribute("content", pageData.title); } })
        .on('meta[property="og:description"]', { element(e) { e.setAttribute("content", pageData.description); } })
        .on("head", {
          element(e) {
            e.append(`<script type="application/ld+json">${JSON.stringify(pageSchema)}</script>`, { html: true });
          }
        })
        .transform(response);
        
      return addSecurityHeaders(rewritten);
    }

    // SCENARIO C: BLOG POSTS (Strategy A: Materialized HTML + Strategy B: Edge Hydration Fallback)
    if (url.pathname.includes("/category/")) {
      const parts = url.pathname.split("/");
      const articleId = parts[parts.length - 1];
      const postSlug = parts.length >= 4 ? parts[parts.length - 2] : null;

      if (!articleId) return addSecurityHeaders(response);

      // --- STRATEGY A: Try to fetch Materialized HTML (Pre-rendered Body) ---
      if (postSlug) {
        try {
           const materializedUrl = `${BACKEND_URL}/api/uploads/posts/${postSlug}.html`;
           const matResp = await fetch(materializedUrl, { 
             headers: { "User-Agent": "Cloudflare-Worker-SEO-Fetcher" } 
           });
           
           if (matResp.ok) {
             const finalResp = new Response(matResp.body, {
               status: 200,
               headers: matResp.headers
             });
             
             finalResp.headers.set("Content-Type", "text/html; charset=utf-8");
             finalResp.headers.set("X-Source", "Materialized-HTML");
             finalResp.headers.set("Cache-Control", "public, max-age=3600");

             // FIX: INJECT BASE TAG
             const fixedResp = new HTMLRewriter()
               .on("head", { element(e) { e.prepend('<base href="/" />', { html: true }); } })
               .transform(finalResp);

             ctx.waitUntil(cache.put(request, fixedResp.clone()));
             return addSecurityHeaders(fixedResp);
           }
        } catch(e) {}
      }

      // --- STRATEGY B: Fallback to Edge Hydration ---
      const apiUrl = `${BACKEND_URL}/api/v1/posts/url/${articleId}`;

      try {
        const apiResp = await fetch(apiUrl, { headers: { "User-Agent": "Cloudflare-Worker-SEO-Bot" } });
        if (!apiResp.ok) return addSecurityHeaders(response);
        const post = await apiResp.json();

        const imageUrl = post.coverImageUrl 
            ? `${BACKEND_URL}/api/uploads/${post.coverImageUrl}.webp`
            : `${FRONTEND_URL}/logo.webp`;

        const authorName = post.authorName || post.author || "Treishvaam Team";

        const schema = {
             "@context": "https://schema.org",
             "@type": "NewsArticle",
             "headline": post.title,
             "image": [imageUrl],
             "datePublished": post.createdAt,
             "dateModified": post.updatedAt,
             "author": [{
                 "@type": "Person",
                 "name": authorName,
                 "url": `${FRONTEND_URL}/about`
             }],
             "publisher": {
                 "@type": "Organization",
                 "name": "Treishvaam Finance",
                 "logo": {
                   "@type": "ImageObject",
                   "url": `${FRONTEND_URL}/logo.webp`
                 }
             }
        };

        const rewritten = new HTMLRewriter()
          .on("title", { element(e) { e.setInnerContent(post.title + " | Treishfin"); } })
          .on('meta[name="description"]', { element(e) { e.setAttribute("content", post.metaDescription || post.title); } })
          .on("head", {
            element(e) {
              e.append(`<script type="application/ld+json">${JSON.stringify(schema)}</script>`, { html: true });
              e.append(
                `<script>window.__PRELOADED_STATE__ = ${safeStringify(post)};</script>`,
                { html: true }
              );
            }
          })
          .transform(response);
        
        rewritten.headers.set("X-Source", "Edge-Hydration");
        return addSecurityHeaders(rewritten);
      } catch (e) { return addSecurityHeaders(response); }
    }

    // SCENARIO D: MARKET DATA
    if (url.pathname.startsWith("/market/")) {
      const rawTicker = url.pathname.split("/market/")[1];
      if (!rawTicker) return addSecurityHeaders(response);

      const decodedTicker = decodeURIComponent(rawTicker);
      const safeTicker = encodeURIComponent(decodedTicker);
      const apiUrl = `${BACKEND_URL}/api/v1/market/widget?ticker=${safeTicker}`;

      try {
        const apiResp = await fetch(apiUrl, { headers: { "User-Agent": "Cloudflare-Worker-SEO-Bot" } });
        if (!apiResp.ok) return addSecurityHeaders(response);
        const marketData = await apiResp.json();
        const quote = marketData.quoteData;

        if (!quote) return addSecurityHeaders(response);

        const pageTitle = `${quote.name} (${quote.ticker}) Price, News & Analysis | Treishfin`;
        const pageDesc = `Real-time stock price for ${quote.name} (${quote.ticker}). Market cap: ${quote.marketCap}. Detailed financial analysis on Treishvaam Finance.`;
        
        const logoUrl = quote.logoUrl || `${FRONTEND_URL}/logo.webp`;

        const schema = {
          "@context": "https://schema.org",
          "@type": "FinancialProduct",
          "name": quote.name,
          "tickerSymbol": quote.ticker,
          "exchangeTicker": quote.exchange || "NYSE", 
          "description": pageDesc,
          "url": `${FRONTEND_URL}/market/${rawTicker}`,
          "image": logoUrl,
          "currentExchangeRate": {
             "@type": "UnitPriceSpecification",
             "price": quote.price,
             "priceCurrency": "USD" 
          }
        };

        const rewritten = new HTMLRewriter()
          .on("title", { element(e) { e.setInnerContent(pageTitle); } })
          .on('meta[name="description"]', { element(e) { e.setAttribute("content", pageDesc); } })
          .on('meta[property="og:title"]', { element(e) { e.setAttribute("content", pageTitle); } })
          .on('meta[property="og:description"]', { element(e) { e.setAttribute("content", pageDesc); } })
          .on("head", {
            element(e) {
              e.append(`<script type="application/ld+json">${JSON.stringify(schema)}</script>`, { html: true });
              e.append(
                `<script>window.__PRELOADED_STATE__ = ${safeStringify(marketData)};</script>`,
                { html: true }
              );
            }
          })
          .transform(response);
          
        return addSecurityHeaders(rewritten);
      } catch (e) { return addSecurityHeaders(response); }
    }

    return addSecurityHeaders(response);
  }
};

// =================================================================================
// 8. HELPER FUNCTIONS
// =================================================================================

/**
 * GENERATES FLATTENED SITEMAP INDEX
 * Uses Cache API to survive Backend Outages.
 */
async function handleFlattenedSitemapIndex(env, frontendUrl, backendUrl, ctx) {
    const cache = caches.default;
    const cacheKey = new Request(`${frontendUrl}/sitemap-metadata-cache`); // Custom key
    
    let metadata = null;

    // 1. Try Cache
    const cachedMeta = await cache.match(cacheKey);
    if (cachedMeta) {
        try { metadata = await cachedMeta.json(); } catch(e){}
    }

    // 2. If no cache, fetch from Backend
    if (!metadata) {
        try {
            const resp = await fetch(`${backendUrl}/api/public/sitemap/meta`, {
                headers: { 'User-Agent': 'Cloudflare-Worker-Sitemap' }
            });
            if (resp.ok) {
                metadata = await resp.json();
                // Cache for 24 hours
                const metaResponse = new Response(JSON.stringify(metadata), {
                    headers: { "Cache-Control": "public, max-age=86400" }
                });
                ctx.waitUntil(cache.put(cacheKey, metaResponse));
            }
        } catch (e) {
            console.error("Backend Metadata Fetch Failed", e);
        }
    }

    // 3. Construct FLATTENED XML
    let xml = `<?xml version="1.0" encoding="UTF-8"?>
<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <sitemap>
    <loc>${frontendUrl}/sitemap-static.xml</loc>
  </sitemap>`;

    // Append Dynamic Children directly (Flattening)
    if (metadata) {
        if (metadata.blogs) {
            metadata.blogs.forEach(file => {
                xml += `
  <sitemap>
    <loc>${frontendUrl}${file}</loc>
  </sitemap>`;
            });
        }
        if (metadata.markets) {
            metadata.markets.forEach(file => {
                xml += `
  <sitemap>
    <loc>${frontendUrl}${file}</loc>
  </sitemap>`;
            });
        }
    }

    xml += `
</sitemapindex>`;

    return new Response(xml, {
        headers: { 
            "Content-Type": "application/xml",
            "Cache-Control": "public, max-age=3600"
        }
    });
}

/**
 * [NEW] GENERATES STATIC SITEMAP (Zero-Dependency)
 * Guaranteed to exist regardless of frontend/backend state.
 */
function handleStaticSitemap(frontendUrl) {
    // Current date for LastMod
    const today = new Date().toISOString().split('T')[0];

    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url>
    <loc>${frontendUrl}/</loc>
    <changefreq>daily</changefreq>
    <priority>1.0</priority>
  </url>
  <url>
    <loc>${frontendUrl}/about</loc>
    <changefreq>monthly</changefreq>
    <priority>0.8</priority>
  </url>
  <url>
    <loc>${frontendUrl}/vision</loc>
    <changefreq>monthly</changefreq>
    <priority>0.8</priority>
  </url>
  <url>
    <loc>${frontendUrl}/contact</loc>
    <changefreq>monthly</changefreq>
    <priority>0.8</priority>
  </url>
  <url>
    <loc>${frontendUrl}/businesses</loc>
    <changefreq>monthly</changefreq>
    <priority>0.8</priority>
  </url>
  <url>
    <loc>${frontendUrl}/sustainability</loc>
    <changefreq>monthly</changefreq>
    <priority>0.7</priority>
  </url>
  <url>
    <loc>${frontendUrl}/investors</loc>
    <changefreq>monthly</changefreq>
    <priority>0.7</priority>
  </url>
  <url>
    <loc>${frontendUrl}/terms</loc>
    <changefreq>yearly</changefreq>
    <priority>0.3</priority>
  </url>
  <url>
    <loc>${frontendUrl}/privacy</loc>
    <changefreq>yearly</changefreq>
    <priority>0.3</priority>
  </url>
</urlset>`;

    return new Response(xml, {
        headers: {
            "Content-Type": "application/xml",
            "Cache-Control": "public, max-age=86400" // Cache for 1 day
        }
    });
}

/**
 * Fetch helper with Caching for Sitemap Files
 */
async function fetchBackendWithCache(url, ctx) {
    const cache = caches.default;
    const cacheKey = new Request(url);
    
    // 1. Check Cache
    const cached = await cache.match(cacheKey);
    if (cached) return cached;

    // 2. Fetch
    try {
        const resp = await fetch(url);
        if (resp.ok) {
            // Cache for 1 hour
            const resToCache = new Response(resp.body, resp);
            resToCache.headers.set("Cache-Control", "public, max-age=3600");
            ctx.waitUntil(cache.put(cacheKey, resToCache.clone()));
            return resToCache;
        }
    } catch(e) {}

    return new Response("Sitemap segment unavailable", { status: 404 });
}