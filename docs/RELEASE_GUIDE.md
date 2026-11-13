# v1.1.0-beta.3 发布与签名

## 当前版本

| 项目 | 值 |
|---|---|
| applicationId | dev.muuko.honorfreeclip2 |
| versionCode | 22 |
| versionName | 1.1.0-beta.3 |
| 新标签 | v1.1.0-beta.3 |
| 已有 beta.2 标签 | 21-1.1.0-beta.2（保留） |

版本的唯一构建来源为 `app/build.gradle`；脚本通过 `scripts/release_metadata.py` 读取。
本次补丁修改了运行时代码并且 beta.2 已存在，因此递增版本和 versionCode。

## 不使用签名密钥的准备

```sh
bash tests/run_host_tests.sh
gradle :app:assembleVerification :app:testVerificationUnitTest :app:lintVerification --no-daemon
# 提交已审查源码后：
bash scripts/prepare_dist.sh verification
```

得到未签名 APK、与提交一致的完整源码 ZIP、许可文件和 SHA256。
未签名验证包不能直接安装。不要把该包改名为 signed/release 后上传。
`git archive` 只包含当前提交；打包脚本拒绝未提交的源码变更。

## 固定签名

继续使用公开 beta.2 对应的原 JKS。已核验的公开证书 SHA-256：

```text
aa55a9ddd7e11aef30dc4c07d595a2f165cfbbbcff1affa3109c946794d64874
```

它是公开证书指纹，不是私钥。不要生成新发布密钥覆盖它。
私钥和密码由维护者保管，不放入源码、issue、对话或构建产物。
本地准备 `HF2_KEYSTORE_PATH`、`HF2_KEYSTORE_PASSWORD`、`HF2_KEY_ALIAS`、
`HF2_KEY_PASSWORD` 四个环境变量后运行：

```sh
bash scripts/build_release_local.sh
```

脚本运行测试、构建 Release、核对包名/版本/非调试配置和旧证书，随后打包。
缺少签名变量的 `assembleRelease` 仍会被拒绝；`assembleVerification` 独立可用。

## GitHub Actions 与草稿预发布

1. 审查 PR 并确认本次源代码、自动化测试结果和真机待验项，再合入 main。
2. 在 GitHub 配置 `release-signing` Environment，添加维护者审批保护。签名 secrets 可以
   放在该 Environment，或继续使用已有同名 Repository secrets：
   `HF2_KEYSTORE_BASE64`、`HF2_KEYSTORE_PASSWORD`、`HF2_KEY_ALIAS`、`HF2_KEY_PASSWORD`。
   不需要把原有密钥复制给自动开发助手。
3. 维护者确认使用原签名后，手动运行 Signed Beta Release，勾选 `confirm_signing`。
   工作流仅接受 main；先测试，再读取密钥；退出时清理临时密钥。
4. 默认创建 **Draft + Pre-release**，附签名 APK、完整源码 ZIP、许可/来源及 SHA256。
   不存在 push/tag 自动发布，也不会改动旧 beta.2。
5. 检查签名/附件并填写 `FINAL_TEST_PLAN.md`。维护者另行确认正式公开后，
   在 GitHub 将草稿发布，保留 Pre-release 勾选。

CI 中的 Actions 固定到完整提交 SHA，使用最小权限。普通 CI 不读取签名 secrets。
未通过真机的跨版本 Hook、真实蓝牙恢复及三槽位操作在 Release Notes 中保持待验证。
