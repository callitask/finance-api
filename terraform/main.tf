/*
 * AI-CONTEXT:
 *
 * Purpose:
 * - Provisions the physical infrastructure on Oracle Cloud.
 *
 * Scope:
 * - VCN (Virtual Cloud Network), Subnet, Internet Gateway.
 * - Ampere A1 Compute Instance (Strictly Free Tier).
 *
 * Critical Dependencies:
 * - Subnet must route to the Internet Gateway for outbound connections
 *   (Cloudflare Tunnel is outbound-initiated and needs NO inbound port).
 *
 * Security Constraints:
 * - ZERO public inbound ports. SSH access is via the OCI Bastion Service only
 *   (provisioned below). Port 22 ingress is allowed ONLY from the Bastion
 *   service's regional source CIDRs (var.bastion_source_cidrs).
 * - Application ports (8080, 15672, 3001, 3306) are never exposed — all
 *   application traffic flows through the outbound Cloudflare Tunnel.
 *
 * Non-Negotiables (CORRECTED 2026-08-23 — see change history):
 * - `shape` MUST be "VM.Standard.A1.Flex".
 * - `ocpus` MUST be 2.
 * - `memory_in_gbs` MUST be 12.
 *   Oracle cut the Always Free Ampere A1 allocation from 4 OCPU/24 GB to
 *   2 OCPU/12 GB in mid-2026. The earlier 4/24 mandate is stale and WILL
 *   fail on apply. Region MUST be ap-hyderabad-1 (tenancy home region —
 *   Always Free A1 capacity exists only in the home region).
 *
 * Change Intent:
 * - Creating the permanent Always-On Master Node.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED:
 * • VCN, Gateway, Subnet, and Compute Instance blocks.
 * • To deploy the highly optimized ARM architecture for the enterprise stack.
 * • Phase 1 Execution.
 * - EDITED (2026-08-23, OCI Migration Phase 1):
 * • ocpus 4 -> 2, memory_in_gbs 24 -> 12 (Always Free allocation cut, mid-2026).
 * • Ubuntu image 22.04 -> 24.04 (align with the Ansible/Packer 24.04 baseline).
 * • REMOVED the public 0.0.0.0/0 TCP/22 ingress rule; SSH is Bastion-only.
 * • ADDED oci_bastion_bastion + Bastion-scoped port-22 ingress (dynamic block
 *   over var.bastion_source_cidrs — see BASTION-NOTE.md for where the regional
 *   CIDR list comes from and how sessions are created).
 * • ADDED OCI Budget + $1 alert rule guardrail (alert rule only if var.alert_email set).
 */

# 1. Fetch Latest Ubuntu 24.04 ARM Image Dynamically
data "oci_core_images" "ubuntu_arm" {
  compartment_id           = var.compartment_ocid
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "24.04"
  shape                    = "VM.Standard.A1.Flex"
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

# 2. Create Virtual Cloud Network (VCN)
resource "oci_core_vcn" "enterprise_vcn" {
  compartment_id = var.compartment_ocid
  display_name   = "treishvaam-enterprise-vcn"
  cidr_block     = "10.0.0.0/16"
}

# 3. Create Internet Gateway
resource "oci_core_internet_gateway" "enterprise_igw" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.enterprise_vcn.id
  display_name   = "treishvaam-igw"
  enabled        = true
}

# 4. Route Table for Internet Access
resource "oci_core_route_table" "enterprise_rt" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.enterprise_vcn.id
  display_name   = "treishvaam-route-table"

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.enterprise_igw.id
  }
}

# 5. Security List (Zero-Trust Focus)
resource "oci_core_security_list" "enterprise_sl" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.enterprise_vcn.id
  display_name   = "treishvaam-security-list"

  egress_security_rules {
    destination = "0.0.0.0/0"
    protocol    = "all"
  }

  # ZERO public inbound ports.
  # Port 22 is allowed ONLY from the OCI Bastion service's regional source
  # CIDRs (populate var.bastion_source_cidrs in terraform.tfvars — the
  # per-region list is published in Oracle's Bastion service documentation;
  # see BASTION-NOTE.md alongside this file).
  # All application traffic flows out via the Cloudflare Tunnel — no 80/443/8080
  # ingress rules exist or may be added.
  dynamic "ingress_security_rules" {
    for_each = var.bastion_source_cidrs
    content {
      protocol = "6" # TCP
      source   = ingress_security_rules.value
      tcp_options {
        min = 22
        max = 22
      }
    }
  }
}

# 5.5 OCI Bastion Service (zero public SSH — sessions are created on demand)
resource "oci_bastion_bastion" "enterprise_bastion" {
  compartment_id       = var.compartment_ocid
  vcn_id               = oci_core_vcn.enterprise_vcn.id
  client_cidr_block    = var.bastion_client_cidr # who may OPEN a session (your IP)
  name                 = "treishvaam-bastion"
  bastion_type         = "STANDARD"
  max_session_ttl_in_seconds = 10800 # 3 h — reconnect for longer operations
  subnet_id            = oci_core_subnet.enterprise_subnet.id
}

# 5.6 Budget guardrail (bill-shock prevention — target spend on this project is $0)
resource "oci_budget_budget" "enterprise_budget" {
  compartment_id = var.budget_compartment_ocid
  display_name   = "treishvaam-budget"
  amount         = "1" # USD — anything above this means we left the Free tier
}

resource "oci_budget_alert_rule" "enterprise_budget_alert" {
  count = var.alert_email != "" ? 1 : 0

  budget_id  = oci_budget_budget.enterprise_budget.id
  display_name = "treishvaam-one-dollar-alert"
  threshold   = "100" # 100% of the $1 budget
  threshold_type = "PERCENTAGE"
  type            = "FORECAST"
  recipients      = var.alert_email
}

# 6. Create Subnet
resource "oci_core_subnet" "enterprise_subnet" {
  compartment_id    = var.compartment_ocid
  vcn_id            = oci_core_vcn.enterprise_vcn.id
  cidr_block        = "10.0.1.0/24"
  display_name      = "treishvaam-subnet"
  route_table_id    = oci_core_route_table.enterprise_rt.id
  security_list_ids = [oci_core_security_list.enterprise_sl.id]
}

# 7. Create Always Free Master Node
resource "oci_core_instance" "enterprise_master_node" {
  compartment_id      = var.compartment_ocid
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name
  display_name        = "Treishvaam-Master-A1"
  shape               = "VM.Standard.A1.Flex"

  shape_config {
    ocpus         = 2
    memory_in_gbs = 12
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.enterprise_subnet.id
    display_name     = "primary-vnic"
    assign_public_ip = true
  }

  source_details {
    source_type             = "image"
    source_id               = data.oci_core_images.ubuntu_arm.images[0].id
    boot_volume_size_in_gbs = 50
  }

  metadata = {
    ssh_authorized_keys = var.ssh_public_key
  }
}

# Helper to get Availability Domain
data "oci_identity_availability_domains" "ads" {
  compartment_id = var.compartment_ocid
}

output "master_node_public_ip" {
  value = oci_core_instance.enterprise_master_node.public_ip
}

output "bastion_ocid" {
  description = "OCID of the Bastion service — create SSH sessions against this (see BASTION-NOTE.md)"
  value       = oci_bastion_bastion.enterprise_bastion.id
}