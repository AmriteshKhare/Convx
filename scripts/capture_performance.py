"""Capture repeatable USB-device frame statistics and a matching Perfetto trace."""
import argparse
import csv
import io
import json
import math
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def percentiles(values):
    ordered = sorted(values)
    return {f"p{p}": ordered[min(len(ordered) - 1, math.ceil(len(ordered) * p / 100) - 1)]
            for p in (50, 90, 99)} if ordered else {}


def summarize(raw):
    rows = []
    for block in raw.split("---PROFILEDATA---")[1::2]:
        for row in csv.DictReader(io.StringIO(block.strip())):
            try:
                frame = {k: int(v) for k, v in row.items() if k and v}
                if frame["Flags"] == 0 and 0 < frame["IntendedVsync"] < frame["FrameCompleted"]:
                    rows.append(frame)
            except (ValueError, KeyError):
                continue
    def stage(end, start):
        return percentiles([(r[end] - r[start]) / 1e6 for r in rows
                            if end in r and start in r and r[end] >= r[start] > 0])
    deadlines = [r for r in rows if 0 < r.get("FrameDeadline", 0) < 2**63 - 1]
    return {
        "frames": len(rows),
        "total_ms": stage("FrameCompleted", "IntendedVsync"),
        "ui_ms": stage("SyncQueued", "HandleInputStart"),
        "render_thread_ms": stage("FrameCompleted", "SyncStart"),
        "gpu_ms": stage("GpuCompleted", "IssueDrawCommandsStart"),
        "frames_over_8_33ms": sum(r["FrameCompleted"] - r["IntendedVsync"] > 1e9 / 120 for r in rows),
        "deadline_frames": len(deadlines),
        "missed_deadlines": sum(r["FrameCompleted"] > r["FrameDeadline"] for r in deadlines),
        "limitation": "gfxinfo retains a limited window; total latency is not presented FPS. Use the matching FrameTimeline trace for sustained FPS and jank.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("label")
    parser.add_argument("--adb", default=r"C:\Users\cosmi\AppData\Local\Android\Sdk\platform-tools\adb.exe")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", default="com.convx.music")
    parser.add_argument("--expect", required=True, help="Visible title text identifying the screen (not a persistent navigation icon)")
    parser.add_argument("--gestures", type=Path, help="JSON list of input argument lists, e.g. [[\"swipe\",540,1700,540,900,250]]")
    parser.add_argument("--out", type=Path, default=Path("artifacts/performance-120fps"))
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", args.label + args.package):
        parser.error("Label and package must contain only letters, numbers, underscore, dot or hyphen")
    out = args.out / args.label
    out.mkdir(parents=True, exist_ok=False)

    def adb(*command, timeout=30):
        result = subprocess.run([args.adb, "-s", args.serial, *map(str, command)],
                                capture_output=True, timeout=timeout, check=True)
        return result.stdout.decode(errors="replace")

    def save_state(suffix):
        for service in ("display", "battery", "thermalservice", "activity", "media_session"):
            text = adb("shell", "dumpsys", service)
            (out / f"{service}-{suffix}.txt").write_text(text, encoding="utf-8")
            if service == "display" and ("studio.screen.sharing" in text or
                                           "Logical Displays: size=1" not in text):
                raise RuntimeError("Capture requires one display and no Android Studio mirroring")

    adb("shell", "echo", "convx-usb-alive")
    save_state("before")
    package = adb("shell", "dumpsys", "package", args.package)
    (out / "package.txt").write_text(package, encoding="utf-8")
    if re.search(r"\bDEBUGGABLE\b", package):
        raise RuntimeError("Performance captures require a non-debuggable release app")
    adb("shell", "uiautomator", "dump", "/sdcard/convx-performance.xml")
    xml = adb("shell", "cat", "/sdcard/convx-performance.xml")
    (out / "screen.xml").write_text(xml, encoding="utf-8")
    tree = ET.fromstring(xml)
    if not any(args.expect == n.get("text") and
               n.get("package") == args.package for n in tree.iter("node")):
        raise RuntimeError(f"Expected screen marker {args.expect!r} is absent")
    adb("shell", "screencap", "-p", "/sdcard/convx-performance.png")
    adb("pull", "/sdcard/convx-performance.png", out / "before.png")
    gestures = json.loads(args.gestures.read_text()) if args.gestures else [
        ["swipe", 540, 1700, 540, 900, 250]] * 3 + [["swipe", 540, 900, 540, 1700, 250]] * 3
    if not gestures or any(not isinstance(g, list) or not g or g[0] not in
                           ("swipe", "tap", "keyevent") for g in gestures):
        raise ValueError("Gestures must be a nonempty list of swipe/tap/keyevent input arguments")
    (out / "gestures.json").write_text(json.dumps(gestures, indent=2))
    duration = math.ceil(sum((int(g[-1]) / 1000 if g[0] == "swipe" and len(g) == 6 else 0.25)
                             + 1 for g in gestures) + 5)
    config = f'''buffers {{ size_kb: 65536 fill_policy: RING_BUFFER }}
data_sources {{ config {{ name: "linux.ftrace" ftrace_config {{
 ftrace_events: "sched/sched_switch" ftrace_events: "sched/sched_wakeup"
 ftrace_events: "power/cpu_frequency"
 atrace_categories: "gfx" atrace_categories: "view" atrace_categories: "am"
 atrace_categories: "wm" atrace_categories: "input" atrace_categories: "dalvik"
 atrace_apps: "{args.package}" }} }} }}
data_sources {{ config {{ name: "android.surfaceflinger.frametimeline" }} }}
data_sources {{ config {{ name: "linux.process_stats" process_stats_config {{ scan_all_processes_on_start: true }} }} }}
duration_ms: {duration * 1000}
'''
    (out / "trace.cfg").write_text(config)
    adb("push", out / "trace.cfg", "/data/local/tmp/convx-performance.cfg")
    adb("shell", "dumpsys", "gfxinfo", args.package, "reset")
    trace = subprocess.Popen([args.adb, "-s", args.serial, "shell",
                              "cat /data/local/tmp/convx-performance.cfg | perfetto --txt -c - "
                              "-o /data/misc/perfetto-traces/convx-performance.pftrace"],
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    try:
        time.sleep(1)
        for gesture in gestures:
            adb("shell", "input", *gesture)
            time.sleep(1)
        raw = adb("shell", "dumpsys", "gfxinfo", args.package, "framestats")
        (out / "framestats.txt").write_text(raw, encoding="utf-8")
        report = summarize(raw)
        stdout, stderr = trace.communicate(timeout=duration + 15)
        (out / "trace-log.txt").write_bytes(stdout + stderr)
        if trace.returncode:
            raise RuntimeError("Perfetto failed; inspect trace-log.txt")
        adb("pull", "/data/misc/perfetto-traces/convx-performance.pftrace", out / "trace.pftrace")
        save_state("after")
        report.update(label=args.label, package=args.package, serial=args.serial,
                      screen_marker=args.expect, trace=str(out / "trace.pftrace"))
        (out / "summary.json").write_text(json.dumps(report, indent=2))
        print(json.dumps(report, indent=2))
    finally:
        if trace.poll() is None:
            trace.terminate()
            trace.communicate(timeout=10)


if __name__ == "__main__":
    main()
