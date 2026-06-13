#!/usr/bin/env python3
"""
高频抓拍场景压力测试脚本
模拟夜间蚊虫/落叶频繁晃动导致的AI信号推送洪峰场景
用法: python3 stress_test.py
"""
import requests
import time
import threading
import random
import sys
import json
from datetime import datetime
from collections import Counter, deque

JAVA_URL = "http://localhost:8080"
PYTHON_URL = "http://localhost:5000"

ANIMAL_TYPES = ["stray_cat", "stray_dog", "pest_animal"]
NOISE_COUNT = 0

stats_lock = threading.Lock()
stats = Counter()
latencies = deque(maxlen=10000)
error_samples = deque(maxlen=20)


def send_java_sync(feeder_id, animal_type, confidence):
    global NOISE_COUNT
    payload = {
        "feederId": feeder_id,
        "timestamp": datetime.now().isoformat(),
        "animalType": animal_type,
        "confidence": confidence,
        "aboveThreshold": confidence >= 0.7,
        "allProbabilities": {
            animal_type: confidence,
            "stray_cat": 0.1 if animal_type != "stray_cat" else confidence,
            "pest_animal": 0.1 if animal_type != "pest_animal" else confidence,
        }
    }
    start = time.time()
    try:
        r = requests.post(f"{JAVA_URL}/api/feeding/ai-signal", json=payload, timeout=5)
        lat = (time.time() - start) * 1000
        with stats_lock:
            stats[f"java_{r.status_code}"] += 1
            latencies.append(lat)
        if r.status_code == 429:
            with stats_lock:
                stats["java_rate_limited"] += 1
        elif r.status_code == 503:
            with stats_lock:
                stats["java_circuit_open"] += 1
        if r.status_code >= 400 and len(error_samples) < 20:
            error_samples.append((r.status_code, r.text[:200]))
    except requests.exceptions.Timeout:
        with stats_lock:
            stats["java_timeout"] += 1
            NOISE_COUNT += 1
    except Exception as e:
        with stats_lock:
            stats[f"java_error_{type(e).__name__}"] += 1
            if len(error_samples) < 20:
                error_samples.append(("EXC", str(e)[:200]))


def send_java_async(feeder_id, animal_type, confidence):
    payload = {
        "feederId": feeder_id,
        "timestamp": datetime.now().isoformat(),
        "animalType": animal_type,
        "confidence": confidence,
        "aboveThreshold": confidence >= 0.7,
        "allProbabilities": {}
    }
    start = time.time()
    try:
        r = requests.post(f"{JAVA_URL}/api/feeding/ai-signal/async", json=payload, timeout=3)
        lat = (time.time() - start) * 1000
        with stats_lock:
            stats[f"async_{r.status_code}"] += 1
            latencies.append(lat)
    except Exception as e:
        with stats_lock:
            stats[f"async_error"] += 1


def worker(worker_id, qps, duration, mode):
    end_time = time.time() + duration
    interval = 1.0 / qps
    feeder_ids = [f"FEEDER-{i:03d}" for i in range(1, 6)]

    while time.time() < end_time:
        fid = random.choice(feeder_ids)
        r = random.random()
        if r < 0.75:
            atype = random.choice(["pest_animal"] * 3 + ["stray_cat", "stray_dog"])
            conf = round(random.uniform(0.80, 0.99), 4)
        elif r < 0.95:
            atype = random.choice(ANIMAL_TYPES)
            conf = round(random.uniform(0.50, 0.80), 4)
        else:
            atype = random.choice(ANIMAL_TYPES)
            conf = round(random.uniform(0.20, 0.50), 4)

        if mode == "sync":
            send_java_sync(fid, atype, conf)
        else:
            send_java_async(fid, atype, conf)

        time.sleep(interval * random.uniform(0.5, 1.5))


def stats_printer(total_duration):
    start = time.time()
    while time.time() - start < total_duration + 2:
        time.sleep(3)
        elapsed = time.time() - start
        with stats_lock:
            s = dict(stats)
            lats = list(latencies)

        total_ok = s.get("java_200", 0) + s.get("async_200", 0) + s.get("async_202", 0)
        total_req = sum(v for k, v in s.items() if k.startswith("java_") or k.startswith("async_"))
        avg_lat = sum(lats) / len(lats) if lats else 0
        p95 = sorted(lats)[int(len(lats) * 0.95)] if len(lats) >= 20 else 0
        qps_actual = total_req / elapsed if elapsed > 0 else 0

        print(f"\n[{elapsed:5.1f}s] QPS~{qps_actual:6.1f} | 200/202={total_ok} "
              f"| 429(限流)={s.get('java_rate_limited', 0)} "
              f"| 503(熔断)={s.get('java_circuit_open', 0)} "
              f"| 超时={s.get('java_timeout', 0)}")
        print(f"         延迟: avg={avg_lat:6.1f}ms  p95={p95:6.1f}ms  样本={len(lats)}")
        print(f"         统计: { {k: v for k, v in sorted(s.items()) if v > 0} }")

        if error_samples:
            print(f"         最近错误样例:")
            for code, msg in list(error_samples)[-3:]:
                print(f"           [{code}] {msg}")


def check_services():
    try:
        r = requests.get(f"{JAVA_URL}/api/feeder/health", timeout=3)
        print(f"[OK] Java backend: HTTP {r.status_code}")
        j = r.json()
        if j.get("data"):
            print(f"     线程池状态: {json.dumps(j['data'], indent=6, ensure_ascii=False)}")
    except Exception as e:
        print(f"[FAIL] Java backend: {e}")
        return False

    try:
        r = requests.get(f"{JAVA_URL}/api/feeding/resilience/status?feederId=FEEDER-001", timeout=3)
        print(f"[OK] Java resilience: HTTP {r.status_code}")
        print(f"     {json.dumps(r.json().get('data', {}), indent=6, ensure_ascii=False)}")
    except Exception as e:
        print(f"[WARN] Could not get resilience status: {e}")

    try:
        r = requests.get(f"{PYTHON_URL}/api/health", timeout=3)
        print(f"[OK] Python AI: HTTP {r.status_code}")
    except Exception as e:
        print(f"[WARN] Python AI: {e} (optional for Java pressure test)")
    return True


def main():
    if len(sys.argv) < 2:
        mode = "async"
        duration = 60
        threads = 20
        qps_per_thread = 10
    else:
        mode = sys.argv[1] if len(sys.argv) > 1 else "async"
        duration = int(sys.argv[2]) if len(sys.argv) > 2 else 60
        threads = int(sys.argv[3]) if len(sys.argv) > 3 else 20
        qps_per_thread = int(sys.argv[4]) if len(sys.argv) > 4 else 10

    total_qps = threads * qps_per_thread
    print("=" * 70)
    print("  高频抓拍场景压力测试 (模拟蚊虫/落叶洪峰)")
    print("=" * 70)
    print(f"  模式:       {mode} (sync / async)")
    print(f"  时长:       {duration}s")
    print(f"  线程数:     {threads}")
    print(f"  单线程QPS:  {qps_per_thread}")
    print(f"  预期总QPS:  ~{total_qps}")
    print("=" * 70)

    if not check_services():
        print("\nJava backend unavailable. Start it first.")
        return

    input("\nPress ENTER to start pressure test...")

    t_printer = threading.Thread(target=stats_printer, args=(duration,), daemon=True)
    t_printer.start()

    workers = []
    for i in range(threads):
        t = threading.Thread(
            target=worker, args=(i, qps_per_thread, duration, mode), daemon=True)
        workers.append(t)
        t.start()

    for t in workers:
        t.join()

    print("\n" + "=" * 70)
    print("  压力测试完成，最终状态查询")
    print("=" * 70)
    check_services()

    with stats_lock:
        print("\n最终统计:", json.dumps(dict(stats), indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
