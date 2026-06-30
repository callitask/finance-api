# ==============================================================================
# AI-CONTEXT:
# Purpose: Multi-stage, memory-optimized build for the Go ZKP Microservice.
# Reason: The previous single-stage build caused the VirtualBox kernel to OOM-kill the container (Exit Code 2) during heavy pipeline execution. This multi-stage approach compiles the binary in an ephemeral layer and isolates the runtime to a minimal footprint, protecting host memory.
# ==============================================================================

# STAGE 1: The Builder (Compiles the code)
FROM golang:1.22-alpine AS builder

# Install build dependencies (git, protobuf)
RUN apk add --no-cache git protoc protobuf-dev

WORKDIR /app

# Pre-fetch Go modules to cache dependencies
COPY go.mod go.sum* ./
RUN go mod download

# Install Protobuf compilers
RUN go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
RUN go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest

# Copy source code
COPY . .

# Generate Protobuf files
RUN mkdir -p pb
RUN protoc --go_out=pb --go_opt=paths=source_relative \
    --go-grpc_out=pb --go-grpc_opt=paths=source_relative \
    aegis_zkp.proto

# Compile the binary with memory-constrained flags
# -w -s strips debugging info to make the binary tiny
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-w -s" -o zkp-service main.go

# STAGE 2: The Minimal Runtime (Runs the code)
# We use Alpine instead of scratch so we can shell in if needed for debugging
FROM alpine:latest

# Install minimal network tools for healthchecks
RUN apk --no-cache add ca-certificates tzdata

WORKDIR /app

# Copy ONLY the compiled binary from the builder stage
COPY --from=builder /app/zkp-service /app/zkp-service

# Expose the gRPC port
EXPOSE 9090

# Set strict memory limits for the Go runtime environment
ENV GOMEMLIMIT=200MiB
ENV GOGC=50

# Run the binary
ENTRYPOINT ["/app/zkp-service"]