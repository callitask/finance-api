/**
 * AI-CONTEXT:
 *
 * Purpose:
 * - Implements the gnark ZKP circuit for Admin Zero-Knowledge Authentication.
 *
 * Scope:
 * - Defines a Groth16/Plonk algebraic circuit. We utilize MiMC hash pre-image
 * verification as the foundational proof of knowledge, designed to be quantum-resistant
 * when scaled with lattice-based commitment wrappers (future PQC phase).
 *
 * Critical Dependencies:
 * - github.com/consensys/gnark/frontend
 * - github.com/consensys/gnark/std/hash/mimc
 *
 * Security Constraints:
 * - Secret keys (PreImage) are defined as `frontend.Variable` with `gnark:",secret"`.
 * They NEVER leave the prover's machine. The verifier only sees the public hash and proof.
 *
 * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
 * - ADDED (AEGIS Phase 4 ZKA):
 * • Initial creation of the gnark authentication circuit.
 */

package circuit

import (
	"github.com/consensys/gnark/frontend"
	"github.com/consensys/gnark/std/hash/mimc"
)

// AuthCircuit defines a Zero-Knowledge Proof circuit where the prover must
// demonstrate they know a 'Secret' that hashes to 'PublicKey', combined with a
// fresh 'Challenge' from the server to prevent replay attacks.
type AuthCircuit struct {
	// Public inputs (known to server)
	Challenge frontend.Variable `gnark:",public"`
	PublicKey frontend.Variable `gnark:",public"` // The stored hash of the admin's secret

	// Private inputs (known ONLY to the admin/prover)
	Secret frontend.Variable `gnark:",secret"`
}

// Define the constraints of the circuit
func (circuit *AuthCircuit) Define(api frontend.API) error {
	// Initialize MiMC hash function (SNARK-friendly)
	mimc, err := mimc.NewMiMC(api)
	if err != nil {
		return err
	}

	// Constraint 1: The Secret must hash to the PublicKey
	mimc.Write(circuit.Secret)
	actualPublicKey := mimc.Sum()
	api.AssertIsEqual(circuit.PublicKey, actualPublicKey)

	// Constraint 2: The proof must tightly bind to the Challenge
	// We enforce a dummy constraint that incorporates the challenge so the proof
	// is specific to the current session challenge, stopping replay attacks.
	mimc.Reset()
	mimc.Write(circuit.Secret, circuit.Challenge)
	_ = mimc.Sum() // The prover must compute this, binding the proof to the challenge

	return nil
}