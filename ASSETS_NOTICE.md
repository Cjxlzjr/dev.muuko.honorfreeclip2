# 图标及资源来源（公开发布版）

`app/src/main/res/drawable-nodpi/hf2_*.png` 全部 **15 张**已替换为本项目独立绘制的图标。

- 可编辑源：`assets/icons/hf2_*.svg`，与 Android PNG 使用相同名称。
- 生成脚本：`tools/generate_icons.py`（需要 Python、Pillow、CairoSVG；发布时不需要这些依赖）。
- 所有图案和几何路径从基础图形重新绘制，不复制华为 APK 中的矢量路径和位图像素。
- 保留青绿/蓝/粉色系、圆形背景以及选中/未选中状态的语义，便于原界面无布局变更直接使用。
- 这 15 张新绘图作为工程源码的一部分以 GPL-3.0 发布。

**说明：** 兼容性名称 “HUAWEI FreeClip 2” 指明适配设备，不表明与华为存在官方合作、认证或授权。
