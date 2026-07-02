# ==============================================================================
# AI-CONTEXT:
# Purpose: Single-stage, memory-optimized build for the Go ZKP Microservice.
# Reason: Multi-stage alpine builds stripped necessary CGO/runtime dependencies causing a fatal panic (Exit 2). 
# We maintain stability by reverting to single-stage but enforcing strict GOMEMLIMITs here and CPU limits in Compose.
# ==============================================================================

FROM golang:1.22-alpine

# Install build and runtime dependencies
RUN apk add --no-cache git protoc protobuf-dev tzdata ca-certificates

WORKDIR /app

# Pre-fetch Go modules to cache dependencies
COPY go.mod go.sum* ./
RUN go mod download

# Install Protobuf compilers
RUN go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
RUN go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest

# Copy all source code (including required configs/templates)
COPY . .

# Generate Protobuf files
RUN mkdir -p pb
RUN protoc --go_out=pb --go_opt=paths=source_relative \
    --go-grpc_out=pb --go-grpc_opt=paths=source_relative \
    aegis_zkp.proto

# Compile the binary
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-w -s" -o zkp-service main.go

# Set strict memory limits for the Go runtime environment to prevent host OOM kills
ENV GOMEMLIMIT=200MiB
ENV GOGC=50

EXPOSE 9090

# Run the compiled application
ENTRYPOINT ["/app/zkp-service"]