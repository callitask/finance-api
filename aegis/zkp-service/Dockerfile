# /**
#  * AI-CONTEXT:
#  *
#  * Purpose:
#  * - Enterprise Zero-Trust Dockerfile for the AEGIS L3-ZKA (Zero-Knowledge Proof) Go Microservice.
#  *
#  * Scope:
#  * - Compiles the Go-based ZKP gRPC server into a minimal, hardened container.
#  *
#  * Critical Dependencies:
#  * - Backend Spring Boot connects to this via gRPC (port 9090).
#  *
#  * Security Constraints:
#  * - Must use multi-stage builds to prevent source code and tooling leakage into production.
#  * - Must run as a compiled, stripped binary.
#  *
#  * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
#  * - ADDED (AEGIS Phase 5):
#  * • Created multi-stage Dockerfile utilizing `golang:1.22-alpine` for building and `alpine:latest` for runtime execution.
#  * • Stripped debug symbols (`-ldflags="-w -s"`) to reduce attack surface and binary size.
#  * • Injected `netcat-openbsd` into the runtime layer for native Docker TCP healthchecks.
#  * • Why: Secures the gRPC authentication engine from shell injection and container escape vectors while ensuring VirtualBox stability.
#  */

# Stage 1: Build
FROM golang:1.22-alpine AS builder

# Install git and required dependencies for go mod
RUN apk add --no-cache git

WORKDIR /app

# Copy source code
COPY . .

# Ensure go.mod exists (failsafe for dynamic init if not checked into source control)
RUN if [ ! -f go.mod ]; then go mod init aegis/zkp-service; fi

# Fetch dependencies
RUN go mod tidy

# Build statically linked binary without debug info to shrink footprint
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-w -s" -o zkp-service main.go

# Stage 2: Runtime Minimal
FROM alpine:latest

# Install network tools for Docker TCP healthchecks
RUN apk --no-cache add netcat-openbsd

WORKDIR /

# Copy compiled binary from builder
COPY --from=builder /app/zkp-service /zkp-service

# Expose gRPC port
EXPOSE 9090

# Execute the binary natively
ENTRYPOINT ["/zkp-service"]