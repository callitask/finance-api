source "docker" "ubuntu" {
  image  = "ubuntu:24.04"
  commit = true
}

build {
  sources = ["source.docker.ubuntu"]

  provisioner "shell" {
    inline = [
      "apt-get update",
      "apt-get install -y ufw fail2ban curl software-properties-common",
      "curl -fsSL https://get.docker.com -o get-docker.sh",
      "sh get-docker.sh"
    ]
  }

  provisioner "salt-masterless" {
    local_state_tree = "saltstack/states"
    log_level        = "info"
  }
}