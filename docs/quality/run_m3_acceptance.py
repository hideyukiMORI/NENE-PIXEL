"""One bounded functional journey; first preserve/isolate the device per the linked protocol.

No benchmarks, retry loop, app-data clear, provider-output overwrite or production hooks.
Only the test APK's named fixtures are read after the actual system picker grants app access.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess


def main():
    parser = argparse.ArgumentParser()
    for name in ('adb', 'serial', 'evidence-id', 'output', 'app-apk', 'test-apk'):
        parser.add_argument('--' + name, required=True)
    args = parser.parse_args()
    assert re.fullmatch(r'[a-z0-9-]{1,35}', args.evidence_id)
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=False)
    package = 'io.github.hideyukimori.nenepixel'
    commands = []

    def call(label, *parts, timeout=60):
        command = [args.adb, '-s', args.serial, *parts]
        commands.append(command)
        (output / 'commands.json').write_text(json.dumps(commands, indent=2), encoding='utf-8')
        try:
            result = subprocess.run(command, capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired as failure:
            (output / (label + '.timeout.txt')).write_bytes((failure.stdout or b'') + (failure.stderr or b''))
            raise
        (output / (label + '.stdout')).write_bytes(result.stdout)
        (output / (label + '.stderr')).write_bytes(result.stderr)
        assert result.returncode == 0, label
        return result.stdout

    guard = 'no_backup/issue-89-user-recovery-20260912'
    call('guard', 'shell', 'run-as', package, 'test', '-d', guard)
    identity = {}
    for name, path in [('app', args.app_apk), ('test', args.test_apk)]:
        raw = Path(path).read_bytes()
        identity[name] = {'path': str(Path(path).resolve()), 'sha256': hashlib.sha256(raw).hexdigest()}
        call('install-' + name, 'install', '-r', '-t', path)
    (output / 'artifacts.json').write_text(json.dumps(identity, indent=2), encoding='utf-8')
    call('enable-test-apk', 'shell', 'pm', 'enable', package + '.test')
    prefix = package + '.acceptance.'
    stages = [
        ('save', prefix + 'DurableMvpJourneyTest#createDrawAndSave'),
        ('load', prefix + 'DurableMvpJourneyTest#restartLoadExportAndAutosave'),
        ('interrupt', prefix + 'InterruptedRecoveryWriteTest#leaveUnfinishedTemporaryWriteBesideVerifiedCandidate'),
        ('recover', prefix + 'DurableMvpJourneyTest#restartRecoverAndSaveAnotherFile'),
        ('information', prefix + 'MvpInformationUiTest'),
    ]
    try:
        for stage, test in stages:
            call(stage + '-stop', 'shell', 'am', 'force-stop', package)
            result = call(stage, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', test,
                          '-e', 'm3Isolated', 'true', '-e', 'm3EvidenceId', args.evidence_id,
                          package + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=180)
            assert re.search(rb'OK \(1 test\)', result), result.decode('utf-8', errors='replace')
            print(stage + ': PASS', flush=True)
        for suffix in ('saved.nenepixel', 'export.png', 'recovered.nenepixel'):
            call(suffix, 'exec-out', 'run-as', package + '.test', 'cat',
                 'files/m3-acceptance/i89-' + args.evidence_id + '-' + suffix)
        expected = call('expected.nenepixel', 'exec-out', 'run-as', package, 'cat',
                        'files/i89-' + args.evidence_id + '-expected.nenepixel')
        assert expected == (output / 'saved.nenepixel.stdout').read_bytes(), 'Original saved file changed'
        for language in ('english', 'japanese', 'simplifiedchinese'):
            for position in ('top', 'bottom'):
                name = 'info-' + language + '-' + position + '.png'
                call(name, 'exec-out', 'run-as', package, 'cat', 'files/i89-' + args.evidence_id + '-' + name)
        (output / 'PASS.txt').write_text('4 process stages + 3-language information case; original saved bytes unchanged.\n', encoding='utf-8')
    finally:
        call('final-stop', 'shell', 'am', 'force-stop', package)
        call('disable-test-apk', 'shell', 'pm', 'disable-user', '--user', '0', package + '.test')
        call('private-final', 'exec-out', 'run-as', package, 'tar', '-cf', '-', 'files', 'no_backup')
        # Restoration is a separate mandatory step, retaining the test record before restoring the guard.


if __name__ == '__main__':
    main()
