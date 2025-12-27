#!/bin/bash
# TARGET: Automated Backup Script (PITR Enabled)
# PURPOSE: Dumps DB, compresses, uploads to MinIO, and rotates old backups.

DB_HOST="${DB_HOST:-treishvaam-db}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS}"
DB_NAME="${DB_NAME:-finance_db}"
S3_ENDPOINT="${S3_ENDPOINT:-http://minio:9000}"
S3_BUCKET="${S3_BUCKET:-treishvaam-backups}"
RETENTION_DAYS=7

aws configure set aws_access_key_id "${MINIO_ACCESS_KEY}"
aws configure set aws_secret_access_key "${MINIO_SECRET_KEY}"
aws configure set default.region us-east-1

echo "Starting Backup Service... (Schedule: Every 24h)"

# Create bucket if not exists
aws --endpoint-url "$S3_ENDPOINT" s3 mb "s3://$S3_BUCKET" 2>/dev/null || true

while true; do
    DATE=$(date +%Y-%m-%d_%H-%M-%S)
    FILE_NAME="backup_${DATE}.sql.gz"

    echo "[Job Started] Creating backup: $FILE_NAME"

    mysqldump -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASS" --single-transaction --quick --master-data=2 "$DB_NAME" | gzip > "/tmp/$FILE_NAME"

    if [ -f "/tmp/$FILE_NAME" ]; then
        FILE_SIZE=$(du -h "/tmp/$FILE_NAME" | cut -f1)
        echo "Database dumped successfully. Size: $FILE_SIZE"

        echo "Uploading to MinIO..."
        aws --endpoint-url "$S3_ENDPOINT" s3 cp "/tmp/$FILE_NAME" "s3://$S3_BUCKET/$FILE_NAME"

        if [ $? -eq 0 ]; then
            echo "Upload SUCCESS."
            rm "/tmp/$FILE_NAME"
        else
            echo "Upload FAILED."
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