#!/bin/bash
# Enterprise Sanitization Script
# Usage: ./sanitize_for_sale.sh
# Purpose: Creates a clean "Version B" distribution from the current code.

echo "Starting Sanitization Process..."

# 1. Create a temporary build folder
mkdir -p dist/fintech-starter-kit
cp -r src pom.xml docker-compose.yml README.md dist/fintech-starter-kit/
cd dist/fintech-starter-kit

# 2. Rename Group ID (Java)
echo "Generalizing Maven Group ID..."
find . -name "pom.xml" -exec sed -i 's/com.treishvaam/com.yourcompany/g' {} +

# 3. Remove Branding from Properties
echo "Cleaning application.properties..."
# Removes lines containing specific Treishvaam secrets if hardcoded
sed -i '/treishvaam_storage_secret/d' src/main/resources/application.properties

# 4. Remove Migration Files containing YOUR Data
# (Assuming V2__add_users_table.xml contains your admin user)
echo "Removing proprietary data migrations..."
rm src/main/resources/db/changelog/V2__add_users_table.xml
# Create a dummy replacement so the app doesn't crash
echo "<databaseChangeLog xmlns='http://www.liquibase.org/xml/ns/dbchangelog' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:schemaLocation='http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.8.xsd'></databaseChangeLog>" > src/main/resources/db/changelog/V2__add_users_table.xml

# 5. Add Commercial License
echo "Applying Commercial License..."
cat <<EOT > LICENSE
PROPRIETARY SOURCE CODE LICENSE AGREEMENT

Copyright (c) $(date +"%Y") Treishvaam Group. All Rights Reserved.

1. GRANT OF LICENSE
Permission is hereby granted to the original purchaser ("Licensee") to use this software 
to build and deploy a single end-product (website or application).

2. RESTRICTIONS
Licensee may NOT:
- Resell, redistribute, or sub-license the source code.
- Use this software to create a competing product (i.e., a "Starter Kit" or "Boilerplate").
- Publicly release the source code.

3. DISCLAIMER
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
EOT

# 6. Zip it up
cd ..
zip -r fintech-starter-kit-v1.0.zip fintech-starter-kit
echo "DONE! Clean product is ready at dist/fintech-starter-kit-v1.0.zip"