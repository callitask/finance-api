# /**
#  * AI-CONTEXT:
#  * Purpose: SaltStack states for VM security baseline including UFW and fail2ban (ARCH-05).
#  * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
#  * - ADDED: Defined UFW rules and fail2ban service enforcements to harden bare-metal instances.
#  */
ufw:
  pkg.installed: []
  service.running:
    - enable: True

ufw_allow_https:
  cmd.run:
    - name: ufw allow 443/tcp

ufw_allow_ssh:
  cmd.run:
    - name: ufw allow 22/tcp

ufw_enable:
  cmd.run:
    - name: ufw --force enable

fail2ban:
  pkg.installed: []
  service.running:
    - enable: True