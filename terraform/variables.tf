/*
 * AI-CONTEXT:
 *
 * Purpose:
 * - Defines mandatory inputs for OCI provisioning.
 *
 * Scope:
 * - Schema definition for Terraform variables.
 *
 * Security Constraints:
 * - NO terraform.tfvars file exists (Zero-Local-Hardcoding, 2026-08-23).
 *   ALL values arrive as TF_VAR_* environment variables injected at runtime
 *   from Infisical (project "OCI KEYS ETC") via `infisical run -- terraform ...`.
 *   See INFISICAL-TERRAFORM-SETUP.md.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED:
 * • Variable definitions for OCI authentication and SSH.
 * • Phase 1 Execution.
 * - EDITED (2026-08-23, OCI Migration Phase 1):
 * • region description corrected to ap-hyderabad-1 (tenancy home region —
 *   Always Free A1 capacity exists only there; the old ap-mumbai-1 example
 *   was wrong).
 * • ADDED bastion_source_cidrs / bastion_client_cidr (Bastion-only SSH),
 *   alert_email + budget_compartment_ocid (budget guardrail).
 * - EDITED (2026-08-23, OCI Migration Phase 2 — Zero-Trust credential handling):
 * • ADDED sensitive `oci_api_private_key` (full PEM contents from Infisical;
 *   preferred mode — zero local secret files).
 * • `private_key_path` demoted to OPTIONAL fallback (default "") — used only
 *   when oci_api_private_key is empty (provider.tf selects automatically).
 */

variable "tenancy_ocid" {
  description = "The OCID of the OCI Tenancy"
  type        = string
}

variable "user_ocid" {
  description = "The OCID of the OCI User executing the terraform"
  type        = string
}

variable "fingerprint" {
  description = "The fingerprint of the API Key"
  type        = string
}

variable "private_key_path" {
  description = "OPTIONAL fallback (Option A): absolute path to a local oci_api_key.pem. Ignored whenever oci_api_private_key is set. Git-ignored via *.pem; keep it outside the repo (~/.oci/)."
  type        = string
  default     = ""
}

variable "oci_api_private_key" {
  description = "PREFERRED (Option B): the COMPLETE contents of the OCI API private key (PEM, including BEGIN/END lines), injected from Infisical as TF_VAR_oci_api_private_key. When set, no local secret file is needed at all."
  type        = string
  default     = ""
  sensitive   = true
}

variable "region" {
  description = "The OCI region — MUST be ap-hyderabad-1 (tenancy home region; Always Free A1 capacity exists only in the home region)"
  type        = string
  default     = "ap-hyderabad-1"
}

variable "compartment_ocid" {
  description = "The OCID of the target compartment"
  type        = string
}

variable "ssh_public_key" {
  description = "The public SSH key for accessing the OCI instance"
  type        = string
}

# --- Bastion-only SSH access (added 2026-08-23) ---

variable "bastion_source_cidrs" {
  description = "Source CIDRs of the OCI Bastion service for ap-hyderabad-1, allowed to reach the instance on TCP/22. Oracle publishes the per-region Bastion service IP ranges in the Bastion documentation ('Bastion service' -> 'Allowed IP ranges'); populate them in terraform.tfvars. No public 0.0.0.0/0 SSH exists."
  type        = list(string)
  default     = []
}

variable "bastion_client_cidr" {
  description = "Your own public IP (CIDR, e.g. 203.0.113.5/32) permitted to OPEN Bastion sessions. Restricts who can create sessions even if the Bastion OCID leaks."
  type        = string
  default     = "0.0.0.0/0" # tighten to your IP in tfvars
}

# --- Budget guardrail (added 2026-08-23) ---

variable "alert_email" {
  description = "Email for the $1 budget alert. Empty string disables the alert RULE (the budget itself is always created)."
  type        = string
  default     = ""
}

variable "budget_compartment_ocid" {
  description = "Compartment the budget watches. Defaults to the tenancy root."
  type        = string
}