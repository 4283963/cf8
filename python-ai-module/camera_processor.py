import cv2
import time
import threading
from datetime import datetime
from queue import Queue, Full
from config import Config
from animal_classifier import LightweightAnimalClassifier
from signal_pusher import AsyncSignalPusher
from frame_prefilter import LightweightFramePrefilter


class AsyncCameraFrameProcessor:
    def __init__(self):
        self.classifier = LightweightAnimalClassifier()
        self.pusher = AsyncSignalPusher()
        self.prefilter = LightweightFramePrefilter()
        self.camera_source = Config.CAMERA_SOURCE
        self.frame_interval = Config.FRAME_INTERVAL
        self.cap = None

        self._inference_queue = Queue(maxsize=Config.INFERENCE_QUEUE_MAX_SIZE)
        self._inf_pool_size = Config.INFERENCE_THREAD_POOL_SIZE
        self._inf_workers = []
        self._shutdown = threading.Event()

        self._last_pushed_per_class = {}
        self._dedup_window = Config.DEDUPLICATION_WINDOW
        self._last_detection_lock = threading.Lock()

        self._stats_lock = threading.Lock()
        self._stats = {
            "frames_read": 0,
            "frames_filtered": 0,
            "frames_enqueued_inf": 0,
            "frames_dropped_inf": 0,
            "inferences_done": 0,
            "low_confidence_skipped": 0,
            "dedup_skipped": 0,
            "pushes_enqueued": 0
        }

        self._start_inference_workers()

    def _start_inference_workers(self):
        for i in range(self._inf_pool_size):
            t = threading.Thread(
                target=self._inference_worker_loop,
                name=f"inference-worker-{i}",
                daemon=True
            )
            t.start()
            self._inf_workers.append(t)

    def _inference_worker_loop(self):
        while not self._shutdown.is_set():
            try:
                item = self._inference_queue.get(timeout=0.5)
            except Exception:
                continue

            try:
                frame = item["frame"]
                timestamp = item["timestamp"]
                self._run_inference_and_push(frame, timestamp)
            except Exception as e:
                print(f"[INF-WORKER] error: {e}")
            finally:
                self._inference_queue.task_done()

    def _run_inference_and_push(self, frame, timestamp):
        try:
            detection = self.classifier.predict(frame)
            self._incr_stat("inferences_done")
        except Exception as e:
            print(f"[INF] inference failed: {e}")
            return

        if not detection["above_threshold"]:
            self._incr_stat("low_confidence_skipped")
            return

        class_name = detection["class_name"]
        now = time.time()

        with self._last_detection_lock:
            last_ts = self._last_pushed_per_class.get(class_name, 0)
            if (now - last_ts) < self._dedup_window:
                self._incr_stat("dedup_skipped")
                return
            self._last_pushed_per_class[class_name] = now

        push_result = self.pusher.enqueue_signal(detection, timestamp)
        if push_result.get("queued"):
            self._incr_stat("pushes_enqueued")

    def process_frame_async(self, frame):
        self._incr_stat("frames_read")

        skip, reason = self.prefilter.should_skip_inference(frame)
        if skip:
            self._incr_stat("frames_filtered")
            return {
                "skipped": True,
                "reason": reason,
                "stage": "prefilter"
            }

        item = {
            "frame": frame.copy(),
            "timestamp": datetime.now().isoformat(),
            "enqueue_ts": time.time()
        }

        try:
            self._inference_queue.put_nowait(item)
            self._incr_stat("frames_enqueued_inf")
            return {
                "skipped": False,
                "queued": True,
                "queue_size": self._inference_queue.qsize()
            }
        except Full:
            self._incr_stat("frames_dropped_inf")
            return {
                "skipped": True,
                "reason": "inference_queue_full",
                "dropped_total": self._get_stat("frames_dropped_inf")
            }

    def process_image_file(self, image_path):
        detection = self.classifier.predict(image_path)
        timestamp = datetime.now().isoformat()
        push_result = self.pusher.push_detection_signal_sync(detection, timestamp)
        self._incr_stat("inferences_done")
        if push_result.get("success"):
            self._incr_stat("pushes_enqueued")
        return {
            "detection": detection,
            "push_result": push_result,
            "timestamp": timestamp
        }

    def _open_camera(self):
        try:
            source = int(self.camera_source) if self.camera_source.isdigit() else self.camera_source
            self.cap = cv2.VideoCapture(source)
            if not self.cap.isOpened():
                print(f"Error: Could not open camera source: {self.camera_source}")
                return False
            print(f"Camera opened successfully: {self.camera_source}")
            self.prefilter.reset()
            return True
        except Exception as e:
            print(f"Error opening camera: {e}")
            return False

    def _release_camera(self):
        if self.cap and self.cap.isOpened():
            self.cap.release()
            print("Camera released")

    def run_live(self):
        if not self._open_camera():
            return

        print("Starting async live camera processing... Press Ctrl+C to stop")
        last_frame_time = 0

        try:
            while True:
                current_time = time.time()
                if (current_time - last_frame_time) < self.frame_interval:
                    time.sleep(max(0, self.frame_interval - (current_time - last_frame_time)) / 2)
                    continue

                ret, frame = self.cap.read()
                if not ret:
                    print("Warning: Could not read frame from camera")
                    time.sleep(1)
                    continue

                last_frame_time = current_time
                self.process_frame_async(frame)

        except KeyboardInterrupt:
            print("\nStopping live processing...")
        finally:
            self._release_camera()
            self.shutdown()

    def _incr_stat(self, key):
        with self._stats_lock:
            self._stats[key] = self._stats.get(key, 0) + 1

    def _get_stat(self, key):
        with self._stats_lock:
            return self._stats.get(key, 0)

    def get_stats(self):
        with self._stats_lock:
            s = dict(self._stats)
        return {
            "processor": s,
            "inference_queue": {
                "current": self._inference_queue.qsize(),
                "max": Config.INFERENCE_QUEUE_MAX_SIZE
            },
            "pusher": self.pusher.get_stats()
        }

    def shutdown(self):
        self._shutdown.set()
        for t in self._inf_workers:
            t.join(timeout=5)
        self.pusher.shutdown()


CameraFrameProcessor = AsyncCameraFrameProcessor
