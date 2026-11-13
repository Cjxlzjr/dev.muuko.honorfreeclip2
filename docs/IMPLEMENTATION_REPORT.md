# v1.1.0-beta.3 交接说明

运行时改动集中于 Hook、布局识别、通信队列及状态失效处理；不调整卡片、弹窗或图标布局。

- `HonorFreeClip2Module` / `LifecycleHookResolver`：解析生命周期继承关系，Hook 去重，原方法先执行，
  posted 注入捕获扩展异常；框架生命周期 Hook 仅在荣耀音频管理进程且通过页面检查后工作。
- `HomeLayoutResolver` / 两个 Injector：保留已知绑定；字段改名时匹配唯一原厂滚动容器；
  无安全插入点时保留原厂页面，不复制/替换原厂点击事件。
- `CompatibilityDiagnostics`：记录系统/原厂 APK 版本、Hook 注册及布局结果，
  限制去重集合大小，不输出设备地址/名称或 Intent 内容。
- `ControlCoordinator`：暂停后旧状态只读，恢复时重新同步；断连清空缓存和代次；失败回读禁用未知值。
- `FreeClipTransport` / `TransportTaskRunner`：保持单次写入和回读验证，
  增加普通设置预读、有界队列、请求期限、单次回调及复用的建链执行器；
  只对已知 SPP UUID 发协议，不探测任意 SDP 服务。
- `FreeClipProtocol` / `EqualizerCodec`：修复异常 TLV 与重复槽位接受问题。
- 构建：versionCode 22 / beta.3，保留原签名要求，增加不读密钥的 verification 构建；
  Release 校验公开 beta.2 证书指纹，只能手动生成草稿。

实际测试结果见 `VALIDATION_REPORT.md`，真机验收清单见根目录 `FINAL_TEST_PLAN.md`。
运行时代码及回包模拟测试通过不替代真实 LSPosed Hook 和固件验收。
