# HonorFreeClip2 v1.1.0-beta.3

在 beta.2 基础上完成兼容性和稳定性修补，保留现有 UI、图标和已验证控制。

- 移除 `FunctionControlActivity.c0()` 单点依赖，解析生命周期继承关系并添加安全布局降级。
- 新增仅写本机日志的系统、原厂 APK 版本、Hook 和布局诊断。
- 普通设置写前回读，未知状态保持只读；限制 SPP 服务范围、有界队列和 20 秒请求期限。
- 改善页面暂停、断连重连及失效结果处理；异常不替换原厂方法结果。
- 拒绝重复/损坏 TLV 和重复 EQ 槽位，增加回归测试、Lint 和旧证书一致性检查。

## 验证

JVM 协议/模拟传输回归、3000 组异常字节流、Android 35 Robolectric 布局测试、
非调试未签名 APK 构建及完整 Lint 的实际结果见 `docs/VALIDATION_REPORT.md`。
签名产物由维护者使用原 beta.2 私钥生成并验证，源码包和 APK 版本为 22 / 1.1.0-beta.3。

## 真机待验

历史目标环境：Magic8 Pro / MagicOS 11.0.0.117、FreeClip 2 / 7.0.0.526。
十段 EQ 与左右 AI 键有历史真机验证；本版新的 Hook 时序、不同原厂音频管理版本、
真实断连重连以及三槽位创建/切换/删除仍待目标设备验收。

普通设置及 AI 键动作仍由耳机实际回读确认。AI 键不重定向第三方助手，固件 OTA 不支持。
布局改动或非中文页面导致锚点不匹配时会跳过注入；深层系统蓝牙阻塞可能需要重启音频管理进程恢复。
请只在 LSPosed 勾选 `com.hihonor.audioaccessorymanager`。

## 更新与来源

使用原 beta.2 发布证书，versionCode 从 21 递增至 22。
旧标签/附件保持不变，新标签为 `v1.1.0-beta.3`。
GPL-3.0-or-later；保留 HuaweiPods / OpenFreebuds 协议研究署名，15 张图标为项目原创资源。
