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
    FRAME_INTERVAL = float(os.getenv("FRAME_INTERVAL", "2.0"))

    CLASS_LABELS = {
        0: "stray_cat",
        1: "stray_dog",
        2: "pest_animal"
    }

    FEEDER_ID = os.getenv("FEEDER_ID", "FEEDER-001")

    FLASK_HOST = os.getenv("FLASK_HOST", "0.0.0.0")
    FLASK_PORT = int(os.getenv("FLASK_PORT", "5000"))
