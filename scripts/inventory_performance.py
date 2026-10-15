"""Inventory registered routes and screen functions; verification starts unproven."""
import json
import re
from pathlib import Path


root = Path(__file__).resolve().parents[1]
screen_root = root / "app/src/main/kotlin/com/convx/music/ui/screens"
screens = (screen_root / "Screens.kt").read_text(encoding="utf-8")
aliases = dict(re.findall(r'object\s+(\w+)\s*:\s*Screens\([\s\S]*?route\s*=\s*"([^"]+)"', screens))
builder = screen_root / "NavigationBuilder.kt"
text = builder.read_text(encoding="utf-8")
routes = []
pattern = r'\b(sharedComposable|composable|dialog)\(\s*(?:route\s*=\s*)?("[^"\n]+"|Screens\.\w+\.route)'
for match in re.finditer(pattern, text):
    expression = match.group(2)
    route = expression[1:-1] if expression.startswith('"') else aliases[expression.split(".")[1]]
    routes.append({"route": route, "registration": match.group(1),
                   "file": str(builder.relative_to(root)).replace("\\", "/"),
                   "line": text.count("\n", 0, match.start()) + 1,
                   "release_visual_verified": False, "release_120fps_verified": False})
functions = []
for source in screen_root.rglob("*.kt"):
    contents = source.read_text(encoding="utf-8")
    for match in re.finditer(r'\bfun\s+(\w*Screen)\s*\(', contents):
        functions.append({"function": match.group(1),
                          "file": str(source.relative_to(root)).replace("\\", "/"),
                          "line": contents.count("\n", 0, match.start()) + 1})
out = root / "artifacts/performance-120fps/coverage-inventory.json"
out.write_text(json.dumps({
    "note": "Static candidates only. Dynamic registrations, player overlays, menu/dialog variants and state/theme branches require manual inventory and matching release/device evidence. Older APK captures do not verify current source.",
    "routes": routes, "screen_functions": functions,
    "required_interactions": ["scroll", "push/pop", "tab switch", "player open/close/drag",
                              "nav collapse/expand", "search open/results", "menu/sheet open/close/drag",
                              "paused/playing scroll", "crossfade", "glass settings scroll and switch press/drag",
                              "dark/light iOS visual check"],
}, indent=2), encoding="utf-8")
print(f"Inventoried {len(routes)} route registrations and {len(functions)} screen functions; all verification remains unproven.")
