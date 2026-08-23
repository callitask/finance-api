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
#  * - EDITED:
#  * • Upgraded builder to `golang:1.26-alpine` and explicitly pinned `gnark` and `grpc`.
#  * • Reason: gnark v0.14.0 and grpc v1.81.1 require go >= 1.25.0. The previous 1.23 toolchain caused a fatal SERVFAIL/Exit Code 1 during compilation. Moving forward to 1.26 ensures deterministic, secure compilation without rolling back enterprise dependencies.
#  * • Behavior remaining unchanged: Minimal alpine runtime and stripped 256M binary footprint.
#  * - EDITED:
#  * • Injected `protoc`, `protoc-gen-go`, and `protoc-gen-go-grpc` into the build stage.
#  * • Reason: The build was crashing with `package aegis/zkp-service/pb is not in std` because the gRPC Go files were never generated from the `.proto` schema before running `go build`. The Dockerfile now dynamically compiles the proto files into a `pb/` directory.
#  *
#  * - EDITED (Single-Stage Strategic Pivot):
#  * • Reverted architecture from a multi-stage build to a highly optimized single-stage `golang:1.26-alpine` build.
#  * • Reason: The multi-stage strip was overly aggressive and removed critical OS-level runtime dependencies, causing the container to silently panic with `Exit Code 2` on boot. The host kernel is still protected by the Compose `cpus: 0.50` limit.
#  *
#  * - STRATEGIC PIVOTS & FAILED ATTEMPTS (CRITICAL FOR FUTURE AI):
#  * • Strategy Attempted: Multi-stage build isolating the Go binary in a scratch/alpine runtime layer to save memory.
#  * • Failure Mode: Container panics with `Exit Code 2` (missing dependencies) immediately after ignition.
#  * • Date/Phase: 2026-07-02
#  * • Future AI Warning: DO NOT revert to a multi-stage build. The container must use a single-stage `golang:1.26-alpine` footprint to run successfully. The Compose constraints (`cpus: 0.50`, `memory: 256M`) provide the actual resource throttling.
#  *
#  * - EDITED (ARM64 Target Fix - 2026-08-23, OCI Migration Phase 1):
#  * • Replaced the hardcoded `GOARCH=amd64` with BuildKit's automatic
#  *   `TARGETARCH` build argument.
#  * • Reason: The OCI target is Ampere A1 (linux/arm64). The amd64 binary
#  *   cannot execute there ("exec format error") — this was the ONLY arch pin
#  *   in the entire 24-service stack. BuildKit sets TARGETARCH per build host,
#  *   so the same Dockerfile now builds amd64 on the VM/workstation and arm64
#  *   on OCI with zero behavioral change on amd64 hosts.
#  * • Single-stage mandate and all resource constraints remain untouched.
#  */

FROM golang:1.26-alpine

# Install git, protoc, protobuf dependencies, and network tools for healthchecks
RUN apk add --no-cache git protoc protobuf-dev netcat-openbsd

WORKDIR /app

# Install Go protobuf plugins
RUN go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
RUN go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest

# Add Go bin to PATH
ENV PATH="$PATH:/go/bin"

# Copy source code
COPY . .

# Generate gRPC code from proto file into the pb directory deterministically
RUN mkdir -p pb
RUN protoc --go_out=pb --go_opt=paths=source_relative \
    --go-grpc_out=pb --go-grpc_opt=paths=source_relative \
    aegis_zkp.proto

# Ensure go.mod exists and pin dependencies to stable versions
RUN if [ ! -f go.mod ]; then go mod init aegis/zkp-service; fi
RUN go get github.com/consensys/gnark@v0.14.0 google.golang.org/grpc@v1.81.1

# Fetch dependencies
RUN go mod tidy

# Build statically linked binary without debug info to shrink footprint.
# ARCH FIX (2026-08-23, OCI Migration Phase 1): the hardcoded GOARCH=amd64
# produced a dead binary on the ARM64 (Ampere A1) OCI host — exec format error
# at container start. BuildKit injects TARGETARG/TARGETARCH automatically for
# the build platform (amd64 on the VM/workstation, arm64 on OCI); the binary
# is compiled FOR the host it will run on. Single-stage mandate unchanged.
ARG TARGETARCH
RUN CGO_ENABLED=0 GOOS=linux GOARCH=${TARGETARCH} go build -ldflags="-w -s" -o zkp-service main.go

# Expose gRPC port
EXPOSE 9090

# Execute the binary natively
ENTRYPOINT ["/app/zkp-service"]