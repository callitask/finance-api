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
 * - Actual values MUST be stored in `terraform.tfvars` which is .gitignored.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED:
 * • Variable definitions for OCI authentication and SSH.
 * • Phase 1 Execution.
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
  description = "Absolute path to the oci_api_key.pem file"
  type        = string
}

variable "region" {
  description = "The OCI region (e.g., ap-mumbai-1)"
  type        = string
}

variable "compartment_ocid" {
  description = "The OCID of the target compartment"
  type        = string
}

variable "ssh_public_key" {
  description = "The public SSH key for accessing the OCI instance"
  type        = string
}