--[[
 * AI-CONTEXT:
 *
 * Purpose:
 * - Implements Edge-level TLS ClientHello JA3 fingerprinting and real IP resolution.
 *
 * Scope:
 * - Generates stable fingerprint for all incoming Nginx requests.
 * - Bypasses simple IP-rotation by fingerprinting the TLS library (bot/scraper stack).
 *
 * Critical Dependencies:
 * - Nginx: Requires OpenResty context.
 * - Backend: AegisBehavioralEngine relies on X-JA3-Fingerprint.
 *
 * Security Constraints:
 * - Do not log raw IP addresses in plain text if avoidable; pass securely to backend.
 *
 * Non-Negotiables:
 * - Must execute in sub-millisecond time. No blocking I/O calls.
 *
 * Change Intent:
 * - Scaffold Phase 1 of AEGIS.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED (AEGIS Phase 1):
 * • Initial creation of JA3 and Real-IP extraction logic.
 * • Implemented Cloudflare header fallback to proxy true client signatures.
]]--

local _M = {}

function _M.get_fingerprint()
    -- Attempt to get Cloudflare's provided JA3 if Bot Management is active
    local cf_ja3 = ngx.req.get_headers()["cf-client-ja3"]
    if cf_ja3 then
        return cf_ja3
    end
    
    -- Fallback: Compute a pseudo-JA3 from available TLS/Client metrics 
    -- (Real JA3 requires native Nginx OpenSSL patch + lua-resty-ja3 library)
    local ua = ngx.req.get_headers()["user-agent"] or "unknown"
    local tls_version = ngx.var.ssl_protocol or "unknown"
    local tls_cipher = ngx.var.ssl_cipher or "unknown"
    
    -- Simple MD5 hash of client characteristics to fingerprint the active tool
    local str = ua .. "|" .. tls_version .. "|" .. tls_cipher
    return ngx.md5(str)
end

function _M.get_real_ip()
    -- Prioritize Cloudflare's true connecting IP, default to Nginx remote address
    return ngx.req.get_headers()["cf-connecting-ip"] or ngx.var.remote_addr
end

return _M