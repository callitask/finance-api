export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // ----------------------------------------------
    // 0. HIGH AVAILABILITY ROBOTS.TXT (Edge-Served)
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

# Sitemap Index (Points to the Dynamic Controller)
Sitemap: https://treishfin.treishvaamgroup.com/sitemap.xml`;

      return new Response(robotsTxt, {
        headers: {
          "Content-Type": "text/plain; charset=utf-8",
          "Cache-Control": "public, max-age=86400"
        }
      });
    }

    // ----------------------------------------------
    // 1. DYNAMIC SITEMAP PROXY (Enterprise Edge Caching)
    // ----------------------------------------------
    if (
      url.pathname === "/sitemap.xml" ||
      url.pathname === "/sitemap-news.xml" ||
      url.pathname === "/feed.xml" ||
      url.pathname.startsWith("/sitemaps/")
    ) {
      const backendUrl = new URL(request.url);
      backendUrl.hostname = "backend.treishvaamgroup.com";
      backendUrl.protocol = "https:";

      const outgoingHeaders = new Headers(request.headers);
      outgoingHeaders.set("Host", "treishfin.treishvaamgroup.com");
      outgoingHeaders.set(
        "User-Agent",
        "Cloudflare-Worker-SitemapFetcher/2.0 (+https://treishfin.treishvaamgroup.com)"
      );

      const proxyReq = new Request(backendUrl.toString(), {
        method: request.method,
        headers: outgoingHeaders,
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

        return new Response(await backendResp.arrayBuffer(), {
          status: backendResp.status,
          statusText: backendResp.statusText,
          headers: respHeaders
        });
      } catch (e) {
        return new Response("Service Unavailable", { status: 503 });
      }
    }

    // ----------------------------------------------
    // 2. BYPASS STATIC ASSETS
    // ----------------------------------------------
    if (
      url.pathname.startsWith("/api") ||
      url.pathname.match(/\.(jpg|jpeg|png|gif|webp|css|js|json|ico|xml)$/)
    ) {
      return fetch(request);
    }

    // ----------------------------------------------
    // 3. FETCH HTML SHELL WITH FAILOVER CACHING
    // ----------------------------------------------
    let response;
    const cacheKey = new Request(url.origin + "/", request);
    const cache = caches.default;

    try {
      response = await fetch(request);
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

    // ------------------------------------------------------
    // SCENARIO A: HOMEPAGE SEO
    // ------------------------------------------------------
    if (url.pathname === "/" || url.pathname === "") {
      const pageTitle = "Treishfin · Treishvaam Finance | Financial News & Analysis";
      const pageDesc = "Stay ahead with the latest financial news, market updates, and expert analysis from Treishvaam Finance.";
      const imageUrl = "https://treishfin.treishvaamgroup.com/logo.webp";
      const canonicalUrl = "https://treishfin.treishvaamgroup.com/";

      const homeSchema = {
        "@context": "https://schema.org",
        "@type": "WebSite",
        "name": "Treishvaam Finance",
        "url": canonicalUrl,
        "potentialAction": {
          "@type": "SearchAction",
          "target": "https://treishfin.treishvaamgroup.com/?q={search_term_string}",
          "query-input": "required name=search_term_string"
        }
      };

      return new HTMLRewriter()
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
    }

    // ------------------------------------------------------
    // SCENARIO B: STATIC PAGES SEO
    // ------------------------------------------------------
    const staticPages = {
      "/about": {
        title: "About Us | Treishfin",
        description: "Learn about Treishvaam Finance, our mission to democratize financial literacy, and our founder Amitsagar Kandpal (Treishvaam).",
        image: "https://treishfin.treishvaamgroup.com/logo.webp"
      },
      "/vision": {
        title: "Treishfin · Our Vision",
        description: "To build a world where financial literacy is a universal skill. Explore the philosophy and roadmap driving Treishvaam Finance.",
        image: "https://treishfin.treishvaamgroup.com/logo.webp"
      },
      "/contact": {
        title: "Treishfin · Contact Us",
        description: "Have questions about financial markets or our platform? Get in touch with the Treishvaam Finance team today.",
        image: "https://treishfin.treishvaamgroup.com/logo.webp"
      }
    };

    if (staticPages[url.pathname]) {
      const pageData = staticPages[url.pathname];
      const canonicalUrl = "https://treishfin.treishvaamgroup.com" + url.pathname;

      const pageSchema = {
        "@context": "https://schema.org",
        "@type": "WebPage",
        "name": pageData.title,
        "description": pageData.description,
        "url": canonicalUrl,
        "publisher": {
          "@type": "Organization",
          "name": "Treishvaam Finance",
          "logo": { "@type": "ImageObject", "url": "https://treishfin.treishvaamgroup.com/logo.webp" }
        }
      };

      return new HTMLRewriter()
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
    }

    // ------------------------------------------------------
    // SCENARIO C: BLOG POSTS
    // ------------------------------------------------------
    if (url.pathname.includes("/category/")) {
      const parts = url.pathname.split("/");
      const articleId = parts[parts.length - 1];
      if (!articleId) return response;

      const apiUrl = `https://backend.treishvaamgroup.com/api/v1/posts/url/${articleId}`;

      try {
        const apiResp = await fetch(apiUrl, { headers: { "User-Agent": "Cloudflare-Worker-SEO-Bot" } });
        if (!apiResp.ok) return response;
        const post = await apiResp.json();

        const schema = {
             "@context": "https://schema.org",
             "@type": "NewsArticle",
             "headline": post.title,
             "image": [`https://backend.treishvaamgroup.com/api/uploads/${post.coverImageUrl}.webp`],
             "datePublished": post.createdAt,
             "dateModified": post.updatedAt,
             "author": [{
                 "@type": "Person",
                 "name": post.author || "Treishvaam",
                 "url": "https://treishfin.treishvaamgroup.com/about"
             }]
        };

        return new HTMLRewriter()
          .on("title", { element(e) { e.setInnerContent(post.title + " | Treishfin"); } })
          .on('meta[name="description"]', { element(e) { e.setAttribute("content", post.metaDescription || post.title); } })
          .on("head", {
            element(e) {
              e.append(`<script type="application/ld+json">${JSON.stringify(schema)}</script>`, { html: true });
            }
          })
          .transform(response);
      } catch (e) { return response; }
    }

    // ------------------------------------------------------
    // SCENARIO D: MARKET DATA (FIXED FOR SPECIAL CHARS)
    // ------------------------------------------------------
    if (url.pathname.startsWith("/market/")) {
      const rawTicker = url.pathname.split("/market/")[1];
      if (!rawTicker) return response;

      // FIX: Ensure we handle URL-encoded tickers properly (e.g., %5EDJI -> ^DJI)
      // We decode first to get the real symbol, then re-encode for the query param
      const decodedTicker = decodeURIComponent(rawTicker);
      const safeTicker = encodeURIComponent(decodedTicker);

      const apiUrl = `https://backend.treishvaamgroup.com/api/v1/market/widget?ticker=${safeTicker}`;

      try {
        const apiResp = await fetch(apiUrl, { headers: { "User-Agent": "Cloudflare-Worker-SEO-Bot" } });
        if (!apiResp.ok) return response;
        const marketData = await apiResp.json();
        const quote = marketData.quoteData;

        if (!quote) return response;

        const pageTitle = `${quote.name} (${quote.ticker}) Price, News & Analysis | Treishfin`;
        const pageDesc = `Real-time stock price for ${quote.name} (${quote.ticker}). Market cap: ${quote.marketCap}. Detailed financial analysis on Treishvaam Finance.`;
        
        const schema = {
          "@context": "https://schema.org",
          "@type": "FinancialProduct",
          "name": quote.name,
          "tickerSymbol": quote.ticker,
          "exchangeTicker": quote.exchange || "NYSE", 
          "description": pageDesc,
          "url": `https://treishfin.treishvaamgroup.com/market/${rawTicker}`,
          "image": quote.logoUrl || "https://treishfin.treishvaamgroup.com/logo.webp",
          "currentExchangeRate": {
             "@type": "UnitPriceSpecification",
             "price": quote.price,
             "priceCurrency": "USD" 
          }
        };

        return new HTMLRewriter()
          .on("title", { element(e) { e.setInnerContent(pageTitle); } })
          .on('meta[name="description"]', { element(e) { e.setAttribute("content", pageDesc); } })
          .on('meta[property="og:title"]', { element(e) { e.setAttribute("content", pageTitle); } })
          .on('meta[property="og:description"]', { element(e) { e.setAttribute("content", pageDesc); } })
          .on("head", {
            element(e) {
              e.append(`<script type="application/ld+json">${JSON.stringify(schema)}</script>`, { html: true });
            }
          })
          .transform(response);
      } catch (e) { return response; }
    }

    return response;
  }
};