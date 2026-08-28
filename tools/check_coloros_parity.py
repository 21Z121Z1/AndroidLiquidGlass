#!/usr/bin/env python3
"""Static guard for the ColorOS SystemUI <-> Kyant comparison architecture."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILES = {
    "inventory": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsSystemUiCompleteInventory.kt",
    "scope": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsSystemUiAuditScope.kt",
    "resolver": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsSystemUiParityResolver.kt",
    "registry": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsSystemUiExecutionRegistry.kt",
    "route_host": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsSystemUiRouteHostView.kt",
    "contract": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsKyantParityContract.kt",
    "parity_ui": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/SystemUiKyantParityOverlay.android.kt",
    "gl": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsSystemUiGlBlurView.kt",
}
texts: dict[str, str] = {}
errors: list[str] = []
for name, path in FILES.items():
    if not path.is_file():
        errors.append(f"missing file: {path.relative_to(ROOT)}")
        continue
    texts[name] = path.read_text(encoding="utf-8")


def require(name: str, needle: str, reason: str) -> None:
    if needle not in texts.get(name, ""):
        errors.append(f"{name}: missing {needle!r} ({reason})")


proven_entries = [
    "com.oplus.systemui.qs.base.seek.OplusQsVerticalSeekBar",
    "com.oplus.systemui.volume.OplusVolumeSeekBar",
    "com.oplus.systemui.qs.media.ProgressiveBlurOverlay",
    "com.oplus.systemui.notification.blur.OplusNotificationTiltShiftBlurContainer",
    "com.oplus.systemui.keyguard.gradientmask.view.GradientBlurImageView",
    "com.oplus.systemui.qs.media.multilight.MultiLightShaderParams",
    "com.oplus.systemui.wallpaperblur.WallpaperBlurDrawable",
]
for entry in proven_entries:
    require("inventory", entry, "proven material entry must be forced into strict inventory")

framework_primitives = [
    "com.oplus.graphics.OplusRenderEffect",
    "com.oplus.view.OplusViewBackgroundRenderEffect",
    "com.oplus.view.material.OplusMaterialUtil",
]
for primitive in framework_primitives:
    require("inventory", primitive, "framework primitive must be forced into inventory")
    require("scope", primitive, "framework primitive must participate in CORE_MATERIAL gate")
    require("resolver", primitive, "framework primitive must have a concrete Kyant contract")

# Each visually executable route must have both a registry entry and an implementation in the
# unified ColorOS A/B host. PostEffect modules are deliberately isolated rather than sharing one
# all-effects sample.
direct_routes = [
    "POST_EFFECT_COMPOSER",
    "POST_EFFECT_SHAPE",
    "POST_EFFECT_OPTICS",
    "POST_EFFECT_STROKE",
    "POST_EFFECT_INNER_SHADOW",
    "POST_EFFECT_METABALL",
    "CHROMATIC_SHADER",
    "BAR_GLOW_SHADER",
    "RAW_METABALL_SHADER",
    "QS_STROKE_SHADER",
    "VOLUME_STROKE_SHADER",
    "NOTIFICATION_STROKE_SHADER",
    "WALLPAPER_BLUR_DRAWABLE",
    "QS_PROGRESSIVE_BLUR_VIEW",
    "NOTIFICATION_TILT_SHIFT_VIEW",
    "KEYGUARD_GRADIENT_BLUR_VIEW",
    "QS_MULTI_LIGHT_SHADER",
    "QS_BUSINESS_SEEKBAR",
    "VOLUME_BUSINESS_SEEKBAR",
]
for route in direct_routes:
    require("registry", route, "direct route must exist in execution registry")
    require("route_host", f"Route.{route}", "direct route must execute in unified route host")

for token in ["PARAMETER_EXECUTOR", "HOST_BOUND", "SURFACE_CONTROL_BOUND", "GL_PIPELINE"]:
    require("route_host", token, "all execution boundaries must be rendered explicitly")

require("scope", "isExecutableGlResource", "unknown GLSL downgrade guard must remain enabled")
require("scope", "ExecutionMode.CAPABILITY_ONLY", "unknown GL assets must be downgradable to audit-only")

contract = texts.get("contract", "")
match = re.search(r"enum class Recipe\s*\{(?P<body>.*?)\n\s*\}", contract, re.S)
recipe_names: list[str] = []
if not match:
    errors.append("contract: could not parse Recipe enum")
else:
    recipe_names = re.findall(r"\b([A-Z][A-Z0-9_]+)\s*,?", match.group("body"))
    recipe_names = [name for name in recipe_names if name != "RECIPE"]
    if not recipe_names:
        errors.append("contract: parsed zero Recipe entries")
    for recipe in recipe_names:
        require("parity_ui", f"Recipe.{recipe}", "every Kyant recipe needs an executable/reference UI branch")

gl_assets = [
    "blur_down_vertex_shader.glsl",
    "blur_down_fragment_shader.glsl",
    "gaussian_blur_vertex_shader.glsl",
    "gaussian_blur_fragment_shader.glsl",
    "blur_up_vertex_shader.glsl",
    "blur_up_fragment_shader.glsl",
    "display_vertex_shader.glsl",
    "display_fragment_shader.glsl",
]
for asset in gl_assets:
    require("gl", asset, "SystemUI GL pipeline must use shipping vertex/fragment asset pair")

for field in [
    "coreUnmapped",
    "coreContractMissing",
    "coreRouteMissing",
    "coreRouteIncompatible",
    "coreDeltaMissing",
]:
    require("scope", field, "strict parity gate dimension must remain enforced")

if errors:
    print("ColorOS parity guard: FAIL", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print("ColorOS parity guard: PASS")
print(f" - proven strict entries: {len(proven_entries)}")
print(f" - framework primitives: {len(framework_primitives)}")
print(f" - direct routes: {len(direct_routes)}")
print(f" - Kyant recipes: {len(recipe_names)}")
print(f" - SystemUI GL assets: {len(gl_assets)}")
