package dev.muuko.honorfreeclip2;

import java.util.ArrayList;
import java.util.List;

/** UI definitions and the writable device settings and conservative unsupported placeholders. */
enum FreeClipSetting {
    SPATIAL_MODE("空间音频", "空间音频模式", "关闭 / 固定 / 头部跟踪", "关闭,固定,头部跟踪", "0,2,1"),
    SPATIAL_SCENE("空间音频", "空间音频场景", "默认 / 音频剧场 / 电影院 / 音乐厅", "默认,音频剧场,电影院,音乐厅", "0,1,2,3"),
    SOUND_EFFECT("声音", "音效预设", "预设音效，使用耳机回读结果", "默认,运动增强,高音增强,清晰人声", "1,10,3,9"),
    CUSTOM_EQ("声音", "自定义均衡器", "独立 EQ 编辑器支持十段保存与会话内撤销", null, null),
    DOUBLE_LEFT("手势", "左耳双击", "双击动作", "播放/暂停,下一首,空间音频,语音助手,无", "1,2,7,0,255"),
    DOUBLE_RIGHT("手势", "右耳双击", "双击动作", "播放/暂停,下一首,空间音频,语音助手,无", "1,2,7,0,255"),
    TRIPLE_LEFT("手势", "左耳三击", "三击动作", "下一首,上一首,无", "2,7,255"),
    TRIPLE_RIGHT("手势", "右耳三击", "三击动作", "下一首,上一首,无", "2,7,255"),
    SWIPE_LEFT("手势", "左耳滑动", "滑动控制", "音量调节,无", "0,255"),
    SWIPE_RIGHT("手势", "右耳滑动", "滑动控制", "音量调节,无", "0,255"),
    LONG_PRESS("手势", "长按语音助手", "待适配：现有 HuaweiPods FreeClip 2 手势控制器未覆盖该固件指令", null, null),
    HEAD_MOTION("头部动作", "头部动作控制", "启用点头 / 摇头动作", null, "boolean"),
    HEAD_NOD("头部动作", "点头动作", "来电时的操作", "无,接听,拒接", "0,1,2"),
    HEAD_SHAKE("头部动作", "摇头动作", "来电时的操作", "无,接听,拒接", "0,1,2"),
    WEAR("智能功能", "智能佩戴检测", "摘下暂停，戴上继续", null, "boolean"),
    DROP("智能功能", "掉落提醒", "耳机意外掉落提醒", null, "boolean"),
    ADAPTIVE("智能功能", "智感音量自适应", "根据环境自动调整音量", null, "boolean"),
    NOISY("智能功能", "嘈杂环境大音量", "嘈杂环境中的音量增强", null, "boolean"),
    LOW_LATENCY("连接", "游戏低时延", "耳机侧低时延开关", null, "boolean"),
    DUAL_DEVICE("连接", "双设备连接", "耳机侧双设备开关", null, "boolean"),
    CASE_SOUND("连接", "充电盒提示音", "控制提示音开关", null, "boolean");

    final String group;
    final String title;
    final String description;
    final String[] labels;
    final int[] values;
    final boolean isBoolean;
    final boolean supported;

    FreeClipSetting(String group, String title, String description, String labels, String values) {
        this.group = group;
        this.title = title;
        this.description = description;
        this.isBoolean = "boolean".equals(values);
        this.supported = values != null;
        this.labels = labels == null ? new String[0] : labels.split(",");
        if (values == null || isBoolean) {
            this.values = new int[0];
        } else {
            String[] parts = values.split(",");
            this.values = new int[parts.length];
            for (int i = 0; i < parts.length; i++) this.values[i] = Integer.parseInt(parts[i]);
        }
    }

    String label(Integer value) {
        if (value == null) return "未读取";
        if (isBoolean) return value == 1 ? "已开启" : "已关闭";
        for (int i = 0; i < values.length; i++) if (values[i] == value) return labels[i];
        return "未知状态";
    }

    boolean accepts(int value) {
        if (!supported) return false;
        if (isBoolean) return value == 0 || value == 1;
        for (int v : values) if (v == value) return true;
        return false;
    }

    static List<FreeClipSetting> forGroup(String group, boolean functionPage) {
        List<FreeClipSetting> result = new ArrayList<>();
        for (FreeClipSetting setting : values()) {
            if (setting.group.equals(group) && !(functionPage && setting == WEAR)) result.add(setting);
        }
        return result;
    }

    static String[] groups() { return new String[]{"空间音频", "声音", "手势", "头部动作", "智能功能", "连接"}; }
}
