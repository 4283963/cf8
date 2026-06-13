import time
import threading
from collections import OrderedDict
from config import Config
from cat_face_extractor import LightweightCatFaceExtractor


class CatFaceDeduplicationTracker:
    """
    猫脸去重跟踪器：
      - 维护一个带时间窗口的猫脸特征队列（默认半小时）
      - 新猫脸进来时：先做特征哈希快速过滤，再做余弦相似度细算
      - 命中（相似度 >= 阈值 且 在窗口内）→ 判定为"重复投喂的胖猫"
    所有操作线程安全。
    """

    def __init__(self):
        self._lock = threading.RLock()
        self._window_seconds = Config.CAT_FACE_DEDUP_WINDOW_SECONDS
        self._similarity_threshold = Config.CAT_FACE_SIMILARITY_THRESHOLD
        self._max_entries = Config.CAT_FACE_TRACK_MAX_ENTRIES
        self._records = OrderedDict()

        self._stats_lock = threading.Lock()
        self._stats = {
            "extractions": 0,
            "extraction_failures": 0,
            "hash_hits": 0,
            "similarity_checks": 0,
            "duplicates_found": 0,
            "new_cats_registered": 0,
            "evicted_expired": 0
        }

    # ---------- 核心 API ----------

    def check_or_register(self, frame, bbox=None):
        """
        检查是否是重复出现的猫。
        :return: dict {
            "is_duplicate": bool,
            "duplicate_cat_id": str or None,
            "last_seen_seconds_ago": float or None,
            "similarity": float or None,
            "cat_face": { feature, feature_hash, snapshot_jpeg_b64, roi } or None,
            "daily_feed_count_for_cat": int
        }
        """
        extractor = LightweightCatFaceExtractor()
        cat_face = extractor.extract(frame, bbox)

        with self._stats_lock:
            self._stats["extractions"] += 1
            if cat_face is None:
                self._stats["extraction_failures"] += 1

        if cat_face is None:
            return {
                "is_duplicate": False,
                "duplicate_cat_id": None,
                "last_seen_seconds_ago": None,
                "similarity": None,
                "cat_face": None,
                "daily_feed_count_for_cat": 0
            }

        now = time.time()
        fhash = cat_face["feature_hash"]
        fvec = cat_face["feature"]

        with self._lock:
            self._evict_expired(now)

            best_id = None
            best_sim = 0.0
            best_ts = 0.0
            best_count = 0

            with self._stats_lock:
                self._stats["hash_hits"] += 1

            for cat_id, rec in self._records.items():
                rec_hash = rec.get("feature_hash", "")
                quick_pass = (rec_hash == fhash) or (
                    sum(1 for a, b in zip(rec_hash[:16], fhash[:16]) if a == b) >= 8
                )
                if not quick_pass:
                    continue
                with self._stats_lock:
                    self._stats["similarity_checks"] += 1
                sim = LightweightCatFaceExtractor.cosine_similarity(fvec, rec.get("feature"))
                if sim > best_sim:
                    best_sim = sim
                    best_id = cat_id
                    best_ts = rec["first_ts"]
                    best_count = rec["daily_count"]

            if best_id is not None and best_sim >= self._similarity_threshold:
                last_seen = now - self._records[best_id]["last_ts"]
                self._records[best_id]["last_ts"] = now
                self._records[best_id]["daily_count"] += 1
                self._records.move_to_end(best_id)
                with self._stats_lock:
                    self._stats["duplicates_found"] += 1
                return {
                    "is_duplicate": True,
                    "duplicate_cat_id": best_id,
                    "last_seen_seconds_ago": round(last_seen, 2),
                    "similarity": round(best_sim, 4),
                    "cat_face": self._strip_large_fields(cat_face),
                    "daily_feed_count_for_cat": self._records[best_id]["daily_count"]
                }

            new_id = f"cat-{fhash[:12]}-{int(now * 1000) % 100000:05d}"
            self._records[new_id] = {
                "feature": fvec,
                "feature_hash": fhash,
                "first_ts": now,
                "last_ts": now,
                "daily_count": 1,
                "snapshot": cat_face["snapshot_jpeg_b64"]
            }
            self._records.move_to_end(new_id)
            while len(self._records) > self._max_entries:
                self._records.popitem(last=False)

            with self._stats_lock:
                self._stats["new_cats_registered"] += 1

            return {
                "is_duplicate": False,
                "duplicate_cat_id": new_id,
                "last_seen_seconds_ago": None,
                "similarity": None,
                "cat_face": self._strip_large_fields(cat_face),
                "daily_feed_count_for_cat": 1
            }

    # ---------- 辅助 ----------

    def _evict_expired(self, now):
        cut = now - self._window_seconds
        evict_ids = []
        for cid, rec in self._records.items():
            if rec["last_ts"] < cut:
                evict_ids.append(cid)
            else:
                break
        for cid in evict_ids:
            del self._records[cid]
        with self._stats_lock:
            self._stats["evicted_expired"] += len(evict_ids)

    @staticmethod
    def _strip_large_fields(cat_face):
        return {
            "feature_hash": cat_face["feature_hash"],
            "snapshot_jpeg_b64": cat_face["snapshot_jpeg_b64"],
            "roi": cat_face["roi"]
        }

    # ---------- 统计 ----------

    def get_stats(self):
        with self._stats_lock:
            s = dict(self._stats)
        with self._lock:
            size = len(self._records)
        return {
            "tracker": s,
            "tracked_cats": size,
            "window_seconds": self._window_seconds,
            "similarity_threshold": self._similarity_threshold,
            "max_entries": self._max_entries
        }
