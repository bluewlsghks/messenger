#!/usr/bin/env python3
"""Quiesced Mongo backup / restore to a NEW database only. Requires MongoDB Database Tools + mongosh.
No --drop, no in-place restore, no credentials in command-line arguments or diagnostic output.
The caller must stop all writers for a consistent standalone-MongoDB snapshot.
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from urllib.parse import parse_qsl, unquote, urlencode, urlsplit, urlunsplit

EPHEMERAL = ('auth_sessions', 'presence_leases', 'media_state', 'request_budgets',
             'push_subscriptions', 'push_jobs', 'search_reindex')

def database(value: str) -> str:
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_-]{0,62}', value):
        raise ValueError('Use an explicit database name: letter followed by letters, digits, _ or -.')
    if value.lower() in ('admin', 'config', 'local'):
        raise ValueError('System databases are not supported.')
    return value

def validate_uri(uri: str, allow_remote: bool) -> None:
    parsed = urlsplit(uri)
    if parsed.scheme not in ('mongodb', 'mongodb+srv') or not parsed.hostname:
        raise ValueError('MONGODB_URI must be a MongoDB connection URI.')
    if not allow_remote and (',' in parsed.netloc.rsplit('@', 1)[-1] or parsed.hostname not in ('localhost', '127.0.0.1', '::1')):
        raise ValueError('Remote connections require --allow-remote. Verify the target first.')

def tool_uri(uri: str) -> str:
    """Remove the default DB, preserving authentication, before explicit dump/namespace selection.

    mongodump rejects a URI database that differs from --db. Leaving a URI database
    during namespace-remapped restore can also narrow the archive unexpectedly.
    Credentials remain only in the private temporary config, never CLI arguments.
    """
    parsed = urlsplit(uri)
    query = parse_qsl(parsed.query, keep_blank_values=True)
    options = {key.lower(): value for key, value in query}
    original_db = unquote(parsed.path.lstrip('/'))
    if parsed.username is not None and original_db and 'authsource' not in options:
        # External mechanisms use $external rather than the URI's default database.
        external = options.get('authmechanism', '').upper() in (
            'GSSAPI', 'PLAIN', 'MONGODB-X509', 'MONGODB-AWS', 'MONGODB-OIDC')
        query.append(('authSource', '$external' if external else original_db))
    return urlunsplit((parsed.scheme, parsed.netloc, '/', urlencode(query), ''))

def run_tool(command: list[str], *, env: dict[str,str] | None = None, timeout: int = 1800) -> str:
    # Mongo tools may echo connection details on failure. Do not propagate raw stderr.
    result = subprocess.run(command, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            encoding='utf-8', timeout=timeout, check=False)
    if result.returncode:
        raise RuntimeError(f'{Path(command[0]).name} failed (exit {result.returncode}). Check credentials, tool compatibility and permissions; raw output was suppressed.')
    return result.stdout

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=('backup','restore'))
    parser.add_argument('--source', required=True, type=database)
    parser.add_argument('--archive', required=True, type=Path)
    parser.add_argument('--target', type=database)
    parser.add_argument('--confirm-new-database')
    parser.add_argument('--writers-stopped', action='store_true')
    parser.add_argument('--allow-remote', action='store_true')
    args = parser.parse_args()
    uri = os.environ.get('MONGODB_URI', '')
    validate_uri(uri, args.allow_remote)
    archive = args.archive.expanduser().resolve()
    if not args.writers_stopped:
        raise ValueError('Stop all application writers first, then pass --writers-stopped. This script does not stop services.')
    if args.operation == 'restore':
        if not args.target or args.target == args.source or args.confirm_new_database != args.target:
            raise ValueError('Restore needs a different --target and an identical --confirm-new-database value.')
        if not archive.is_file(): raise ValueError('Backup archive does not exist.')
        env = dict(os.environ, BACKUP_TARGET=args.target)
        # Check collection existence, not merely document count. Existing empty collections also refuse restore.
        check = "const c=new Mongo(process.env.MONGODB_URI); const names=c.getDB(process.env.BACKUP_TARGET).getCollectionNames(); print(JSON.stringify(names));"
        if json.loads(run_tool(['mongosh','--nodb','--quiet','--eval',check],env=env,timeout=60).strip()):
            raise ValueError('Target database already has collections. Restore was refused; nothing was dropped.')
        command = ['mongorestore', f'--archive={archive}', '--gzip', f'--nsInclude={args.source}.*',
                   f'--nsFrom={args.source}.*', f'--nsTo={args.target}.*', '--stopOnError']
        for collection in EPHEMERAL: command.append(f'--nsExclude={args.source}.{collection}')
    else:
        if archive.exists(): raise ValueError('Archive exists; choose a new file. Backups are never overwritten.')
        archive.parent.mkdir(parents=True, exist_ok=True)
        # Create with exclusive ownership before the tool writes. No world-readable backup by default.
        fd = os.open(archive, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600); os.close(fd)
        command = ['mongodump', f'--db={args.source}', f'--archive={archive}', '--gzip']
        for collection in EPHEMERAL: command += ['--excludeCollection', collection]
    with tempfile.TemporaryDirectory(prefix='messenger-mongo-') as directory:
        config = Path(directory)/'connection.json'
        config.write_text(json.dumps({'uri': tool_uri(uri)}), encoding='utf-8'); config.chmod(0o600)
        command += [f'--config={config}']  # JSON is a YAML subset supported by Database Tools config.
        try: run_tool(command)
        except BaseException:
            if args.operation == 'backup': archive.unlink(missing_ok=True)
            raise
    print(f'{args.operation.upper()} complete. Session/presence/push/media leases excluded. Verify business data before directing the app at a restored database.')
    return 0

if __name__ == '__main__':
    try: sys.exit(main())
    except (ValueError, RuntimeError, OSError, subprocess.TimeoutExpired) as error:
        print(f'Backup operation refused or failed: {error}', file=sys.stderr); sys.exit(1)
