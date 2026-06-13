import os
import threading
import time
from flask import Flask, request, jsonify
from flask_cors import CORS
from werkzeug.utils import secure_filename
from config import Config
from animal_classifier import LightweightAnimalClassifier
from camera_processor import AsyncCameraFrameProcessor
from signal_pusher import AsyncSignalPusher

app = Flask(__name__)
CORS(app)

app.config["MAX_CONTENT_LENGTH"] = 16 * 1024 * 1024
ALLOWED_EXTENSIONS = {"png", "jpg", "jpeg", "bmp"}

classifier = LightweightAnimalClassifier()
pusher = AsyncSignalPusher()
processor = AsyncCameraFrameProcessor()


def allowed_file(filename):
    return "." in filename and filename.rsplit(".", 1)[1].lower() in ALLOWED_EXTENSIONS


@app.route("/api/health", methods=["GET"])
def health_check():
    return jsonify({
        "status": "healthy",
        "service": "python-ai-module",
        "feederId": Config.FEEDER_ID,
        "timestamp": int(time.time())
    })


@app.route("/api/stats", methods=["GET"])
def get_stats():
    return jsonify(processor.get_stats())


@app.route("/api/classify", methods=["POST"])
def classify_image():
    if "image" not in request.files:
        return jsonify({"error": "No image file provided"}), 400

    file = request.files["image"]
    if file.filename == "":
        return jsonify({"error": "No file selected"}), 400

    if not allowed_file(file.filename):
        return jsonify({"error": "Invalid file type. Allowed: png, jpg, jpeg, bmp"}), 400

    filename = secure_filename(file.filename)
    upload_dir = "./uploads"
    os.makedirs(upload_dir, exist_ok=True)
    filepath = os.path.join(upload_dir, filename)
    file.save(filepath)

    try:
        result = classifier.predict(filepath)
        return jsonify(result)
    except Exception as e:
        return jsonify({"error": f"Classification failed: {str(e)}"}), 500
    finally:
        if os.path.exists(filepath):
            os.remove(filepath)


@app.route("/api/push-signal", methods=["POST"])
def push_signal():
    if "image" not in request.files:
        return jsonify({"error": "No image file provided"}), 400

    file = request.files["image"]
    if file.filename == "":
        return jsonify({"error": "No file selected"}), 400

    if not allowed_file(file.filename):
        return jsonify({"error": "Invalid file type. Allowed: png, jpg, jpeg, bmp"}), 400

    filename = secure_filename(file.filename)
    upload_dir = "./uploads"
    os.makedirs(upload_dir, exist_ok=True)
    filepath = os.path.join(upload_dir, filename)
    file.save(filepath)

    try:
        result = processor.process_image_file(filepath)
        return jsonify(result)
    except Exception as e:
        return jsonify({"error": f"Processing failed: {str(e)}"}), 500
    finally:
        if os.path.exists(filepath):
            os.remove(filepath)


@app.route("/api/push-signal/async", methods=["POST"])
def push_signal_async():
    if "image" not in request.files:
        return jsonify({"error": "No image file provided"}), 400

    file = request.files["image"]
    if file.filename == "":
        return jsonify({"error": "No file selected"}), 400

    if not allowed_file(file.filename):
        return jsonify({"error": "Invalid file type. Allowed: png, jpg, jpeg, bmp"}), 400

    import numpy as np
    import cv2
    try:
        file_bytes = np.frombuffer(file.read(), np.uint8)
        frame = cv2.imdecode(file_bytes, cv2.IMREAD_COLOR)
        if frame is None:
            return jsonify({"error": "Could not decode image"}), 400
    except Exception as e:
        return jsonify({"error": f"Image decode failed: {str(e)}"}), 400

    result = processor.process_frame_async(frame)
    return jsonify({
        "accepted": True,
        "async_result": result,
        "message": "Frame queued for async processing. Check /api/stats for progress."
    }), 202


@app.route("/api/simulate", methods=["POST"])
def simulate_detection():
    data = request.get_json()
    if not data:
        return jsonify({"error": "No JSON payload provided"}), 400

    animal_type = data.get("animalType")
    confidence = data.get("confidence", 0.95)

    if animal_type not in Config.CLASS_LABELS.values():
        return jsonify({
            "error": f"Invalid animalType. Must be one of: {list(Config.CLASS_LABELS.values())}"
        }), 400

    class_id = {v: k for k, v in Config.CLASS_LABELS.items()}[animal_type]

    detection_result = {
        "class_id": class_id,
        "class_name": animal_type,
        "confidence": confidence,
        "above_threshold": confidence >= Config.CONFIDENCE_THRESHOLD,
        "all_probabilities": {
            label: (confidence if label == animal_type else round((1 - confidence) / 2, 4))
            for label in Config.CLASS_LABELS.values()
        }
    }

    mode = data.get("mode", "async")
    if mode == "async":
        result = pusher.enqueue_signal(detection_result)
        return jsonify({
            "accepted": True,
            "async_result": result,
            "message": "Signal queued for async push"
        }), 202
    else:
        result = pusher.push_detection_signal_sync(detection_result)
        return jsonify(result)


if __name__ == "__main__":
    print("=" * 60)
    print(f"  Python AI Module (with Async + Circuit Breaker + Prefilter)")
    print("=" * 60)
    print(f"  Host:            {Config.FLASK_HOST}:{Config.FLASK_PORT}")
    print(f"  Feeder ID:       {Config.FEEDER_ID}")
    print(f"  Java Backend:    {Config.JAVA_BACKEND_URL}")
    print(f"  Confidence:      {Config.CONFIDENCE_THRESHOLD}")
    print(f"  Inf Workers:     {Config.INFERENCE_THREAD_POOL_SIZE} (qsize={Config.INFERENCE_QUEUE_MAX_SIZE})")
    print(f"  Push Workers:    {Config.PUSH_THREAD_POOL_SIZE} (qsize={Config.PUSH_QUEUE_MAX_SIZE})")
    print(f"  Circuit Fail:    {Config.CIRCUIT_FAILURE_THRESHOLD}/{Config.CIRCUIT_TIME_WINDOW}s")
    print(f"  Prefilter Area:  {Config.PREFILTER_MIN_CONTOUR_AREA}-{Config.PREFILTER_MAX_CONTOUR_AREA}")
    print("=" * 60)
    app.run(host=Config.FLASK_HOST, port=Config.FLASK_PORT, debug=False, threaded=True)
