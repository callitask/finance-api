#!/bin/bash
# TARGET: Cleanup Legacy Auth Files
# PURPOSE: Removes files that are incompatible with Phase 17 Keycloak Architecture

echo "Removing Legacy Security Files..."
rm src/main/java/com/treishvaam/financeapi/security/JwtTokenProvider.java
rm src/main/java/com/treishvaam/financeapi/security/JwtTokenFilter.java
rm src/main/java/com/treishvaam/financeapi/controller/OAuth2Controller.java

echo "Cleanup Complete. Legacy auth code removed."