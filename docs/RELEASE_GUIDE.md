# v1.1.0-beta.2 发布与签名指南

## 版本约定

- `applicationId`: `dev.muuko.honorfreeclip2`（不修改；Android 更新依赖它）
- `versionCode`: `21`
- `versionName`: `1.1.0-beta.2`
- 推荐 Release 标签：`21-1.1.0-beta.2`
- 发布文件名：`HonorFreeClip2-v1.1.0-beta.2-release.apk`

## GitHub Actions

1. 上传**完整**源码，确保 `.github/workflows/build-apk.yml` 在 push/PR 上先运行 JVM/静态检查并构建 Debug APK。
2. 在仓库 `Settings → Secrets and variables → Actions` 新增四个 Repository secrets：
   - `HF2_KEYSTORE_BASE64`: 固定 JKS 文件的 base64 文本，不包含换行。
   - `HF2_KEYSTORE_PASSWORD`: JKS 存储密码。
   - `HF2_KEY_ALIAS`: 私钥 alias。
   - `HF2_KEY_PASSWORD`: 私钥密码。
3. `Signed Beta Release` Workflow 可通过 `workflow_dispatch` 手动运行。该工作流只生成签名构建产物，不自动创建 Release。先完成真机验收并核对产物，再手动创建标签 `21-1.1.0-beta.2` 和 Pre-release。
4. 工作流验证 APK 签名、生成 SHA256、上传签名 APK；私钥不会加入 artifact 和源码 ZIP。

**重要：** 不要在 issue、README、构建日志或 ZIP 中上传 JKS 和密码。请离线安全备份私钥。如果更换签名，已安装旧版不能直接覆盖更新。

## 本地签名构建（Linux/macOS）

需要 Android SDK 35、Build Tools 35.0.0、JDK 17 和 Gradle 8.11.1：

```sh
export HF2_KEYSTORE_PATH="$HOME/keys/hf2-release.jks"
export HF2_KEYSTORE_PASSWORD='你的存储密码'
export HF2_KEY_ALIAS='hf2'
export HF2_KEY_PASSWORD='你的私钥密码'
bash scripts/build_release_local.sh
```

产物位于 `dist/`；执行 `apksigner verify` 并生成 SHA-256。未准备固定密钥和最终真机测试结果时先不要发布。

## 构建与验收顺序

1. `python3 tests/check_release.py`：检查 15 对新图标、版本、作用域、文案和签名配置。
2. `bash tests/run_host_tests.sh`：检查协议逻辑与模拟传输。
3. Android Debug APK 构建 / 设备安装冒烟测试。
4. `FINAL_TEST_PLAN.md` 所列真机回归并填写实测结论。
5. 使用固定签名生成 Release APK 并验证 SHA256。
6. 上传 `LICENSE`、源码 ZIP、APK、Release Notes；将标签标为 **Pre-release**。

## GitHub Release 最低要求

- Release APK 已签名，开发者可以证明签名证书保持不变。
- 有源代码、GPL-3.0 许可和上游署名；不包含旧厂商图标。
- 已列清设备、MagicOS、耳机固件、LSPosed 版本及所有未验证项目。
- 首发 Release 使用 Pre-release。对其他机型/固件不承诺兼容。
