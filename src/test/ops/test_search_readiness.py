"""Readiness waits for initialization but never relaxes the free-only license policy."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

path = Path(__file__).resolve().parents[3] / 'scripts/validate/elasticsearch_ready.py'
spec = importlib.util.spec_from_file_location('search_readiness', path)
ready = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ready)

class SearchReadinessTests(unittest.TestCase):
    def test_active_basic_is_ready(self):
        self.assertTrue(ready.basic_ready({'license': {'type': 'basic', 'status': 'active'}}))

    def test_not_initialized_yet_is_not_ready(self):
        for payload in (None, {}, {'license': None}, {'license': {}}, ''):
            self.assertFalse(ready.basic_ready(payload))

    def test_inactive_basic_is_not_ready(self):
        self.assertFalse(ready.basic_ready({'license': {'type': 'basic', 'status': 'expired'}}))

    def test_trials_and_paid_licenses_are_refused(self):
        for kind in ('trial', 'platinum', 'enterprise'):
            with self.assertRaises(ValueError):
                ready.basic_ready({'license': {'type': kind, 'status': 'active'}})

    def test_timeout_is_bounded(self):
        with self.assertRaises(ValueError): ready.wait_for_basic(0)
        with self.assertRaises(ValueError): ready.wait_for_basic(121)
        with patch.object(ready.time, 'monotonic', side_effect=[0, 61]):
            with self.assertRaises(RuntimeError): ready.wait_for_basic(60)

if __name__ == '__main__': unittest.main()
