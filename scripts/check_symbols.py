#!/usr/bin/env python3
"""Heuristic "unresolved reference" finder for Kotlin sources without a compiler.

For every call `name(` / `name {` that is not a member access (`.name`), and every capitalised
type reference, checks that the name is imported, declared in the same package, declared in the
file, a Kotlin auto-import, or a known receiver-scoped DSL/member function. Prints suspects.
False positives are expected; the list is meant for manual review.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = [os.path.join(ROOT, "app/src/main/java"), os.path.join(ROOT, "app/src/test/java")]

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_project import strip_code  # noqa: E402  (re-use the string/comment stripper)

KOTLIN_AUTO = set("""
listOf mapOf setOf emptyList emptyMap emptySet buildList buildMap buildString mutableListOf mutableMapOf
mutableSetOf listOfNotNull arrayOf intArrayOf println require check error repeat run with apply also let
takeIf maxOf minOf lazy runCatching synchronized TODO Pair Triple String Int Long Boolean Float Double Char
Byte Short ByteArray Array Any Unit Nothing Exception IllegalStateException IllegalArgumentException
RuntimeException Throwable HashMap ArrayList LinkedHashSet HashSet LinkedHashMap Regex StringBuilder List Map
Set MutableList MutableMap MutableSet Collection Iterable Sequence CharSequence Comparable Result Lazy
Suppress Deprecated Volatile Synchronized JvmStatic JvmOverloads OptIn Throws UnsupportedOperationException
IndexOutOfBoundsException NumberFormatException NoSuchElementException ClassCastException emptyArray
enumValues enumValueOf requireNotNull checkNotNull sortedMapOf Comparator compareBy also to until downTo
step in is as Enum Number Function0 Function1 KClass
""".split())

# Members / DSL functions commonly called on an implicit receiver.
RECEIVER_MEMBERS = set("""
item items itemsIndexed composable navArgument put add append copy init launch async apply invoke
stickyHeader drawRoundRect drawRect drawCircle drawLine size onDispose onPauseOrDispose
getString getSharedPreferences startActivity stopSelf goAsync setContent enableEdgeToEdge
addOnPictureInPictureModeChangedListener enterPictureInPictureMode onCreate onDestroy onStart onStop
setMediaNotificationProvider super this setValue getValue contains remove clear first last
emit send cancel close addListener removeListener values keys entries get set forEach map
initialize generateKeyPair generateKey setFlags addFlags clearFlags moveTaskToBack finish
requireContext requireActivity getSystemService handleIntent onNewIntent onUserLeaveHint
publish withController startTicker playTracks findStartDestination popUpTo navigate popBackStack
""".split())


def kotlin_files():
    for base in SRC:
        for d, _, files in os.walk(base):
            for f in files:
                if f.endswith(".kt"):
                    yield os.path.join(d, f)


top_level = {}
for path in kotlin_files():
    text = open(path, encoding="utf-8").read()
    pkg = re.search(r"^package\s+([\w.]+)", text, re.M).group(1)
    names = top_level.setdefault(pkg, set())
    for m in re.finditer(r"^(?:(?:@\w+(?:\([^)]*\))?|private|internal|public|data|sealed|enum|abstract|open|inline|value|annotation|suspend|const|operator|infix|lateinit|tailrec)\s+)*(?:class|interface|object|fun|val|var|typealias)\s+(?:<[^>]+>\s*)?(?:[\w.<>, ?*]+?\.)?(\w+)", text, re.M):
        names.add(m.group(1))

report = []
for path in kotlin_files():
    raw = open(path, encoding="utf-8").read()
    pkg = re.search(r"^package\s+([\w.]+)", raw, re.M).group(1)
    known = set(KOTLIN_AUTO) | top_level.get(pkg, set()) | RECEIVER_MEMBERS
    for imp in re.findall(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?", raw, re.M):
        known.add(imp[1] or imp[0].rsplit(".", 1)[1])
    code = strip_code(raw)
    code = re.sub(r"^(package|import) .*$", "", code, flags=re.M)
    # declarations anywhere in the file (nested classes, locals, params, lambda params)
    known |= set(re.findall(r"\b(?:class|interface|object|fun|val|var|typealias)\s+(?:<[^>]+>\s*)?(?:[\w.<>, ?*]+?\.)?(\w+)", code))
    known |= set(re.findall(r"[(,]\s*(?:vararg\s+|noinline\s+|crossinline\s+)?(\w+)\s*:", code))
    known |= set(re.findall(r"\b(\w+)\s*(?:,\s*\w+\s*)*->", code))
    known |= set(re.findall(r"\(\s*(\w+)\s*,\s*(\w+)\s*\)\s*->", code) and sum(re.findall(r"\(\s*(\w+)\s*,\s*(\w+)\s*\)", code), ()))
    for kw in ("if", "when", "for", "while", "catch", "return", "fun", "object", "constructor", "super", "this",
               "throw", "else", "try", "finally", "do", "in", "is", "as", "get", "set", "init", "field", "it"):
        known.add(kw)
    suspects = set()
    for m in re.finditer(r"(?<![\w.@:])([A-Za-z_]\w*)\s*(?:\(|\{)", code):
        name = m.group(1)
        if name not in known and not name.isupper():
            suspects.add(name)
    for m in re.finditer(r"(?:[:<,]\s*|\bas\??\s+|\bis\s+)([A-Z]\w*)", code):
        name = m.group(1)
        if name not in known and len(name) > 1:
            suspects.add(name)
    if suspects:
        report.append((os.path.relpath(path, ROOT), sorted(suspects)))

for rel, names in report:
    print(f"{rel}: {', '.join(names)}")
print(f"\n{len(report)} file(s) with suspects")

# ---- extension functions that need an explicit import when called with a dot ----
EXT = {
    "padding": "androidx.compose.foundation.layout.padding",
    "fillMaxSize": "androidx.compose.foundation.layout.fillMaxSize",
    "fillMaxWidth": "androidx.compose.foundation.layout.fillMaxWidth",
    "fillMaxHeight": "androidx.compose.foundation.layout.fillMaxHeight",
    "height": "androidx.compose.foundation.layout.height",
    "width": "androidx.compose.foundation.layout.width",
    "size": "androidx.compose.foundation.layout.size",
    "aspectRatio": "androidx.compose.foundation.layout.aspectRatio",
    "heightIn": "androidx.compose.foundation.layout.heightIn",
    "widthIn": "androidx.compose.foundation.layout.widthIn",
    "statusBarsPadding": "androidx.compose.foundation.layout.statusBarsPadding",
    "navigationBarsPadding": "androidx.compose.foundation.layout.navigationBarsPadding",
    "imePadding": "androidx.compose.foundation.layout.imePadding",
    "consumeWindowInsets": "androidx.compose.foundation.layout.consumeWindowInsets",
    "clickable": "androidx.compose.foundation.clickable",
    "combinedClickable": "androidx.compose.foundation.combinedClickable",
    "background": "androidx.compose.foundation.background",
    "border": "androidx.compose.foundation.border",
    "verticalScroll": "androidx.compose.foundation.verticalScroll",
    "clip": "androidx.compose.ui.draw.clip",
    "graphicsLayer": "androidx.compose.ui.graphics.graphicsLayer",
    "pointerInput": "androidx.compose.ui.input.pointer.pointerInput",
    "focusRequester": "androidx.compose.ui.focus.focusRequester",
    "transformable": "androidx.compose.foundation.gestures.transformable",
    "collectAsStateWithLifecycle": "androidx.lifecycle.compose.collectAsStateWithLifecycle",
    "collectAsLazyPagingItems": "androidx.paging.compose.collectAsLazyPagingItems",
    "itemKey": "androidx.paging.compose.itemKey",
    "itemContentType": "androidx.paging.compose.itemContentType",
    "cachedIn": "androidx.paging.cachedIn",
    "stateIn": "kotlinx.coroutines.flow.stateIn",
    "flatMapLatest": "kotlinx.coroutines.flow.flatMapLatest",
    "debounce": "kotlinx.coroutines.flow.debounce",
    "distinctUntilChanged": "kotlinx.coroutines.flow.distinctUntilChanged",
    "flowOn": "kotlinx.coroutines.flow.flowOn",
    "receiveAsFlow": "kotlinx.coroutines.flow.receiveAsFlow",
    "asStateFlow": "kotlinx.coroutines.flow.asStateFlow",
    "withTransaction": "androidx.room.withTransaction",
    "toHttpUrl": "okhttp3.HttpUrl.Companion.toHttpUrl",
    "toMediaType": "okhttp3.MediaType.Companion.toMediaType",
    "toRequestBody": "okhttp3.RequestBody.Companion.toRequestBody",
    "jsonObject": "kotlinx.serialization.json.jsonObject",
    "jsonPrimitive": "kotlinx.serialization.json.jsonPrimitive",
    "jsonArray": "kotlinx.serialization.json.jsonArray",
    "intOrNull": "kotlinx.serialization.json.intOrNull",
    "longOrNull": "kotlinx.serialization.json.longOrNull",
    "isActive": "kotlinx.coroutines.isActive",
}
FLOW_ONLY = {"map": "kotlinx.coroutines.flow.map", "filter": "kotlinx.coroutines.flow.filter", "first": "kotlinx.coroutines.flow.first"}

missing = []
for path in kotlin_files():
    raw = open(path, encoding="utf-8").read()
    code = strip_code(raw)
    imports = set(re.findall(r"^import\s+([\w.]+)", raw, re.M))
    imported_names = {i.rsplit(".", 1)[1] for i in imports}
    for name, fq in EXT.items():
        if name in ("size", "height", "width"):
            used = re.search(r"\.\s*" + name + r"\(\s*[\d(]", code) or re.search(r"Modifier\s*\.\s*" + name + r"\(", code)
        elif name == "isActive":
            used = re.search(r"while\s*\(\s*isActive", code)
        else:
            used = re.search(r"\.\s*" + name + r"\b\s*[({]", code)
        if used and name not in imported_names:
            # defined locally as a member/extension in the same file?
            if re.search(r"fun\s+(?:[\w.<>]+\.)?" + name + r"\s*\(", code):
                continue
            missing.append(f"{os.path.relpath(path, ROOT)}: .{name}() without import {fq}")
    # Flow operators on things that look like flows
    for name, fq in FLOW_ONLY.items():
        if re.search(r"(Flow|flow|\.typing|\.settings|\.ui|messages|_query|query|Value)\s*\)?\s*\.\s*" + name + r"\s*[({]", code) and name not in imported_names:
            missing.append(f"{os.path.relpath(path, ROOT)}: possible Flow.{name} without import {fq}")

for m in missing:
    print("MISSING", m)
print(f"{len(missing)} possible missing extension import(s)")
