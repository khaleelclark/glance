#!/usr/bin/env python3
"""Build, verify, and sign a clean personal release without putting secrets in Git."""

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get("ANDROID_HOME", Path.home() / "Android" / "Sdk"))
APK_SIGNER = SDK / "build-tools" / "36.0.0" / "apksigner"
APT = SDK / "build-tools" / "36.0.0" / "aapt"
KEY_DIR = Path(os.environ.get("GLANCE_SIGNING_DIR", Path.home() / ".local" / "share" / "glance"))
KEYSTORE = KEY_DIR / "personal-release.jks"
PASSWORD_FILE = KEY_DIR / "signing-password.txt"
UNSIGNED = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release-unsigned.apk"
OUT_DIR = ROOT / "release"
EXPECTED_CERTIFICATE_SHA256 = "837148EAF9B9DEFAB6617ED0EB2F62FA49E04A14F216C4CFD719B8D71778977E"


def run(*args: str, env: dict[str, str] | None = None) -> str:
    result = subprocess.run(
        args, cwd=ROOT, env=env, check=True, capture_output=True, text=True,
    )
    return result.stdout + result.stderr


def release_fingerprint(verification: str) -> str:
    match = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)", verification)
    if not match or match.group(1).upper() != EXPECTED_CERTIFICATE_SHA256:
        raise RuntimeError("Release certificate does not match the existing Glance signer")
    return match.group(1).upper()


def main() -> int:
    for required in (APK_SIGNER, APT, KEYSTORE, PASSWORD_FILE, ROOT / "gradlew"):
        if not required.is_file():
            print("Missing required signing input: " + str(required), file=sys.stderr)
            return 1
    if KEYSTORE.stat().st_mode & 0o077 or PASSWORD_FILE.stat().st_mode & 0o077:
        print("Signing files must be readable only by their owner (mode 0600).", file=sys.stderr)
        return 1
    if run("git", "status", "--porcelain").strip():
        print("Commit all source and documentation before signing.", file=sys.stderr)
        return 1

    source_commit = run("git", "rev-parse", "HEAD").strip()
    build_env = os.environ.copy()
    build_env.setdefault("ANDROID_HOME", str(SDK))
    OUT_DIR.mkdir(exist_ok=True)
    build_log = OUT_DIR / "clean-build.log"
    try:
        build_output = run(
        str(ROOT / "gradlew"), ":app:clean", ":app:testDebugUnitTest",
        ":app:lintDebug", ":app:lintRelease", ":app:assembleRelease",
        ":wear:clean", ":wear:testDebugUnitTest", ":wear:lintDebug",
        ":wear:lintRelease", ":wear:assembleRelease",
        "--no-daemon", "--no-build-cache", env=build_env,
        )
    except subprocess.CalledProcessError as error:
        build_log.write_text((error.stdout or "") + (error.stderr or ""), encoding="utf-8")
        print("Release build failed; see " + str(build_log), file=sys.stderr)
        return 1
    build_log.write_text(build_output, encoding="utf-8")
    if run("git", "status", "--porcelain").strip() or run("git", "rev-parse", "HEAD").strip() != source_commit:
        print("Source changed during the release build.", file=sys.stderr)
        return 1
    if not UNSIGNED.is_file():
        print("Release build did not produce an unsigned APK.", file=sys.stderr)
        return 1

    result_files = sorted((ROOT / "app" / "build" / "test-results" / "testDebugUnitTest").glob("TEST-*.xml"))
    test_rows = [ET.parse(path).getroot() for path in result_files]
    test_count = sum(int(row.get("tests", "0")) for row in test_rows)
    failures = sum(int(row.get("failures", "0")) + int(row.get("errors", "0")) +
                   int(row.get("skipped", "0")) for row in test_rows)
    if test_count == 0 or failures:
        print("Unit test evidence is missing or incomplete.", file=sys.stderr)
        return 1
    watch_rows = [ET.parse(path).getroot() for path in
                  sorted((ROOT / "wear/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))]
    watch_tests = sum(int(row.get("tests", "0")) for row in watch_rows)
    watch_failures = sum(int(row.get("failures", "0")) + int(row.get("errors", "0")) +
                         int(row.get("skipped", "0")) for row in watch_rows)
    if watch_tests == 0 or watch_failures:
        print("Watch unit test evidence is missing or incomplete.", file=sys.stderr)
        return 1
    lint_warnings = 0
    for variant in ("debug", "release"):
        lint_path = ROOT / "app" / "build" / "reports" / f"lint-results-{variant}.xml"
        issues = ET.parse(lint_path).getroot().findall("issue")
        if any(issue.get("severity") in ("Error", "Fatal") for issue in issues):
            print(f"{variant} lint contains errors.", file=sys.stderr)
            return 1
        lint_warnings += sum(issue.get("severity") == "Warning" for issue in issues)
    watch_lint_warnings = 0
    for variant in ("debug", "release"):
        issues = ET.parse(ROOT / "wear/build/reports" / f"lint-results-{variant}.xml").getroot().findall("issue")
        if any(issue.get("severity") in ("Error", "Fatal") for issue in issues):
            print(f"Watch {variant} lint contains errors.", file=sys.stderr)
            return 1
        watch_lint_warnings += sum(issue.get("severity") == "Warning" for issue in issues)

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
        fingerprint = release_fingerprint(verification)
        temporary.replace(out_apk)
        sha256 = hashlib.sha256(out_apk.read_bytes()).hexdigest()
        manifest = {
            "applicationId": "com.khaleel.glance",
            "versionCode": version_code,
            "versionName": version_name,
            "apk": out_apk.name,
            "apkSha256": sha256,
            "certificateSha256": fingerprint,
            "sourceCommit": source_commit,
            "tested": {
                "unitTests": f"passed: {test_count} tests, 0 failures/errors/skips",
                "lintDebugRelease": f"passed: 0 errors, {lint_warnings} total warnings",
                "emulator": "not run for this exact source commit; see docs/device-validation.md for prior evidence",
                "liveLibreLinkUpAccount": "not tested",
                "GalaxyS25Ultra": "not tested",
            },
        }
        (OUT_DIR / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        (OUT_DIR / f"glance-{version_name}-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        watch_unsigned = ROOT / "wear/build/outputs/apk/release/wear-release-unsigned.apk"
        watch_badging = run(str(APT), "dump", "badging", str(watch_unsigned))
        watch_package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", watch_badging, re.M)
        if not watch_package or watch_package.groups() != package.groups():
            raise RuntimeError("Phone and watch package/version metadata do not match")
        watch_apk = OUT_DIR / f"glance-{version_name}-watch.apk"
        watch_temp = watch_apk.with_suffix(".apk.tmp")
        try:
            run(str(APK_SIGNER), "sign", "--ks", str(KEYSTORE), "--ks-key-alias", "glance-personal",
                "--ks-pass", "env:GLANCE_SIGNING_PASSWORD", "--key-pass", "env:GLANCE_SIGNING_PASSWORD",
                "--out", str(watch_temp), str(watch_unsigned), env=env)
            watch_verification = run(str(APK_SIGNER), "verify", "--verbose", "--print-certs", str(watch_temp))
            release_fingerprint(watch_verification)
            watch_temp.replace(watch_apk)
        finally:
            watch_temp.unlink(missing_ok=True)
        watch_manifest = {
            "applicationId": "com.khaleel.glance", "versionCode": version_code, "versionName": version_name,
            "apk": watch_apk.name, "apkSha256": hashlib.sha256(watch_apk.read_bytes()).hexdigest(),
            "certificateSha256": manifest["certificateSha256"], "sourceCommit": source_commit,
            "companion": "Requires same-signer Glance phone app and Android Wear OS pairing",
            "tested": {
                "unitTests": f"passed: {watch_tests} tests, 0 failures/errors/skips",
                "lintDebugRelease": f"passed: 0 errors, {watch_lint_warnings} total warnings",
                "emulator": "not run for this exact source commit; see docs/watch-validation.md",
                "GalaxyWatch7": "not tested",
            },
        }
        (OUT_DIR / f"glance-{version_name}-watch-manifest.json").write_text(json.dumps(watch_manifest, indent=2) + "\n", encoding="utf-8")
        print("Signed and verified: " + str(out_apk))
        print("APK SHA-256: " + sha256)
        print("Certificate SHA-256: " + manifest["certificateSha256"])
        print("Source commit: " + source_commit)
        print(f"Verification: {test_count} tests; 0 lint errors")
        print("Signed and verified watch: " + str(watch_apk))
        print("Watch APK SHA-256: " + watch_manifest["apkSha256"])
        print(f"Watch verification: {watch_tests} tests; 0 lint errors")
        return 0
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    raise SystemExit(main())
