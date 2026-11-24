// AI-CONTEXT:
// Purpose: Entry point for the Go-based ZKP microservice.
// Scope: Listens on gRPC port 9090, receives proof requests, and delegates them to the gnark circuit.
// Critical Dependencies: Requires aegis_zkp.proto generated code (`pb` package); compiled Groth16
//   VerifyingKey (InitVerifier, per gnark v0.14.0).
// Security Constraints: Bound strictly to 0.0.0.0:9090 within the Docker Compose network.
//   Fail closed: exits on Groth16 init failure (VULN-04d); ConfidenceScore is 0.0 whenever !isValid.
// Future AI Guidance: Use Resilience4j Circuit Breakers in Java when calling this service to prevent cascade failures.
//
// IMMUTABLE CHANGE HISTORY:
// - ADDED: Initial gRPC server implementation for Phase 4 ZKP integration.
// - DO-NOT-DELETE RULE: This section must never be deleted.
// - EDITED (Batch 3A - P3.1, VULN-04d):
//   • main(): calls `circuit.InitVerifier()` before `net.Listen` — log.Fatalf on failure (fail fast).
//   • VerifyProof(): restructured success path — `score := 0.0; if isValid { score = 100.0 }` and
//     ConfidenceScore is 0.0 whenever !isValid (previously always 100.0 on success path).
//   • Behavior preserved: error path returns 0.0 + ErrorMessage; valid path returns IsValid + score.

package main

import (
	"context"
	"log"
	"net"

	"google.golang.org/grpc"

	pb "aegis/zkp-service/pb"
	"aegis/zkp-service/circuit"
)

type server struct {
	pb.UnimplementedZkpValidatorServer
}

func (s *server) VerifyProof(ctx context.Context, req *pb.ZkpRequest) (*pb.ZkpResponse, error) {
	log.Printf("Received proof verification request for session: %s", req.GetSessionId())

	// Delegate to the Gnark circuit verification logic
	isValid, err := circuit.VerifyLatticeSchnorrProof(req.GetPublicKey(), req.GetProofData(), req.GetChallengeString())

	if err != nil {
		return &pb.ZkpResponse{
			IsValid:         false,
			ConfidenceScore: 0.0,
			ErrorMessage:    err.Error(),
		}, nil
	}

	// VULN-04d: ConfidenceScore is 0.0 whenever !isValid (parallel fail-closed semantics).
	score := 0.0
	if isValid {
		score = 100.0
	}
	return &pb.ZkpResponse{
		IsValid:         isValid,
		ConfidenceScore: score,
		ErrorMessage:    "",
	}, nil
}

func main() {
	// Compile AuthCircuit once at boot; fail fast if Groth16 init fails (zero-trust).
	if err := circuit.InitVerifier(); err != nil {
		log.Fatalf("Failed to initialize AEGIS ZKP Groth16 verifier: %v", err)
	}

	lis, err := net.Listen("tcp", ":9090")
	if err != nil {
		log.Fatalf("Failed to listen on port 9090: %v", err)
	}

	s := grpc.NewServer()
	pb.RegisterZkpValidatorServer(s, &server{})

	log.Printf("AEGIS ZKP Microservice listening at %v", lis.Addr())
	if err := s.Serve(lis); err != nil {
		log.Fatalf("Failed to serve: %v", err)
	}
}