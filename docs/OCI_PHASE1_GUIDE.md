/**
 * AI-CONTEXT:
 *
 * Purpose:
 * - Enterprise operational manual for establishing the OCI Always Free Hybrid-Cloud Master Node.
 * - Bridges the gap between non-coder reality and complex IaC (Terraform) requirements.
 *
 * Scope:
 * - Guides the creation of the OCI account.
 * - Extracts critical Zero-Trust identification (OCIDs).
 * - Prepares the Tailscale Zero-Trust Mesh network.
 *
 * Critical Dependencies:
 * - Backend: Pre-requisite for deploying the Phase 2 Database Replica.
 * - Frontend: N/A (Infrastructure layer).
 * - Worker / SEO / Sitemap: N/A
 *
 * Security Constraints:
 * - NEVER commit the OCI private key (`.pem`) to Git.
 * - NEVER expose OCIDs publicly, though they are identifiers, they map the blast radius.
 *
 * Non-Negotiables:
 * - Must explicitly instruct the user to select the ARM A1 shape to prevent billing.
 *
 * Change Intent:
 * - Initializing Phase 1 of the Hybrid-Cloud Disaster Recovery architecture.
 *
 * Future AI Guidance:
 * - Once Phase 1 is complete, AI must read this guide to verify what the user has accomplished before generating Phase 2 replication scripts.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED:
 * • OCI Phase 1 setup instructions.
 * • To ensure the user can securely deploy Terraform without prior Cloud Ops experience.
 * • Phase 1 Execution Phase.
 */

# ENTERPRISE HYBRID-CLOUD: PHASE 1 INITIALIZATION

This document guides you through creating your permanent Oracle Cloud (OCI) Master Node securely and setting up the Zero-Trust Mesh network.

## STEP 1: Claiming the Always Free OCI Account
1. Go to [Oracle Cloud Free Tier](https://www.oracle.com/cloud/free/) and sign up.
2. **CRITICAL:** When selecting your "Home Region" (e.g., Mumbai, Hyderabad, Frankfurt), choose carefully. Your Always Free ARM instance can *only* be created in your Home Region. You cannot change this later.
3. You will need a credit card for verification, but Oracle guarantees they will not charge you unless you explicitly upgrade to a Paid account.

## STEP 2: Extracting Terraform Variables (OCIDs)
Terraform needs to know *who* you are to build the server automatically.
1. **Tenancy OCID:** Click the Profile Icon (top right) -> **Tenancy: [Name]**. Copy the `OCID` starting with `ocid1.tenancy...`.
2. **User OCID:** Click Profile Icon -> **My Profile**. Copy the `OCID` starting with `ocid1.user...`.
3. **Compartment OCID:** Go to Hamburger Menu (top left) -> **Identity & Security** -> **Compartments**. Copy the OCID of the root compartment (usually named after your tenancy).

## STEP 3: Generating the API Key (Zero-Trust Authentication)
1. Go to Profile Icon -> **My Profile** -> Scroll down to **API Keys** (bottom left).
2. Click **Add API Key**.
3. Select **Generate API Key Pair**.
4. **DOWNLOAD THE PRIVATE KEY.** Save it on your local VBox machine as `F:\BACKEND PROJECG\finance-api\finance-api\oci_api_key.pem`. 
5. Click Add. It will show a "Configuration File Preview". 
6. Copy the `fingerprint` and `region` values from that preview text.

## STEP 4: Creating the Mesh Network (Tailscale)
To securely connect OCI and VBox without opening database ports to the internet:
1. Create a free account at [Tailscale.com](https://tailscale.com/).
2. Install Tailscale on your local VBox server: `curl -fsSL https://tailscale.com/install.sh | sh`
3. Run `sudo tailscale up` on the VBox server to authenticate it.

## STEP 5: Executing Terraform
Once you have the OCIDs and the API Key:
1. Open the file `terraform/terraform.tfvars` (you must create this based on `variables.tf`) and paste your extracted OCIDs.
2. Run the following commands in the `terraform/` directory on your local machine:
   - `terraform init`
   - `terraform plan` (Review the output. It should say exactly 4 resources to add).
   - `terraform apply -auto-approve`

Once complete, Terraform will output the secure internal IP address of your new Always Free Enterprise Server. Notify the AI to proceed to Phase 2.