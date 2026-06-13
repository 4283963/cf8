import requests
import time
import json
from datetime import datetime
from config import Config


class SignalPusher:
    def __init__(self):
        self.endpoint = Config.AI_SIGNAL_ENDPOINT
        self.feeder_id = Config.FEEDER_ID
        self.session = requests.Session()
        self.session.headers.update({
            "Content-Type": "application/json"
        })

    def push_detection_signal(self, detection_result, frame_timestamp=None):
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

        try:
            response = self.session.post(
                self.endpoint,
                data=json.dumps(payload),
                timeout=10
            )
            response.raise_for_status()
            result = response.json()

            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] "
                  f"Signal pushed successfully: {detection_result['class_name']} "
                  f"(confidence: {detection_result['confidence']:.4f})")
            print(f"  Backend response: {result}")

            return {
                "success": True,
                "status_code": response.status_code,
                "data": result
            }

        except requests.exceptions.ConnectionError:
            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] "
                  f"Connection failed: Java backend unreachable at {self.endpoint}")
            return {
                "success": False,
                "error": "connection_error",
                "message": "Java backend unreachable"
            }
        except requests.exceptions.Timeout:
            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] "
                  f"Request timed out when pushing signal")
            return {
                "success": False,
                "error": "timeout",
                "message": "Request timed out"
            }
        except requests.exceptions.HTTPError as e:
            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] "
                  f"HTTP error: {e}")
            return {
                "success": False,
                "error": "http_error",
                "status_code": e.response.status_code if e.response else None,
                "message": str(e)
            }
        except Exception as e:
            print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] "
                  f"Unexpected error pushing signal: {e}")
            return {
                "success": False,
                "error": "unknown",
                "message": str(e)
            }
