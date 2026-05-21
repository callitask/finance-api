#!/bin/bash
# /**
#  * AI-CONTEXT:
#  *
#  * Purpose:
#  * - Automated Database Backup Script (PITR Enabled & Encrypted).
#  * - Dumps MariaDB, compresses, encrypts, uploads to MinIO, and rotates old backups.
#  *
#  * Scope:
#  * - Runs in an infinite loop (every 24h) inside the backup container.
#  *
#  * Security Constraints:
#  * - Database dumps contain highly sensitive PII and encrypted fields.
#  * - They MUST be encrypted at rest (AES-256-CBC) before leaving the host to MinIO.
#  * - BACKUP_ENCRYPTION_KEY must never be hardcoded.
#  *
#  * IMMUTABLE CHANGE HISTORY (DO NOT DELETE):
#  * - EDITED (Phase 4):
#  * • Added missing AI-CONTEXT header.
#  * • Implemented AES-256-CBC encryption via OpenSSL on the `.sql.gz` dump file before S3 upload.
#  * • Added a strict fail-safe: script will `exit 1` and abort the backup process if `BACKUP_ENCRYPTION_KEY` is not provided.
#  * • Changed MinIO upload to use the `.enc` file extension.
#  */

DB_HOST="${DB_HOST:-treishvaam-db}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS}"
DB_NAME="${DB_NAME:-finance_db}"
S3_ENDPOINT="${S3_ENDPOINT:-http://minio:9000}"
S3_BUCKET="${S3_BUCKET:-treishvaam-backups}"
RETENTION_DAYS=7

# Phase 4: Strict Encryption Enforcement
if [ -z "$BACKUP_ENCRYPTION_KEY" ]; then
    echo "❌ SECURITY ALERT: BACKUP_ENCRYPTION_KEY is not set!"
    echo "Aborting backup process to prevent unencrypted data leakage."
    exit 1
fi

aws configure set aws_access_key_id "${MINIO_ACCESS_KEY}"
aws configure set aws_secret_access_key "${MINIO_SECRET_KEY}"
aws configure set default.region us-east-1

echo "Starting Backup Service... (Schedule: Every 24h, Encryption: AES-256-CBC Enabled)"

# Create bucket if not exists
aws --endpoint-url "$S3_ENDPOINT" s3 mb "s3://$S3_BUCKET" 2>/dev/null || true

while true; do
    DATE=$(date +%Y-%m-%d_%H-%M-%S)
    FILE_NAME="backup_${DATE}.sql.gz"
    ENC_FILE_NAME="${FILE_NAME}.enc"

    echo "[Job Started] Creating backup: $FILE_NAME"

    # Dump and compress
    mysqldump -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASS" --single-transaction --quick --master-data=2 "$DB_NAME" | gzip > "/tmp/$FILE_NAME"

    if [ -f "/tmp/$FILE_NAME" ]; then
        FILE_SIZE=$(du -h "/tmp/$FILE_NAME" | cut -f1)
        echo "Database dumped successfully. Size: $FILE_SIZE"

        # Phase 4: Encrypt the dump
        echo "Encrypting backup file with AES-256-CBC..."
        openssl enc -aes-256-cbc -salt -in "/tmp/$FILE_NAME" -out "/tmp/$ENC_FILE_NAME" -pass pass:"$BACKUP_ENCRYPTION_KEY" -pbkdf2

        if [ $? -eq 0 ] && [ -f "/tmp/$ENC_FILE_NAME" ]; then
            echo "Encryption SUCCESS. Uploading to MinIO..."
            
            # Upload encrypted file
            aws --endpoint-url "$S3_ENDPOINT" s3 cp "/tmp/$ENC_FILE_NAME" "s3://$S3_BUCKET/$ENC_FILE_NAME"

            if [ $? -eq 0 ]; then
                echo "Upload SUCCESS."
                # Clean up both files securely
                rm "/tmp/$FILE_NAME" "/tmp/$ENC_FILE_NAME"
            else
                echo "Upload FAILED."
            fi
        else
            echo "Encryption FAILED. Backup aborted for security."
        fi
    else
        echo "Error: Backup file was not created."
    fi

    # Retention Policy
    echo "Checking for old backups..."
    aws --endpoint-url "$S3_ENDPOINT" s3 ls "s3://$S3_BUCKET/" | while read -r line; do
        createDate=`echo $line|awk {'print $1"\t"$2'}`
        createDate=`date -d"$createDate" +%s`
        olderThan=`date -d"-$RETENTION_DAYS days" +%s`
        if [[ $createDate -lt $olderThan ]]
        then
            fileName=`echo $line|awk {'print $4'}`
            if [ ! -z "$fileName" ]; then
                echo "Deleting old backup: $fileName"
                aws --endpoint-url "$S3_ENDPOINT" s3 rm "s3://$S3_BUCKET/$fileName"
            fi
        fi
    done

    echo "[Job Finished] Sleeping for 24 hours..."
    sleep 86400 # 24 Hours
done