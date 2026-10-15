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
    """Check opt-in behavior, validation failures, and private file permissions."""

    def setUp(self):
        """Give each test its own output file and the repository's public fixture key."""
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

    def test_missing_secret_preserves_existing_setup(self):
        """No secret must leave an already installed key untouched."""
        self.destination.write_bytes(b"existing")
        self.assertEqual(self.restore("").returncode, 0)
        self.assertEqual(self.destination.read_bytes(), b"existing")

    def test_valid_key_is_restored_privately(self):
        """A valid key must survive encoding and have private file permissions."""
        original = (self.root / "app/debug.keystore").read_bytes()
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
