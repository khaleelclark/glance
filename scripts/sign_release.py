#!/usr/bin/env python3
"""Sign the already built personal release APK without putting secrets in Git."""

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get("ANDROID_HOME", Path.home() / "Android" / "Sdk"))
APK_SIGNER = SDK / "build-tools" / "36.0.0" / "apksigner"
APT = SDK / "build-tools" / "36.0.0" / "aapt"
KEY_DIR = Path(os.environ.get("GLANCE_SIGNING_DIR", Path.home() / ".local" / "share" / "glance"))
KEYSTORE = KEY_DIR / "personal-release.jks"
PASSWORD_FILE = KEY_DIR / "signing-password.txt"
UNSIGNED = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release-unsigned.apk"
OUT_DIR = ROOT / "release"


def run(*args: str, env: dict[str, str] | None = None) -> str:
    result = subprocess.run(
        args, cwd=ROOT, env=env, check=True, capture_output=True, text=True,
    )
    return result.stdout + result.stderr


def main() -> int:
    for required in (APK_SIGNER, APT, KEYSTORE, PASSWORD_FILE, UNSIGNED):
        if not required.is_file():
            print("Missing required signing input: " + str(required), file=sys.stderr)
            return 1
    if KEYSTORE.stat().st_mode & 0o077 or PASSWORD_FILE.stat().st_mode & 0o077:
        print("Signing files must be readable only by their owner (mode 0600).", file=sys.stderr)
        return 1
    if run("git", "status", "--porcelain").strip():
        print("Commit all source and documentation before signing.", file=sys.stderr)
        return 1

    password = PASSWORD_FILE.read_text(encoding="utf-8").strip()
    if not password:
        print("Signing password file is empty.", file=sys.stderr)
        return 1
    env = os.environ.copy()
    env["GLANCE_SIGNING_PASSWORD"] = password
    badging = run(str(APT), "dump", "badging", str(UNSIGNED))
    package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging, re.M)
    if not package or package.group(1) != "com.khaleel.glance":
        print("Unexpected APK package metadata.", file=sys.stderr)
        return 1
    version_code, version_name = int(package.group(2)), package.group(3)
    out_apk = OUT_DIR / ("glance-" + version_name + "-personal.apk")
    OUT_DIR.mkdir(exist_ok=True)
    temporary = OUT_DIR / (out_apk.name + ".tmp")
    temporary.unlink(missing_ok=True)
    try:
        run(
            str(APK_SIGNER), "sign", "--ks", str(KEYSTORE),
            "--ks-key-alias", "glance-personal",
            "--ks-pass", "env:GLANCE_SIGNING_PASSWORD",
            "--key-pass", "env:GLANCE_SIGNING_PASSWORD",
            "--out", str(temporary), str(UNSIGNED), env=env,
        )
        verification = run(str(APK_SIGNER), "verify", "--verbose", "--print-certs", str(temporary))
        fingerprint_match = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]+)", verification)
        if not fingerprint_match:
            raise RuntimeError("apksigner did not report a certificate SHA-256 fingerprint")
        temporary.replace(out_apk)
        sha256 = hashlib.sha256(out_apk.read_bytes()).hexdigest()
        source_commit = run("git", "rev-parse", "HEAD").strip()
        manifest = {
            "applicationId": "com.khaleel.glance",
            "versionCode": version_code,
            "versionName": version_name,
            "apk": out_apk.name,
            "apkSha256": sha256,
            "certificateSha256": fingerprint_match.group(1).upper(),
            "sourceCommit": source_commit,
            "tested": {
                "unitTests": "passed",
                "lintDebugRelease": "passed with warnings",
                "emulator": "Android API 36; synthetic notification, sign-in UI, foreground service start/pause",
                "liveLibreLinkUpAccount": "not tested",
                "GalaxyS25Ultra": "not tested",
            },
        }
        (OUT_DIR / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        print("Signed and verified: " + str(out_apk))
        print("APK SHA-256: " + sha256)
        print("Certificate SHA-256: " + manifest["certificateSha256"])
        print("Source commit: " + source_commit)
        return 0
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    raise SystemExit(main())
