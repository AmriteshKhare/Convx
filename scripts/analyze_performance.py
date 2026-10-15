"""Summarize app FrameTimeline presentation and RenderThread slices from a capture."""
import argparse
import json
from pathlib import Path

from perfetto.trace_processor import TraceProcessor
from capture_performance import percentiles


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("trace", type=Path)
    parser.add_argument("--package", default="com.convx.music")
    args = parser.parse_args()
    with TraceProcessor(trace=str(args.trace)) as tp:
        processes = [r.upid for r in tp.query("SELECT upid, name FROM process") if r.name == args.package]
        if not processes:
            raise RuntimeError("App process absent from trace")
        upids = ",".join(map(str, processes))
        frames = list(tp.query(f"""SELECT ts, dur, jank_type, present_type, layer_name
            FROM actual_frame_timeline_slice WHERE upid IN ({upids}) AND dur > 0
            ORDER BY ts"""))
        layers = {}
        for f in frames:
            layers.setdefault(f.layer_name, []).append(f)
        report = {"trace": str(args.trace), "layers": {}, "render_thread": {}}
        for name, group in layers.items():
            intervals = [(b.ts + b.dur - a.ts - a.dur) / 1e6
                         for a, b in zip(group, group[1:]) if b.ts + b.dur > a.ts + a.dur]
            report["layers"][name] = {
                "frames": len(group), "janky_frames": sum(f.jank_type != "None" for f in group),
                "latency_ms": percentiles([f.dur / 1e6 for f in group]),
                "presentation_interval_ms": percentiles(intervals),
                "jank_types": {kind: sum(f.jank_type == kind for f in group)
                               for kind in sorted(set(f.jank_type for f in group))},
                "present_types": {kind: sum(f.present_type == kind for f in group)
                                  for kind in sorted(set(f.present_type for f in group))},
            }
        slices = list(tp.query(f"""SELECT s.name, s.dur / 1e6 AS ms
            FROM slice s JOIN thread_track tt ON s.track_id = tt.id
            JOIN thread t ON tt.utid = t.utid
            WHERE t.upid IN ({upids}) AND t.name = 'RenderThread' AND s.dur > 0"""))
        for name in ("DrawFrame", "flush layers", "flush commands", "eglSwapBuffersWithDamageKHR", "dequeueBuffer"):
            values = [s.ms for s in slices if s.name.startswith(name)]
            if values:
                report["render_thread"][name] = {"count": len(values), "ms": percentiles(values)}
        report["note"] = "Includes settling and idle frames; intervals across idle gaps do not measure active-scroll FPS. Inspect gesture windows in the trace."
        (args.trace.parent / "trace-summary.json").write_text(json.dumps(report, indent=2))
        print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
