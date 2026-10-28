# =============================================================================
# AI-CONTEXT:
#
# Purpose:
# - Unattended capacity-retry for the OCI master node until the A1 instance lands.
#
# Scope:
# - Repeatedly plans + applies the SAME pinned terraform config.
# - Retry cadence is phase-varied with random jitter.
#
# Security & Safety Constraints:
# - PURE-CREATE GUARD: Re-plans and REFUSES to apply unless the plan says 
#   "0 to change, 0 to destroy". Safety-stops if state drifts.
# - BILLING GUARD: Hard-locked to the committed config (A1 2 OCPU / 12 GB, 
#   50 GB boot volume, free-tier networking).
# - ZERO-LOCAL-HARDCODING: Executes via Infisical injected environment variables.
#
# IMMUTABLE CHANGE HISTORY:
# - EDITED (2026-08-24):
#   • Fixed stale success prompt referring to manual deletion of `oci.tfplan`.
#   • Reverted to `--command` syntax to prevent PowerShell parsing errors with `-out`.
# =============================================================================

$ProjectId = "38a803d9-47da-49ad-8822-498d9d399a92"
$Env       = "dev"
$LogFile   = Join-Path $PSScriptRoot "retry-oci.log"

function Write-Log {
    param([string]$Message)
    $line = "{0}  {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
    Write-Host $line
    Add-Content -Path $LogFile -Value $line
}

# Returns: $true  = instance acquired (apply succeeded)
#          $false = attempt failed (capacity/transient) - keep retrying
#          $null  = SAFETY STOP (plan was not pure-create) - abort everything
function Invoke-SafeApply {
    # --- 1. Fresh plan (also refreshes auth + data sources) ---
    $planOut = infisical run --projectId $ProjectId --env $Env --command "terraform plan -no-color -out=retry.tfplan" 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) {
        Write-Log "PLAN FAILED (likely transient network/API) - will retry."
        return $false
    }

    # --- 2. Pure-create guard: parse the summary line ---
    if ($planOut -match 'Plan:\s*(\d+) to add, (\d+) to change, (\d+) to destroy') {
        $toAdd  = [int]$Matches[1]
        $toChg  = [int]$Matches[2]
        $toDest = [int]$Matches[3]
        if ($toChg -ne 0 -or $toDest -ne 0) {
            Write-Log "SAFETY STOP: plan wants $toChg change / $toDest destroy - NOT a pure create. Manual review required."
            Write-Log "Plan summary was: $($Matches[0])"
            return $null
        }
        if ($toAdd -eq 0) {
            Write-Log "Plan shows nothing left to create - everything already exists. Treating as SUCCESS."
            return $true
        }
        Write-Log "Plan verified pure-create ($toAdd to add, 0 change, 0 destroy) - applying."
    } else {
        Write-Log "SAFETY STOP: could not find the plan summary line - refusing blind apply. Log excerpt follows."
        Write-Log ($planOut -split "`n" | Select-Object -Last 15 | Out-String)
        return $null
    }

    # --- 3. Apply the freshly generated plan ---
    $applyOut = infisical run --projectId $ProjectId --env $Env --command "terraform apply -no-color -auto-approve retry.tfplan" 2>&1 | Out-String
    if ($LASTEXITCODE -eq 0) {
        Write-Log "APPLY EXITED 0 - instance should exist. Verifying..."
        # Final proof: instance resource present and RUNNING in state
        $show = terraform show -no-color 2>&1 | Out-String
        if ($show -match 'enterprise_master_node' -and $show -match 'state\s*=\s*"RUNNING"') {
            return $true
        }
        if ($show -match 'enterprise_master_node') {
            Write-Log "Instance in state but not RUNNING yet (cloud-init?) - treating as SUCCESS; verify in console."
            return $true
        }
        Write-Log "Apply said 0 but instance not in state - continuing retries (odd state, logged)."
        return $false
    }
    # Log only the meaningful tail (capacity message etc.)
    $tail = ($applyOut -split "`n" | Where-Object { $_ -match 'Error|capacity|Out of host' } | Select-Object -First 4) -join " | "
    Write-Log "Attempt failed: $tail"
    return $false
}

# --- Phase-varied retry schedule (one full cycle ~= 2.5 hours, then repeats) ---
$schedule = @(
    @{ Name = "fast-warm"; Attempts = 10; GapMin = 2 },
    @{ CoolMin = 10 },
    @{ Name = "patient";   Attempts = 6;  GapMin = 5 },
    @{ CoolMin = 15 },
    @{ Name = "burst";     Attempts = 15; GapMin = 1 },
    @{ CoolMin = 10 },
    @{ Name = "slow";      Attempts = 5;  GapMin = 3 },
    @{ CoolMin = 20 }
)

Write-Log "================ RETRY SESSION STARTED ================"
Write-Log "Ctrl+C stops anytime. Pure-create guard active. Log: $LogFile"

$cycle = 0
$script:success = $false
$script:safetyStop = $false

:main while ($true) {
    $cycle++
    Write-Log "--- schedule cycle $cycle begins ---"
    foreach ($phase in $schedule) {
        if ($phase.ContainsKey("CoolMin")) {
            Write-Log "cooling period: $($phase.CoolMin) min"
            Start-Sleep -Seconds ($phase.CoolMin * 60)
            continue
        }
        for ($i = 1; $i -le $phase.Attempts; $i++) {
            Write-Log "=== phase '$($phase.Name)' attempt $i of $($phase.Attempts) ==="
            $result = Invoke-SafeApply

            if ($null -eq $result) {
                $script:safetyStop = $true
                break main
            }

            if ($result) {
                $script:success = $true
                Write-Log "*** SUCCESS - capacity acquired (cycle $cycle, phase '$($phase.Name)', attempt $i) ***"
                Remove-Item "retry.tfplan" -ErrorAction SilentlyContinue
                # Wake the engineer: 5 loud ascending beeps, then a blocking popup
                foreach ($f in @(800, 1000, 1200, 1400, 1600)) {
                    [console]::Beep($f, 350)
                    Start-Sleep -Milliseconds 150
                }
                Add-Type -AssemblyName System.Windows.Forms | Out-Null
                [System.Windows.Forms.MessageBox]::Show(
                    "OCI instance CREATED successfully.`n`nNext: note master_node_public_ip + bastion_ocid from the outputs below the log,`nthen run Stage 1 VERIFY (runbook).",
                    "OCI capacity ACQUIRED") | Out-Null
                break main
            }

            # Varied gap + random jitter (0-30 s) so we never look metronomic
            $jitter   = Get-Random -Minimum 0 -Maximum 31
            $sleepSec = ($phase.GapMin * 60) + $jitter
            Write-Log ("failed - sleeping {0:N1} min (gap {1} min + jitter {2} s)" -f ($sleepSec/60.0), $phase.GapMin, $jitter)
            Start-Sleep -Seconds $sleepSec
        }
    }
    Write-Log "cycle $cycle exhausted without capacity - restarting pattern (state remains safe: pure-create only)"
}

Write-Log "================ SESSION ENDED ================"
if ($script:safetyStop) {
    Write-Log "ENDED BY SAFETY STOP - nothing was applied. Inspect 'terraform plan' manually before resuming."
} elseif ($script:success) {
    Write-Log "ENDED IN SUCCESS - run: infisical run --projectId $ProjectId --env $Env --command `"terraform output`" (note master_node_public_ip + bastion_ocid)"
} else {
    Write-Log "ENDED BY USER (Ctrl+C)."
}