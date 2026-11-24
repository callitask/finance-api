// AI-CONTEXT:
// Purpose: Gnark circuit definition for lattice-based Schnorr proofs + real Groth16 verifier.
// Scope: Defines constraints; InitVerifier() compiles AuthCircuit and loads the BN254 Groth16
//   VerifyingKey once; VerifyLatticeSchnorrProof() rejects malformed/forged proofs via
//   groth16.Verify (strict fail-closed: true, nil only when Groth16 verification succeeds).
// Critical Dependencies: github.com/consensys/gnark (v0.14.0, per Dockerfile pin),
//   std/hash/mimc (circuit-side hashing), backend/groth16 (verification).
// Security Constraints:
// - Fail closed: any error/malformed input/empty publicKey/truncated proof → (false, err).
// - Prover secret (Secret) never leaves the prover.
// - Wire format: proofData = 32-byte ChallengeBinding || raw Groth16 BN254 proof bytes
//   (aegis_zkp.proto `bytes proof_data` — unchanged).
// - Byte→scalar mapping is SHA-256 reduced mod ecc.BN254.ScalarField() and MUST match
//   auth_circuit_test.go's expectations exactly.
// Future AI Guidance: Keep the fail-closed order: verifier-init → length checks → ReadFrom →
//   witness build → groth16.Verify. Never return true on a verification error.
//
// IMMUTABLE CHANGE HISTORY:
// - ADDED: Initial Gnark circuit definition for Phase 4 ZKP integration.
// - DO-NOT-DELETE RULE: This section must never be deleted.
// - EDITED (Batch 3A - P3.1, VULN-04d):
//   • Replaced the stub VerifyLatticeSchnorrProof (returned true,nil for any non-empty
//     publicKey+proofData) with real Groth16 verification.
//   • Implemented InitVerifier(): compiles AuthCircuit once over BN254 via
//     frontend.Compile(ecc.BN254.ScalarField(), r1cs.NewBuilder, &AuthCircuit{}); loads
//     groth16.VerifyingKey from $AEGIS_ZKP_VK_PATH if set (fail closed if unreadable/corrupt);
//     otherwise runs groth16.Setup(ccs) once at boot (zero-volume container, well under 256M).
//   • Wire format: proofData = 32-byte ChallengeBinding || raw gnark Groth16 proof bytes.
//   • bytesToScalar(b): len==32 → SetBytes mod scalar field; else SHA-256 mod scalar field.
//   • Strict semantics: return (true, nil) ONLY when groth16.Verify returns nil.
// - FIX (Batch 3B, Step 0 — audit findings): • Import path fix: `github.com/consensys/gnark/ecc/bn254`
//   → `github.com/consensys/gnark-crypto/ecc`; `github.com/consensys/gnark/frontend/r1cs` →
//   `github.com/consensys/gnark/frontend/cs/r1cs`. • `groth16.VerifyingKey` is an interface in
//   gnark v0.14.0 (not a struct pointer): `var zkpVk groth16.VerifyingKey`; VK-path branch uses
//   `groth16.NewVerifyingKey(ecc.BN254)` + `if _, err := vk.ReadFrom(f); err != nil`; Setup branch
//   `zkpVk = vk` (no `&`). • `proof.ReadFrom` returns `(int64, error)` — 2-return handled.

package circuit

import (
	"bytes"
	"crypto/sha256"
	"errors"
	"fmt"
	"math/big"
	"os"
	"sync"

	"github.com/consensys/gnark/backend/groth16"
	"github.com/consensys/gnark-crypto/ecc"
	"github.com/consensys/gnark/frontend"
	"github.com/consensys/gnark/frontend/cs/r1cs"
)

// zkpVk is the BN254 Groth16 VerifyingKey (gnark v0.14.0: interface, not struct pointer),
// initialized once via InitVerifier().
var zkpVk groth16.VerifyingKey
var zkpInitOnce sync.Once
var zkpInitErr error

// InitVerifier compiles the AuthCircuit and loads the Groth16 VerifyingKey exactly once.
// Called from main() via sync.Once before the gRPC server starts (fail-fast).
func InitVerifier() error {
	zkpInitOnce.Do(func() {
		// 1. Compile the AuthCircuit once over the BN254 scalar field
		ccs, err := frontend.Compile(ecc.BN254.ScalarField(), r1cs.NewBuilder, &AuthCircuit{})
		if err != nil {
			zkpInitErr = fmt.Errorf("failed to compile AuthCircuit: %w", err)
			return
		}

		// 2. Obtain the verifying key: mounted file if configured, else Setup()
		if vkPath := os.Getenv("AEGIS_ZKP_VK_PATH"); vkPath != "" {
			vk := groth16.NewVerifyingKey(ecc.BN254)
			f, err := os.Open(vkPath)
			if err != nil {
				zkpInitErr = fmt.Errorf("failed to open AEGIS_ZKP_VK_PATH %q: %w", vkPath, err)
				return
			}
			defer f.Close()
			if _, err := vk.ReadFrom(f); err != nil {
				zkpInitErr = fmt.Errorf("failed to load Groth16 VerifyingKey from %q: %w", vkPath, err)
				return
			}
			zkpVk = vk
		} else {
			// Default zero-volume container boot: run Setup once so vk is an authentic
			// BN254 Groth16 VerifyingKey that mathematically rejects forged/random/empty proofs.
			_, vk, err := groth16.Setup(ccs)
			if err != nil {
				zkpInitErr = fmt.Errorf("failed to setup Groth16 verifier: %w", err)
				return
			}
			zkpVk = vk
		}
	})
	return zkpInitErr
}

// bytesToScalar deterministically reduces bytes to a BN254 scalar field element:
// 32-byte values → new(big.Int).SetBytes(b) mod ScalarField(); other lengths → SHA-256.
// Keeps the verifier and auth_circuit_test.go on the same mapping (VULN-04d).
func bytesToScalar(b []byte) *big.Int {
	var x *big.Int
	if len(b) == 32 {
		x = new(big.Int).SetBytes(b)
	} else {
		sum := sha256.Sum256(b)
		x = new(big.Int).SetBytes(sum[:])
	}
	return new(big.Int).Mod(x, ecc.BN254.ScalarField())
}

// VerifyLatticeSchnorrProof replaces the accepting stub with real Groth16 verification.
// Signature is preserved (aegis_zkp.proto unchanged).
func VerifyLatticeSchnorrProof(publicKey []byte, proofData []byte, challenge []byte) (bool, error) {
	// Fail closed: verifier must be initialized
	if zkpVk == nil {
		return false, errors.New("AEGIS ZKP: verifier not initialized (fail closed)")
	}
	// Fail closed on empty params / truncated proofs
	if len(publicKey) == 0 || len(challenge) == 0 || len(proofData) <= 32 {
		return false, errors.New("AEGIS ZKP: empty publicKey/challenge or truncated proof (fail closed)")
	}

	// Wire format: 32-byte ChallengeBinding || raw gnark Groth16 BN254 proof bytes
	bindingBytes := proofData[:32]
	rawProofBytes := proofData[32:]

	// Deserialize the proof (malformed/random bytes fail closed with err)
	proof := groth16.NewProof(ecc.BN254)
	if _, err := proof.ReadFrom(bytes.NewReader(rawProofBytes)); err != nil {
		return false, fmt.Errorf("AEGIS ZKP: malformed Groth16 proof: %w", err)
	}

	// Map wire bytes to BN254 field elements (verifier/test consistent — see bytesToScalar)
	challengeBinding := bytesToScalar(bindingBytes)
	pubKeyScalar := bytesToScalar(publicKey)
	challengeScalar := bytesToScalar(challenge)

	// Construct the public witness (VULN-04d: ChallengeBinding bound to proof)
	assignment := AuthCircuit{
		Challenge:        frontend.Variable(challengeScalar),
		PublicKey:        frontend.Variable(pubKeyScalar),
		ChallengeBinding: frontend.Variable(challengeBinding),
	}
	pubWitness, err := frontend.NewWitness(&assignment, ecc.BN254.ScalarField(), frontend.PublicOnly())
	if err != nil {
		return false, fmt.Errorf("AEGIS ZKP: failed to build public witness: %w", err)
	}

	// Strictly and exclusively return true when groth16.Verify returns nil
	if err := groth16.Verify(proof, zkpVk, pubWitness); err != nil {
		return false, fmt.Errorf("AEGIS ZKP: Groth16 verification failed: %w", err)
	}
	return true, nil
}
