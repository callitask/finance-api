# /**
#  * AI-CONTEXT:
#  *
#  * Purpose:
#  * - Builds the automated backup service container.
#  *
#  * Scope:
#  * - Alpine-based image containing necessary CLI tools for DB dumping and S3 uploading.
#  *
#  * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
#  * - EDITED (Pipeline Stabilization):
#  * • Added `openssl` to support AES-256-CBC encryption of database dumps.
#  * • Added `coreutils` to replace Alpine's busybox `date` with GNU `date`, resolving `-7 days` retention syntax errors.
#  * • Added AI-CONTEXT block.
#  */
FROM alpine:3.19

# Install dependencies for MySQL backup, S3 upload, encryption, and GNU date manipulation
RUN apk add --no-cache mysql-client aws-cli bash gzip openssl coreutils

WORKDIR /scripts

# Copy backup scripts
COPY backup.sh .
COPY restore.sh .

RUN chmod +x backup.sh restore.sh

# Default command: Run the backup loop
CMD ["./backup.sh"]