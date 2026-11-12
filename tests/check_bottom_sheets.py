#!/usr/bin/env python3
"""Source-level guard for HonorFreeClip2's modal layout and retained protocol path."""
from pathlib import Path
root = Path(__file__).resolve().parent.parent
src = root / 'app/src/main/java/dev/muuko/honorfreeclip2'
panel = (src / 'SmartAudioPanel.java').read_text(encoding='utf-8')
sheet = (src / 'SettingsSheet.java').read_text(encoding='utf-8')
eq = (src / 'EqualizerEditorDialog.java').read_text(encoding='utf-8')
for title, symbol in [
    ('手势控制','gestureDetail'),('头部控制','headDetail'),('AI 键','aiDetail'),
    ('设备设置','deviceDetail'),('充电盒设置','caseDetail'),('实验室','labDetail'),
]:
    assert f'SettingsSheet.show(a, "{title}", {symbol})' in panel, (title, symbol)
    assert f'SmartAudioStyle.card(a, root);' not in panel[panel.index(f'LinearLayout {symbol} ='):][:100], symbol
assert 'toggleExclusive(' not in panel
assert 'SettingsSheet.showChoices(' in panel
assert 'SettingsSheet.showInfo(' in panel
assert 'dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);' in sheet
assert 'dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);' in eq
assert 'origin.addView(detail, previousParams)' in sheet
assert 'if (restoreCurrent != null) restoreCurrent.run();' in sheet
assert 'View.MeasureSpec.AT_MOST' in sheet
assert 'TLV01=左侧、TLV02=右侧已通过单侧切换 A/B 验证' not in panel
assert 'showAiActions(a, session, 1)' in panel and 'showAiActions(a, session, 2)' in panel
assert 'setPositiveButton("确认修改"' in panel
assert 'session.changeSoundPreset(options.get(which).id)' in panel
print('PASS: six config tiles are bottom sheets, dynamic choices and AI confirmation checked')
