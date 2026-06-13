import cv2
import numpy as np


class LightweightCatFaceExtractor:
    """
    轻量级猫脸特征提取器。
    为了避免阻塞主线程，不使用深度学习模型，采用传统CV组合特征：
      1) YCrCb颜色空间颜色直方图（捕捉毛色分布）
      2) 灰度HOG梯度直方图（捕捉脸型轮廓纹理）
    两个向量做L2归一化后拼接，得到一个低维特征向量用于相似度比对。
    """

    HOG_WIN_SIZE = (64, 64)
    HOG_CELL_SIZE = (8, 8)
    HOG_BLOCK_SIZE = (16, 16)
    HOG_BLOCK_STRIDE = (8, 8)
    HOG_NBINS = 9

    HIST_BINS = 16

    def __init__(self):
        self._hog = cv2.HOGDescriptor(
            self.HOG_WIN_SIZE,
            self.HOG_BLOCK_SIZE,
            self.HOG_BLOCK_STRIDE,
            self.HOG_CELL_SIZE,
            self.HOG_NBINS
        )

    def extract(self, frame, bbox=None):
        """
        提取猫脸特征向量。
        :param frame: 原始BGR帧
        :param bbox: 可选 (x, y, w, h) 猫脸候选框。如果为None，则取帧中心区域作为ROI
        :return: dict {
            "feature": np.ndarray 一维归一化特征向量,
            "feature_hash": str 32位十六进制快速比对哈希,
            "snapshot_jpeg_b64": str 抓拍快照（JPEG base64，供前端展示）,
            "roi": (x, y, w, h) 实际使用的ROI
        } 或 None（提取失败）
        """
        try:
            h, w = frame.shape[:2]
            if bbox is None:
                bx, by, bw, bh = w // 4, h // 4, w // 2, h // 2
            else:
                bx, by, bw, bh = [int(v) for v in bbox]
                bx = max(0, min(bx, w - 2))
                by = max(0, min(by, h - 2))
                bw = max(2, min(bw, w - bx))
                bh = max(2, min(bh, h - by))

            roi = frame[by:by + bh, bx:bx + bw]
            if roi.size == 0:
                return None

            hist_feat = self._color_histogram(roi)
            hog_feat = self._hog_descriptor(roi)
            if hist_feat is None or hog_feat is None:
                return None

            feature = np.concatenate([hist_feat, hog_feat]).astype(np.float32)
            norm = np.linalg.norm(feature)
            if norm > 1e-9:
                feature = feature / norm

            feature_hash = self._quick_hash(feature)
            snapshot = self._encode_snapshot(roi)

            return {
                "feature": feature,
                "feature_hash": feature_hash,
                "snapshot_jpeg_b64": snapshot,
                "roi": [int(bx), int(by), int(bw), int(bh)]
            }
        except Exception as e:
            print(f"[CatFaceExtractor] extract failed: {e}")
            return None

    def _color_histogram(self, roi):
        try:
            ycrcb = cv2.cvtColor(roi, cv2.COLOR_BGR2YCrCb)
            chans = cv2.split(ycrcb)
            feats = []
            for c in chans:
                h = cv2.calcHist([c], [0], None, [self.HIST_BINS], [0, 256])
                h = cv2.normalize(h, h).flatten()
                feats.append(h)
            return np.concatenate(feats).astype(np.float32)
        except Exception:
            return None

    def _hog_descriptor(self, roi):
        try:
            gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
            resized = cv2.resize(gray, self.HOG_WIN_SIZE, interpolation=cv2.INTER_AREA)
            feat = self._hog.compute(resized).flatten()
            n = np.linalg.norm(feat)
            if n > 1e-9:
                feat = feat / n
            return feat.astype(np.float32)
        except Exception:
            return None

    def _quick_hash(self, feature):
        try:
            arr = (feature * 1000).astype(np.int64) & 0xFFFFFFFF
            h = 2166136261
            for v in arr[::max(1, len(arr) // 32)]:
                h ^= int(v)
                h = (h * 16777619) & 0xFFFFFFFF
            buf = h.to_bytes(4, 'big')
            rest = 28
            acc = h
            for i, v in enumerate(arr):
                acc = ((acc * 31) + int(v)) & 0xFFFFFFFF
                if i % 17 == 0:
                    buf += (acc & 0xFF).to_bytes(1, 'big')
                    rest -= 1
                    if rest <= 0:
                        break
            while len(buf) < 32:
                acc = (acc * 131 + len(buf)) & 0xFFFFFFFF
                buf += (acc & 0xFF).to_bytes(1, 'big')
            return buf[:32].hex()
        except Exception:
            return "0" * 32

    def _encode_snapshot(self, roi):
        try:
            small = cv2.resize(roi, (240, 240), interpolation=cv2.INTER_AREA)
            ok, enc = cv2.imencode('.jpg', small, [int(cv2.IMWRITE_JPEG_QUALITY), 70])
            if not ok:
                return ""
            import base64
            return base64.b64encode(enc.tobytes()).decode('ascii')
        except Exception:
            return ""

    @staticmethod
    def cosine_similarity(a, b):
        if a is None or b is None:
            return 0.0
        if len(a) != len(b):
            return 0.0
        dot = float(np.dot(a, b))
        na = float(np.linalg.norm(a))
        nb = float(np.linalg.norm(b))
        if na < 1e-9 or nb < 1e-9:
            return 0.0
        return max(0.0, min(1.0, dot / (na * nb)))
