"""URI normalization must not move credentials into arguments or change authentication."""
import importlib.util
from pathlib import Path
import unittest
from urllib.parse import parse_qs, urlsplit

path = Path(__file__).resolve().parents[3] / "scripts/ops/mongo_backup.py"
spec = importlib.util.spec_from_file_location("backup_uri", path)
backup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backup)

class BackupUriTests(unittest.TestCase):
    def test_database_is_selected_by_explicit_dump_and_restore_namespace(self):
        self.assertEqual(backup.tool_uri("mongodb://127.0.0.1:27017/app"), "mongodb://127.0.0.1:27017/")

    def test_authentication_database_is_not_silently_changed(self):
        uri = urlsplit(backup.tool_uri("mongodb://user:p%40ss@localhost/app?retryWrites=true"))
        self.assertEqual(uri.netloc, "user:p%40ss@localhost")
        self.assertEqual(uri.path, "/")
        self.assertEqual(parse_qs(uri.query), {"retryWrites": ["true"], "authSource": ["app"]})

    def test_explicit_auth_source_wins(self):
        uri = urlsplit(backup.tool_uri("mongodb://user:pass@localhost/app?authSource=admin&tls=true"))
        self.assertEqual(parse_qs(uri.query), {"authSource": ["admin"], "tls": ["true"]})

    def test_decodes_percent_encoded_database_once(self):
        uri = urlsplit(backup.tool_uri("mongodb://user:pass@localhost/my%2Dapp"))
        self.assertEqual(parse_qs(uri.query)["authSource"], ["my-app"])

    def test_external_authentication_mechanism(self):
        uri = urlsplit(backup.tool_uri("mongodb://user@localhost/app?authMechanism=PLAIN"))
        self.assertEqual(parse_qs(uri.query)["authSource"], ["$external"])

    def test_default_admin_authentication_is_preserved_without_path(self):
        self.assertEqual(backup.tool_uri("mongodb://user:pass@localhost"), "mongodb://user:pass@localhost/")

    def test_remote_srv_requires_consent_and_retains_tls(self):
        original = "mongodb+srv://user:pass@example.invalid/app?tls=true&authSource=admin"
        with self.assertRaises(ValueError): backup.validate_uri(original, False)
        backup.validate_uri(original, True)
        uri = urlsplit(backup.tool_uri(original))
        self.assertEqual(uri.scheme, "mongodb+srv")
        self.assertEqual(parse_qs(uri.query)["tls"], ["true"])

    def test_seed_list_cannot_bypass_local_only_guard(self):
        with self.assertRaises(ValueError):
            backup.validate_uri("mongodb://localhost:27017,remote.invalid:27017/app", False)

if __name__ == "__main__": unittest.main()
