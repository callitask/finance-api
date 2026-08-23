# Terraform × Infisical — Zero-Local-Hardcoding Setup (replaces terraform.tfvars)

> **No `terraform.tfvars` file is created. Ever.** Every terraform input is
> injected at runtime from your Infisical project **"OCI KEYS ETC"** via
> `TF_VAR_*` environment variables (`infisical run -- terraform ...`). When the
> command finishes, the values exist nowhere on disk.
>
> Honest classification (so your threat model is accurate):
> - **Crown jewel:** the API **private key**. Everything else here — OCIDs,
>   fingerprint, SSH public key, CIDRs, email — are *identifiers/configuration*,
>   not credentials. Storing them in Infisical is excellent hygiene (single
>   source of truth, survives laptop loss, one-place rotation), but the private
>   key is the only value that grants access on its own.

## 1. Secrets to add in Infisical (project: **OCI KEYS ETC**)

Use the SAME environment where you already put `tenancy_ocid`, `user_ocid`,
`oci_api_key_fingerprint`, `oci_api_key_public.pem` (keep those as they are —
the new names below are additive, nothing gets renamed).

| Secret name (must be EXACTLY this) | Value | Where the value comes from |
|---|---|---|
| `TF_VAR_tenancy_ocid` | `ocid1.tenancy.oc1...` | same as your existing `tenancy_ocid` — copy it |
| `TF_VAR_user_ocid` | `ocid1.user.oc1...` | same as existing `user_ocid` |
| `TF_VAR_fingerprint` | `aa:bb:...` | same as existing `oci_api_key_fingerprint` |
| `TF_VAR_compartment_ocid` | `ocid1.compartment.oc1...` | Console ☰ → Identity & Security → Compartments → your compartment → OCID (tenancy root OK) |
| `TF_VAR_budget_compartment_ocid` | same as tenancy OCID | budgets live at tenancy root |
| `TF_VAR_region` | `ap-hyderabad-1` | fixed |
| `TF_VAR_ssh_public_key` | `ssh-ed25519 AAAA... you@host` | `cat ~/.ssh/id_ed25519.pub` on your workstation |
| `TF_VAR_bastion_client_cidr` | **`0.0.0.0/0`** — decided 2026-08-24 for the dynamic-IP reality (Jio rotates your address; security lists accept CIDRs only, never hostnames). This layer alone grants NOTHING: opening a session still requires full OCI IAM sign-in + the SSH keypair baked into the session. Mandatory compensating control: **MFA ON** (see §1a below). | n/a |
| `TF_VAR_bastion_source_cidrs` | **DO NOT create this secret at all** (leave it out of Infisical). Terraform's built-in default `[]` applies — zero port-22 ingress on first apply. The Console's "Add SSH security rule" auto-fix at first bastion session discovers Oracle's regional CIDRs; backfill them into this secret afterwards so terraform owns the rule. (The delivered main.tf also tolerates a blank "" value defensively, but absent = cleanest.) | n/a initially |
| `TF_VAR_alert_email` | your email | — |
| `TF_VAR_oci_api_private_key` | the **complete PEM contents** of `oci_api_key.pem`, INCLUDING the `-----BEGIN/END RSA PRIVATE KEY-----` lines (Infisical secrets are multiline — paste the whole file) | the key you generate in Stage 1a |

## 1a. Mandatory compensating controls for the 0.0.0.0/0 client CIDR (Jio reality)

Because the client-CIDR layer is now open, the remaining gates MUST be strong:

1. **Enable MFA on your OCI account (do this before the first apply):**
   Console → Profile icon → **My profile** → **Security** (left rail) →
   **Two-factor authentication / MFA** → enable **Mobile app verification**
   (scan QR with a TOTP app — Google Authenticator, Authy, etc.). Verify with
   the generated code. Every console login (and therefore every bastion
   session creation) now needs the rotating code — a stolen password alone
   grants nothing.
2. **Session TTL stays 3 h max** (already in the terraform) — no standing door.
3. **The instance's port 22 remains closed to the world** — that rule (the
   `bastion_source_cidrs` one) is Oracle-side static service CIDRs, unaffected
   by your Jio IP. Only the *session-creation* filter is open.
4. Optional hardening later: a least-privilege IAM user/policy that can create
   bastion sessions but not manage compute — say the word and it becomes a
   Phase 3 add-on; not required to proceed.

**IPv6 note:** the Bastion client CIDR is IPv4. If OCI ever rejects a session
creation with a CIDR error, your egress that moment was IPv6 — retry (Jio
rotates), or temporarily force IPv4. This is cosmetic, not a security hole.

## 2. Finding the two scoping values you'll need in commands

- **Project ID:** Infisical dashboard → open project **OCI KEYS ETC** →
  **Settings** (left rail) → the **Project ID** (a UUID) — or copy it straight
  from the browser URL: `.../projects/<THIS-UUID>/...`
- **Environment slug:** the env tab where you placed the secrets (typically
  `dev` if you never created others). Dashboard → project → the env tabs show
  the slug.

## 3. Running terraform (PowerShell, from `<repo>/terraform`)

```powershell
# 1a (once): generate the key pair; upload the PUBLIC half to OCI, paste the
#     PRIVATE half into TF_VAR_oci_api_private_key (Stage 1a/1b of the runbook):
openssl genrsa -out $env:USERPROFILE\.oci\oci_api_key.pem 2048
openssl rsa -pubout -in $env:USERPROFILE\.oci\oci_api_key.pem -out $env:USERPROFILE\.oci\oci_api_key_public.pem
Get-Content $env:USERPROFILE\.oci\oci_api_key.pem | Set-Clipboard   # paste into Infisical

terraform init

infisical run --projectId <PROJECT_ID> --env <ENV_SLUG> -- terraform plan -out=oci.tfplan
# READ the plan: expect create = vcn, igw, route table, security list, subnet,
# bastion, instance, budget (+alert rule). Nothing else, nothing destroyed.

infisical run --projectId <PROJECT_ID> --env <ENV_SLUG> -- terraform apply oci.tfplan
del oci.tfplan    # plan files can embed interpolated values — keep transient
```

If PowerShell ever chokes on the multiline PEM env var (rare), fallback
**Option A**: leave `TF_VAR_oci_api_private_key` empty and set
`TF_VAR_private_key_path=C:\Users\7303150607\.oci\oci_api_key.pem` instead —
the delivered `provider.tf` supports both modes automatically (key contents
wins when present; the file path is used when it isn't).

## 4. Rotation / replacement (your "what if we need to change values" question)

| Scenario | Procedure | Blast radius |
|---|---|---|
| Any identifier/config changed (compartment, email, CIDRs) | Edit the secret in Infisical → re-run `infisical run -- terraform apply` | None — one place, next apply picks it up |
| Laptop lost/stolen | Nothing to revoke except the local pem copy (if Option A). Generate a new key pair, paste new private key into Infisical, upload new public key to OCI, delete the old public key in the console | Infrastructure untouched |
| Suspected key compromise | Console → Profile → My profile → API keys → delete old key (instantly kills the old private key everywhere) → generate + upload new pair → update `TF_VAR_fingerprint` + `TF_VAR_oci_api_private_key` in Infisical → re-run apply | Zero downtime — nothing about the running infrastructure changes |
| Emergency full lockdown | Delete ALL API keys in the console — terraform (and anyone else) is locked out until new keys are issued | OCI resources keep running |

## 5. State-file hygiene (the gap the pasted analysis missed)

`terraform.tfstate` — created next to your `.tf` files after the first apply —
**contains** resource attributes including `bastion_client_cidr`,
`ssh_public_key`, `alert_email`, compartment/bastion OCIDs (the private key is
provider config and is **not** stored in state). Rules:

- It is already git-ignored (`*.tfstate`, `*.tfstate.backup` — Phase-1 .gitignore).
- Never move it into the repo, never paste it anywhere.
- It is your infrastructure's ledger — DO back it up somewhere private
  (e.g. an Infisical-adjacent secure store or encrypted drive); losing it
  means `terraform import` gymnastics later.
- Remote state (OCI Object Storage backend) is the eventual enterprise
  upgrade — noted as future work, not needed for one instance.

## 6. Troubleshooting — `did not find a proper configuration for private key`

Seen on 2026-08-24 (first live plan, Windows/PowerShell). It means BOTH key
inputs reached terraform empty: `TF_VAR_oci_api_private_key` didn't survive the
trip (the PEM is multiline — either the Infisical UI paste lost its newlines,
or the CLI's Windows env injection dropped it), so the dual-mode provider fell
back to `private_key_path`, which was also unset.

**Deterministic unblock — now ZERO-path (2026-08-24 refinement):** the
provider resolves the key in three tiers, in order:
1. `TF_VAR_oci_api_private_key` (full PEM from Infisical — where multiline env injection works; not reliable on Windows)
2. `TF_VAR_private_key_path` (explicit override — now **optional**, delete it if you created it)
3. **Convention default: `~/.oci/oci_api_key.pem`** — terraform's own `pathexpand()` resolves this on whatever machine runs the plan. **No machine-specific path is stored anywhere.**

So on Windows: keep the pem at the standard location (it already is:
`C:\Users\<you>\.oci\oci_api_key.pem` IS `~/.oci/oci_api_key.pem`), delete the
`TF_VAR_private_key_path` secret if you added it, empty/delete
`TF_VAR_oci_api_private_key`, delete the stale `oci.tfplan`, re-run. On any
future PC: install terraform + infisical CLI, drop the pem at `~/.oci/` —
works with zero edits.

Security posture: unchanged in practice — the local pem exists regardless (it
was generated locally), lives outside the repo, is git-ignored via `*.pem`,
and the Infisical copy remains as the laptop-loss backup.

**60-second diagnostic (tells you WHICH failure it was — run before emptying the secret if you want the answer):**

```powershell
infisical secrets export --projectId <PROJECT_ID> --env dev --format dot-env | Out-File -Encoding utf8 $env:TEMP\infcheck.env
(Get-Content $env:TEMP\infcheck.env | Where-Object { $_ -like 'TF_VAR_oci_api_private_key=*' }).Length
Remove-Item $env:TEMP\infcheck.env
```

- ≈ **1755–1790** → the stored PEM is intact; the CLI's env injection is the culprit → stay on Option A permanently.
- ≈ **30–40** → the secret value is empty/short (paste lost the PEM) → re-paste via `Get-Content $env:USERPROFILE\.oci\oci_api_key.pem | Set-Clipboard` if you want Option B back.

## 7. What changed in the delivered files for this

| File | Change |
|---|---|
| `terraform/provider.tf` | dual-mode: `private_key` (contents, from Infisical — preferred) with automatic fallback to `private_key_path` (local file, Option A) |
| `terraform/variables.tf` | new sensitive `oci_api_private_key` (default ""); `private_key_path` now optional (default "") |
| `terraform/main.tf` | delivered in this folder (Phase-2 version): `bastion_source_cidrs` filtered through a local so a blank Infisical secret can never inject an invalid CIDR; comments updated for the Infisical flow |
| `terraform.tfvars.example` | **deleted** — this document replaces it |
| `PHASE-2-RUNBOOK.md` Stage 1 | rewritten to the Infisical flow (no tfvars step) |
