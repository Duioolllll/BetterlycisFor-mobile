#!/usr/bin/env python3
"""Regress the actual 0.4.0 install blocker and guard malformed resource tables."""
import argparse
import json
import tempfile
import zipfile
from pathlib import Path
from apk_packaging import check_resource_table

parser = argparse.ArgumentParser()
parser.add_argument('--fixed-apk')
options = parser.parse_args()
root = Path(__file__).resolve().parents[1]
checks = []


def rejects(path, reason):
    try:
        check_resource_table(path)
    except ValueError as error:
        assert reason in str(error), str(error)
        checks.append(path.name + ': correctly rejected ' + reason)
    else:
        raise AssertionError(str(path) + ' was accepted')


# These are release binaries, not generated copies of the validator's algorithm.
rejects(root / 'downloads/BetterLyrics-Mobile-0.4.0.apk', 'uncompressed')
assert check_resource_table(root / 'downloads/BetterLyrics-Mobile-0.3.0.apk')['alignment'] == 4
checks.append('0.3.0 known-good resource table accepted')
with tempfile.TemporaryDirectory() as temporary:
    folder = Path(temporary)
    missing = folder / 'missing.apk'
    with zipfile.ZipFile(missing, 'w') as archive:
        archive.writestr('unrelated.txt', 'test')
    rejects(missing, 'exactly one')
    unaligned = folder / 'unaligned.apk'
    # An ordinary ZIP_STORED file with no padding is not Android zip-aligned.
    with zipfile.ZipFile(unaligned, 'w') as archive:
        archive.writestr('x', b'')
        archive.writestr('resources.arsc', b'fixture', compress_type=zipfile.ZIP_STORED)
    rejects(unaligned, 'aligned to 4 bytes')
if options.fixed_apk:
    report = check_resource_table(options.fixed_apk)
    checks.append('fixed APK resource table accepted: ' + json.dumps(report))
print(json.dumps({'passed': True, 'checks': checks}, indent=2))
