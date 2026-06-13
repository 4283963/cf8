import cv2
import time
from datetime import datetime
from config import Config
from animal_classifier import LightweightAnimalClassifier
from signal_pusher import SignalPusher


class CameraFrameProcessor:
    def __init__(self):
        self.classifier = LightweightAnimalClassifier()
        self.pusher = SignalPusher()
        self.camera_source = Config.CAMERA_SOURCE
        self.frame_interval = Config.FRAME_INTERVAL
        self.cap = None
        self._last_detection = None
        self._detection_cooldown = 5.0

    def _open_camera(self):
        try:
            source = int(self.camera_source) if self.camera_source.isdigit() else self.camera_source
            self.cap = cv2.VideoCapture(source)
            if not self.cap.isOpened():
                print(f"Error: Could not open camera source: {self.camera_source}")
                return False
            print(f"Camera opened successfully: {self.camera_source}")
            return True
        except Exception as e:
            print(f"Error opening camera: {e}")
            return False

    def _release_camera(self):
        if self.cap and self.cap.isOpened():
            self.cap.release()
            print("Camera released")

    def _should_detect(self, current_class_name):
        now = time.time()
        if self._last_detection is None:
            return True
        last_time, last_class = self._last_detection
        if (now - last_time) >= self._detection_cooldown:
            return True
        if last_class != current_class_name:
            return True
        return False

    def process_frame(self, frame):
        detection = self.classifier.predict(frame)

        if not detection["above_threshold"]:
            return None

        class_name = detection["class_name"]

        if not self._should_detect(class_name):
            return None

        self._last_detection = (time.time(), class_name)

        timestamp = datetime.now().isoformat()
        push_result = self.pusher.push_detection_signal(detection, timestamp)

        return {
            "detection": detection,
            "push_result": push_result,
            "timestamp": timestamp
        }

    def process_image_file(self, image_path):
        detection = self.classifier.predict(image_path)
        timestamp = datetime.now().isoformat()
        push_result = self.pusher.push_detection_signal(detection, timestamp)
        return {
            "detection": detection,
            "push_result": push_result,
            "timestamp": timestamp
        }

    def run_live(self):
        if not self._open_camera():
            return

        print("Starting live camera processing... Press Ctrl+C to stop")
        last_frame_time = 0

        try:
            while True:
                current_time = time.time()
                if (current_time - last_frame_time) < self.frame_interval:
                    time.sleep(0.1)
                    continue

                ret, frame = self.cap.read()
                if not ret:
                    print("Warning: Could not read frame from camera")
                    time.sleep(1)
                    continue

                last_frame_time = current_time
                result = self.process_frame(frame)

                if result:
                    det = result["detection"]
                    print(f"  -> Detected: {det['class_name']} ({det['confidence']:.4f})")

        except KeyboardInterrupt:
            print("\nStopping live processing...")
        finally:
            self._release_camera()
