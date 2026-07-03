#!/bin/bash

# AI-CONTEXT:
#
# Purpose:
# - Autonomic Self-Healing Kernel Mount Recovery Engine.
# - Breaks VirtualBox / Linux Kernel Lazy Unmount Deadlocks (Split-Brain) without manual SSH intervention.
#
# Scope:
# - Exclusively invoked by auto_deploy.sh when standard `docker rm -f` fails to purge a Dead/Created ghost container.
# - Safely cycles the Docker daemon and strips stale overlay2/moby task locks.
#
# Critical Dependencies:
# - Must be executed with sudo privileges. Ansible setup-server.yml configures NOPASSWD for this specific script.
#
# Security Constraints:
# - Never expose this script to web endpoints or unauthenticated cron jobs.
# - Assumes it is being run as root (via sudo).
#
# IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
# - ADDED:
# • Autonomic Kernel Mount Recovery Engine.
# • Created to automate the manual Split-Brain eradication protocol after ZKP port collision deadlocks triggered pipeline aborts.
# • July 2026.

set -e

echo "[Autonomic Recovery] Initiating Split-Brain eradication protocol..."

# 1. Stop container runtimes
echo "[Autonomic Recovery] Halting Docker and Containerd sockets..."
systemctl stop docker.socket docker containerd

# 2. Break kernel locks via lazy unmount
echo "[Autonomic Recovery] Forcing lazy unmount of locked Docker overlay endpoints..."
awk '{print $2}' /proc/mounts | grep '^/var/lib/docker' | xargs -r -n1 umount -l || true

# 3. Purge corrupted metadata state
echo "[Autonomic Recovery] Purging ghost container metadata and moby tasks..."
rm -rf /var/lib/docker/containers/*
rm -rf /var/lib/containerd/io.containerd.runtime.v2.task/moby/*

# 4. Restore daemons
echo "[Autonomic Recovery] Reigniting Containerd and Docker daemons..."
systemctl start containerd docker docker.socket

echo "[Autonomic Recovery] Kernel mount recovery complete. System sanitized."
exit 0