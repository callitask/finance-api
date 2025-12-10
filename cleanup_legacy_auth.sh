{
  "type": "uploaded file",
  "fileName": "PROJECT CODE/BACKEND CODE FILES/cleanup_legacy_auth.sh",
  "fullContent": "#!/bin/bash\n\n# TARGET: Cleanup Legacy Auth Files\n# PURPOSE: Removes files that are incompatible with Phase 17 Keycloak Architecture\n\necho \"Removing Legacy Security Files...\"\n\n# 1. Remove Custom JWT Provider (Replaced by Keycloak)\nrm src/main/java/com/treishvaam/financeapi/security/JwtTokenProvider.java\n\n# 2. Remove Custom JWT Filter (Replaced by OAuth2 Resource Server Filter)\nrm src/main/java/com/treishvaam/financeapi/security/JwtTokenFilter.java\n\n# 3. Remove Old OAuth Controller (Replaced by Keycloak Login Flow)\nrm src/main/java/com/treishvaam/financeapi/controller/OAuth2Controller.java\n\necho \"Cleanup Complete. Legacy auth code removed.\""
}