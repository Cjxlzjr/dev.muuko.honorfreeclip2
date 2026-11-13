# HonorFreeClip2 — v1.1.0-beta.3

通过 LSPosed 将 FreeClip 2 的额外控制功能集成到荣耀耳机设置页面。非官方社区项目，采用 GPL-3.0-or-later，保留上游署名。

**发布状态：代码与自动化验证完成后，等待维护者确认签名及发布；本版本真机回归待执行。**
既有 beta.2 已发布，本次使用 `versionCode=22` 和标签 `v1.1.0-beta.3`，不覆盖旧附件。

## 功能

- 十段自定义 EQ 读取、编辑、保存和单次撤销；写前复核，写后要求完整回读。
- 自定义音效三槽位查看、新建、切换和受限删除；三槽位变更仍待目标固件真机验收。
- 左右 AI 键独立设置「唤醒智能体／无」，未知动作保持只读。唤醒目标由 MagicOS 决定。
- 空间音频、手势、头部控制、佩戴检测及其他 FreeClip 2 设置。
- 保持 beta.2 的卡片、底部弹窗、图标及原厂控制顺序；不依赖华为智慧音频 APP。

## 兼容性与安全降级

从 Android 生命周期及继承链解析 Hook，不再调用或 Hook `FunctionControlActivity.c0()`。
原厂类或绑定字段变化时，使用宿主进程内的生命周期回调和已验证布局锚点降级；
主页动态匹配要求唯一、包含原厂「更多设置」的滚动区域。布局、设备或协议无法识别时跳过扩展或保持只读。

所有 Hook 均在原方法之后执行，不替换原方法、返回值或异常。只作用于
`com.hihonor.audioaccessorymanager`，不 Hook 蓝牙进程或 system_server。
通信使用已知 SPP UUID，写入不自动重发；有界队列、20 秒请求期限及断连代次检查防止过期请求继续修改。

`HF2_COMPAT` 诊断仅输出到本机日志，包含模块、MagicOS/Android、原厂音频管理 APK 版本、
Hook 注册及布局匹配结果；普通设置页不显示诊断。日志不记录蓝牙地址、设备名和 Intent 内容。
系统属性读取失败时记录 `unknown`，不影响使用。

## 验证范围

历史真机环境：Honor Magic8 Pro（BKQ-AN90）、MagicOS 11.0.0.117 / Android 17、
FreeClip 2 固件 7.0.0.526。十段 EQ 和左右 AI 键已有历史真机验证。
**beta.3 新的 Hook 时序、跨音频管理版本、真实断连重连及三槽位变更需要真机复核。**

自动化覆盖协议与异常输入、模拟 Socket 读写、连接失效、超时及回调隔离；
Robolectric 检查布局降级、去重和原厂按钮保留。详见
[验证记录](docs/VALIDATION_REPORT.md) 与 [真机验收表](FINAL_TEST_PLAN.md)。

## 安装

1. 从 [Releases](https://github.com/Cjxlzjr/dev.muuko.honorfreeclip2/releases) 下载经签名的 APK。
2. 安装，在支持现代 Xposed API 101 的 LSPosed 中启用模块，作用域仅勾选荣耀音频管理。
3. 强制停止 `com.hihonor.audioaccessorymanager`，重新打开耳机详情页。
4. 更新需使用相同证书。公开 beta.2 证书指纹保存在 [signing-certificate.sha256](signing-certificate.sha256)。

`*-unsigned.apk` 是未签名构建验证产物，需先使用原发布密钥签名才能安装。
本项目不会通过更换签名来绕过 Android 的更新验证。

## 构建与测试

工具链：JDK 17、Android SDK 35 / Build Tools 35.0.0、AGP 8.9.2、Gradle 8.11.1。
项目不附带 Gradle Wrapper，使用已安装的 Gradle。

```sh
bash tests/run_host_tests.sh
gradle :app:assembleVerification :app:testVerificationUnitTest :app:lintVerification --no-daemon
```

`verification` 与 Release 共用代码、资源和非调试配置，不创建或读取签名密钥。
通常的 Debug 构建仍可用，由开发者自行决定是否使用本地调试密钥。

Termux ARM64 构建（默认 Debug，使用设备本地调试签名）：

```sh
unzip -p /sdcard/Download/HonorFreeClip2-v1.1.0-beta.3-source.zip \
  build_honorfreeclip2_beta3_termux.sh > ~/build_honorfreeclip2_beta3.sh
bash ~/build_honorfreeclip2_beta3.sh
```

固定发布签名及草稿 Release 的准备方式见 [发布指南](docs/RELEASE_GUIDE.md)。
CI 在 push/PR 时只构建未签名验证包；签名工作流仅允许手动确认后从 main 运行，生成草稿预发布。

## 诊断、许可与发布材料

- `sh /sdcard/Download/collect_compat_logs.sh`：只收集模块兼容性日志，分享前检查内容。
- [故障排查](docs/TROUBLESHOOTING.md)、[更新日志](CHANGELOG.md)、[发布说明](docs/RELEASE_NOTES.md)。
- [LICENSE](LICENSE)、[第三方来源](NOTICE.md)、[图标来源](ASSETS_NOTICE.md)、[安全问题](SECURITY.md)。

15 张内嵌图标和 SVG 保留项目原创资源，不捆绑原厂 APK 或提取图标。
HUAWEI、HONOR 和 FreeClip 商标归各自权利人所有。
