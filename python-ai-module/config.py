import os
from dotenv import load_dotenv

load_dotenv()


class Config:
    JAVA_BACKEND_URL = os.getenv("JAVA_BACKEND_URL", "http://localhost:8080")
    AI_SIGNAL_ENDPOINT = f"{JAVA_BACKEND_URL}/api/feeding/ai-signal"
    FEEDER_DEVICE_ENDPOINT = f"{JAVA_BACKEND_URL}/api/feeder"

    MODEL_WEIGHTS_PATH = os.getenv("MODEL_WEIGHTS_PATH", "./models/animal_classifier.pth")
    CONFIDENCE_THRESHOLD = float(os.getenv("CONFIDENCE_THRESHOLD", "0.7"))

    CAMERA_SOURCE = os.getenv("CAMERA_SOURCE", "0")
    FRAME_INTERVAL = float(os.getenv("FRAME_INTERVAL", "0.2"))

    CLASS_LABELS = {
        0: "stray_cat",
        1: "stray_dog",
        2: "pest_animal"
    }

    FEEDER_ID = os.getenv("FEEDER_ID", "FEEDER-001")

    FLASK_HOST = os.getenv("FLASK_HOST", "0.0.0.0")
    FLASK_PORT = int(os.getenv("FLASK_PORT", "5000"))

    INFERENCE_THREAD_POOL_SIZE = int(os.getenv("INFERENCE_THREAD_POOL_SIZE", "2"))
    INFERENCE_QUEUE_MAX_SIZE = int(os.getenv("INFERENCE_QUEUE_MAX_SIZE", "32"))

    PUSH_THREAD_POOL_SIZE = int(os.getenv("PUSH_THREAD_POOL_SIZE", "4"))
    PUSH_QUEUE_MAX_SIZE = int(os.getenv("PUSH_QUEUE_MAX_SIZE", "256"))
    PUSH_CONNECTION_TIMEOUT = float(os.getenv("PUSH_CONNECTION_TIMEOUT", "2.0"))
    PUSH_READ_TIMEOUT = float(os.getenv("PUSH_READ_TIMEOUT", "3.0"))

    CIRCUIT_FAILURE_THRESHOLD = int(os.getenv("CIRCUIT_FAILURE_THRESHOLD", "10"))
    CIRCUIT_SUCCESS_THRESHOLD = int(os.getenv("CIRCUIT_SUCCESS_THRESHOLD", "3"))
    CIRCUIT_HALF_OPEN_DELAY = float(os.getenv("CIRCUIT_HALF_OPEN_DELAY", "15.0"))
    CIRCUIT_TIME_WINDOW = float(os.getenv("CIRCUIT_TIME_WINDOW", "30.0"))

    PREFILTER_MIN_CONTOUR_AREA = int(os.getenv("PREFILTER_MIN_CONTOUR_AREA", "3000"))
    PREFILTER_MAX_CONTOUR_AREA = int(os.getenv("PREFILTER_MAX_CONTOUR_AREA", "500000"))
    PREFILTER_FRAME_DIFF_THRESHOLD = int(os.getenv("PREFILTER_FRAME_DIFF_THRESHOLD", "25"))
    PREFILTER_SIGNIFICANT_MOTION_RATIO = float(os.getenv("PREFILTER_SIGNIFICANT_MOTION_RATIO", "0.01"))

    DEDUPLICATION_WINDOW = float(os.getenv("DEDUPLICATION_WINDOW", "8.0"))

    CAT_FACE_DEDUP_ENABLED = os.getenv("CAT_FACE_DEDUP_ENABLED", "true").lower() == "true"
    CAT_FACE_DEDUP_WINDOW_SECONDS = int(os.getenv("CAT_FACE_DEDUP_WINDOW_SECONDS", "1800"))
    CAT_FACE_SIMILARITY_THRESHOLD = float(os.getenv("CAT_FACE_SIMILARITY_THRESHOLD", "0.82"))
    CAT_FACE_TRACK_MAX_ENTRIES = int(os.getenv("CAT_FACE_TRACK_MAX_ENTRIES", "200"))
