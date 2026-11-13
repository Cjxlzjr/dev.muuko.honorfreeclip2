# 来源及第三方许可

本项目是社区开发的非官方 FreeClip 2 兼容性扩展，采用 GNU GPL v3.0-or-later（见 `LICENSE`）。

参考/派生协议研究：
- HuaweiPods by Nshpiter: https://github.com/Nshpiter/HuaweiPods （GPL-3.0；保留上游署名）。
- OpenFreebuds by melianmiko: https://github.com/melianmiko/OpenFreebuds — 用于协议字段与自定义 EQ 三槽位操作的交叉参考；未在此工程中捆绑其资源。

本项目所有 `hf2_*.png` 图标来自 `assets/icons/` 内本项目原创 SVG 的离线渲染；旧版从厂商 APK 提取的图标均未包含在本发布包中。详见 `ASSETS_NOTICE.md`。

HUAWEI、HONOR 和 FreeClip 等商标归各自权利人所有，本项目不隶属于或代表相关厂商。

构建依赖：libxposed API 101.0.1 仅作为 compileOnly 接口，不捆绑框架实现；由 LSPosed 运行时提供。
JUnit / Robolectric 和 tests/android_stubs 仅用于自动化测试，不随发布 APK 分发。
