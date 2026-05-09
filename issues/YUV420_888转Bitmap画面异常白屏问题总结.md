# YUV_420_888 转 Bitmap 画面异常及白屏问题总结

## 问题现象

熄屏检测修复后出现三个画面问题，按修复顺序：

1. **画面逆时针旋转 90 度** — 预览画面方向不对
2. **颜色异常** — 紫色/绿色通道错乱，明显 YUV 通道取错
3. **白屏崩溃** — Camera 启动后预览完全不显示，logcat 报 `[BITMAP] conversion failed: Bad position 1928408/1928407`

## 根因分析

三个问题全部集中在 `imageProxyToBitmap()` 的 YUV_420_888 → NV21 → JPEG → Bitmap 转换过程。

### 问题 1：画面旋转 90°

**根因**：后置相机传感器硬件方向为横屏。`ImageProxy.imageInfo.rotationDegrees` 记录了传感器坐标到屏幕坐标的旋转角度（通常为 90°），但转换代码完全忽略了该字段，直接将传感器原始帧转为 Bitmap，导致画面方向不对。

### 问题 2：颜色异常

**根因**：YUV_420_888 的 U/V chroma plane 的 `pixelStride` 在不同设备上可能为 1（planar，连续排列）或 2（semi-planar，交错排列）。原代码假设 chroma 数据是连续排列的（pixelStride=1），逐字节读取 U 和 V：

```kotlin
// pixelStride=2 时，内存布局为 U0, X, U1, X, U2, X, ... (X=填充字节)
uBuffer.get()  → U0 (正确)
uBuffer.get()  → X  (填充字节被当成颜色数据) ← 颜色错误
vBuffer.get()  → V0 (正确)
vBuffer.get()  → X  (填充字节被当成颜色数据) ← 颜色错误
```

每次读取都错位，chroma 数据一半是有效一半是填充垃圾，导致 NV21 的色度数据完全错误，画面呈现紫/绿色。

### 问题 3：白屏崩溃

**根因**：修复颜色问题时，采用了混合 `position() + get() + position(skip)` 的方案来跳过 pixelStride 填充字节：

```kotlin
if (uPixelStride > 1) uBuffer.position(uBuffer.position() + uPixelStride - 1)
```

目的是每次 `get()` 后手动将 buffer position 再跳过 `pixelStride - 1` 个字节。但问题在于 `rowStride`、`pixelStride`、`width` 三者的乘积关系因设备而异，在 chroma plane 最后一行的最后一列，`position(skip)` 算出的值恰好比 buffer limit 大 1：

```
requested position = 1928408, buffer limit = 1928407
```

详见 `issues/熄屏后运动检测不亮屏问题总结.md` 中描述的根因深挖过程。

## 解决方案

### 核心思路

将 chroma 数据读取从 **有状态的 position 操作** 改为 **无状态的绝对索引 `get(index)`**，一步直达目标位置，完全不依赖 buffer 内部 position 指针。

### 修改内容

**`DoorbellService.kt:imageProxyToBitmap()`  — chroma 读取改用绝对索引**

修改前（混合 position + get + skip position，易越界）：

```kotlin
for (row in 0 until uvHeight) {
    uBuffer.position(row * uRowStride)
    vBuffer.position(row * vRowStride)
    for (col in 0 until width / 2) {
        nv21[uvPos++] = vBuffer.get()
        nv21[uvPos++] = uBuffer.get()
        if (uPixelStride > 1) uBuffer.position(uBuffer.position() + uPixelStride - 1)
        if (vPixelStride > 1) vBuffer.position(vBuffer.position() + vPixelStride - 1)
    }
}
```

修改后（绝对索引 stateless 读取）：

```kotlin
for (row in 0 until uvHeight) {
    for (col in 0 until width / 2) {
        nv21[uvPos++] = vBuffer.get(row * vRowStride + col * vPixelStride)
        nv21[uvPos++] = uBuffer.get(row * uRowStride + col * uPixelStride)
    }
}
```

`get(int index)` 不改变 buffer 的 position/limit 状态，直接通过 offset 计算目标地址。无论 `pixelStride=1` 还是 `pixelStride=2`，`row * rowStride + col * pixelStride` 都能精确定位到第 `row` 行第 `col` 个 chroma 样本。

**`DoorbellService.kt:imageProxyToBitmap()`  — 增加旋转处理**

```kotlin
val rotationDegrees = imageProxy.imageInfo.rotationDegrees
// ... 生成 bitmap ...
if (rotationDegrees != 0) {
    val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
```

### 完整修复后的数据流

```
ImageProxy (YUV_420_888, 传感器方向)
  └→ imageProxyToBitmap:
       1. 读取 rotationDegrees (e.g. 90°)
       2. Y plane: position() + bulk get() 逐行拷贝
       3. U/V planes: absolute get(index) 按 pixelStride 取样 → NV21
       4. YuvImage → JPEG → BitmapFactory.decode
       5. Matrix.postRotate(rotationDegrees) 校正方向
  └→ Bitmap (正确方向和颜色)
       └→ _previewBitmap.value = bitmap
            └→ UI Image 组件显示
```

### 关键经验

| 问题 | 错误方法 | 正确方法 |
|---|---|---|
| 画面旋转 | 忽略 `rotationDegrees` | `Bitmap.createBitmap` + `Matrix.postRotate` |
| 颜色错误 | 假设 `pixelStride=1` 连续读取 | 使用 `get(row * rowStride + col * pixelStride)` |
| 白屏崩溃 | `position(get()) + position(skip)` 混合操作 | 绝对索引 `get(index)`，不操作 buffer position |

**规避原则**：在 YUV_420_888 这类格式复杂、设备差异大的场景下，对 `ByteBuffer` 应优先使用绝对索引 `get(index)` 而非有状态 `position() + get()`，避免因 `rowStride` / `pixelStride` 组合变化导致的越界问题。
