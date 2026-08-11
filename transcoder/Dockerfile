# /**
#  * AI-CONTEXT:
#  * Purpose: Hyper-minimal Alpine FFmpeg transcoder for the Treishvaam Video Pipeline.
#  * Scope: Runs a lightweight Python RabbitMQ listener (<15MB idle RAM).
#  * Security Constraints: Runs as a strictly non-root user. No docker.sock mounted.
#  * IMMUTABLE CHANGE HISTORY:
#  * - ADDED: Phase 2 Asynchronous Smart Transcoder implementation.
#  *   Why: Provides on-demand processing power without violating Zero-Trust constraints.
#  *
#  * - DO-NOT-DELETE RULE:
#  * This IMMUTABLE CHANGE HISTORY section acts as the institutional memory for future AI sessions.
#  * It must never be deleted, truncated, rewritten, or regenerated. Future AI must append only.
#  */
FROM alpine:3.19

# Install FFmpeg, Python3, and the Alpine package for Pika (RabbitMQ client) to avoid heavy pip overhead
RUN apk add --no-cache ffmpeg python3 py3-pika

# Fort Knox: Create non-root system user
RUN addgroup -S transcoder && adduser -S transcoder -G transcoder

# Setup working directory and volume mount points with correct permissions
RUN mkdir -p /app/uploads/raw /app/uploads/hls && chown -R transcoder:transcoder /app
WORKDIR /app

# Copy the lightweight asynchronous listener
COPY --chown=transcoder:transcoder transcoder.py .

# Drop root privileges
USER transcoder

# Execute python in unbuffered mode to stream logs directly to Docker/Loki
CMD ["python3", "-u", "transcoder.py"]