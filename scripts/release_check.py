"""Bounded, offline checks for files selected for public Git publication.

Run before staging, then again with --index immediately before a public commit.
Only names, sizes, and hashes are reported; rejected contents are never printed.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import struct
import subprocess
import sys
import threading
import zipfile
import zlib

MAX_FILES = 1000
MAX_TEXT_BYTES = 2 * 1024 * 1024
MAX_FILE_BYTES = 16 * 1024 * 1024
MAX_TOTAL_BYTES = 100 * 1024 * 1024
WRAPPER_SHA256 = "cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8"
ROOT_FILES = {
    ".gitignore", ".gitattributes", "LICENSE", "README.md", "ART_EXPERIMENTS.md", "RELEASE_NOTES.md", "MOD_USAGE_GUIDE.md",
    "TESTING_GUIDE.md", "CONTRIBUTING.md", "CHANGELOG.md", "SECURITY.md",
    "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew",
    "gradlew.bat", "build.ps1", "build.sh",
}
PRIVATE_PARTS = {
    ".git", ".gradle", ".aws", ".codex", ".agents", ".idea", ".vscode",
    "build", "bin", "logs", "saves", "world", "worlds", "playerdata", "stats",
    "advancements", "evidence", "backups", "install-backups", "node_modules",
}
SECRET_PATTERNS = (
    ("private-key", re.compile(rb"-----BEGIN (?:[A-Z]+ )*PRIVATE KEY-----")),
    ("github-token", re.compile(rb"\bgh[pousr]_[A-Za-z0-9]{30,}\b|\bgithub_pat_[A-Za-z0-9_]{40,}\b")),
    ("cloud-access-key", re.compile(rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b")),
    ("credential-url", re.compile(rb"https?://[^\s/@:]+:[^\s/@]+@")),
    ("private-machine-path", re.compile(rb"(?i)(?:[A-Z]:[\\/](?:Users|AIExperiments|Program Files)|/(?:Users|home)/[A-Za-z0-9_.-]+)")),
    ("minecraft-account-data", re.compile(rb'"(?:accessToken|refreshToken|authenticationDatabase|clientToken)"\s*:')),
)


class PublicationError(ValueError):
    """A selected public file violates the reviewed publication boundary."""


def allowed_path(name):
    if not name or len(name) > 240 or "\\" in name or any(ord(c) < 32 or ord(c) > 126 for c in name):
        return False
    path = PurePosixPath(name)
    parts = name.split("/")
    if path.is_absolute() or any(p in {"", ".", ".."} for p in parts):
        return False
    if any(p.lower() in PRIVATE_PARTS for p in parts) or any(p.lower().startswith("run") for p in parts[:-1]):
        return False
    if name in ROOT_FILES:
        return True
    if name in {"gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties"}:
        return True
    if name == ".github/workflows/build.yml":
        return True
    if name.startswith("scripts/release") and len(parts) == 2 and path.suffix == ".py":
        return True
    if name.startswith("docs/") and path.suffix in {".md", ".png"}:
        return not any(p.startswith(".") for p in parts)
    if len(parts) >= 4 and parts[0] == "src" and parts[1] in {"main", "test", "smoke", "showcase"}:
        if parts[2] == "java":
            return path.suffix == ".java"
        if parts[2] == "resources":
            return path.suffix in {".json", ".mcmeta", ".fsh", ".vsh", ".png", ".properties", ".ps1"}
    return False


def check_png(data):
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise PublicationError("public image is not a PNG")
    offset = 8
    chunks = 0
    saw_header = saw_image = False
    while offset < len(data):
        chunks += 1
        if chunks > 512 or offset + 12 > len(data):
            raise PublicationError("invalid or oversized PNG structure")
        size, kind = struct.unpack_from(">I4s", data, offset)
        end = offset + 12 + size
        if end > len(data) or kind not in {b"IHDR", b"IDAT", b"IEND", b"sRGB", b"gAMA", b"cHRM", b"pHYs", b"PLTE", b"tRNS"}:
            raise PublicationError("invalid PNG or unreviewed image metadata")
        if zlib.crc32(data[offset + 4:end - 4]) & 0xffffffff != struct.unpack_from(">I", data, end - 4)[0]:
            raise PublicationError("PNG checksum mismatch")
        if kind == b"IHDR":
            if saw_header or chunks != 1 or size != 13:
                raise PublicationError("invalid PNG header")
            width, height = struct.unpack_from(">II", data, offset + 8)
            if not (0 < width <= 8192 and 0 < height <= 8192):
                raise PublicationError("PNG dimensions exceed the limit")
            saw_header = True
        elif not saw_header:
            raise PublicationError("PNG header is missing")
        if kind == b"IDAT":
            saw_image = True
        if kind == b"IEND":
            if size or end != len(data) or not saw_image:
                raise PublicationError("invalid PNG ending or trailing data")
            return
        offset = end
    raise PublicationError("PNG ending is missing")


def check_public_files(files):
    """Validate a mapping of exact public paths to their selected bytes."""
    if not files or len(files) > MAX_FILES:
        raise PublicationError("public file count is empty or exceeds the limit")
    manifest = []
    total = 0
    for name, data in sorted(files.items()):
        if not allowed_path(name):
            raise PublicationError("path outside public allowlist")
        total += len(data)
        if len(data) > MAX_FILE_BYTES or total > MAX_TOTAL_BYTES:
            raise PublicationError("public file or aggregate size exceeds the limit")
        digest = hashlib.sha256(data).hexdigest()
        if name == "gradle/wrapper/gradle-wrapper.jar":
            if digest != WRAPPER_SHA256:
                raise PublicationError("Gradle wrapper does not match the official Gradle 8.8 checksum")
        elif name.endswith(".png"):
            check_png(data)
        else:
            if len(data) > MAX_TEXT_BYTES:
                raise PublicationError("public text exceeds the limit")
            try:
                data.decode("utf-8")
            except UnicodeDecodeError:
                raise PublicationError("public text is not UTF-8") from None
            if b"\x00" in data:
                raise PublicationError("unexpected binary data in public text")
            for label, pattern in SECRET_PATTERNS:
                if pattern.search(data):
                    raise PublicationError("sensitive content detected: " + label)
        manifest.append({"path": name, "bytes": len(data), "sha256": digest})
    return {"files": manifest, "file_count": len(manifest), "total_bytes": total}


def bounded_json(data):
    if len(data) > 16384:
        raise PublicationError("mod metadata exceeds the limit")
    depth = 0
    in_string = escaped = False
    for char in data.decode("utf-8"):
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
        elif char == '"':
            in_string = True
        elif char in "[{":
            depth += 1
            if depth > 24:
                raise PublicationError("mod metadata nesting exceeds the limit")
        elif char in "]}":
            depth -= 1
    def unique_pairs(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise PublicationError("duplicate mod metadata key")
            result[key] = value
        return result
    try:
        return json.loads(data, object_pairs_hook=unique_pairs)
    except (ValueError, UnicodeError, RecursionError):
        raise PublicationError("malformed mod metadata") from None


def check_mod_jar(data, expected_version=None):
    """Reject bundled fixtures/dependencies and bound archive and metadata parsing."""
    if len(data) > MAX_FILE_BYTES:
        raise PublicationError("mod archive exceeds the limit")
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            entries = archive.infolist()
            if len(entries) > 500 or sum(e.file_size for e in entries) > MAX_FILE_BYTES:
                raise PublicationError("expanded mod archive exceeds the limit")
            seen = set()
            metadata = None
            for entry in entries:
                name = entry.filename
                if name in seen or name.startswith("/") or "\\" in name or any(p in {".", ".."} for p in name.split("/")):
                    raise PublicationError("unsafe or duplicate mod archive entry")
                seen.add(name)
                if entry.is_dir():
                    continue
                if entry.flag_bits & 1 or entry.file_size > MAX_TEXT_BYTES or entry.file_size > max(1, entry.compress_size) * 200:
                    raise PublicationError("unsafe mod archive compression or size")
                if stat.S_IFMT(entry.external_attr >> 16) == stat.S_IFLNK:
                    raise PublicationError("mod archive contains a symbolic link")
                production_class = name.startswith("com/example/blockblend/") and name.endswith(".class")
                production_resource = name.startswith(("assets/blockblend/", "data/blockblend/")) and PurePosixPath(name).suffix in {".json", ".png", ".fsh", ".vsh"}
                if not (production_class or production_resource or name in {"META-INF/MANIFEST.MF", "LICENSE", "fabric.mod.json", "pack.mcmeta", "blockblend.client.mixins.json", "blockblend.refmap.json"}):
                    raise PublicationError("mod archive contains a nonproduction file")
                if any(segment in {"smoke", "showcase", "test", "tests"} for segment in name.lower().split("/")) or re.search(r"(?:Test|Tests)(?:\$[^/]*)?\.class$", name):
                    raise PublicationError("mod archive contains smoke, showcase, or test code")
                payload = archive.read(entry)
                if name == "fabric.mod.json":
                    metadata = bounded_json(payload)
            if not isinstance(metadata, dict) or metadata.get("id") != "blockblend" or not isinstance(metadata.get("version"), str):
                raise PublicationError("unexpected mod metadata")
            if expected_version is not None and metadata["version"] != expected_version:
                raise PublicationError("mod version differs from the release version")
            if metadata.get("entrypoints") != {"main": ["com.example.blockblend.BlockBlendMod"], "client": ["com.example.blockblend.client.BlockBlendClient"]}:
                raise PublicationError("unexpected production entrypoints")
            if "LICENSE" not in seen or "com/example/blockblend/BlockBlendMod.class" not in seen:
                raise PublicationError("mod archive lacks required production files")
            return {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(), "entries": len(entries), "version": metadata["version"]}
    except (zipfile.BadZipFile, UnicodeError, RuntimeError, OSError):
        raise PublicationError("malformed mod archive") from None


def git_read(root, *args, limit=MAX_FILE_BYTES):
    """Read bounded Git output without credentials, shell expansion, or config writes."""
    process = subprocess.Popen(["git", "-c", "core.excludesFile=", "-C", str(root), *args], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    timer = threading.Timer(30, process.kill)
    timer.start()
    try:
        output = process.stdout.read(limit + 1)
        if len(output) > limit:
            process.kill()
            raise PublicationError("Git output exceeds the limit")
        if process.wait() != 0:
            raise PublicationError("read-only Git check failed")
        return output
    finally:
        timer.cancel()
        process.stdout.close()
        if process.poll() is None:
            process.kill()
        process.wait()


def selected_files(root, index=False):
    actual_root = git_read(root, "rev-parse", "--show-toplevel", limit=4096).decode("utf-8").strip()
    if os.path.normcase(str(Path(actual_root).resolve())) != os.path.normcase(str(root.resolve())):
        raise PublicationError("Git root differs from the intended public root")
    files = {}
    if index:
        entries = git_read(root, "ls-files", "--stage", "-z", limit=1024 * 1024).split(b"\0")
        for entry in filter(None, entries):
            metadata, raw_name = entry.split(b"\t", 1)
            mode, object_id, stage = metadata.split()
            if mode not in {b"100644", b"100755"} or stage != b"0" or not re.fullmatch(rb"[0-9a-f]{40,64}", object_id):
                raise PublicationError("index contains a link, submodule, or unresolved entry")
            name = raw_name.decode("utf-8")
            if not allowed_path(name):
                raise PublicationError("index contains a path outside the public allowlist")
            size = int(git_read(root, "cat-file", "-s", object_id.decode(), limit=128))
            if size > MAX_FILE_BYTES:
                raise PublicationError("indexed file exceeds the limit")
            files[name] = git_read(root, "cat-file", "blob", object_id.decode())
            if len(files) > MAX_FILES or sum(map(len, files.values())) > MAX_TOTAL_BYTES:
                raise PublicationError("indexed tree exceeds the limit")
    else:
        names = git_read(root, "ls-files", "--cached", "--others", "--exclude-standard", "-z", limit=1024 * 1024).split(b"\0")
        for raw_name in filter(None, names):
            name = raw_name.decode("utf-8")
            if not allowed_path(name):
                raise PublicationError("worktree selection contains a path outside the public allowlist")
            path = root / name
            ancestors = [path]
            while ancestors[-1] != root:
                ancestors.append(ancestors[-1].parent)
            if any(p.is_symlink() or getattr(p.lstat(), "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0) for p in ancestors):
                raise PublicationError("selected public path crosses a link or junction")
            if not path.is_file() or path.stat().st_size > MAX_FILE_BYTES:
                raise PublicationError("selected public path is missing or oversized")
            with path.open("rb") as handle:
                files[name] = handle.read(MAX_FILE_BYTES + 1)
            if len(files) > MAX_FILES or sum(map(len, files.values())) > MAX_TOTAL_BYTES:
                raise PublicationError("selected worktree exceeds the limit")
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--index", action="store_true", help="scan staged Git blobs instead of working files")
    parser.add_argument("--jar", type=Path, help="check the exact production mod archive")
    parser.add_argument("--built-jar", action="store_true", help="check the single nonsources JAR in build/libs")
    parser.add_argument("--version", help="required release version when checking a JAR")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    try:
        result = check_public_files(selected_files(root, args.index))
        jar = args.jar
        if args.built_jar:
            candidates = [p for p in (root / "build/libs").glob("blockblend-*.jar") if not p.name.endswith("-sources.jar")]
            if len(candidates) != 1 or jar is not None:
                raise PublicationError("expected exactly one production build JAR")
            jar = candidates[0]
        if jar is not None:
            if jar.stat().st_size > MAX_FILE_BYTES:
                raise PublicationError("mod archive exceeds the limit")
            with jar.open("rb") as handle:
                result["jar"] = check_mod_jar(handle.read(MAX_FILE_BYTES + 1), args.version)
        result.update({"status": "PASS", "selection": "index" if args.index else "worktree"})
        print(json.dumps(result, sort_keys=True, indent=2))
    except (PublicationError, OSError, UnicodeError, ValueError) as error:
        print(json.dumps({"status": "FAIL", "reason": str(error) if isinstance(error, PublicationError) else "publication check failed"}))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
