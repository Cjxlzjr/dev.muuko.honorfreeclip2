# HonorFreeClip2 — v1.1.0-beta.2

**非官方社区项目**，通过 LSPosed 将 FreeClip 2 的额外控制功能集成到荣耀耳机设置页面。未获得华为或荣耀官方认证。

> **当前阶段：Beta 待真机最终验收。** 旧版十段自定义 EQ 编辑、左右 AI 键读写已有历史真机验证。三槽位音效创建、跨槽位切换、删除已通过模拟协议测试，但仍待 FreeClip 2 固件真机最终验收。本包是完整的发布候选源码，不预先宣称通过未实际执行的测试。

## 主要功能

- 十段自定义 EQ 读取、编辑和保存，写入前复核并在写入后验证回包。
- 三槽位音效管理（0x64、0x65、0x66）：查看、新建、切换、受限删除；对危险操作有二次确认，不会在未确认时自动重试。
- 左右 AI 键独立设置：提供「唤醒智能体／无」两种动作，并自动读取状态；其他未知动作保持只读。协议详情保留在源码和测试中，不显示在设置页。
- 空间音频、手势、头部控制、佩戴检测及其他 FreeClip 2 设置。
- 原有主页卡片与圆角底部弹窗保持不变，图标全部由本项目 SVG 离线生成，不依赖华为智慧音频 APP 资源。

### AI 键和语音助手的边界

AI 键的“唤醒智能体”是耳机固件动作；**被唤醒的助手由 MagicOS 和厂商语音服务决定**。模块中的「系统语音助手设置」只负责打开系统设置，**不会将耳机按键事件重定向至指定第三方语音助手**。不要将其描述为已经适配第三方语音助手。

## 已验证环境与适配范围

- 手机：**Honor Magic8 Pro**（BKQ-AN90）
- 系统：**MagicOS 11.0.0.117**（Android 17）
- 耳机：**HUAWEI FreeClip 2**，固件 **7.0.0.526**
- Root + 兼容现代 Xposed API 101 的 LSPosed；只给 `com.hihonor.audioaccessorymanager` 勾选模块作用域。

其他机型、固件版本尚未完成兼容性验证。无需安装华为智慧音频 APP。

## 截图

当前源码包未包含经脱敏的真机截图；待目标设备回归后补充，不以模拟画面代替实机效果。

## 安装

1. 下载由可信渠道使用项目源码编译、**经校验签名**的 APK。测试阶段可以使用 Debug 构建；公开发布请使用受保护的固定发布签名。
2. 安装 APK，在 LSPosed 中启用 HonorFreeClip2，**只勾选** `com.hihonor.audioaccessorymanager`。
3. 强制停止荣耀音频管理应用后重新进入耳机详情页：

   ```sh
   su
   /system/bin/am force-stop com.hihonor.audioaccessorymanager
   ```

4. 更新原有安装时必须使用相同签名；如果此前测试版使用另一个签名，Android 可能要求先卸载旧模块。卸载前先记录模块设置和签名信息。

## 本地构建

Android Studio：JDK 17、Android SDK 35、Android Gradle Plugin 8.9.2、Gradle 8.11.1。项目不捆绑 gradle wrapper 二进制；系统须提供 Gradle 8.11.1。

```sh
bash tests/run_host_tests.sh
python3 tests/check_release.py
gradle :app:assembleDebug --no-daemon
```

手机 Termux：将源码 ZIP 放入 `/sdcard/Download/`，使用包内的一键构建脚本（可按需安装 JDK 17、SDK 35、Gradle 8.11.1 和 ARM64 aapt2）：

```sh
unzip -p /sdcard/Download/HonorFreeClip2-v1.1.0-beta.2-source.zip \
  build_honorfreeclip2_beta2_termux.sh > ~/build_honorfreeclip2_beta2.sh
bash ~/build_honorfreeclip2_beta2.sh
```

该脚本生成 **Debug** APK，不应作为公开正式签名 APK。可选自定义 ZIP 路径是脚本第一个参数。

### 正式签名发布

签名私钥**不会**放在源码或 GitHub Actions artifact 中。详细步骤见 [`docs/RELEASE_GUIDE.md`](docs/RELEASE_GUIDE.md)。本地构建：

```sh
export HF2_KEYSTORE_PATH="$HOME/keys/hf2-release.jks"
export HF2_KEYSTORE_PASSWORD='（自行设置）'
export HF2_KEY_ALIAS='hf2'
export HF2_KEY_PASSWORD='（自行设置）'
bash scripts/build_release_local.sh
```

未配置四项环境变量时 Release 构建会被主动拒绝，防止生成未签名的“正式” APK。**不要**把密码粘贴到 issue、日志或仓库文件。

## 故障排查

- 模块不显示：确认 LSPosed 已启用模块、作用域仅包含 `com.hihonor.audioaccessorymanager`，强制停止该应用后重试。
- 设置无法读取：确认耳机已连接并重试；如果原厂页面自身也卡住，先禁用模块对照，必要时参考 [设备识别问题](docs/TROUBLESHOOTING.md)。
- APK 无法覆盖安装：确认新旧 APK 的签名证书相同；卸载旧版前备份设置。
- 分享诊断日志前请移除蓝牙地址、设备名称等隐私信息。

## 测试与发布材料

- [真机回归与验收记录](FINAL_TEST_PLAN.md)：包含待执行项、复现步骤及验收标准。
- [Beta 发布指南](docs/RELEASE_GUIDE.md)：CI、签名、版本号、GitHub Release 与保留签名。
- [更新日志](CHANGELOG.md) 和 [Beta 发布说明草稿](docs/RELEASE_NOTES.md)。
- [故障排查](docs/TROUBLESHOOTING.md)：包括蓝牙设备记录异常导致 `modelId is empty` 时如何恢复。
- [原创图标资源说明](ASSETS_NOTICE.md)、[许可和来源](NOTICE.md)、[GPL-3.0 许可文件](LICENSE)。

## 版权及免责

基于/参考 HuaweiPods 等公开协议研究，保留上游署名与 GPL-3.0 许可。15 张内嵌图标与 SVG 均由本项目重新绘制。HUAWEI、HONOR 与 FreeClip 属于各自权利人的商标。本项目不是华为或荣耀的官方产品。

