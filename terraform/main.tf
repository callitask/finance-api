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
 * - Subnet must route to the Internet Gateway for outbound Tailscale connection.
 *
 * Security Constraints:
 * - Ingress rules strictly limited. Only port 22 (SSH) allowed over public IP. 
 * - Application ports (8080, 15672, 3001, 3306) will ONLY be accessed via Tailscale encrypted mesh (internal IPs).
 *
 * Non-Negotiables:
 * - `shape` MUST be "VM.Standard.A1.Flex".
 * - `ocpus` MUST be 4.
 * - `memory_in_gbs` MUST be 24.
 *
 * Change Intent:
 * - Creating the permanent Always-On Master Node.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED:
 * • VCN, Gateway, Subnet, and Compute Instance blocks.
 * • To deploy the highly optimized ARM architecture for the enterprise stack.
 * • Phase 1 Execution.
 */

# 1. Fetch Latest Ubuntu 22.04 ARM Image Dynamically
data "oci_core_images" "ubuntu_arm" {
  compartment_id           = var.compartment_ocid
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "22.04"
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

  # Only allow SSH from the outside world. 
  # All other traffic (DB, API) goes through Cloudflare Tunnel or Tailscale Mesh.
  ingress_security_rules {
    protocol = "6" # TCP
    source   = "0.0.0.0/0"
    tcp_options {
      min = 22
      max = 22
    }
  }
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
    ocpus         = 4
    memory_in_gbs = 24
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