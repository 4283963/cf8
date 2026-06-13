import requests
import time
import json
import threading
from collections import deque
from datetime import datetime
from queue import Queue, Full
from config import Config
from circuit_breaker import CircuitBreaker


class AsyncSignalPusher:
    def __init__(self):
        self.endpoint = Config.AI_SIGNAL_ENDPOINT
        self.feeder_id = Config.FEEDER_ID
        self.circuit = CircuitBreaker()

        self._queue = Queue(maxsize=Config.PUSH_QUEUE_MAX_SIZE)
        self._pool_size = Config.PUSH_THREAD_POOL_SIZE
        self._workers = []
        self._shutdown = threading.Event()

        self._stats_lock = threading.Lock()
        self._stats = {
            "enqueued": 0, "dropped": 0, "sent": 0,
            "failed": 0, "short_circuited": 0
        }

        self._session = requests.Session()
        adapter = requests.adapters.HTTPAdapter(
            pool_connections=8,
            pool_maxsize=32,
            max_retries=1,
            pool_block=False
        )
        self._session.mount("http://", adapter)
        self._session.mount("https://", adapter)
        self._session.headers.update({"Content-Type": "application/json"})

        self._start_workers()

    def _start_workers(self):
        for i in range(self._pool_size):
            t = threading.Thread(target=self._worker_loop, name=f"push-worker-{i}", daemon=True)
            t.start()
            self._workers.append(t)

    def _worker_loop(self):
        while not self._shutdown.is_set():
            try:
                item = self._queue.get(timeout=0.5)
            except Exception:
                continue

            try:
                payload = item["payload"]
                detection = item["detection"]
                self._do_push(payload, detection)
            except Exception as e:
                self._incr_stat("failed")
                print(f"[PUSH-WORKER] unexpected error: {e}")
            finally:
                self._queue.task_done()

    def _do_push(self, payload, detection):
        if not self.circuit.can_execute():
            self._incr_stat("short_circuited")
            return

        try:
            response = self._session.post(
                self.endpoint,
                data=json.dumps(payload),
                timeout=(Config.PUSH_CONNECTION_TIMEOUT, Config.PUSH_READ_TIMEOUT)
            )

            if 200 <= response.status_code < 300:
                self.circuit.record_success()
                self._incr_stat("sent")
            else:
                self.circuit.record_failure()
                self._incr_stat("failed")

        except (requests.exceptions.ConnectionError,
                requests.exceptions.Timeout,
                requests.exceptions.HTTPError,
                Exception):
            self.circuit.record_failure()
            self._incr_stat("failed")

    def enqueue_signal(self, detection_result, frame_timestamp=None, cat_dedup_info=None):
        if frame_timestamp is None:
            frame_timestamp = datetime.now().isoformat()

        payload = {
            "feederId": self.feeder_id,
            "timestamp": frame_timestamp,
            "animalType": detection_result["class_name"],
            "confidence": round(detection_result["confidence"], 4),
            "aboveThreshold": detection_result["above_threshold"],
            "allProbabilities": detection_result["all_probabilities"]
        }

        if cat_dedup_info is not None:
            payload["duplicateCat"] = bool(cat_dedup_info.get("is_duplicate", False))
            payload["catFaceId"] = cat_dedup_info.get("duplicate_cat_id")
            payload["catFaceHash"] = (cat_dedup_info.get("cat_face") or {}).get("feature_hash")
            payload["catSnapshotB64"] = (cat_dedup_info.get("cat_face") or {}).get("snapshot_jpeg_b64")
            payload["catLastSeenAgoSec"] = cat_dedup_info.get("last_seen_seconds_ago")
            payload["catSimilarity"] = cat_dedup_info.get("similarity")
            payload["catDailyFeedCount"] = cat_dedup_info.get("daily_feed_count_for_cat", 0)

        item = {
            "payload": payload,
            "detection": detection_result,
            "enqueue_ts": time.time()
        }

        try:
            self._queue.put_nowait(item)
            self._incr_stat("enqueued")
            return {
                "success": True,
                "queued": True,
                "queue_size": self._queue.qsize(),
                "circuit_state": self.circuit.get_state()
            }
        except Full:
            self._incr_stat("dropped")
            return {
                "success": False,
                "queued": False,
                "reason": "push_queue_full",
                "dropped_total": self._get_stat("dropped")
            }

    def push_detection_signal_sync(self, detection_result, frame_timestamp=None, cat_dedup_info=None):
        if frame_timestamp is None:
            frame_timestamp = datetime.now().isoformat()

        payload = {
            "feederId": self.feeder_id,
            "timestamp": frame_timestamp,
            "animalType": detection_result["class_name"],
            "confidence": round(detection_result["confidence"], 4),
            "aboveThreshold": detection_result["above_threshold"],
            "allProbabilities": detection_result["all_probabilities"]
        }

        if cat_dedup_info is not None:
            payload["duplicateCat"] = bool(cat_dedup_info.get("is_duplicate", False))
            payload["catFaceId"] = cat_dedup_info.get("duplicate_cat_id")
            payload["catFaceHash"] = (cat_dedup_info.get("cat_face") or {}).get("feature_hash")
            payload["catSnapshotB64"] = (cat_dedup_info.get("cat_face") or {}).get("snapshot_jpeg_b64")
            payload["catLastSeenAgoSec"] = cat_dedup_info.get("last_seen_seconds_ago")
            payload["catSimilarity"] = cat_dedup_info.get("similarity")
            payload["catDailyFeedCount"] = cat_dedup_info.get("daily_feed_count_for_cat", 0)

        if not self.circuit.can_execute():
            return {
                "success": False,
                "error": "circuit_open",
                "message": "Circuit breaker is OPEN - skipping push to avoid cascade failure"
            }

        try:
            response = self._session.post(
                self.endpoint,
                data=json.dumps(payload),
                timeout=(Config.PUSH_CONNECTION_TIMEOUT, Config.PUSH_READ_TIMEOUT)
            )
            response.raise_for_status()
            result = response.json()
            self.circuit.record_success()

            return {
                "success": True,
                "status_code": response.status_code,
                "data": result
            }

        except requests.exceptions.ConnectionError:
            self.circuit.record_failure()
            return {
                "success": False,
                "error": "connection_error",
                "message": "Java backend unreachable"
            }
        except requests.exceptions.Timeout:
            self.circuit.record_failure()
            return {
                "success": False,
                "error": "timeout",
                "message": "Request timed out"
            }
        except requests.exceptions.HTTPError as e:
            self.circuit.record_failure()
            return {
                "success": False,
                "error": "http_error",
                "status_code": e.response.status_code if e.response else None,
                "message": str(e)
            }
        except Exception as e:
            self.circuit.record_failure()
            return {
                "success": False,
                "error": "unknown",
                "message": str(e)
            }

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
            **s,
            "queue_current": self._queue.qsize(),
            "queue_max": Config.PUSH_QUEUE_MAX_SIZE,
            "circuit": self.circuit.get_metrics()
        }

    def shutdown(self):
        self._shutdown.set()
        for t in self._workers:
            t.join(timeout=5)
        self._session.close()


SignalPusher = AsyncSignalPusher
