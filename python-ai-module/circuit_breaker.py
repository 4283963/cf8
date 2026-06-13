import time
import threading
from collections import deque
from enum import Enum
from config import Config


class CircuitState(Enum):
    CLOSED = "CLOSED"
    OPEN = "OPEN"
    HALF_OPEN = "HALF_OPEN"


class CircuitBreaker:
    def __init__(self):
        self.state = CircuitState.CLOSED
        self._lock = threading.Lock()
        self._failure_times = deque()
        self._success_count = 0
        self._last_open_time = 0
        self._failure_threshold = Config.CIRCUIT_FAILURE_THRESHOLD
        self._success_threshold = Config.CIRCUIT_SUCCESS_THRESHOLD
        self._half_open_delay = Config.CIRCUIT_HALF_OPEN_DELAY
        self._time_window = Config.CIRCUIT_TIME_WINDOW

    def _clean_old_failures(self, now):
        cutoff = now - self._time_window
        while self._failure_times and self._failure_times[0] < cutoff:
            self._failure_times.popleft()

    def can_execute(self):
        with self._lock:
            now = time.time()
            if self.state == CircuitState.CLOSED:
                self._clean_old_failures(now)
                return True
            elif self.state == CircuitState.OPEN:
                if (now - self._last_open_time) >= self._half_open_delay:
                    self.state = CircuitState.HALF_OPEN
                    self._success_count = 0
                    return True
                return False
            elif self.state == CircuitState.HALF_OPEN:
                return True
            return False

    def record_success(self):
        with self._lock:
            now = time.time()
            if self.state == CircuitState.HALF_OPEN:
                self._success_count += 1
                if self._success_count >= self._success_threshold:
                    self.state = CircuitState.CLOSED
                    self._failure_times.clear()
                    print(f"[CircuitBreaker] -> CLOSED after {self._success_count} successes")
            elif self.state == CircuitState.CLOSED:
                pass

    def record_failure(self):
        with self._lock:
            now = time.time()
            if self.state == CircuitState.HALF_OPEN:
                self.state = CircuitState.OPEN
                self._last_open_time = now
                print(f"[CircuitBreaker] -> OPEN (failure in HALF_OPEN)")
                return

            self._clean_old_failures(now)
            self._failure_times.append(now)

            if len(self._failure_times) >= self._failure_threshold:
                self.state = CircuitState.OPEN
                self._last_open_time = now
                print(f"[CircuitBreaker] -> OPEN ({len(self._failure_times)} failures in window)")

    def get_state(self):
        with self._lock:
            return self.state.value

    def get_metrics(self):
        with self._lock:
            now = time.time()
            self._clean_old_failures(now)
            return {
                "state": self.state.value,
                "recent_failures": len(self._failure_times),
                "success_count_in_half_open": self._success_count,
                "seconds_until_half_open": max(0, int(self._half_open_delay - (now - self._last_open_time)))
                    if self.state == CircuitState.OPEN else 0
            }
