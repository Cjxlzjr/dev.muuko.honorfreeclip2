# HonorFreeClip2 v1.1.0-beta.2 — 发布说明（草稿）

这是面向荣耀 Magic8 Pro 与华为 FreeClip 2 的非官方 LSPosed 增强控制模块 Beta 版。

**本次 Beta.2 修正**
- AI 键设置页面移除了调试说明、原始字节结果、探测对比和复制功能；仅保留用户设置和刷新入口。
- 保持原有独立蓝牙读写、状态复核与写入结果确认；错误时清除过时的 AI 键状态，避免按旧值操作。

**其他更新内容**
- 继承十段自定义 EQ 编辑/保存、AI 键左右独立读写、空间音频及手势等控制。
- 三槽位音效管理：读取、创建、切换及受限删除（写入前复核和回包确认机制）。
- 15 个界面图标替换为原创 SVG + PNG，颜色与原界面接近；去掉从厂商 APK 提取的图片资源。
- 完善设备重新配对恢复说明、构建步骤、自动测试、Release 签名流程。

**兼容设备**
- Honor Magic8 Pro / MagicOS 11.0.0.117（Android 17）
- HUAWEI FreeClip 2 / 7.0.0.526
- Root、LSPosed API 101；作用域仅 `com.hihonor.audioaccessorymanager`

**待验收项**
- 三槽位创建、切换和删除的当前固件真机结果尚待确认，请谨慎使用。完成 `FINAL_TEST_PLAN.md` 后再将本文件改为正式 Release 说明。
- AI 键唤醒目标受 MagicOS 控制，系统设置入口不保证改变耳机的唤醒目标。

**已知设备识别问题的恢复**
- 原厂耳机页若报告 `modelId is empty` 并持续加载，曾在取消并重新配对后恢复；详见 `docs/TROUBLESHOOTING.md`。

许可：GPL-3.0；上游 HuaweiPods 保留署名。所有产品商标属于各自权利人。
