#!/bin/bash
# TARGET: Disaster Recovery Restore Script
# USAGE: ./restore.sh <filename>
#
# FIXED (2026-08-23, OCI Migration Phase 1): this script predates backup-side
# encryption — it used to pipe the downloaded .enc file straight into gunzip,
# which fails on every current backup. It now decrypts with openssl
# (aes-256-cbc + pbkdf2, matching backup.sh) between download and import.
# Requires BACKUP_ENCRYPTION_KEY in the environment (sourced from Infisical —
# never hardcoded). Legacy unencrypted .sql.gz backups still restore cleanly:
# the decrypt step is skipped when the key is absent AND the file is not .enc.

BACKUP_FILE=$1

if [ -z "$BACKUP_FILE" ]; then
    echo "Usage: ./restore.sh <backup_filename>"
    echo "Available backups:"
    aws --endpoint-url "$S3_ENDPOINT" s3 ls "s3://$S3_BUCKET/"
    exit 1
fi

echo "WARNING: This will OVERWRITE the current database '$DB_NAME'."
echo "Are you sure? (Type 'yes' to confirm)"
read confirmation

if [ "$confirmation" != "yes" ]; then
    echo "Restore cancelled."
    exit 0
fi

echo "Downloading $BACKUP_FILE from MinIO..."
aws --endpoint-url "$S3_ENDPOINT" s3 cp "s3://$S3_BUCKET/$BACKUP_FILE" "/tmp/restore.download"

if [ ! -f "/tmp/restore.download" ]; then
    echo "Download failed. File not found."
    exit 1
fi

# Decrypt if this is an encrypted backup (or a key was provided). The
# backup pipeline (backup.sh) produces *.sql.gz.enc via openssl
# aes-256-cbc -salt -pbkdf2; older backups are plain *.sql.gz.
if [[ "$BACKUP_FILE" == *.enc ]] || [ -n "$BACKUP_ENCRYPTION_KEY" ]; then
    if [ -z "$BACKUP_ENCRYPTION_KEY" ]; then
        echo "RESTORE ABORTED: '$BACKUP_FILE' is an encrypted backup but BACKUP_ENCRYPTION_KEY is not set."
        echo "Source it from Infisical (prod environment) and re-run."
        rm -f "/tmp/restore.download"
        exit 1
    fi
    echo "Decrypting (aes-256-cbc / pbkdf2)..."
    openssl enc -d -aes-256-cbc -salt -pbkdf2 \
        -pass env:BACKUP_ENCRYPTION_KEY \
        -in "/tmp/restore.download" -out "/tmp/restore.sql.gz"
    rm -f "/tmp/restore.download"
    if [ ! -s "/tmp/restore.sql.gz" ]; then
        echo "RESTORE ABORTED: decryption produced an empty file — wrong BACKUP_ENCRYPTION_KEY?"
        rm -f "/tmp/restore.sql.gz"
        exit 1
    fi
else
    mv "/tmp/restore.download" "/tmp/restore.sql.gz"
fi

echo "Restoring database..."
gunzip < "/tmp/restore.sql.gz" | mysql -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME"

if [ $? -eq 0 ]; then
    echo "RESTORE COMPLETE. System data recovered."
    rm -f "/tmp/restore.sql.gz"
else
    echo "Restore FAILED during MySQL import. Keeping /tmp/restore.sql.gz for inspection."
fi
