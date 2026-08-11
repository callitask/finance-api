# /**
#  * AI-CONTEXT:
#  * Purpose: Asynchronous RabbitMQ listener for HLS Video Transcoding.
#  * Scope: Idles at <15MB RAM. Wakes on message, consumes up to 384MB limit executing FFmpeg, and returns to sleep.
#  * Security Constraints: Strictly parses JSON. Does not execute unsanitized shell commands.
#  * IMMUTABLE CHANGE HISTORY:
#  * - ADDED: Phase 2 Smart Transcoder Architecture. 
#  *   Why: Offloads heavy CPU work from Tomcat into a contained, elastic process loop.
#  *
#  * - EDITED (Phase 3 - Video Transcoder Optimization):
#  *   Uncommented FFmpeg subprocess execution and directed stdout/stderr to DEVNULL.
#  *   Reason: Prevents Python memory bloat and protects the 384MB Docker RAM limit while processing video.
#  *
#  * - DO-NOT-DELETE RULE:
#  * This IMMUTABLE CHANGE HISTORY section acts as the institutional memory for future AI sessions.
#  * It must never be deleted, truncated, rewritten, or regenerated. Future AI must append only.
#  */
import os
import time
import json
import subprocess
import pika
import logging

# Configure minimal logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - [Transcoder] - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

RABBITMQ_HOST = os.getenv('RABBITMQ_HOST', 'rabbitmq')
RABBITMQ_USER = os.getenv('RABBITMQ_USER', 'guest')
RABBITMQ_PASS = os.getenv('RABBITMQ_PASS', 'guest')
ENABLE_4K = os.getenv('ENABLE_4K_TRANSCODING', 'false').lower() == 'true'

def process_video(ch, method, properties, body):
    try:
        data = json.loads(body.decode('utf-8'))
        video_id = data.get('videoId')
        input_file = f"/app/uploads/raw/{video_id}.mp4"
        output_dir = f"/app/uploads/hls/{video_id}"
        
        if not video_id:
            raise ValueError("Payload missing videoId")

        logger.info(f"Waking up from idle. Processing job for videoId: {video_id}")
        
        os.makedirs(output_dir, exist_ok=True)

        # Build secure, array-based FFmpeg command (prevents shell injection)
        # using 'nice -n 19' to throttle CPU priority and protect the VirtualBox host
        ffmpeg_cmd = [
            "nice", "-n", "19", 
            "ffmpeg", "-y", 
            "-i", input_file,
            "-profile:v", "main",
            "-crf", "20",
            "-sc_threshold", "0",
            "-g", "48",
            "-keyint_min", "48",
            "-hls_time", "6",
            "-hls_playlist_type", "vod",
            "-b:v", "5000k",
            "-maxrate", "5350k",
            "-bufsize", "7500k",
            "-b:a", "192k",
            "-vf", "scale=1920:1080", # Capped at 1080p to prevent OOM
            f"{output_dir}/1080p.m3u8"
        ]

        # Log 4K toggle status without breaking existing flow
        if ENABLE_4K:
            logger.info("ENABLE_4K_TRANSCODING is TRUE. 4K variant generation logic would execute here.")
        else:
            logger.info("ENABLE_4K_TRANSCODING is FALSE. Hard-capping resolution at 1080p.")

        logger.info("Executing FFmpeg...")
        result = subprocess.run(ffmpeg_cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if result.returncode != 0:
            raise RuntimeError(f"FFmpeg failed with exit code: {result.returncode}")
        
        logger.info(f"Processing complete for {video_id}. Dropping RAM and returning to idle sleep.")
        ch.basic_ack(delivery_tag=method.delivery_tag)

    except Exception as e:
        logger.error(f"Transcoding job failed: {str(e)}")
        # Nack without requeueing to prevent poison-pill crash loops
        ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

def connect_to_rabbitmq():
    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASS)
    parameters = pika.ConnectionParameters(host=RABBITMQ_HOST, credentials=credentials, heartbeat=60)
    
    while True:
        try:
            logger.info(f"Attempting connection to RabbitMQ at {RABBITMQ_HOST}...")
            connection = pika.BlockingConnection(parameters)
            channel = connection.channel()
            
            # Ensure the queue exists before consuming
            channel.queue_declare(queue='video.transcode.queue', durable=True)
            
            # Fetch 1 message at a time to prevent memory bloat
            channel.basic_qos(prefetch_count=1)
            channel.basic_consume(queue='video.transcode.queue', on_message_callback=process_video)
            
            logger.info("Connection successful. Listener is active and idling (<15MB RAM).")
            channel.start_consuming()
            
        except pika.exceptions.AMQPConnectionError:
            logger.warning("RabbitMQ not ready. Retrying in 5 seconds...")
            time.sleep(5)
        except Exception as e:
            logger.error(f"Unexpected connection error: {str(e)}. Reconnecting...")
            time.sleep(5)

if __name__ == '__main__':
    connect_to_rabbitmq()