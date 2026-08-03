#!/usr/bin/env python3
"""Generate lean Android library module skeletons for Battery Forensics."""
from pathlib import Path

ROOT = Path("/Users/akshay/Developer/Battery-Forensics")

MODULES = {
    "core": ([], False, False, False, True),
    "battery": (["core"], False, False, False, False),
    "analytics": (["core", "database"], False, False, False, False),
    "diagnostics": (["core", "ruleengine"], False, False, False, False),
    "monitoring": (
        ["core", "database", "battery", "display", "thermal", "telephony", "wifi", "permissions"],
        False, False, True, False,
    ),
    "telephony": (["core"], False, False, False, False),
    "wifi": (["core"], False, False, False, False),
    "display": (["core"], False, False, False, False),
    "thermal": (["core"], False, False, False, False),
    "parser": (["core"], False, False, False, False),
    "reporting": (["core", "diagnostics"], False, False, False, True),
    "export": (["core", "reporting", "ai", "diagnostics"], False, False, False, True),
    "timeline": (["core"], False, False, False, False),
    "ruleengine": (["core"], False, False, False, False),
    "statistics": (["core"], False, False, False, False),
    "ai": (["core", "diagnostics", "reporting"], False, False, False, True),
    "permissions": (["core"], False, False, False, False),
    "settings": (["core"], False, False, True, False),
    "database": (["core"], False, True, True, False),
    "charts": (["core"], True, False, False, False),
    "shizuku": (["core"], False, False, False, False),
}

for name, (deps, compose, room, hilt, serialization) in MODULES.items():
    mod = ROOT / name
    java_pkg = mod / f"src/main/java/com/batteryforensics/{name}"
    test_pkg = mod / f"src/test/java/com/batteryforensics/{name}"
    java_pkg.mkdir(parents=True, exist_ok=True)
    test_pkg.mkdir(parents=True, exist_ok=True)

    (mod / "src/main/AndroidManifest.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<manifest />\n'
    )
    (mod / "consumer-rules.pro").write_text("")
    (mod / "proguard-rules.pro").write_text("")

    plugins = [
        "alias(libs.plugins.android.library)",
        "alias(libs.plugins.kotlin.android)",
    ]
    if compose:
        plugins.append("alias(libs.plugins.kotlin.compose)")
    if hilt:
        plugins.append("alias(libs.plugins.hilt)")
    if room or hilt:
        plugins.append("alias(libs.plugins.ksp)")
    if serialization:
        plugins.append("alias(libs.plugins.kotlin.serialization)")

    # dedupe while preserving order
    seen = set()
    unique_plugins = []
    for p in plugins:
        if p not in seen:
            seen.add(p)
            unique_plugins.append(p)

    build_features = ["        buildConfig = false"]
    if compose:
        build_features.append("        compose = true")

    project_deps = [f'implementation(project(":{d}"))' for d in deps]
    deps_block = []
    if project_deps:
        deps_block.extend(project_deps)
    deps_block.append("implementation(libs.androidx.core.ktx)")
    deps_block.append("implementation(libs.kotlinx.coroutines.android)")

    if compose:
        deps_block.extend([
            "implementation(platform(libs.compose.bom))",
            "implementation(libs.compose.ui)",
            "implementation(libs.compose.material3)",
            "implementation(libs.vico.compose)",
            "implementation(libs.vico.compose.m3)",
            "implementation(libs.vico.core)",
        ])
    if room:
        deps_block.extend([
            "api(libs.room.runtime)",
            "api(libs.room.ktx)",
            "ksp(libs.room.compiler)",
            "testImplementation(libs.room.testing)",
            "testImplementation(libs.robolectric)",
        ])
    if hilt:
        deps_block.extend([
            "implementation(libs.hilt.android)",
            "ksp(libs.hilt.compiler)",
        ])
    if serialization:
        deps_block.append("implementation(libs.kotlinx.serialization.json)")
    if name == "shizuku":
        deps_block.extend([
            "implementation(libs.shizuku.api)",
            "implementation(libs.shizuku.provider)",
        ])
    if name == "settings":
        deps_block.append("implementation(libs.androidx.datastore.preferences)")
    if name == "monitoring":
        deps_block.extend([
            "implementation(libs.androidx.work.runtime.ktx)",
            "implementation(libs.androidx.work.hilt)",
            "ksp(libs.androidx.hilt.compiler)",
        ])

    deps_block.extend([
        "testImplementation(libs.junit)",
        "testImplementation(libs.truth)",
        "testImplementation(libs.kotlinx.coroutines.test)",
        "testImplementation(libs.turbine)",
    ])

    content = f"""plugins {{
{chr(10).join('    ' + p for p in unique_plugins)}
}}

android {{
    namespace = "com.batteryforensics.{name}"
    compileSdk = 35

    defaultConfig {{
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }}

    compileOptions {{
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }}

    kotlinOptions {{
        jvmTarget = "17"
    }}

    buildFeatures {{
{chr(10).join(build_features)}
    }}
}}

dependencies {{
{chr(10).join('    ' + d for d in deps_block)}
}}
"""
    (mod / "build.gradle.kts").write_text(content)

    class_name = "".join(part.capitalize() for part in name.replace("-", "_").split("_"))
    (java_pkg / f"{class_name}Module.kt").write_text(
        f"""package com.batteryforensics.{name}

/** Module marker for :{name}. */
object {class_name}Module
"""
    )

print(f"Generated {len(MODULES)} library modules")
