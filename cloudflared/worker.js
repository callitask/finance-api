export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // =================================================================================
    // 1. UNIVERSAL HEADER INJECTION (CRITICAL FIX)
    // =================================================================================
    // We create an "Enhanced Request" immediately. This ensures that EVERY downstream 
    // fetch (API, HTML, Static) carries the Geo-Location headers.
    
    const cf = request.cf || {};
    const newHeaders = new Headers(request.headers);
    
    // Geo Intelligence Headers
    newHeaders.set("X-Visitor-City", cf.city || "Unknown");
    newHeaders.set("X-Visitor-Region", cf.region || "Unknown");
    newHeaders.set("X-Visitor-Country", cf.country || "Unknown");
    newHeaders.set("X-Visitor-Continent", cf.continent || "Unknown");
    newHeaders.set("X-Visitor-Timezone", cf.timezone || "UTC");
    newHeaders.set("X-Visitor-Lat", cf.latitude || "0");
    newHeaders.set("X-Visitor-Lon", cf.longitude || "0");
    newHeaders.set("X-Visitor-Device-Colo", cf.colo || "Unknown");

    // Standardize Host header for Backend communication if needed
    // (We keep the original host for the frontend, but the backend proxy will need adjustment below)

    // =================================================================================
    // 2. ROBOTS.TXT (Edge Served)
    // =================================================================================
    if (url.pathname === "/robots.txt") {
      const robotsTxt = `User-agent: *
Allow: /
Allow: /api/posts
Allow: /api/categories
Allow: /api/market
Allow: /api/news
Disallow: /api/auth/
Disallow: /api/contact/
Disallow: /api/admin/
Disallow: /dashboard/
Disallow: /?q=*
Disallow: /silent-check-sso.html
Disallow: /login
Sitemap: https://treishfin.treishvaamgroup.com/sitemap.xml`;

      return new Response(robotsTxt, {
        headers: {
          "Content-Type": "text/plain; charset=utf-8",
          "Cache-Control": "public, max-age=86400"
        }
      });
    }

    // =================================================================================
    // 3. BACKEND API & SITEMAP PROXY
    // =================================================================================
    // Routes: /api/*, /sitemap*, /feed.xml
    if (
      url.pathname.startsWith("/api") || 
      url.pathname.startsWith("/sitemap") || 
      url.pathname === "/feed.xml"
    ) {
      const backendUrl = new URL(request.url);
      backendUrl.hostname = "backend.treishvaamgroup.com";
      backendUrl.protocol = "https:";

      // Create proxy request using the ENHANCED headers
      const proxyReq = new Request(backendUrl.toString(), {
        headers: newHeaders, // Passes the injected City/Region headers
        method: request.method,
        body: request.body,
        redirect: request.redirect
      });

      // Important: Ensure the Host header matches the target backend if required, 
      // or pass the original to let the backend know the public domain.
      // Usually, for a proxy, we want the backend to know it's serving 'treishfin.treishvaamgroup.com'
      proxyReq.headers.set("Host", "treishfin.treishvaamgroup.com");
      proxyReq.headers.set("X-Forwarded-Host", "treishfin.treishvaamgroup.com");

      try {
        const backendResp = await fetch(proxyReq);
        
        // Add caching headers for non-API static XMLs if needed
        const respHeaders = new Headers(backendResp.headers);
        if (url.pathname.endsWith(".xml") || url.pathname === "/feed.xml") {
           respHeaders.set("Cache-Control", "public, max-age=60, s-maxage=60");
        }

        return new Response(backendResp.body, {
          status: backendResp.status,
          statusText: backendResp.statusText,
          headers: respHeaders
        });
      } catch (e) {
        return new Response("Service Unavailable", { status: 503 });
      }
    }

    // =================================================================================
    // 4. STATIC ASSETS & HTML SHELL
    // =================================================================================
    
    // Create the request for the origin (Frontend) using the ENHANCED headers
    const enhancedRequest = new Request(request.url, {
      headers: newHeaders,
      method: request.method,
      body: request.body,
      redirect: request.redirect
    });

    // A. Static Assets (Images, JS, CSS) - Bypass worker caching, let CF standard caching handle it
    if (url.pathname.match(/\.(jpg|jpeg|png|gif|webp|css|js|json|ico|xml)$/)) {
      return fetch(enhancedRequest);
    }

    // B. HTML Page Handling (React App + SEO)
    let response;
    const cacheKey = new Request(url.origin + "/", request); // Cache key based on root
    const cache = caches.default;

    try {
      // Try fetching fresh HTML from origin
      response = await fetch(enhancedRequest);
      
      // If valid HTML, cache it briefly
      if (response.ok && response.headers.get("content-type")?.includes("text/html")) {
        const clone = response.clone();
        const cacheHeaders = new Headers(clone.headers);
        cacheHeaders.set("Cache-Control", "public, max-age=300"); // 5 min cache for HTML
        ctx.waitUntil(cache.put(cacheKey, new Response(clone.body, { headers: cacheHeaders })));
      }
    } catch (e) {
      response = null;
    }

    // Fallback to cache if origin fails
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
    // 5. SEO INJECTION (HTMLRewriter)
    // =================================================================================
    
    // SCENARIO A: HOMEPAGE
    if (url.pathname === "/" || url.pathname === "") {
      const homeSchema = {
        "@context": "https://schema.org",
        "@type": "WebSite",
        "name": "Treishvaam Finance",
        "url": "https://treishfin.treishvaamgroup.com/",
        "potentialAction": {
          "@type": "SearchAction",
          "target": "https://treishfin.treishvaamgroup.com/?q={search_term_string}",
          "query-input": "required name=search_term_string"
        }
      };
      
      return new HTMLRewriter()
        .on("title", { element(e) { e.setInnerContent("Treishfin · Treishvaam Finance | Financial News & Analysis"); } })
        .on('meta[name="description"]', { element(e) { e.setAttribute("content", "Stay ahead with the latest financial news, market updates, and expert analysis from Treishvaam Finance."); } })
        .on("head", { element(e) { e.append(`<script type="application/ld+json">${JSON.stringify(homeSchema)}</script>`, { html: true }); } })
        .transform(response);
    }

    // SCENARIO B: STATIC PAGES
    const staticPages = {
      "/about": { title: "About Us | Treishfin", desc: "Learn about Treishvaam Finance, our mission to democratize financial literacy." },
      "/vision": { title: "Treishfin · Our Vision", desc: "To build a world where financial literacy is a universal skill." },
      "/contact": { title: "Treishfin · Contact Us", desc: "Have questions about financial markets or our platform? Get in touch." }
    };

    if (staticPages[url.pathname]) {
      const p = staticPages[url.pathname];
      return new HTMLRewriter()
        .on("title", { element(e) { e.setInnerContent(p.title); } })
        .on('meta[name="description"]', { element(e) { e.setAttribute("content", p.desc); } })
        .transform(response);
    }

    // SCENARIO C: BLOG POSTS
    if (url.pathname.includes("/category/")) {
      const parts = url.pathname.split("/");
      const articleId = parts[parts.length - 1];
      if (articleId) {
        try {
          const apiUrl = `https://backend.treishvaamgroup.com/api/v1/posts/url/${articleId}`;
          const apiResp = await fetch(apiUrl, { headers: { "User-Agent": "Cloudflare-Worker-SEO-Bot" } });
          if (apiResp.ok) {
            const post = await apiResp.json();
            const schema = {
                 "@context": "https://schema.org", "@type": "NewsArticle",
                 "headline": post.title,
                 "image": [`https://backend.treishvaamgroup.com/api/uploads/${post.coverImageUrl}.webp`],
                 "datePublished": post.createdAt,
                 "author": [{ "@type": "Person", "name": post.author || "Treishvaam" }]
            };
            return new HTMLRewriter()
              .on("title", { element(e) { e.setInnerContent(post.title + " | Treishfin"); } })
              .on('meta[name="description"]', { element(e) { e.setAttribute("content", post.metaDescription || post.title); } })
              .on("head", { element(e) { e.append(`<script type="application/ld+json">${JSON.stringify(schema)}</script>`, { html: true }); } })
              .transform(response);
          }
        } catch (e) { /* ignore API fail, serve raw HTML */ }
      }
    }

    // SCENARIO D: MARKET DATA
    if (url.pathname.startsWith("/market/")) {
      const rawTicker = url.pathname.split("/market/")[1];
      if (rawTicker) {
        try {
          const safeTicker = encodeURIComponent(decodeURIComponent(rawTicker));
          const apiUrl = `https://backend.treishvaamgroup.com/api/v1/market/widget?ticker=${safeTicker}`;
          const apiResp = await fetch(apiUrl, { headers: { "User-Agent": "Cloudflare-Worker-SEO-Bot" } });
          if (apiResp.ok) {
            const data = await apiResp.json();
            const quote = data.quoteData;
            if (quote) {
               return new HTMLRewriter()
                .on("title", { element(e) { e.setInnerContent(`${quote.name} (${quote.ticker}) Price & News | Treishfin`); } })
                .on('meta[name="description"]', { element(e) { e.setAttribute("content", `Real-time stock price for ${quote.name} (${quote.ticker}). Market cap: ${quote.marketCap}.`); } })
                .transform(response);
            }
          }
        } catch (e) { /* ignore */ }
      }
    }

    return response;
  }
};