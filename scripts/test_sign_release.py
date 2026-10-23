"""Release identity checks with synthetic files and a stubbed tool runner."""

import contextlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import sign_release


class ReleaseSignerTest(unittest.TestCase):
    def test_existing_certificate_is_accepted(self):
        output = "Signer #1 certificate SHA-256 digest: " + sign_release.EXPECTED_CERTIFICATE_SHA256.lower()
        self.assertEqual(sign_release.EXPECTED_CERTIFICATE_SHA256, sign_release.release_fingerprint(output))

    def test_missing_certificate_is_rejected(self):
        with self.assertRaises(RuntimeError):
            sign_release.release_fingerprint("No signer reported")

    def test_replacement_certificate_cannot_overwrite_an_existing_release(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            signer, aapt = root / "apksigner", root / "aapt"
            key, password = root / "synthetic-key", root / "synthetic-password"
            unsigned = root / "app/build/outputs/apk/release/app-release-unsigned.apk"
            output = root / "release"
            output.mkdir()
            previous = output / "glance-0.32.0-personal.apk"
            previous.write_bytes(b"previous release")
            for file in [signer, aapt, key, password, root / "gradlew", unsigned]:
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text("synthetic")
                file.chmod(0o600)
            for module in ["app", "wear"]:
                result = root / module / "build/test-results/testDebugUnitTest/TEST-synthetic.xml"
                result.parent.mkdir(parents=True)
                result.write_text('<testsuite tests="1" failures="0" errors="0" skipped="0"/>')
                for variant in ["debug", "release"]:
                    lint = root / module / f"build/reports/lint-results-{variant}.xml"
                    lint.parent.mkdir(parents=True, exist_ok=True)
                    lint.write_text("<issues/>")

            def tool(*args, env=None):
                if args[0] == "git":
                    return "" if args[1] == "status" else "synthetic-source\n"
                if args[0] == str(root / "gradlew"):
                    return "synthetic build"
                if args[0] == str(aapt):
                    return "package: name='com.khaleel.glance' versionCode='37' versionName='0.32.0'\n"
                if args[0] == str(signer) and args[1] == "sign":
                    Path(args[args.index("--out") + 1]).write_bytes(b"replacement APK")
                    return ""
                if args[0] == str(signer) and args[1] == "verify":
                    return "Signer #1 certificate SHA-256 digest: " + "aa" * 32
                self.fail("Unexpected external tool call")

            with patch.multiple(sign_release, ROOT=root, SDK=root / "sdk", APK_SIGNER=signer,
                                APT=aapt, KEYSTORE=key, PASSWORD_FILE=password,
                                UNSIGNED=unsigned, OUT_DIR=output, run=tool):
                with contextlib.redirect_stdout(io.StringIO()):
                    with self.assertRaisesRegex(RuntimeError, "existing Glance signer"):
                        sign_release.main()
            self.assertEqual(b"previous release", previous.read_bytes())
            self.assertFalse((output / "manifest.json").exists())
            self.assertFalse((output / "glance-0.32.0-personal.apk.tmp").exists())


if __name__ == "__main__":
    unittest.main()
