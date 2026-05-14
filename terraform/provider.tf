/*
 * AI-CONTEXT:
 *
 * Purpose:
 * - Defines the Oracle Cloud Infrastructure (OCI) provider for Terraform.
 *
 * Scope:
 * - Authentication routing for IaC execution.
 *
 * Critical Dependencies:
 * - Requires local `oci_api_key.pem`.
 *
 * Security Constraints:
 * - No hardcoded credentials here. Driven entirely by variables.
 *
 * Non-Negotiables:
 * - Provider version locked to ensure deterministic deployments.
 *
 * Change Intent:
 * - Phase 1 Hybrid-Cloud configuration.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED:
 * • Initial OCI provider block.
 * • To facilitate zero-touch provisioning of the master node.
 * • Phase 1 Execution.
 */

terraform {
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = ">= 5.0.0"
    }
  }
}

provider "oci" {
  tenancy_ocid     = var.tenancy_ocid
  user_ocid        = var.user_ocid
  fingerprint      = var.fingerprint
  private_key_path = var.private_key_path
  region           = var.region
}