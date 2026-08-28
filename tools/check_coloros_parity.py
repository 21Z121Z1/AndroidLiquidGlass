#!/usr/bin/env python3
"""Static guard for the ColorOS SystemUI <-> Kyant comparison architecture."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILES = {
    "inventory": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsSystemUiCompleteInventory.kt",
    "external_catalog": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsExternalLiquidGlassCatalog.kt",
    "coui_presets": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsCouiPresetInventory.kt",
    "systemui_recipes": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsSystemUiShippingRecipeInventory.kt",
    "interactive_bridge": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsSystemUiInteractiveEffectBridge.kt",
    "interactive_recipes": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsSystemUiInteractiveRecipeInventory.kt",
    "interactive_resolver": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsSystemUiInteractiveParityResolver.kt",
    "parameter_audit": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsSystemUiParameterAuditBridge.kt",
    "scope": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsSystemUiAuditScope.kt",
    "resolver": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsSystemUiParityResolver.kt",
    "registry": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsSystemUiExecutionRegistry.kt",
    "route_host": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsSystemUiRouteHostView.kt",
    "clock_surface": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsClockGlassSurfaceView.kt",
    "material_bridge": ROOT / "app/src/androidMain/kotlin/com/kyant/backdrop/catalog/coloros/ColorOsMaterialBridge.kt",
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

cross_package_visual_primitives = [
    "com.coui.appcompat.COUIMaterialBlurEffect",
    "com.coui.appcompat.COUIMaterialStrokeEffect",
    "com.coui.appcompat.spotlight.COUISpotLightEffect",
    "com.coui.appcompat.toolbar.ToolbarMaterialEffectDelegate",
    "com.coui.appcompat.toolbar.AppBarBlurHelper",
    "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.GlassEffectBuilder",
]
for primitive in cross_package_visual_primitives:
    require("inventory", primitive, "SystemUI-consumed cross-package visual primitive must be in strict inventory")
    require("scope", primitive, "cross-package visual primitive must participate in CORE_MATERIAL gate")
    require("resolver", primitive, "cross-package visual primitive must have a concrete Kyant contract")

for token in ["DexFile", "UX_PACKAGE", "CLOCK_PACKAGE", "external://", "discoverAssetShaders", "discoverRawShaders"]:
    require("external_catalog", token, "external uxdesign/personality-clocks DEX+shader discovery must remain enabled")
require("inventory", "ColorOsExternalLiquidGlassCatalog", "external runtime discoveries must merge into strict inventory")
require("scope", 'mapping.group.startsWith("自动发现 · 外部")', "external discoveries must participate in CORE_MATERIAL gate")
require("resolver", "externalDiscoveredContract", "external discoveries must receive conservative Kyant contracts")

for token in ["BLUR_PREFIX", "STROKE_PREFIX", "SPOTLIGHT_PREFIX", "TOOLBAR_PREFIX", "bridge.catalog()"]:
    require("coui_presets", token, "per-preset COUI inventory must remain runtime-driven")
require("inventory", "ColorOsCouiPresetInventory", "all COUI preset rows must merge into complete inventory")
require("registry", "ColorOsCouiPresetInventory.BLUR_PREFIX", "exact COUI blur preset URI must have a route")
require("registry", "ColorOsCouiPresetInventory.STROKE_PREFIX", "exact COUI stroke preset URI must have a route")
require("registry", "ColorOsCouiPresetInventory.SPOTLIGHT_PREFIX", "exact COUI spotlight preset URI must have a route")
require("registry", "ColorOsCouiPresetInventory.TOOLBAR_PREFIX", "exact COUI toolbar category URI must have a route")
require("route_host", "exactPreset(implementation", "unified host must execute exact COUI preset URI without family substitution")
require("resolver", "couiPresetContract", "every exact COUI preset row must get a family-accurate Kyant contract")

for token in ["MATERIAL_PREFIX", "BLUR_MIX_PREFIX", "presetBridge", "blurMixBridge", "DIRECT_SHADER", "SYSTEM_UI_HOST"]:
    require("systemui_recipes", token, "SystemUI shipping material/blur-mix inventory must remain runtime-driven")
require("inventory", "ColorOsSystemUiShippingRecipeInventory", "SystemUI shipping recipe rows must merge into complete inventory")
require("scope", "ColorOsSystemUiShippingRecipeInventory.MATERIAL_PREFIX", "shipping material recipe rows must be CORE_MATERIAL")
require("scope", "ColorOsSystemUiShippingRecipeInventory.BLUR_MIX_PREFIX", "shipping blur/mix recipe rows must be CORE_MATERIAL")
require("registry", "ColorOsSystemUiShippingRecipeInventory.MATERIAL_PREFIX", "exact SystemUI material recipe URI must resolve to shipping preset executor")
require("registry", "ColorOsSystemUiShippingRecipeInventory.BLUR_MIX_PREFIX", "exact SystemUI blur/mix URI must distinguish direct shader from host-only")
require("route_host", "it.id == exactId", "unified host must select the exact SystemUI adapter/getter preset id")
require("route_host", "it.id == exactId", "unified host must select the exact SystemUI blur/mix recipe id")
require("route_host", "must not execute through the direct shader bridge", "HOST_ONLY blur/mix recipes must be rejected by the direct executor")
require("resolver", "systemUiShippingContract", "every exact SystemUI recipe row must receive a precise Kyant contract")
require("registry", "Route.SYSTEMUI_HOST", "host-only shipping recipes must remain explicit SystemUI host boundaries")

# Interactive SystemUI material APIs must stay independently executable and fail closed on draw.
for token in [
    "NOTIFICATION_SPOTLIGHT_PREFIX",
    "QS_MEDIA_SPOTLIGHT_PREFIX",
    "VOLUME_SETTINGS_SPOTLIGHT",
    "SCENARIO_METABALL_LIGHT",
    "notificationSpotLightKinds",
    "qsMediaClipShapes",
]:
    require("interactive_recipes", token, "runtime interactive SystemUI states must expand into strict rows")
require("inventory", "ColorOsSystemUiInteractiveRecipeInventory", "interactive SystemUI rows must merge into complete inventory")
require("scope", "ColorOsSystemUiInteractiveRecipeInventory.PREFIX", "interactive rows must participate in CORE_MATERIAL gate")
require("scope", "ColorOsSystemUiInteractiveParityResolver.resolve", "interactive contracts must resolve before generic SystemUI rules")
for token in [
    "markLazyParamsKind",
    "drawSpotLightEffect",
    "handleMotionEvent",
    "ScenarioLightBackgroundDrawable",
    "restartLight",
    "dispose",
    "onRuntimeStatus",
    "real SystemUI interactive draw completed",
]:
    require("interactive_bridge", token, "interactive bridge must execute real shipping draw/touch/lifecycle and expose runtime failures")
for token in ["Recipe.SPOTLIGHT", "Recipe.METABALL_NEAREST", "Kind.NEAREST_ONLY"]:
    require("interactive_resolver", token, "interactive ColorOS effects require explicit non-equivalence Kyant contracts")
for token in [
    "ColorOsSystemUiInteractiveRecipeInventory.NOTIFICATION_SPOTLIGHT_PREFIX",
    "ColorOsSystemUiInteractiveRecipeInventory.QS_MEDIA_SPOTLIGHT_PREFIX",
    "ColorOsSystemUiInteractiveRecipeInventory.VOLUME_SETTINGS_SPOTLIGHT",
    "ColorOsSystemUiInteractiveRecipeInventory.SCENARIO_METABALL_LIGHT",
]:
    require("registry", token, "every interactive recipe family must resolve to a direct ColorOS route")
require("route_host", "waiting for first real SystemUI draw", "interactive route must not report PASS before shipping draw executes")
require("route_host", "EffectHostView", "unified host must receive first-frame runtime status from interactive effect host")

for token in ["SYSTEM_UI_PACKAGE", "UX_PACKAGE", "CLOCK_PACKAGE", 'implementation.startsWith("external://")']:
    require("parameter_audit", token, "parameter/resource audit must cover all strict-inventory package owners")
require("parameter_audit", "inspectExternalShader", "external AGSL/GLSL rows must inspect the real APK resource")

require("material_bridge", "fun applyBlur", "COUI blur route must call the installed vendor bridge")
require("material_bridge", "fun applyStroke", "COUI stroke route must call the installed vendor bridge")
require("material_bridge", "fun applySpotLight", "COUI spotlight route must call the installed vendor bridge")
require("material_bridge", "fun applyToolbarStack", "COUI toolbar stack route must call the installed vendor bridge")
require("material_bridge", "fun applyGradientBlur", "COUI progressive blur route must call the installed vendor bridge")
require("clock_surface", "bridge.locationColor()", "glass surface must use the vendor GlassRegion marker")
require("clock_surface", "bridge.apply(this, bg, glass, mix, mask, light)", "glass surface must attach the real vendor RenderEffect")

direct_routes = [
    "POST_EFFECT_COMPOSER",
    "POST_EFFECT_SHAPE",
    "POST_EFFECT_OPTICS",
    "POST_EFFECT_STROKE",
    "POST_EFFECT_INNER_SHADOW",
    "POST_EFFECT_METABALL",
    "COUI_MATERIAL_BLUR",
    "COUI_MATERIAL_STROKE",
    "COUI_SPOTLIGHT",
    "COUI_TOOLBAR_STACK",
    "COUI_PROGRESSIVE_BLUR",
    "KEYGUARD_GLASS_BUILDER",
    "SYSTEMUI_NOTIFICATION_SPOTLIGHT",
    "SYSTEMUI_QS_MEDIA_SPOTLIGHT",
    "SYSTEMUI_VOLUME_SETTINGS_SPOTLIGHT",
    "SYSTEMUI_SCENARIO_METABALL_LIGHT",
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
print(f" - cross-package visual primitives: {len(cross_package_visual_primitives)}")
print(" - external DEX/shader discovery: guarded")
print(" - runtime COUI preset expansion: guarded")
print(" - runtime SystemUI shipping recipe expansion: guarded")
print(" - direct SystemUI interactive optics/metaball: guarded")
print(f" - direct routes: {len(direct_routes)}")
print(f" - Kyant recipes: {len(recipe_names)}")
print(f" - SystemUI GL assets: {len(gl_assets)}")
