# Co-authored-by: OpenAI Codex
"""Exercise key restoration with real keytool validation and isolated destinations."""

import base64
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest


class RestoreDebugKeystoreTest(unittest.TestCase):
    """Check required secrets, validation failures, and private file permissions."""

    @classmethod
    def setUpClass(cls):
        """Generate a disposable signing key instead of depending on a committed secret."""
        cls.fixture_directory = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.fixture_directory.cleanup)
        fixture = Path(cls.fixture_directory.name) / "fixture.keystore"
        subprocess.run(
            ["keytool", "-genkeypair", "-keystore", str(fixture),
             "-storetype", "PKCS12", "-alias", "androiddebugkey",
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "1",
             "-dname", "CN=Disposable Test Key", "-storepass", "android",
             "-keypass", "android"],
            check=True, capture_output=True,
        )
        cls.fixture_bytes = fixture.read_bytes()

    def setUp(self):
        """Give each test its own output file."""
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.destination = Path(self.directory.name) / "private-debug.keystore"
        self.root = Path(__file__).resolve().parents[2]

    def restore(self, encoded):
        """Run CI restoration without inheriting signing secrets."""
        env = dict(os.environ, DEBUG_KEYSTORE_BASE64=encoded)
        return subprocess.run(
            ["bash", str(self.root / "scripts/restore-debug-keystore.sh"), str(self.destination)],
            env=env, capture_output=True, text=True,
        )

    def test_missing_secret_fails_without_replacing_existing_key(self):
        """A required secret must not be bypassed by an existing output file."""
        self.destination.write_bytes(b"existing")
        self.assertNotEqual(self.restore("").returncode, 0)
        self.assertEqual(self.destination.read_bytes(), b"existing")

    def test_missing_secret_does_not_create_key(self):
        """Missing secrets must produce an actionable failure without creating a key."""
        result = self.restore("")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("DEBUG_KEYSTORE_BASE64 is required", result.stderr)
        self.assertFalse(self.destination.exists())

    def test_valid_key_is_restored_privately(self):
        """A valid key must survive encoding and have private file permissions."""
        original = self.fixture_bytes
        result = self.restore(base64.b64encode(original).decode())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.destination.read_bytes(), original)
        self.assertEqual(stat.S_IMODE(self.destination.stat().st_mode), 0o600)

    def test_invalid_encoding_preserves_existing_key(self):
        """Malformed secret text must fail without replacing a working key."""
        self.destination.write_bytes(b"existing")
        self.assertNotEqual(self.restore("!invalid!").returncode, 0)
        self.assertEqual(self.destination.read_bytes(), b"existing")

    def test_invalid_keystore_is_rejected(self):
        """Encoded arbitrary bytes must not become a signing key."""
        self.assertNotEqual(self.restore(base64.b64encode(b"not a key").decode()).returncode, 0)
        self.assertFalse(self.destination.exists())
