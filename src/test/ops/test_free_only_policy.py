"""Repository default guards; do not start services or call external APIs."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[3]

class FreeOnlyPolicyTests(unittest.TestCase):
    def test_paid_api_is_disabled_independently_of_legacy_ai_flag(self):
        config = (ROOT / "src/main/resources/application.yml").read_text()
        self.assertIn("allow-paid-services: ${APP_ALLOW_PAID_SERVICES:false}", config)
        self.assertIn("enabled: ${APP_OPENAI_ENABLED:false}", config)

    def test_local_and_ci_search_explicitly_use_basic_license(self):
        for name in ("deploy/compose.dev.yml", ".github/workflows/roadmap-ci.yml"):
            text = (ROOT / name).read_text()
            self.assertIn("xpack.license.self_generated.type: basic", text, name)
            self.assertNotIn("xpack.license.self_generated.type: trial", text, name)

    def test_optional_infrastructure_is_not_enabled_by_default(self):
        text = (ROOT / "src/main/resources/application.yml").read_text()
        for variable in ("APP_PUSH_ENABLED", "APP_SEARCH_ENABLED", "APP_BROKER_RELAY_ENABLED"):
            self.assertIn("${" + variable + ":false}", text)

if __name__ == "__main__": unittest.main()
