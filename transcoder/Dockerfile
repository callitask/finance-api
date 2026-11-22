# /**
#  * AI-CONTEXT:
#  * Purpose: Hyper-minimal Alpine FFmpeg transcoder for the Treishvaam Video Pipeline.
#  * Scope: Runs a lightweight Python RabbitMQ listener (<15MB idle RAM).
#  * Security Constraints: Runs as a strictly non-root user. No docker.sock mounted.
#  * IMMUTABLE CHANGE HISTORY:
#  * - ADDED: Phase 2 Asynchronous Smart Transcoder implementation.
#  *   Why: Provides on-demand processing power without violating Zero-Trust constraints.
#  *
#  * - EDITED (P0 - Phase 0 Security Hardening, Nov 22 2026):
#  * Replaced `addgroup -S transcoder && adduser -S transcoder -G transcoder` with `addgroup -g 1000 -S transcoder && adduser -S -G transcoder transcoder` (GID 1000 parity with backend `1000:1000` + `app_uploads` 2775 + `chmod g+s`; VULN-07b).
#  *
#  * - DO-NOT-DELETE RULE:
#  * This IMMUTABLE CHANGE HISTORY section acts as the institutional memory for future AI sessions.
#  * It must never be deleted, truncated, rewritten, or regenerated. Future AI must append only.
#  */
FROM alpine:3.19

# Install FFmpeg, Python3, and the Alpine package for Pika (RabbitMQ client) to avoid heavy pip overhead
RUN apk add --no-cache ffmpeg python3 py3-pip && pip3 install --no-cache-dir --break-system-packages pika

# Fort Knox: Create non-root system user
RUN addgroup -g 1000 -S transcoder && adduser -S -G transcoder transcoder

# Setup working directory and volume mount points with correct permissions
RUN mkdir -p /app/uploads/raw /app/uploads/hls && chown -R transcoder:transcoder /app
WORKDIR /app

# Copy the lightweight asynchronous listener
COPY --chown=transcoder:transcoder transcoder.py .

# Drop root privileges
USER transcoder

# Execute python in unbuffered mode to stream logs directly to Docker/Loki
CMD ["python3", "-u", "transcoder.py"]