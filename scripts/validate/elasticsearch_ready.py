#!/usr/bin/env python3
"""Wait for a locally initialized, active Basic license without starting a trial."""
import json
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import urlopen

URL = 'http://127.0.0.1:9200/_license'

def basic_ready(payload):
    license_info = payload.get('license') if isinstance(payload, dict) else None
    if not isinstance(license_info, dict) or not license_info.get('type'):
        return False
    if license_info['type'] != 'basic':
        raise ValueError('Only the Basic license is allowed; no trial or paid license is accepted.')
    return license_info.get('status') == 'active'

def wait_for_basic(timeout_seconds=60):
    if not 1 <= timeout_seconds <= 120:
        raise ValueError('Readiness timeout must be 1..120 seconds.')
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            with urlopen(URL, timeout=min(3, max(0.1, deadline - time.monotonic()))) as response:
                raw = response.read(65537)
                if len(raw) > 65536:
                    raise ValueError('Unexpected oversized license response.')
                try:
                    payload = json.loads(raw)
                except (json.JSONDecodeError, UnicodeDecodeError):
                    payload = None
                if basic_ready(payload):
                    print('PASS: local Elasticsearch has an active Basic license.')
                    return
        except HTTPError as error:
            if error.code not in (404, 503):
                raise RuntimeError(f'License readiness failed with HTTP {error.code}.') from None
        except (URLError, TimeoutError, ConnectionError):
            pass
        time.sleep(min(1, max(0, deadline - time.monotonic())))
    raise RuntimeError('Active Basic license was not ready before the timeout.')

if __name__ == '__main__':
    try:
        wait_for_basic()
    except (ValueError, RuntimeError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
