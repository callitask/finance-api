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
 * - Credentials are NEVER hardcoded here. All values arrive as TF_VAR_*
 *   environment variables, injected at runtime from Infisical (project
 *   "OCI KEYS ETC") via:  infisical run --projectId <id> --env <env> -- terraform ...
 * - No terraform.tfvars file exists (Zero-Local-Hardcoding posture,
 *   2026-08-23 — see INFISICAL-TERRAFORM-SETUP.md).
 *
 * Security Constraints:
 * - Dual-mode key delivery, contents preferred over path:
 *   • Option B (preferred): TF_VAR_oci_api_private_key carries the full PEM
 *     contents from Infisical — zero secret files on the workstation.
 *   • Option A (fallback): TF_VAR_private_key_path points at a local
 *     ~/.oci/oci_api_key.pem (git-ignored via *.pem; lives outside the repo).
 *   The provider block below selects automatically: contents win when present.
 *
 * Non-Negotiables:
 * - Provider version locked to ensure deterministic deployments.
 *
 * Change Intent:
 * - Phase 1 Execution; Zero-Trust credential handling refinement (Phase 2).
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED:
 * • Initial OCI provider block.
 * • To facilitate zero-touch provisioning of the master node.
 * • Phase 1 Execution.
 * - EDITED (2026-08-23, OCI Migration Phase 2):
 * • private_key_path replaced by dual-mode private_key/private_key_path
 *   selection so the API private key can live in Infisical instead of a
 *   workstation file. No other behavior changed.
 * - EDITED (2026-08-24, pathless-default refinement):
 * • Three-tier fallback added — when no path secret exists, terraform itself
 *   resolves the Oracle-conventional ~/.oci/oci_api_key.pem via pathexpand()
 *   on whatever machine runs it. Machine-specific paths no longer need to be
 *   stored in Infisical at all.
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
  tenancy_ocid = var.tenancy_ocid
  user_ocid    = var.user_ocid
  fingerprint  = var.fingerprint
  region       = var.region

  # Three-tier key delivery (no hardcoded machine paths anywhere):
  #   1. TF_VAR_oci_api_private_key — full PEM contents from Infisical
  #      (works only where the CLI survives multiline env injection — not Windows).
  #   2. TF_VAR_private_key_path    — explicit override path from Infisical.
  #   3. CONVENTION DEFAULT: ~/.oci/oci_api_key.pem — resolved by terraform's
  #      pathexpand() on WHATEVER machine runs the plan (Linux/macOS/Windows),
  #      matching Oracle's own tooling convention. A new PC only needs the pem
  #      dropped into the standard location — nothing to edit anywhere.
  private_key      = var.oci_api_private_key != "" ? var.oci_api_private_key : null
  private_key_path = var.oci_api_private_key != "" ? null : (
    var.private_key_path != "" ? var.private_key_path : pathexpand("~/.oci/oci_api_key.pem")
  )
}
