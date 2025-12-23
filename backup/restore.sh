#!/bin/bash
# TARGET: Disaster Recovery Restore Script
# USAGE: ./restore.sh <filename>

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
aws --endpoint-url "$S3_ENDPOINT" s3 cp "s3://$S3_BUCKET/$BACKUP_FILE" "/tmp/restore.sql.gz"

if [ -f "/tmp/restore.sql.gz" ]; then
    echo "Restoring database..."
    gunzip < "/tmp/restore.sql.gz" | mysql -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASS" "$DB_NAME"

    if [ $? -eq 0 ]; then
        echo "RESTORE COMPLETE. System data recovered."
        rm "/tmp/restore.sql.gz"
    else
        echo "Restore FAILED during MySQL import."
    fi
else
    echo "Download failed. File not found."
fi