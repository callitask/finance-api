FROM alpine:3.19

# Install dependencies for MySQL backup and S3 upload
RUN apk add --no-cache mysql-client aws-cli bash gzip

WORKDIR /scripts

# Copy backup scripts
COPY backup.sh .
COPY restore.sh .

RUN chmod +x backup.sh restore.sh

# Default command: Run the backup loop
CMD ["./backup.sh"]