#!/bin/bash
# =================================================================================
# TREISHVAAM FINANCE - SEO ARCHITECTURE VERIFICATION TOOL
# =================================================================================
# Usage: ./verify_seo.sh <user-friendly-slug>
# Example: ./verify_seo.sh my-amazing-blog-post
#
# What this checks:
# 1. Did Backend generate the HTML file? (Checks MinIO public URL)
# 2. Does Cloudflare Worker find and serve that file? (Checks Headers)
# 3. Is the HTML valid for SEO? (Checks for Body Content)
# 4. Is the HTML valid for React? (Checks for Hydration State)
# =================================================================================

SLUG=$1
BACKEND_URL="https://backend.treishvaamgroup.com"
FRONTEND_URL="https://treishfin.treishvaamgroup.com"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

if [ -z "$SLUG" ]; then
  echo "Usage: ./verify_seo.sh <post-slug>"
  echo "Example: ./verify_seo.sh market-rally-2025"
  exit 1
fi

echo -e "\n========================================================"
echo -e "🔍 STARTING VERIFICATION FOR: ${YELLOW}$SLUG${NC}"
echo -e "========================================================"

# ------------------------------------------------------------------
# STEP 1: CHECK MATERIALIZATION (Backend -> MinIO -> Nginx)
# ------------------------------------------------------------------
echo -e "\n1. Checking Materialized HTML File..."
FILE_URL="$BACKEND_URL/api/v1/uploads/posts/$SLUG.html"

# Fetch headers only
HTTP_CODE=$(curl -o /dev/null --silent --head --write-out '%{http_code}\n' "$FILE_URL")

if [ "$HTTP_CODE" -eq "200" ]; then
  echo -e "${GREEN}✅ SUCCESS:${NC} HTML file exists at MinIO/Nginx."
  echo "   URL: $FILE_URL"
else
  echo -e "${RED}❌ FAILURE:${NC} HTML file not found ($HTTP_CODE)."
  echo "   Action: Check Backend logs for 'HtmlMaterializerService' errors."
  echo "   Action: Ensure you have 'Published' or 'Updated' this post since the code update."
  exit 1
fi

# ------------------------------------------------------------------
# STEP 2: CHECK CLOUDFLARE WORKER & FRONTEND DELIVERY
# ------------------------------------------------------------------
echo -e "\n2. Checking Cloudflare Worker Delivery..."

# Construct a valid-looking URL structure that the Worker expects
# /category/CATEGORY_SLUG/POST_SLUG/ARTICLE_ID
TEST_URL="$FRONTEND_URL/category/test-category/$SLUG/test-id"

# Fetch full response with headers
RESPONSE=$(curl -s -D - "$TEST_URL" -H "User-Agent: Googlebot/2.1")

# Extract specific headers/content
HEADER_CHECK=$(echo "$RESPONSE" | grep -i "X-Source: Materialized-HTML")
FALLBACK_CHECK=$(echo "$RESPONSE" | grep -i "X-Fallback-Source")
CONTENT_CHECK=$(echo "$RESPONSE" | grep -i "window.__PRELOADED_STATE__")
BODY_CHECK=$(echo "$RESPONSE" | grep -i "server-content")

# Analysis
if [[ ! -z "$HEADER_CHECK" ]]; then
  echo -e "${GREEN}✅ SUCCESS:${NC} Worker served Materialized HTML (Hit Strategy A)."
elif [[ ! -z "$FALLBACK_CHECK" ]]; then
  echo -e "${YELLOW}⚠️  WARNING:${NC} Worker used Fallback (Strategy B)."
  echo "   This is good for uptime, but means it didn't find the static HTML file."
  echo "   Verify the slug matches exactly."
else
  echo -e "${RED}❌ FAILURE:${NC} Unknown response source. Worker might be bypassed or failing."
fi

# ------------------------------------------------------------------
# STEP 3: CHECK SEO CONTENT (Google's View)
# ------------------------------------------------------------------
echo -e "\n3. Checking SEO Content Quality..."

if [[ ! -z "$BODY_CHECK" ]]; then
  echo -e "${GREEN}✅ SUCCESS:${NC} Static Body Content found (<div id='server-content'>)."
  echo "   Googlebot will see your article text immediately."
else
  echo -e "${RED}❌ FAILURE:${NC} Static Body Content MISSING."
  echo "   The page is empty. Google will see 'Thin Content'."
fi

# ------------------------------------------------------------------
# STEP 4: CHECK REACT HYDRATION (User Experience)
# ------------------------------------------------------------------
echo -e "\n4. Checking React Hydration Support..."

if [[ ! -z "$CONTENT_CHECK" ]]; then
  echo -e "${GREEN}✅ SUCCESS:${NC} Hydration State found (window.__PRELOADED_STATE__)."
  echo "   React will attach seamlessly without flickering."
else
  echo -e "${RED}❌ FAILURE:${NC} Hydration State MISSING."
  echo "   React will wipe the content and re-fetch, causing a flicker."
fi

echo -e "\n========================================================"
echo -e "🏁 VERIFICATION SUMMARY"
echo -e "========================================================"
if [[ ! -z "$HEADER_CHECK" ]] && [[ ! -z "$BODY_CHECK" ]] && [[ ! -z "$CONTENT_CHECK" ]]; then
  echo -e "${GREEN}ALL SYSTEMS GO! 🚀${NC}"
  echo "Your architecture is Enterprise-Ready."
else
  echo -e "${YELLOW}SOME CHECKS FAILED.${NC} Review output above."
fi