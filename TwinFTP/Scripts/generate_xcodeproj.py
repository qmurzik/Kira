#!/usr/bin/env python3
"""Генератор TwinFTP.xcodeproj (project.pbxproj + общая схема).

Проект собирается из каталога App/TwinFTP: все *.swift попадают в Sources,
Assets.xcassets — в Resources, Info.plist используется как INFOPLIST_FILE.
Локальный Swift-пакет Packages/TwinCore подключается как XCLocalSwiftPackageReference.

Запуск (из каталога TwinFTP):  python3 Scripts/generate_xcodeproj.py
Генератор детерминирован: одинаковое дерево файлов даёт одинаковый проект.
"""
import hashlib
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP_DIR = os.path.join(ROOT, "App", "TwinFTP")
UITEST_DIR = os.path.join(ROOT, "App", "TwinFTPUITests")
PROJECT_DIR = os.path.join(ROOT, "TwinFTP.xcodeproj")

BUNDLE_ID = "com.twinftp.app"
DEPLOYMENT_TARGET = "17.0"
MARKETING_VERSION = "1.0.0"
BUILD_NUMBER = "1"


def oid(key):
    return hashlib.md5(key.encode("utf-8")).hexdigest()[:24].upper()


def q(s):
    """Кавычки для значений pbxproj, если нужны."""
    import re
    if s and re.fullmatch(r"[A-Za-z0-9_./]+", s):
        return s
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


objects = {}  # id -> (isa, dict)


def add(obj_id, isa, fields):
    objects[obj_id] = (isa, fields)
    return obj_id


def file_type(name):
    ext = os.path.splitext(name)[1].lower()
    return {
        ".swift": "sourcecode.swift",
        ".plist": "text.plist.xml",
        ".xcassets": "folder.assetcatalog",
        ".strings": "text.plist.strings",
        ".json": "text.json",
        ".entitlements": "text.plist.entitlements",
    }.get(ext, "text")


def build_group(path, rel, target_key, sources, resources):
    """Рекурсивно строит PBXGroup для каталога. Возвращает id группы."""
    children = []
    entries = sorted(os.listdir(path), key=lambda n: (not os.path.isdir(os.path.join(path, n)) or n.endswith(".xcassets"), n.lower()))
    for name in entries:
        if name.startswith("."):
            continue
        full = os.path.join(path, name)
        child_rel = os.path.join(rel, name)
        if os.path.isdir(full) and not name.endswith(".xcassets"):
            gid = build_group(full, child_rel, target_key, sources, resources)
            children.append(gid)
            continue
        fid = add(oid("file:" + child_rel), "PBXFileReference", {
            "lastKnownFileType": file_type(name),
            "path": name,
            "sourceTree": "<group>",
        })
        children.append(fid)
        if name.endswith(".swift"):
            bid = add(oid("build:" + child_rel), "PBXBuildFile", {"fileRef": fid})
            sources.append(bid)
        elif name.endswith(".xcassets") or (name.endswith(".json") and target_key == "app"):
            bid = add(oid("build:" + child_rel), "PBXBuildFile", {"fileRef": fid})
            resources.append(bid)
    gid = add(oid("group:" + rel), "PBXGroup", {
        "children": children,
        "path": os.path.basename(path),
        "sourceTree": "<group>",
    })
    return gid


def main():
    app_sources, app_resources = [], []
    app_group = build_group(APP_DIR, "App/TwinFTP", "app", app_sources, app_resources)
    # Группа App/TwinFTP должна ссылаться на путь App/TwinFTP относительно проекта
    objects[app_group][1]["path"] = "App/TwinFTP"
    objects[app_group][1]["name"] = "TwinFTP"

    has_uitests = os.path.isdir(UITEST_DIR) and any(f.endswith(".swift") for f in os.listdir(UITEST_DIR))
    ui_sources, ui_resources = [], []
    ui_group = None
    if has_uitests:
        ui_group = build_group(UITEST_DIR, "App/TwinFTPUITests", "uitests", ui_sources, ui_resources)
        objects[ui_group][1]["path"] = "App/TwinFTPUITests"
        objects[ui_group][1]["name"] = "TwinFTPUITests"

    app_product = add(oid("product:app"), "PBXFileReference", {
        "explicitFileType": "wrapper.application",
        "includeInIndex": "0",
        "path": "TwinFTP.app",
        "sourceTree": "BUILT_PRODUCTS_DIR",
    })
    products_children = [app_product]
    ui_product = None
    if has_uitests:
        ui_product = add(oid("product:uitests"), "PBXFileReference", {
            "explicitFileType": "wrapper.cfbundle",
            "includeInIndex": "0",
            "path": "TwinFTPUITests.xctest",
            "sourceTree": "BUILT_PRODUCTS_DIR",
        })
        products_children.append(ui_product)
    products_group = add(oid("group:Products"), "PBXGroup", {
        "children": products_children, "name": "Products", "sourceTree": "<group>"})

    pkg_ref = add(oid("pkg:TwinCore"), "XCLocalSwiftPackageReference", {"relativePath": "Packages/TwinCore"})
    pkg_dep = add(oid("pkgdep:TwinCore"), "XCSwiftPackageProductDependency", {"package": pkg_ref, "productName": "TwinCore"})
    pkg_build = add(oid("build:pkg:TwinCore"), "PBXBuildFile", {"productRef": pkg_dep})

    main_children = [app_group]
    if ui_group:
        main_children.append(ui_group)
    main_children.append(products_group)
    main_group = add(oid("group:main"), "PBXGroup", {"children": main_children, "sourceTree": "<group>"})

    # Фазы сборки приложения
    src_phase = add(oid("phase:app:sources"), "PBXSourcesBuildPhase", {
        "buildActionMask": "2147483647", "files": app_sources, "runOnlyForDeploymentPostprocessing": "0"})
    fw_phase = add(oid("phase:app:frameworks"), "PBXFrameworksBuildPhase", {
        "buildActionMask": "2147483647", "files": [pkg_build], "runOnlyForDeploymentPostprocessing": "0"})
    res_phase = add(oid("phase:app:resources"), "PBXResourcesBuildPhase", {
        "buildActionMask": "2147483647", "files": app_resources, "runOnlyForDeploymentPostprocessing": "0"})

    def target_configs(key, settings_common):
        ids = []
        for cfg in ("Debug", "Release"):
            s = dict(settings_common)
            if cfg == "Debug":
                s["SWIFT_OPTIMIZATION_LEVEL"] = "-Onone"
                s["SWIFT_ACTIVE_COMPILATION_CONDITIONS"] = "DEBUG"
            else:
                s["SWIFT_OPTIMIZATION_LEVEL"] = "-O"
                s["SWIFT_COMPILATION_MODE"] = "wholemodule"
            ids.append(add(oid(f"cfg:{key}:{cfg}"), "XCBuildConfiguration", {"buildSettings": s, "name": cfg}))
        return add(oid(f"cfglist:{key}"), "XCConfigurationList", {
            "buildConfigurations": ids, "defaultConfigurationIsVisible": "0", "defaultConfigurationName": "Release"})

    app_settings = {
        "ASSETCATALOG_COMPILER_APPICON_NAME": "AppIcon",
        "ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME": "AccentColor",
        "CODE_SIGN_STYLE": "Automatic",
        "CURRENT_PROJECT_VERSION": BUILD_NUMBER,
        "DEVELOPMENT_TEAM": "",
        "ENABLE_PREVIEWS": "YES",
        "GENERATE_INFOPLIST_FILE": "NO",
        "INFOPLIST_FILE": "App/TwinFTP/Resources/Info.plist",
        "IPHONEOS_DEPLOYMENT_TARGET": DEPLOYMENT_TARGET,
        "LD_RUNPATH_SEARCH_PATHS": ["$(inherited)", "@executable_path/Frameworks"],
        "MARKETING_VERSION": MARKETING_VERSION,
        "PRODUCT_BUNDLE_IDENTIFIER": BUNDLE_ID,
        "PRODUCT_NAME": "$(TARGET_NAME)",
        "SDKROOT": "iphoneos",
        "SUPPORTED_PLATFORMS": "iphoneos iphonesimulator",
        "SUPPORTS_MACCATALYST": "NO",
        "SUPPORTS_MAC_DESIGNED_FOR_IPHONE_IPAD": "NO",
        "SWIFT_EMIT_LOC_STRINGS": "NO",
        "SWIFT_VERSION": "5.0",
        "TARGETED_DEVICE_FAMILY": "1,2",
    }
    app_cfg_list = target_configs("app", app_settings)
    pkg_target_dep_list = []
    app_target = add(oid("target:app"), "PBXNativeTarget", {
        "buildConfigurationList": app_cfg_list,
        "buildPhases": [src_phase, fw_phase, res_phase],
        "buildRules": [],
        "dependencies": pkg_target_dep_list,
        "name": "TwinFTP",
        "packageProductDependencies": [pkg_dep],
        "productName": "TwinFTP",
        "productReference": app_product,
        "productType": "com.apple.product-type.application",
    })
    targets = [app_target]

    if has_uitests:
        ui_src_phase = add(oid("phase:ui:sources"), "PBXSourcesBuildPhase", {
            "buildActionMask": "2147483647", "files": ui_sources, "runOnlyForDeploymentPostprocessing": "0"})
        ui_fw_phase = add(oid("phase:ui:frameworks"), "PBXFrameworksBuildPhase", {
            "buildActionMask": "2147483647", "files": [], "runOnlyForDeploymentPostprocessing": "0"})
        proxy = add(oid("proxy:ui->app"), "PBXContainerItemProxy", {
            "containerPortal": oid("project"), "proxyType": "1", "remoteGlobalIDString": app_target, "remoteInfo": "TwinFTP"})
        dep = add(oid("dep:ui->app"), "PBXTargetDependency", {"target": app_target, "targetProxy": proxy})
        ui_settings = {
            "CODE_SIGN_STYLE": "Automatic",
            "CURRENT_PROJECT_VERSION": BUILD_NUMBER,
            "GENERATE_INFOPLIST_FILE": "YES",
            "IPHONEOS_DEPLOYMENT_TARGET": DEPLOYMENT_TARGET,
            "MARKETING_VERSION": MARKETING_VERSION,
            "PRODUCT_BUNDLE_IDENTIFIER": BUNDLE_ID + ".uitests",
            "PRODUCT_NAME": "$(TARGET_NAME)",
            "SDKROOT": "iphoneos",
            "SWIFT_VERSION": "5.0",
            "TARGETED_DEVICE_FAMILY": "1,2",
            "TEST_TARGET_NAME": "TwinFTP",
        }
        ui_cfg_list = target_configs("ui", ui_settings)
        ui_target = add(oid("target:ui"), "PBXNativeTarget", {
            "buildConfigurationList": ui_cfg_list,
            "buildPhases": [ui_src_phase, ui_fw_phase],
            "buildRules": [],
            "dependencies": [dep],
            "name": "TwinFTPUITests",
            "productName": "TwinFTPUITests",
            "productReference": ui_product,
            "productType": "com.apple.product-type.bundle.ui-testing",
        })
        targets.append(ui_target)

    proj_settings_common = {
        "ALWAYS_SEARCH_USER_PATHS": "NO",
        "CLANG_ENABLE_MODULES": "YES",
        "CLANG_ENABLE_OBJC_ARC": "YES",
        "COPY_PHASE_STRIP": "NO",
        "ENABLE_STRICT_OBJC_MSGSEND": "YES",
        "ENABLE_USER_SCRIPT_SANDBOXING": "YES",
        "GCC_C_LANGUAGE_STANDARD": "gnu17",
        "IPHONEOS_DEPLOYMENT_TARGET": DEPLOYMENT_TARGET,
        "SDKROOT": "iphoneos",
        "SWIFT_VERSION": "5.0",
    }
    proj_cfgs = []
    for cfg in ("Debug", "Release"):
        s = dict(proj_settings_common)
        if cfg == "Debug":
            s.update({"DEBUG_INFORMATION_FORMAT": "dwarf", "ENABLE_TESTABILITY": "YES", "GCC_OPTIMIZATION_LEVEL": "0",
                      "ONLY_ACTIVE_ARCH": "YES", "MTL_ENABLE_DEBUG_INFO": "INCLUDE_SOURCE"})
        else:
            s.update({"DEBUG_INFORMATION_FORMAT": "dwarf-with-dsym", "ENABLE_NS_ASSERTIONS": "NO", "VALIDATE_PRODUCT": "YES",
                      "MTL_ENABLE_DEBUG_INFO": "NO"})
        proj_cfgs.append(add(oid(f"cfg:project:{cfg}"), "XCBuildConfiguration", {"buildSettings": s, "name": cfg}))
    proj_cfg_list = add(oid("cfglist:project"), "XCConfigurationList", {
        "buildConfigurations": proj_cfgs, "defaultConfigurationIsVisible": "0", "defaultConfigurationName": "Release"})

    target_attrs = {app_target: {"CreatedOnToolsVersion": "16.0"}}
    if has_uitests:
        target_attrs[oid("target:ui")] = {"CreatedOnToolsVersion": "16.0", "TestTargetID": app_target}
    add(oid("project"), "PBXProject", {
        "attributes": {"BuildIndependentTargetsInParallel": "1", "LastSwiftUpdateCheck": "1600", "LastUpgradeCheck": "1600",
                       "TargetAttributes": target_attrs},
        "buildConfigurationList": proj_cfg_list,
        "compatibilityVersion": "Xcode 15.0",
        "developmentRegion": "ru",
        "hasScannedForEncodings": "0",
        "knownRegions": ["ru", "Base", "en"],
        "mainGroup": main_group,
        "minimizedProjectReferenceProxies": "1",
        "packageReferences": [pkg_ref],
        "productRefGroup": products_group,
        "projectDirPath": "",
        "projectRoot": "",
        "targets": targets,
    })

    write_project()
    write_scheme(app_target, oid("target:ui") if has_uitests else None)
    print(f"OK: {len(app_sources)} Swift-файлов, {len(app_resources)} ресурсов, UI-тесты: {'да' if has_uitests else 'нет'}")


def render_value(v, indent):
    pad = "\t" * indent
    if isinstance(v, dict):
        out = "{\n"
        for k in sorted(v.keys()):
            out += f"{pad}\t{q(k)} = {render_value(v[k], indent + 1)};\n"
        return out + pad + "}"
    if isinstance(v, list):
        out = "(\n"
        for item in v:
            out += f"{pad}\t{render_value(item, indent + 1)},\n"
        return out + pad + ")"
    return q(str(v))


def write_project():
    os.makedirs(PROJECT_DIR, exist_ok=True)
    lines = ["// !$*UTF8*$!", "{", "\tarchiveVersion = 1;", "\tclasses = {", "\t};", "\tobjectVersion = 60;", "\tobjects = {", ""]
    by_isa = {}
    for oid_, (isa, fields) in objects.items():
        by_isa.setdefault(isa, []).append((oid_, fields))
    for isa in sorted(by_isa.keys()):
        lines.append(f"/* Begin {isa} section */")
        for oid_, fields in sorted(by_isa[isa]):
            body = {"isa": isa}
            body.update(fields)
            lines.append(f"\t\t{oid_} = {render_value(body, 2)};")
        lines.append(f"/* End {isa} section */")
        lines.append("")
    lines += ["\t};", f"\trootObject = {oid('project')};", "}", ""]
    with open(os.path.join(PROJECT_DIR, "project.pbxproj"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def write_scheme(app_target, ui_target):
    scheme_dir = os.path.join(PROJECT_DIR, "xcshareddata", "xcschemes")
    os.makedirs(scheme_dir, exist_ok=True)
    app_ref = f'''<BuildableReference
               BuildableIdentifier = "primary"
               BlueprintIdentifier = "{app_target}"
               BuildableName = "TwinFTP.app"
               BlueprintName = "TwinFTP"
               ReferencedContainer = "container:TwinFTP.xcodeproj">
            </BuildableReference>'''
    testables = ""
    if ui_target:
        testables = f'''
         <TestableReference
            skipped = "NO">
            <BuildableReference
               BuildableIdentifier = "primary"
               BlueprintIdentifier = "{ui_target}"
               BuildableName = "TwinFTPUITests.xctest"
               BlueprintName = "TwinFTPUITests"
               ReferencedContainer = "container:TwinFTP.xcodeproj">
            </BuildableReference>
         </TestableReference>'''
    xml = f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme
   LastUpgradeVersion = "1600"
   version = "1.7">
   <BuildAction
      parallelizeBuildables = "YES"
      buildImplicitDependencies = "YES">
      <BuildActionEntries>
         <BuildActionEntry
            buildForTesting = "YES"
            buildForRunning = "YES"
            buildForProfiling = "YES"
            buildForArchiving = "YES"
            buildForAnalyzing = "YES">
            {app_ref}
         </BuildActionEntry>
      </BuildActionEntries>
   </BuildAction>
   <TestAction
      buildConfiguration = "Debug"
      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
      shouldUseLaunchSchemeArgsEnv = "YES">
      <Testables>{testables}
      </Testables>
   </TestAction>
   <LaunchAction
      buildConfiguration = "Debug"
      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
      launchStyle = "0"
      useCustomWorkingDirectory = "NO"
      ignoresPersistentStateOnLaunch = "NO"
      debugDocumentVersioning = "YES"
      debugServiceExtension = "internal"
      allowLocationSimulation = "YES">
      <BuildableProductRunnable
         runnableDebuggingMode = "0">
         {app_ref}
      </BuildableProductRunnable>
   </LaunchAction>
   <ProfileAction
      buildConfiguration = "Release"
      shouldUseLaunchSchemeArgsEnv = "YES"
      savedToolIdentifier = ""
      useCustomWorkingDirectory = "NO"
      debugDocumentVersioning = "YES">
   </ProfileAction>
   <AnalyzeAction
      buildConfiguration = "Debug">
   </AnalyzeAction>
   <ArchiveAction
      buildConfiguration = "Release"
      revealArchiveInOrganizer = "YES">
   </ArchiveAction>
</Scheme>
'''
    with open(os.path.join(scheme_dir, "TwinFTP.xcscheme"), "w", encoding="utf-8") as f:
        f.write(xml)


if __name__ == "__main__":
    sys.exit(main())
