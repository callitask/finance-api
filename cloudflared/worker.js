export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // =================================================================================
    // 0. CONFIGURATION (Zero Trust / Environment Agnostic)
    // =================================================================================
    // Fallbacks provided only for safety; these should come from Cloudflare Variables.
    const BACKEND_URL = env.BACKEND_URL || "https://backend.treishvaamgroup.com";
    const FRONTEND_URL = env.FRONTEND_URL || "https://treishfin.treishvaamgroup.com";

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
    // 3. DYNAMIC SITEMAP PROXY
    // ----------------------------------------------
    if (
      url.pathname === "/sitemap.xml" ||
      url.pathname === "/sitemap-news.xml" ||
      url.pathname === "/feed.xml" ||
      url.pathname.startsWith("/sitemaps/")
    ) {
      const targetUrl = new URL(request.url);
      targetUrl.hostname = backendConfig.hostname;
      targetUrl.protocol = backendConfig.protocol;

      const sitemapHeaders = new Headers(enhancedHeaders);
      sitemapHeaders.set("Host", new URL(FRONTEND_URL).hostname);
      sitemapHeaders.set(
        "User-Agent",
        `Cloudflare-Worker-SitemapFetcher/2.0 (+${FRONTEND_URL})`
      );

      const proxyReq = new Request(targetUrl.toString(), {
        method: request.method,
        headers: sitemapHeaders,
        body: request.method === "GET" || request.method === "HEAD" ? undefined : await request.arrayBuffer(),
        redirect: "manual"
      });

      try {
        const backendResp = await fetch(proxyReq);
        const respHeaders = new Headers(backendResp.headers);

        if (url.pathname.endsWith(".xml")) {
          respHeaders.set("Content-Type", "text/xml; charset=utf-8");
        } else if (url.pathname === "/feed.xml") {
          respHeaders.set("Content-Type", "application/rss+xml; charset=utf-8");
        }

        respHeaders.set("Cache-Control", "public, max-age=60, s-maxage=60");

        const finalResp = new Response(await backendResp.arrayBuffer(), {
          status: backendResp.status,
          statusText: backendResp.statusText,
          headers: respHeaders
        });
        
        return addSecurityHeaders(finalResp);

      } catch (e) {
        return new Response("Service Unavailable", { status: 503 });
      }
    }

    // ----------------------------------------------
    // 4. API PROXY (Secure Routing)
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
    // 7. SEO INTELLIGENCE & EDGE HYDRATION
    // =================================================================================
    
    // SCENARIO A: HOMEPAGE
    if (url.pathname === "/" || url.pathname === "") {
      const pageTitle = "Treishfin · Treishvaam Finance | Financial News & Analysis";
      const pageDesc = "Stay ahead with the latest financial news, market updates, and expert analysis from Treishvaam Finance.";
      
      const homeSchema = {
        "@context": "https://schema.org",
        "@type": "WebSite",
        "name": "Treishvaam Finance",
        "url": FRONTEND_URL + "/",
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
        description: "Learn about Treishvaam Finance, our mission to democratize financial literacy, and our founder Amitsagar Kandpal (Treishvaam).",
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
      // Path format: /category/:categorySlug/:userFriendlySlug/:urlArticleId
      // parts: ["", "category", "cat-slug", "post-slug", "article-id"]
      // We need "post-slug" (index 3, or length-2) to find the file in MinIO/Nginx.
      
      const articleId = parts[parts.length - 1];
      const postSlug = parts.length >= 4 ? parts[parts.length - 2] : null;

      if (!articleId) return addSecurityHeaders(response);

      // --- STRATEGY A: Try to fetch Materialized HTML (Pre-rendered Body) ---
      // This file is generated by the Backend and stored in MinIO/Nginx
      if (postSlug) {
        try {
           // FIX: Removed '/v1' so this hits Nginx (Static File) instead of Spring Boot (API)
           const materializedUrl = `${BACKEND_URL}/api/uploads/posts/${postSlug}.html`;
           const matResp = await fetch(materializedUrl, { 
             headers: { "User-Agent": "Cloudflare-Worker-SEO-Fetcher" } 
           });
           
           if (matResp.ok) {
             // HIT! Serve the pre-baked HTML.
             // It already has <title>, meta tags, JSON-LD, and Full <body> content.
             const finalResp = new Response(matResp.body, {
               status: 200,
               headers: matResp.headers
             });
             
             // Ensure correct content type and source tag
             finalResp.headers.set("Content-Type", "text/html; charset=utf-8");
             finalResp.headers.set("X-Source", "Materialized-HTML");
             finalResp.headers.set("Cache-Control", "public, max-age=3600");

             // CACHE FIX: Store this successful HTML in Edge Cache
             ctx.waitUntil(cache.put(request, finalResp.clone()));
             
             return addSecurityHeaders(finalResp);
           }
        } catch(e) {
           // Ignore error and fall back to Strategy B
        }
      }

      // --- STRATEGY B: Fallback to Edge Hydration (Original Logic) ---
      const apiUrl = `${BACKEND_URL}/api/v1/posts/url/${articleId}`;

      try {
        const apiResp = await fetch(apiUrl, { headers: { "User-Agent": "Cloudflare-Worker-SEO-Bot" } });
        if (!apiResp.ok) return addSecurityHeaders(response);
        const post = await apiResp.json();

        // Schema.org Data
        const schema = {
             "@context": "https://schema.org",
             "@type": "NewsArticle",
             "headline": post.title,
             "image": [`${BACKEND_URL}/api/uploads/${post.coverImageUrl}.webp`],
             "datePublished": post.createdAt,
             "dateModified": post.updatedAt,
             "author": [{
                 "@type": "Person",
                 "name": post.author || "Treishvaam",
                 "url": `${FRONTEND_URL}/about`
             }]
        };

        const rewritten = new HTMLRewriter()
          .on("title", { element(e) { e.setInnerContent(post.title + " | Treishfin"); } })
          .on('meta[name="description"]', { element(e) { e.setAttribute("content", post.metaDescription || post.title); } })
          .on("head", {
            element(e) {
              // 1. Inject JSON-LD Schema
              e.append(`<script type="application/ld+json">${JSON.stringify(schema)}</script>`, { html: true });
              
              // 2. EDGE HYDRATION: Inject the post data so React doesn't have to fetch it again
              e.append(
                `<script>window.__PRELOADED_STATE__ = ${safeStringify(post)};</script>`,
                { html: true }
              );
            }
          })
          .transform(response);
          
        return addSecurityHeaders(rewritten);
      } catch (e) { return addSecurityHeaders(response); }
    }

    // SCENARIO D: MARKET DATA (With Edge Hydration)
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
        
        const schema = {
          "@context": "https://schema.org",
          "@type": "FinancialProduct",
          "name": quote.name,
          "tickerSymbol": quote.ticker,
          "exchangeTicker": quote.exchange || "NYSE", 
          "description": pageDesc,
          "url": `${FRONTEND_URL}/market/${rawTicker}`,
          "image": quote.logoUrl || `${FRONTEND_URL}/logo.webp`,
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
              // 1. Inject JSON-LD
              e.append(`<script type="application/ld+json">${JSON.stringify(schema)}</script>`, { html: true });
              
              // 2. EDGE HYDRATION: Inject market data for instant rendering
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