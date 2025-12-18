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
    // EXPLANATION FOR AI/DEV:
    // We do NOT serve static files. We proxy these requests to the Spring Boot
    // 'SitemapController'.
    //
    // CACHING STRATEGY:
    // - Edge Cache: 60 seconds.
    // - Why? If a bot hits us 1000 times/sec, Cloudflare answers 999 times.
    //   Only 1 request hits the Backend DB. This prevents DDoS/Crash.
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

        // --- ENTERPRISE EDGE CACHE ---
        // Cache at the Edge for 60 seconds to protect the backend DB.
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
    const cacheKey = new Request(url.origin + "/", request); // Cache based on root URL
    const cache = caches.default;

    try {
      // A. Try fetching fresh content from Backend
      response = await fetch(request);

      // If backend is healthy (200 OK), update the cache silently
      if (response.ok) {
        const clone = response.clone();
        const cacheHeaders = new Headers(clone.headers);
        cacheHeaders.set("Cache-Control", "public, max-age=3600"); // Cache for 1 hour
        
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

    // B. Failover: If fetch failed or returned 5xx, try cache
    if (!response || response.status >= 500) {
      const cachedResponse = await cache.match(cacheKey);
      if (cachedResponse) {
        response = new Response(cachedResponse.body, cachedResponse);
        response.headers.set("X-Fallback-Source", "Worker-Cache");
      } else {
        if (!response) return new Response("Service Unavailable - Backend Down & No Cache", { status: 503 });
      }
    }

    // ------------------------------------------------------
    // SCENARIO A: HOMEPAGE SEO
    // ------------------------------------------------------
    if (url.pathname === "/" || url.pathname === "") {
      const pageTitle = "Treishfin · Treishvaam Finance | Financial News & Analysis";
      const pageDesc = "Stay ahead with the latest financial news, market updates, and expert analysis from Treishvaam Finance. Your daily source for insights on stocks, crypto, and trading.";
      const imageUrl = "https://treishfin.treishvaamgroup.com/logo.webp";
      const canonicalUrl = "https://treishfin.treishvaamgroup.com/";

      const homeSchema = {
        "@context": "https://schema.org",
        "@type": "WebSite",
        "name": "Treishvaam Finance",
        "url": canonicalUrl,
        "potentialAction": {
          "@type": "SearchAction",
          "target": {
            "@type": "EntryPoint",
            "urlTemplate": "https://treishfin.treishvaamgroup.com/?q={search_term_string}"
          },
          "query-input": "required name=search_term_string"
        }
      };

      return new HTMLRewriter()
        .on("title", { element(e) { e.setInnerContent(pageTitle); } })
        .on('meta[name="description"]', { element(e) { e.setAttribute("content", pageDesc); } })
        .on('meta[property="og:title"]', { element(e) { e.setAttribute("content", pageTitle); } })
        .on('meta[property="og:description"]', { element(e) { e.setAttribute("content", pageDesc); } })
        .on('meta[property="og:image"]', { element(e) { e.setAttribute("content", imageUrl); } })
        .on('meta[property="og:url"]', { element(e) { e.setAttribute("content", canonicalUrl); } })
        .on('meta[name="twitter:title"]', { element(e) { e.setAttribute("content", pageTitle); } })
        .on('meta[name="twitter:description"]', { element(e) { e.setAttribute("content", pageDesc); } })
        .on('meta[name="twitter:image"]', { element(e) { e.setAttribute("content", imageUrl); } })
        .on('link[rel="canonical"]', { element(e) { e.setAttribute("href", canonicalUrl); } })
        .on("head", {
          element(e) {
            const script = `<script type="application/ld+json">${JSON.stringify(homeSchema)}</script>`;
            e.append(script, { html: true });
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
        .on('meta[property="og:image"]', { element(e) { e.setAttribute("content", pageData.image); } })
        .on('meta[property="og:url"]', { element(e) { e.setAttribute("content", canonicalUrl); } })
        .on('meta[name="twitter:title"]', { element(e) { e.setAttribute("content", pageData.title); } })
        .on('meta[name="twitter:description"]', { element(e) { e.setAttribute("content", pageData.description); } })
        .on('meta[name="twitter:image"]', { element(e) { e.setAttribute("content", pageData.image); } })
        .on('link[rel="canonical"]', { element(e) { e.setAttribute("href", canonicalUrl); } })
        .on("head", {
          element(e) {
            const script = `<script type="application/ld+json">${JSON.stringify(pageSchema)}</script>`;
            e.append(script, { html: true });
          }
        })
        .transform(response);
    }

    // ------------------------------------------------------
    // SCENARIO C: BLOG POSTS (Advanced Schema Injection)
    // ------------------------------------------------------
    if (url.pathname.includes("/category/")) {
      const parts = url.pathname.split("/");
      const articleId = parts[parts.length - 1];

      if (!articleId) return response;

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 2000);

      const apiUrl = `https://backend.treishvaamgroup.com/api/v1/posts/url/${articleId}`;

      try {
        const apiResp = await fetch(apiUrl, {
          headers: { "User-Agent": "Cloudflare-Worker-SEO-Bot" },
          signal: controller.signal
        });

        clearTimeout(timeoutId);

        if (!apiResp.ok) return response;

        const post = await apiResp.json();

        let authorName = post.author;
        if (authorName === "callitask@gmail.com" || !authorName) {
          authorName = "Treishvaam";
        }

        const pageTitle = `Treishfin · ${post.title}`;
        const pageDesc = post.metaDescription || post.customSnippet || post.title;
        const imageUrl = post.coverImageUrl
          ? `https://backend.treishvaamgroup.com/api/uploads/${post.coverImageUrl}.webp`
          : `https://treishfin.treishvaamgroup.com/logo.webp`;

        const categorySlug = post.category ? post.category.slug : "uncategorized";
        const categoryName = post.category ? post.category.name : "General";
        const canonicalUrl = `https://treishfin.treishvaamgroup.com/category/${categorySlug}/${post.userFriendlySlug}/${post.urlArticleId}`;

        const isNews = ['News', 'Markets', 'Crypto', 'Economy', 'Stocks'].includes(categoryName);
        const schemaType = isNews ? 'NewsArticle' : 'BlogPosting';

        const breadcrumbSchema = {
          "@context": "https://schema.org",
          "@type": "BreadcrumbList",
          "name": "Breadcrumbs",
          "itemListElement": [{
            "@type": "ListItem",
            "position": 1,
            "name": "Home",
            "item": "https://treishfin.treishvaamgroup.com/"
          }, {
            "@type": "ListItem",
            "position": 2,
            "name": categoryName,
            "item": `https://treishfin.treishvaamgroup.com/category/${categorySlug}`
          }, {
            "@type": "ListItem",
            "position": 3,
            "name": post.title,
            "item": canonicalUrl
          }]
        };

        const articleSchema = {
          "@context": "https://schema.org",
          "@type": schemaType,
          "mainEntityOfPage": { "@type": "WebPage", "@id": canonicalUrl },
          "headline": post.title,
          "image": {
            "@type": "ImageObject",
            "url": imageUrl,
            "width": 1200,
            "height": 675
          },
          "datePublished": post.createdAt,
          "dateModified": post.updatedAt || post.createdAt,
          "author": {
            "@type": "Person",
            "name": authorName,
            "url": "https://treishfin.treishvaamgroup.com/about",
            "jobTitle": "Financial Analyst"
          },
          "publisher": {
            "@type": "Organization",
            "name": "Treishvaam Finance",
            "logo": {
              "@type": "ImageObject",
              "url": "https://treishfin.treishvaamgroup.com/logo.webp",
              "width": 600,
              "height": 60
            }
          },
          "description": pageDesc,
          "keywords": post.keywords || "",
          "speakable": {
            "@type": "SpeakableSpecification",
            "cssSelector": ["h1", "article p"]
          }
        };

        const schemas = [breadcrumbSchema, articleSchema];

        const videoRegex = /src="https:\/\/(?:www\.)?youtube\.com\/embed\/([^"?]+)/;
        const match = post.content ? post.content.match(videoRegex) : null;

        if (match && match[1]) {
          const videoId = match[1];
          const videoSchema = {
            "@context": "https://schema.org",
            "@type": "VideoObject",
            "name": post.title,
            "description": pageDesc,
            "thumbnailUrl": imageUrl,
            "uploadDate": post.createdAt,
            "embedUrl": `https://www.youtube.com/embed/${videoId}`,
            "contentUrl": `https://www.youtube.com/watch?v=${videoId}`
          };
          schemas.push(videoSchema);
        }

        return new HTMLRewriter()
          .on("title", { element(e) { e.setInnerContent(pageTitle); } })
          .on('meta[name="description"]', { element(e) { e.setAttribute("content", pageDesc); } })
          .on('meta[property="og:title"]', { element(e) { e.setAttribute("content", post.title); } })
          .on('meta[property="og:description"]', { element(e) { e.setAttribute("content", pageDesc); } })
          .on('meta[property="og:image"]', { element(e) { e.setAttribute("content", imageUrl); } })
          .on('meta[property="og:url"]', { element(e) { e.setAttribute("content", canonicalUrl); } })
          .on('meta[name="twitter:title"]', { element(e) { e.setAttribute("content", post.title); } })
          .on('meta[name="twitter:description"]', { element(e) { e.setAttribute("content", pageDesc); } })
          .on('meta[name="twitter:image"]', { element(e) { e.setAttribute("content", imageUrl); } })
          .on('link[rel="canonical"]', { element(e) { e.setAttribute("href", canonicalUrl); } })

          .on("head", {
            element(e) {
              const schemaScript = `<script type="application/ld+json" id="server-schema">${JSON.stringify(schemas)}</script>`;
              const stateScript = `<script>window.__PRELOADED_STATE__ = ${JSON.stringify(post)};</script>`;
              e.append(schemaScript, { html: true });
              e.append(stateScript, { html: true });
            }
          })

          .on("body", {
            element(e) {
              const noscriptContent = `
                <noscript>
                  <div class="seo-content">
                    <h1>${post.title}</h1>
                    <p><strong>Published:</strong> ${post.createdAt}</p>
                    <p><strong>Author:</strong> ${authorName}</p>
                    <div class="article-body">
                      ${post.content}
                    </div>
                  </div>
                </noscript>
              `;
              e.append(noscriptContent, { html: true });
            }
          })
          .transform(response);
      } catch (e) {
        return response;
      }
    }

    return response;
  }
};