// AI-CONTEXT:
// Purpose: Entry point for the Go-based ZKP microservice.
// Scope: Listens on gRPC port 9090, receives proof requests, and delegates them to the gnark circuit.
// Critical Dependencies: Requires aegis_zkp.proto generated code (`pb` package).
// Security Constraints: Bound strictly to 0.0.0.0:9090 within the Docker Compose network.
// Future AI Guidance: Use Resilience4j Circuit Breakers in Java when calling this service to prevent cascade failures.
//
// IMMUTABLE CHANGE HISTORY:
// - ADDED: Initial gRPC server implementation for Phase 4 ZKP integration.
// - DO-NOT-DELETE RULE: This section must never be deleted.

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

	return &pb.ZkpResponse{
		IsValid:         isValid,
		ConfidenceScore: 100.0,
		ErrorMessage:    "",
	}, nil
}

func main() {
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