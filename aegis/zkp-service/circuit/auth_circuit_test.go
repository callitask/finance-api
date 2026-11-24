// AI-CONTEXT:
// Purpose: Unit test for AuthCircuit + VerifyLatticeSchnorrProof (VULN-04d — real Groth16).
// Scope: Proves a genuine Groth16 proof generated from AuthCircuit is accepted (true, nil);
//   forged/malformed/truncated/wrong-challenge/empty-key proofs are rejected (false, err).
// Critical Dependencies: github.com/consensys/gnark (v0.14.0, per Dockerfile pin).
// Security Constraints: Tests MUST stay fail-closed; no test may assert acceptance of anything
//   but a genuinely prover-generated proof.
// Future AI Guidance: Keep `bytesToScalar` mapping consistent with schnorr_lattice.go; do not
//   regenerate the genuine proof path into a different circuit or modulus.
//
// IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
// - ADDED (Batch 3A - P3.1, VULN-04d):
//   • genuineProofData(): compiles AuthCircuit, runs groth16.Setup + NewProver + Prove,
//     serializes the Groth16 proof, and returns wire format = 32-byte ChallengeBinding || proof.
//   • TestAuthCircuitVerifyLatticeSchnorrProof(): (1) genuine proof → (true, nil);
//     (2) forged "AAAA" → (false, non-nil err); (3) 64 zero bytes → (false, non-nil err);
//     (4) bit-flipped genuine proof → (false, non-nil err); (5) wrong challenge bytes →
//     (false, non-nil err); (6) empty publicKey → (false, non-nil err).
// - FIX (Batch 3B, Step 0 — audit findings): • Import fixes: `github.com/consensys/gnark/ecc/bn254`
//   → `github.com/consensys/gnark-crypto/ecc`; `github.com/consensys/gnark/frontend/r1cs` →
//   `github.com/consensys/gnark/frontend/cs/r1cs`; added `nativeMimc` (`github.com/consensys/gnark-crypto/ecc/bn254/fr/mimc`).
//   • genuineProofData() replaced: returns `(pubKeyBytes, proofData)`; compiles AuthCircuit, runs
//   `groth16.Setup(ccs)`, assigns `zkpVk = vk`; computes genuine BN254 MiMC hashes with
//   `nativeMimc.NewMiMC()` + `h.Write(...)` + `h.Sum(nil)` matching AuthCircuit.Define; builds the
//   assignment with `frontend.Variable(...)`; runs `groth16.Prove(ccs, pk, w)`; returns
//   `pubKeyBytes` + `append(bindingBytes, proofBuf.Bytes()...)`.
//   • Test uses the returned `publicKey, proofData` — so VerifyLatticeSchnorrProof verifies the
//   genuine proof against the test's matching verifying key.

package circuit

import (
	"bytes"
	"testing"

	"github.com/consensys/gnark/backend/groth16"
	"github.com/consensys/gnark-crypto/ecc"
	"github.com/consensys/gnark/frontend"
	"github.com/consensys/gnark/frontend/cs/r1cs"
	nativeMimc "github.com/consensys/gnark-crypto/ecc/bn254/fr/mimc"
)

// genuineProofData returns a wire-format proof (32-byte ChallengeBinding || Groth16 proof)
// generated from the real AuthCircuit via gnark's prover. The assignment's public inputs
// (Challenge, PublicKey, ChallengeBinding) are the same byte→scalar mappings used by
// VerifyLatticeSchnorrProof so a genuine proof verifies with (true, nil).
func genuineProofData(t *testing.T, secret, challenge []byte) (pubKeyBytes []byte, proofData []byte) {
	t.Helper()

	ccs, err := frontend.Compile(ecc.BN254.ScalarField(), r1cs.NewBuilder, &AuthCircuit{})
	if err != nil {
		t.Fatalf("compile: %v", err)
	}
	pk, vk, err := groth16.Setup(ccs)
	if err != nil {
		t.Fatalf("setup: %v", err)
	}
	zkpVk = vk // Make VerifyLatticeSchnorrProof verify against the same key

	secretScalar := bytesToScalar(secret)
	challengeScalar := bytesToScalar(challenge)
	secretBytes32 := secretScalar.FillBytes(make([]byte, 32))
	challengeBytes32 := challengeScalar.FillBytes(make([]byte, 32))

	h := nativeMimc.NewMiMC()
	h.Write(secretBytes32)
	pubKeyBytes = h.Sum(nil) // 32-byte BN254 field element

	h.Reset()
	h.Write(secretBytes32)
	h.Write(challengeBytes32)
	bindingBytes := h.Sum(nil) // 32-byte BN254 field element

	assignment := AuthCircuit{
		Secret:           frontend.Variable(secretScalar),
		PublicKey:        frontend.Variable(bytesToScalar(pubKeyBytes)),
		Challenge:        frontend.Variable(challengeScalar),
		ChallengeBinding: frontend.Variable(bytesToScalar(bindingBytes)),
	}
	w, err := frontend.NewWitness(&assignment, ecc.BN254.ScalarField())
	if err != nil {
		t.Fatalf("new witness: %v", err)
	}
	proof, err := groth16.Prove(ccs, pk, w)
	if err != nil {
		t.Fatalf("prove: %v", err)
	}

	var proofBuf bytes.Buffer
	if _, err := proof.WriteTo(&proofBuf); err != nil {
		t.Fatalf("serialize proof: %v", err)
	}

	return pubKeyBytes, append(bindingBytes, proofBuf.Bytes()...)
}

func TestAuthCircuitVerifyLatticeSchnorrProof(t *testing.T) {
	const challengeStr = "test-challenge-abc123"
	publicKey, proofData := genuineProofData(t, []byte("admin-test-secret"), []byte(challengeStr))

	// 1. Genuine Groth16 proof → VerifyLatticeSchnorrProof returns (true, nil)
	ok, err := VerifyLatticeSchnorrProof(publicKey, proofData, []byte(challengeStr))
	if !ok || err != nil {
		t.Fatalf("genuine proof rejected: ok=%v err=%v", ok, err)
	}

	// 2. Forged proof "AAAA" (len 4 <= 32) → (false, non-nil err)
	if ok, err := VerifyLatticeSchnorrProof(publicKey, []byte("AAAA"), []byte(challengeStr)); ok || err == nil {
		t.Fatalf("forged 'AAAA' should fail closed: ok=%v err=%v", ok, err)
	}

	// 3. Forged 64-zero-byte proof → (false, non-nil err)
	if ok, err := VerifyLatticeSchnorrProof(publicKey, make([]byte, 64), []byte(challengeStr)); ok || err == nil {
		t.Fatalf("forged 64-zero-byte proof should fail closed: ok=%v err=%v", ok, err)
	}

	// 4. Bit-flipped genuine proof → (false, non-nil err)
	mutated := append([]byte(nil), proofData...)
	mutated[len(mutated)-1] ^= 0xFF
	if ok, err := VerifyLatticeSchnorrProof(publicKey, mutated, []byte(challengeStr)); ok || err == nil {
		t.Fatalf("bit-flipped proof should fail closed: ok=%v err=%v", ok, err)
	}

	// 5. Wrong challenge bytes → (false, non-nil err)
	if ok, err := VerifyLatticeSchnorrProof(publicKey, proofData, []byte("wrong-challenge")); ok || err == nil {
		t.Fatalf("wrong challenge should fail closed: ok=%v err=%v", ok, err)
	}

	// 6. Empty publicKey → (false, non-nil err)
	if ok, err := VerifyLatticeSchnorrProof(nil, proofData, []byte(challengeStr)); ok || err == nil {
		t.Fatalf("empty publicKey should fail closed: ok=%v err=%v", ok, err)
	}
}
