import cv2
import numpy as np
from config import Config


class LightweightFramePrefilter:
    def __init__(self):
        self._prev_gray = None
        self._min_area = Config.PREFILTER_MIN_CONTOUR_AREA
        self._max_area = Config.PREFILTER_MAX_CONTOUR_AREA
        self._diff_threshold = Config.PREFILTER_FRAME_DIFF_THRESHOLD
        self._motion_ratio = Config.PREFILTER_SIGNIFICANT_MOTION_RATIO

    def _frame_to_gray(self, frame):
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        gray = cv2.GaussianBlur(gray, (21, 21), 0)
        return gray

    def should_skip_inference(self, frame):
        """
        轻量级帧预过滤：
        1. 计算帧间差分
        2. 检测显著运动区域轮廓
        3. 根据目标大小（面积）快速判断是否为动物级目标
        返回 True=跳过推理（蚊虫/落叶等小干扰）, False=需要进入AI推理
        """
        if frame is None:
            return True, "empty_frame"

        current_gray = self._frame_to_gray(frame)
        frame_area = frame.shape[0] * frame.shape[1]

        if self._prev_gray is None or self._prev_gray.shape != current_gray.shape:
            self._prev_gray = current_gray
            return False, "initial_frame"

        frame_delta = cv2.absdiff(self._prev_gray, current_gray)
        self._prev_gray = current_gray.copy()

        thresh = cv2.threshold(frame_delta, self._diff_threshold, 255, cv2.THRESH_BINARY)[1]
        thresh = cv2.dilate(thresh, None, iterations=2)

        motion_pixels = cv2.countNonZero(thresh)
        motion_pixel_ratio = motion_pixels / frame_area if frame_area > 0 else 0

        if motion_pixel_ratio < self._motion_ratio:
            return True, f"no_significant_motion (ratio={motion_pixel_ratio:.4f})"

        contours, _ = cv2.findContours(thresh.copy(), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

        if not contours:
            return True, "no_contours"

        significant_contours = []
        for c in contours:
            area = cv2.contourArea(c)
            if self._min_area <= area <= self._max_area:
                significant_contours.append(area)

        if not significant_contours:
            max_area = max(cv2.contourArea(c) for c in contours) if contours else 0
            return True, f"no_animal_size_targets (max_area={max_area}, min_required={self._min_area})"

        return False, f"significant_targets count={len(significant_contours)}, areas={significant_contours[:3]}"

    def reset(self):
        self._prev_gray = None
