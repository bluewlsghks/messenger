"""Offline deployment guards; no cloud APIs, credentials, or paid resources."""
import os
import pathlib
import subprocess
import tempfile
import unittest

import yaml

ROOT = pathlib.Path(__file__).resolve().parents[3]
LAUNCHER = ROOT / "scripts/start-render.sh"


class LauncherTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        java = pathlib.Path(self.temp.name) / "java"
        java.write_text('#!/bin/sh\nprintf "%s\\n" "$@"\n', encoding="utf-8")
        java.chmod(0o755)
        # Synthetic data only. The stub records arguments, never the environment.
        self.env = {
            "PATH": self.temp.name + os.pathsep + os.defpath,
            "MONGODB_URI": "mongodb://example.invalid/test",
            "APP_AES_KEY_BASE64": "synthetic-aes-do-not-print",
            "APP_JWT_SECRET_BASE64": "synthetic-jwt-do-not-print",
            "RENDER_EXTERNAL_URL": "https://messenger-test.onrender.com",
        }

    def tearDown(self):
        self.temp.cleanup()

    def run_launcher(self, extra=()):
        return subprocess.run(["sh", str(LAUNCHER), *extra], env=self.env,
                              capture_output=True, text=True, timeout=5, check=False)

    def test_render_port_origin_and_paid_ai_guard(self):
        self.env.update(PORT="12345", SERVER_PORT="8081", SERVER_ADDRESS="127.0.0.1",
                        APP_ALLOWED_ORIGINS="https://old.trycloudflare.com", APP_OPENAI_ENABLED="true")
        result = self.run_launcher()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("--server.port=12345", result.stdout)
        self.assertIn("--server.address=0.0.0.0", result.stdout)
        self.assertIn("--app.allowed-origins=https://messenger-test.onrender.com", result.stdout)
        self.assertIn("--app.openai.enabled=false", result.stdout)
        self.assertNotIn("trycloudflare", result.stdout)
        for key in ("MONGODB_URI", "APP_AES_KEY_BASE64", "APP_JWT_SECRET_BASE64"):
            self.assertNotIn(self.env[key], result.stdout + result.stderr)

    def test_default_port(self):
        self.assertIn("--server.port=10000", self.run_launcher().stdout)

    def test_missing_secrets_fail_without_printing_values(self):
        for key in ("MONGODB_URI", "APP_AES_KEY_BASE64", "APP_JWT_SECRET_BASE64"):
            with self.subTest(key=key):
                value = self.env.pop(key)
                result = self.run_launcher()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(key, result.stderr)
                self.assertEqual(result.stdout, "")
                self.env[key] = value

    def test_missing_render_origin_is_not_replaced_by_a_wildcard(self):
        self.env.pop("RENDER_EXTERNAL_URL")
        self.assertNotEqual(self.run_launcher().returncode, 0)

    def test_invalid_origins_are_rejected(self):
        for origin in ("http://messenger.onrender.com", "https://messenger.onrender.com/",
                       "https://messenger.onrender.com/login", "https://evil.example",
                       "https://*.onrender.com", "https://messenger.onrender.com,https://evil.example"):
            with self.subTest(origin=origin):
                self.env["RENDER_EXTERNAL_URL"] = origin
                self.assertNotEqual(self.run_launcher().returncode, 0)

    def test_invalid_ports_are_rejected(self):
        for port in ("0", "80", "65536", "999999999999999999999", "abc", "8081; echo injection"):
            with self.subTest(port=port):
                self.env["PORT"] = port
                result = self.run_launcher()
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, "")

    def test_arbitrary_launch_arguments_are_rejected(self):
        self.assertNotEqual(self.run_launcher(["--app.openai.enabled=true"]).returncode, 0)


class BlueprintTests(unittest.TestCase):
    def test_only_one_explicitly_free_service_no_extras(self):
        blueprint = yaml.safe_load((ROOT / "render.yaml").read_text())
        self.assertEqual(set(blueprint), {"previews", "services"})
        self.assertEqual(blueprint["previews"]["generation"], "off")
        self.assertEqual(len(blueprint["services"]), 1)
        service = blueprint["services"][0]
        self.assertEqual(service["type"], "web")
        self.assertEqual(service["runtime"], "docker")
        self.assertEqual(service["plan"], "free")
        self.assertEqual(service["numInstances"], 1)
        self.assertEqual(service["autoDeployTrigger"], "off")
        self.assertFalse(set(service) & {"disk", "scaling", "domains", "previews", "initialDeployHook"})
        variables = {entry["key"]: entry for entry in service["envVars"]}
        self.assertEqual(set(variables), {"MONGODB_URI", "APP_AES_KEY_BASE64",
                                         "APP_JWT_SECRET_BASE64", "APP_OPENAI_ENABLED"})
        for key in ("MONGODB_URI", "APP_AES_KEY_BASE64", "APP_JWT_SECRET_BASE64"):
            self.assertEqual(variables[key], {"key": key, "sync": False})
        self.assertEqual(variables["APP_OPENAI_ENABLED"]["value"], "false")

    def test_docker_has_no_secret_build_args_and_runs_nonroot(self):
        docker = (ROOT / "Dockerfile").read_text()
        self.assertNotIn("COPY . .", docker)
        self.assertFalse(any(line.startswith("ARG ") for line in docker.splitlines()))
        self.assertIn("USER 10001:10001", docker)
        self.assertIn("-XX:MaxRAMPercentage=50.0", docker)
        ignore = (ROOT / ".dockerignore").read_text()
        self.assertEqual(next(line for line in ignore.splitlines() if not line.startswith("#")), "**")
        self.assertNotIn("!.env", ignore)
        self.assertNotIn("!.public", ignore)


if __name__ == "__main__":
    unittest.main(verbosity=2)
