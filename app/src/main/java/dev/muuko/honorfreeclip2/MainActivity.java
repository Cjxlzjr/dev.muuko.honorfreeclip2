package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Standalone module instructions; no dependency on the Huawei app. */
public final class MainActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = UiKit.createPage(this, "HonorFreeClip2",
                "V1.1.0 Beta 1 · 自定义 EQ 三槽位管理");
        UiKit.addSection(this, root, "运行方式",
                "LSPosed 仅选择 com.hihonor.audioaccessorymanager。"
                + " 不需要安装、启动或 Hook 华为智慧音频。");
        UiKit.addSection(this, root, "自动回读",
                "使用单一 RFCOMM/SPP 控制通道读取耳机空间音频、手势、智能开关与 EQ 状态，"
                + "修改后要求耳机实际回包确认。");
        UiKit.addSection(this, root, "十段 EQ 独立保存",
                "自定义预设允许编辑十个频段和名称；三槽位支持管理、新建、切换和受限删除。"
                + " 写入前会复核完整槽位列表，写入后检查 CRC 有效回包。新建、切换、删除尚待 FreeClip 2 真机验证。");
        UiKit.addSection(this, root, "尚未适配",
                "左右 AI 键只控制唤醒智能体或无动作；唤醒目标由 MagicOS 决定，模块不会重定向至第三方助手。三槽位新建、切换、删除尚待固件真机验收；固件 OTA 暂不支持。");
        TextView hint = UiKit.text(this,
                "升级后请在 LSPosed 取消华为智慧音频的旧作用域，强停并重新打开荣耀音频配件管理。",
                13, UiKit.MUTED);
        root.addView(hint);
    }
}
