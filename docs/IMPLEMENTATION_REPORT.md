# V1.1.0 Beta 1 改造交接报告

## 本次交付完成项（代码和文档）

1. **图标资源**：删除旧版 15 张厂商 APK 资源，重绘相同数量和资源名称的 SVG 图标；生成 96×96 PNG；保留颜色、卡片背景与空间音频选中态，不修改布局。
2. **AI 助手提示**：将页面描述改成「系统语音设置（仅跳转）」，在 UI 直接提示耳机 AI 键的唤醒目标由 MagicOS/厂商服务决定，不会重定向第三方助手。
3. **真机验收准备**：扩充 `FINAL_TEST_PLAN.md` 为 T01–T12，列出每项通过条件、回归顺序、失败保护与诊断日志；附真实发生的 iConnect `modelId is empty` 与重新配对恢复记录。
4. **构建发布**：修改应用版本为 `versionCode=21`, `versionName=1.1.0-beta.2`，新增 CI Debug 构建、签名 Release 工作流、SHA256 检查、本地 Release 签名脚本和 Termux Debug 编译脚本。

## 本地已执行的自动化检查

- `bash tests/run_host_tests.sh`：通过（涵盖 EQ 编解码、AI 键、三槽位协议与模拟 Socket 读写）。
- `python3 tests/check_release.py`：通过（15 对图标、SVG/PNG、版本、LSPosed 作用域、文案、密钥配置）。
- Bash 脚本静态语法检查：通过。
- XML/SVG 资源解析和 ZIP 文件 CRC：通过。

## **必须由开发者在实际设备或 CI 补完的验收**

- **Android SDK/Gradle 完整 APK 编译**：本次处理环境未安装 Android SDK 和 Gradle，因此未生成或声称已验证 APK。
- **签名 Release 构建**：需开发者自己的固定 JKS，不能在源码里生成或共享密钥。
- **真机 T01–T12**：特别是新增/切换/删除预设。不要把协议模拟测试代替真实固件验证。

## 不宜改动的行为边界

- LSPosed 作用域仅 `com.hihonor.audioaccessorymanager`；不要扩到 system_server 或 Bluetooth 主进程。
- EQ / AI 键危险写入保留单次写入 + 读回确认；失败后不自动重试。
- 对原厂无限加载先看 iConnect `modelId is empty`，必要时重新配对；不要大规模启用系统组件或清空蓝牙数据。

## 交接顺序

1. GitHub Actions 运行 `Beta CI`，取得可安装 Debug APK。
2. 在真机上执行 `FINAL_TEST_PLAN.md` 并填写结果，缺失测试时保持 Beta/RC 标记。
3. 设置 GitHub Secrets，使用 `Signed Beta Release` 生成固定签名 APK。
4. 核对签名证书和 APK SHA256，并发布带有真实测试信息的 GitHub Pre-release。
