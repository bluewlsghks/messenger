"""Apply this one reviewed, hash-pinned source bundle on an isolated runner only."""
import hashlib
import json
import lzma
from pathlib import Path
import subprocess
import sys

BASE = '4ffa27fc9874d01b9804195678b223b506abcc65'
DIGEST = 'c597e297e5920b2985e627ccdf25c50376b2a545e23d89ec1b698d9b72bc1a3e'

def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()

def sha(data):
    return hashlib.sha256(data).hexdigest()

parts = Path(sys.argv[1])
compressed = b''.join((parts / f'{i:02d}').read_bytes() for i in range(1, 16))
assert len(compressed) == 89960 and sha(compressed) == DIGEST, 'Bundle mismatch'
decoder = lzma.LZMADecompressor(memlimit=128 * 1024 * 1024)
raw = decoder.decompress(compressed, max_length=1_000_000)
assert decoder.eof and not decoder.unused_data, 'Invalid or oversized bundle'
manifest = json.loads(raw)
assert manifest['base'] == BASE and git('rev-parse', 'HEAD') == BASE, 'Wrong baseline'
assert len(manifest['files']) == 124
assert not git('status', '--porcelain'), 'Runner checkout must be clean'
changes = []
for name, change in manifest['files'].items():
    path = Path(name)
    assert not path.is_absolute() and '..' not in path.parts and '.git' not in path.parts
    assert path.parts[0] in ('src', 'scripts', 'tests', 'docs', 'deploy', '.github') or name in (
        'README.md', '.gitignore', 'build.gradle.kts'), name
    assert not any(parent.is_symlink() for parent in (path, *path.parents)), name
    old = path.read_bytes() if path.exists() else None
    if 'beforeGitBlob' in change:
        assert old is not None and git('hash-object', name) == change['beforeGitBlob'], name
    elif change['before'] is None:
        assert old is None, name
    else:
        assert old is not None and sha(old) == change['before'], name
    if change.get('delete'):
        new = None
    elif 'text' in change:
        new = change['text'].encode('utf-8')
    else:
        lines = old.decode('utf-8').splitlines(keepends=True)
        previous = 0
        for start, end, replacement in change['ops']:
            assert previous <= start <= end <= len(lines), name
            previous = end
        for start, end, replacement in reversed(change['ops']):
            lines[start:end] = replacement.splitlines(keepends=True)
        new = ''.join(lines).encode('utf-8')
    assert (sha(new) if new is not None else None) == change['after'], name
    changes.append((name, path, new))
# All source hashes are verified before any write. Workflows are applied separately
# through the authenticated connector, not via a runner token's workflow permission.
for name, path, new in changes:
    if name.startswith('.github/workflows/'):
        continue
    if new is None:
        path.unlink()
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(new)
print('Verified all 124 paths; applied non-workflow source changes.')
