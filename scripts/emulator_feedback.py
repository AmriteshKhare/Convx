"""ADB UI helper for the feedback checks; saves screenshots and accessibility trees."""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = Path(r"C:\Users\cosmi\AppData\Local\Android\Sdk\platform-tools\adb.exe")
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "artifacts" / "emulator-feedback-2026-10-05"
SERIAL = "emulator-5554"
PACKAGE = "com.convx.music.debug"


def adb(*args, timeout=25, binary=False):
    result = subprocess.run([str(ADB), "-s", SERIAL, *map(str, args)],
                            capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors="replace").strip())
    return result.stdout if binary else result.stdout.decode(errors="replace").strip()


def tree():
    adb("shell", "rm", "-f", "/sdcard/convx-feedback.xml")
    status = adb("shell", "uiautomator", "dump", "--compressed", "/sdcard/convx-feedback.xml")
    if "ERROR" in status:
        raise RuntimeError(status)
    xml = adb("shell", "cat", "/sdcard/convx-feedback.xml")
    return xml, ET.fromstring(xml)


def nodes(root):
    return [{k: node.get(k, "") for k in
             ("text", "content-desc", "class", "bounds", "clickable", "checked", "enabled")}
            for node in root.iter("node")
            if node.get("text") or node.get("content-desc") or node.get("clickable") == "true"]


def snapshot(label):
    OUT.mkdir(parents=True, exist_ok=True)
    adb("shell", "screencap", "-p", "/sdcard/convx-feedback.png")
    adb("pull", "/sdcard/convx-feedback.png", OUT / f"{label}.png")
    xml, root = tree()
    (OUT / f"{label}.xml").write_text(xml, encoding="utf-8")
    visible = nodes(root)
    (OUT / f"{label}.json").write_text(json.dumps(visible, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(visible, ensure_ascii=True))


def tap_text(text, index=0, exact=True):
    _, root = tree()
    matches = [node for node in root.iter("node")
               if any(value == text if exact else text.lower() in value.lower()
                      for value in (node.get("text", ""), node.get("content-desc", "")))]
    if index >= len(matches):
        raise RuntimeError(f"Cannot find {text!r}; visible text: " +
                           json.dumps([n for n in nodes(root) if n["text"] or n["content-desc"]]))
    bounds = list(map(int, re.findall(r"\d+", matches[index].get("bounds", ""))))
    if len(bounds) != 4:
        raise RuntimeError("Element has no usable bounds")
    adb("shell", "input", "tap", (bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2)
    print(f"Tapped {text!r} at {bounds}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["snapshot", "tap", "tap-part", "point", "text", "back", "swipe", "launch", "state"])
    parser.add_argument("args", nargs="*")
    args = parser.parse_args()
    if args.action == "snapshot":
        snapshot(args.args[0])
    elif args.action in ("tap", "tap-part"):
        tap_text(args.args[0], int(args.args[1]) if len(args.args) > 1 else 0, exact=args.action == "tap")
    elif args.action == "point":
        adb("shell", "input", "tap", *args.args)
    elif args.action == "text":
        adb("shell", "input", "text", args.args[0].replace(" ", "%s"))
    elif args.action == "back":
        adb("shell", "input", "keyevent", "4")
    elif args.action == "swipe":
        print(adb("shell", "input", "swipe", *args.args))
    elif args.action == "launch":
        print(adb("shell", "am", "start", "-n", PACKAGE + "/com.convx.music.WelcomeActivity"))
    elif args.action == "state":
        state = adb("shell", "dumpsys", "media_session")
        (OUT / "media-session.txt").write_text(state, encoding="utf-8")
        print("\n".join(line for line in state.splitlines()
                        if any(k in line for k in (PACKAGE, "PlaybackState", "metadata:", "duration="))))
