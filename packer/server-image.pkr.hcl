# /**
#  * AI-CONTEXT:
#  * Purpose: Packer template for generating golden VM images with Docker, Infisical, and SaltStack (ARCH-05).
#  * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
#  * - ADDED: Initial Packer configuration for server imaging to support future OCI Hybrid-Cloud migration.
#  */
packer {
  required_plugins {
    docker = {
      version = ">= 1.0.8"
      source  = "github.com/hashicorp/docker"
    }
  }
}

source "docker" "ubuntu" {
  image  = "ubuntu:24.04"
  commit = true
}

build {
  sources = ["source.docker.ubuntu"]

  provisioner "shell" {
    inline = [
      "apt-get update",
      "apt-get install -y curl ufw fail2ban salt-minion"
    ]
  }

  provisioner "salt-masterless" {
    local_state_tree = "saltstack/states"
    log_level        = "info"
  }
}