# FreeClip 2 EQ independent write evidence

用户提供的 `base.apk` 有 17 个 DEX。静态跟踪结果：

- `classes14.dex`: `Lsg/d;->setEqAdjust(Ljava/lang/String;ILjava/lang/String;[BI)V` 获取 UTF-8 名称字节，向 `Lq9/b;->a1(Ljava/lang/String;II[B[BB)V` 传入地址、预设 ID、段数 10、名称字节、增益字节和操作参数（官方设置链为 `1`）。
- `classes11.dex`: `Lq9/b;->a1(...)V` 调用 `Lcom/huawei/audiodevicekit/audiobluetooth/layer/data/mbb/d;->b4(BB[B[BB)[B` 创建 MBB 数据帧，然后调用 `Lq9/b;->I0(Ljava/lang/String;[B)V` 发送。
- `mbb/d.b4` 的字节拼装顺序恢复为：`2B 49 01 01 <ID> 02 01 0A 05 01 01 03 0A <10 signed gains> 04 <UTF8 length> <UTF8 bytes>`；外层 `5A 00 <len16>` 加 `CRC16-XMODEM`。
- `DirectEqualizerCodec.encode` 与 HuaweiPods 的通用 `buildCustomPacket(..., operationValue=1)` 在字节结构上相同。

证据边界：**静态调用链及帧编码有依据，但单靠 APK 静态分析无法证明当前 FreeClip 2 固件接受直连写入；此外不能排除官方内部还需要先建立会话、设置特定状态或通过路由控制连接。** 因此 V0.8 采用保守的单频段试验写入、写入前重新回读及写入后严格校验。
