#!/usr/bin/env python3
"""Static release checks. Uses standard library only."""
from pathlib import Path
from xml.etree import ElementTree
import struct
import re
ROOT = Path(__file__).resolve().parent.parent
res = ROOT / 'app/src/main/res/drawable-nodpi'
svgdir = ROOT / 'assets/icons'
expected = {
    'hf2_gesture', 'hf2_head', 'hf2_ic_charging_case_setting',
    'hf2_ic_eq', 'hf2_ic_laboratory', 'hf2_ic_settings',
    'hf2_ic_update', 'hf2_ic_wear_detection', 'hf2_voice',
    *(f'hf2_spatial_{m}_{s}' for m in ('off', 'fixed', 'follow')
      for s in ('normal', 'selected'))
}
assert {p.stem for p in res.glob('hf2_*.png')} == expected
assert {p.stem for p in svgdir.glob('hf2_*.svg')} == expected
for stem in sorted(expected):
    ElementTree.parse(svgdir / f'{stem}.svg')
    png = (res / f'{stem}.png').read_bytes()
    assert png[:8] == b'\x89PNG\r\n\x1a\n', stem
    width, height = struct.unpack('>II', png[16:24])
    assert (width,height) == (96,96), (stem,width,height)

gradle = (ROOT/'app/build.gradle').read_text(encoding='utf-8')
assert re.search(r'versionCode\s+22\b',gradle)
assert "versionName '1.1.0-beta.3'" in gradle
assert 'HF2_KEYSTORE_PASSWORD' in gradle
assert (ROOT/'app/src/main/resources/META-INF/xposed/scope.list').read_text(encoding='utf-8').strip() == 'com.hihonor.audioaccessorymanager'
ui = (ROOT/'app/src/main/java/dev/muuko/honorfreeclip2/SmartAudioPanel.java').read_text(encoding='utf-8')
assert '系统语音助手设置' in ui
assert '具体唤醒对象由系统决定' in ui
assert '刷新状态  ›' in ui
# Prevent accidental re-introduction of development-only AI key widgets.
ai_section = ui.split('detailCaption(a, aiDetail,')[1].split('private static void openAssistantSettings')[0]
for debug_label in ('原始字段', '复制本次 AI 键探测结果', '未进行探测', 'TLV01', 'TLV02', '2B/17', '0x00', '0xFF', 'probeResult'):
    assert debug_label not in ai_section, f'Unexpected AI debug UI: {debug_label}'
assert not (ROOT/'PRIVATE_ICON_MAPPING.md').exists()
print('Release static checks passed: 15 SVG/PNG pairs, version, scope, voice disclosure, signing config.')

assert (ROOT / 'LICENSE').read_text(encoding='utf-8').startswith('                    GNU GENERAL PUBLIC LICENSE')
cert = (ROOT / 'signing-certificate.sha256').read_text().strip()
assert re.fullmatch(r'[a-f0-9]{64}', cert)
module = (ROOT/'app/src/main/java/dev/muuko/honorfreeclip2/HonorFreeClip2Module.java').read_text()
assert 'getDeclaredMethod("c0")' not in module
assert 'LifecycleHookResolver.resumeMethods' in module
assert 'Object result = chain.proceed();' in module
assert 'buildFeatures { buildConfig true }' in gradle
assert 'verification {' in gradle and 'signingConfig null' in gradle
for workflow in (ROOT/'.github/workflows').glob('*.yml'):
    text = workflow.read_text()
    for ref in re.findall(r'uses:\s*([^\s#]+)', text):
        assert re.fullmatch(r'[A-Za-z0-9_.\-/]+@[a-f0-9]{40}', ref), (workflow.name, ref)
release = (ROOT/'.github/workflows/release.yml').read_text()
assert 'workflow_dispatch:' in release and 'push:' not in release and 'tags:' not in release
assert '--draft --prerelease' in release and 'confirm_signing' in release
assert 'if: always()' in release and 'rm -rf "$RUNNER_TEMP/hf2_keys"' in release
assert 'scripts/prepare_dist.sh release' in release
for svg in svgdir.glob('*.svg'):
    for element in ElementTree.parse(svg).iter():
        assert element.tag.rsplit('}', 1)[-1] != 'image', svg.name
        assert not any(k.rsplit('}', 1)[-1] == 'href' for k in element.attrib), svg.name
# Check distributable source locations; generated caches and test fixtures are intentionally excluded.
secret_patterns = [r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
                   r'gh[pousr]_[A-Za-z0-9]{30,}', r'github_pat_[A-Za-z0-9_]{30,}']
for base in [ROOT/'app/src/main', ROOT/'assets', ROOT/'scripts', ROOT/'.github']:
    for path in base.rglob('*'):
        if not path.is_file() or path.suffix == '.png':
            continue
        assert path.suffix not in {'.jks', '.keystore', '.p12', '.pem', '.key'}, path
        source = path.read_text(encoding='utf-8')
        for pattern in secret_patterns:
            assert not re.search(pattern, source), f'Potential credential in {path.relative_to(ROOT)}'
print('PASS: lifecycle fallback, unsigned validation, pinned CI, draft-only release, certificate and source privacy guards')
