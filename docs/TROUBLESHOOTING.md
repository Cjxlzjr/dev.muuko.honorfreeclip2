# 故障排查：FreeClip 2 设置页一直显示加载

## 已实际观察的故障

在 MagicOS 11.0.0.117 上，出现原厂耳机配置页加载失败时，日志曾同时出现：

```text
AudioAccessoryManager:EarphoneDetailsViewModel: --loadDevice: device not available
iconnect:IconnectSdkImpl: getDeviceInfo info is null
iconnect:IconnectSdkImpl: getModelId deviceManagerInfo is null
AudioAccessoryManager:BaseActivity: java.lang.IllegalArgumentException: modelId is empty
```

原厂 `MenuSettingService` 此时已启动，但 iConnect 无法提供完整设备管理信息。禁用本 LSPosed 模块后仍然复现；**将耳机重新配对后，用户反馈已恢复正常**。这是已观察的恢复方法，不意味着所有加载问题都由配对记录引起。

## 建议排查顺序

1. 先在 LSPosed 中临时禁用 HonorFreeClip2，确认原厂配置页是否也卡住，以区分模块 UI 和系统设备记录。
2. 在系统蓝牙页确认耳机蓝牙音频是否已连接，并尝试断开重连、放回盒内再取出。
3. 若 iConnect 的设备管理信息仍为空，记录现有设备设置信息后**取消配对并重新配对**；之前一例通过此操作恢复。
4. 如果还是失败，再检查 `com.hihonor.audioaccessorymanager`、`com.hihonor.iconnect`、`com.hihonor.nearby` 是否启用、是否抛出 ServiceConnection 或 Binder 异常。

## 只读日志抓取

在 Root Shell 中：

```sh
logcat -d -v threadtime | grep -Ei 'AudioAccessoryManager|iconnect:|MenuSettingService|modelId is empty|DeviceProfilesSettings' | tail -n 240 > /sdcard/Download/hf2_device_issue.txt
```

请勿为处理此问题随意启用全部系统服务，也不要清除 Android 蓝牙或 iConnect 数据。重配对会重建部分耳机相关的配对/可信关系，先记录现有配置。


## AI 键设置仍显示协议调试信息

从 `1.1.0-beta.2` 起，AI 键设置页仅显示左右触控区、系统语音助手入口及“刷新状态”，不再显示原始字段或复制探测数据。

如安装新版后仍看到旧页面，请在 LSPosed 中确认模块版本和作用域，然后完全结束 `com.hihonor.audioaccessorymanager`，重新进入耳机设置页；必要时重启手机以重新注入模块。

在 Termux 中使用 `1.1.0-beta.2` 源码构建的新 APK 应显示 `versionCode=21`。
