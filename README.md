# Pokedex · 全世代宝可梦图鉴识别机

> 本项目基于 **[zihang-huang (Zihang Huang)](https://github.com/zihang-huang)** 原创开源的 **[zihang-huang/iDex](https://github.com/zihang-huang/iDex)**（iOS SwiftUI 版，MIT License）进行原生 Android 移植与全世代功能扩展。
> 使用 **Kotlin + Jetpack Compose** 构建，内置 100% 离线端侧 AI 视觉识别（ViT-1025 ONNX）、全世代 1025 宝可梦图鉴、第一代动画经典机身外观与动画风格声线 DSP。

---

## 🌟 核心特性与扩展

### 1. 全世代 1~1025 号宝可梦全量支持（第 1 至第 9 世代）
* **全世代名录与数据**：收录全国图鉴 `#0001 妙蛙种子` 到 `#1025 桃歹郎` 全部资料，包含中/英/日三语官方译名、属性、身高、体重及六维种族值。
* **纯正简体中文图鉴说明**：基于 PokéAPI 与 52Poké（神奇宝贝百科）数据源清洗整理，全世代 1025 只宝可梦均提供标准简体中文图鉴介绍。
* **分世代检索与筛选**：支持中、英、日、图鉴编号模糊搜索，支持按「第 1 世代 ~ 第 9 世代」分类筛选及「仅显示已发现」过滤。

### 2. 端侧离线全世代 AI 视觉识别（ViT-1025 ONNX Runtime）
* **1025 类视觉神经网络**：基于 [`skshmjn/Pokemon-classifier-gen9-1025`](https://huggingface.co/skshmjn/Pokemon-classifier-gen9-1025)（Apache-2.0）导出的全世代 1025 分类 Vision Transformer 模型。
* **移动端 INT8 动态量化**：通过 `scripts/model/export_1025_onnx.py` 导出并量化为 **~84 MB**（`national_classifier.onnx`），在移动设备上离线完成预处理与推理。
* **100% 离线与隐私安全**：所有图像识别与预处理均在手机本地执行，无需联网，图像不会离开设备。
* **周期性预览识别与手动/相册扫描**：开启传感器后后台周期性抽帧识别取景框内容，亦支持手动点击扫描或从系统相册导入图片识别。

### 3. 官方全量资产内置
* **1025 首叫声音频**：内置全世代 1025 份叫声音频（`assets/cries/`，源自 PokéAPI/cries）。
* **1025 张像素精灵图**：内置全世代 1025 张像素精灵图（`assets/sprites/`）。

### 4. 动画风格图鉴声线处理（RoboVoice DSP）
* **纯 Kotlin 离线 DSP 近似**：在系统 TTS 合成语音基础上，依次经过带通滤波（模拟小喇叭频段）→ 环形调制（金属微颤感）→ 梳状滤波（外壳共鸣腔）→ 软饱和 → 归一化，离线近似动画图鉴低沉且略带机械感的播报效果。
* **三档音色预设**：支持在设置中一键切换「动画图鉴」、「机械音」与「自然原声」并支持即时试听（建议配合系统内的 Google 语音服务男声使用以获得更佳听感）。

### 5. 第一代动画经典红壳外观与交互
* **标志性工业造型**：顶部阶梯式轮廓、大蓝镜头、红黄绿三色指示灯、白色边框屏幕、扬声器格栅与黑色十字键。
* **独立电源开关**：启动 App 默认关闭相机（取景器显示深色 LCD 与当前精灵剪影），按电源键后才启动摄像头取景。
* **独立叫声按钮（CRY）**：配备独立叫声播放按键，点击精灵像素图亦可播放专属叫声。
* **右半屏 2×5 蓝色经典键盘**：提供叫声、朗读、停止、资料、能力值、翻页、目录及设置等功能按键。

### 6. 折叠屏与多形态屏幕自适应
* **双形态响应式布局**：
  * **大屏 / 折叠屏展开态**：呈现左右双栏（左侧取景器 + 中间铰链 + 右侧数据屏与键盘）；
  * **普通手机 / 外屏态**：呈现单栏紧凑形态，右侧数据面板支持滑入滑出切换。
* **状态延续**：支持配置变更不重建 Activity，保持当前选中条目与浏览状态。

---

## 📦 下载安装（APK）

前往本仓库的 **[GitHub Releases](../../releases)** 页面即可直接下载预编译的安装包 `Pokedex-v1.0.0.apk` 进行本地侧载安装（支持 Android 8.0 / API 26 及以上设备，包含 `arm64-v8a` / `armeabi-v7a` / `x86_64` ABI）。

> **签名说明**：默认公开分发的 `v1.0.0` APK 为本地侧载构建版（使用 Debug Key 签名，便于直接安装体验）；如需使用正式发布签名，请在本地构建时通过环境变量提供 `IDEX_KEYSTORE_FILE`、`IDEX_KEYSTORE_PASSWORD`、`IDEX_KEY_ALIAS` 与 `IDEX_KEY_PASSWORD`。

---

## 📂 项目工程目录结构

```text
Pokedex/
├── LICENSE                                   # MIT 开源许可证（含原作者与贡献者声明）
├── NOTICE.md                                 # 第三方模型、数据、字体与音频归属声明
├── README.md
├── app/
│   ├── build.gradle.kts                      # 应用级构建配置（CameraX, ONNX Runtime Android 等）
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml           # 清单文件（相机权限、TTS queries、自适应配置）
│       │   ├── assets/
│       │   │   ├── national_classifier.onnx  # 1025 类 INT8 视觉识别 ONNX 模型
│       │   │   ├── pokemon-national.json     # 1025 只宝可梦三语数据与图鉴文本
│       │   │   ├── pokemon_labels.json       # 1025 类分类标签映射表
│       │   │   ├── cries/                    # 1025 首叫声音频
│       │   │   ├── sprites/                  # 1025 张精灵像素图
│       │   │   └── licenses/                 # 字体、音频许可证与归属文档
│       │   └── java/com/idex/android/
│       │       ├── MainActivity.kt           # 入口 Activity 与折叠屏铰链监听
│       │       ├── DexViewModel.kt           # 状态管理、扫描仲裁与生命周期协同
│       │       ├── audio/
│       │       │   ├── DexAudio.kt           # 音效、叫声与 TTS 朗读管道
│       │       │   └── RoboVoice.kt          # 动画图鉴声线 DSP 变声与 WAV 解析器
│       │       ├── camera/
│       │       │   └── CameraController.kt   # 生命周期安全的 CameraX 取景器适配器
│       │       ├── data/
│       │       │   └── Catalog.kt            # 宝可梦实体、目录检索与多语言过滤
│       │       ├── recognition/
│       │       │   ├── PokemonClassifier.kt  # ONNX Runtime 推理引擎与纯函数评分策略
│       │       │   └── ScanImagePreparer.kt  # 图像裁剪、EXIF 纠偏与像素预算预处理
│       │       └── ui/
│       │           ├── DexApp.kt             # 双栏/单栏响应式自适应布局
│       │           ├── LeftDexHalf.kt        # 左半屏（红壳镜头、取景框与电源键）
│       │           ├── RightDexHalf.kt       # 右半屏（数据显示屏、2x5 蓝键盘、状态栏）
│       │           ├── DexComponents.kt      # 像素文字、平涂按钮、铰链与资产解码组件
│       │           └── Sheets.kt             # 图鉴列表（世代/已发现筛选）与设置弹窗
│       └── test/java/com/idex/android/       # JVM 单元测试（CatalogTest, RoboVoiceTest, PokemonScoringTest）
├── scripts/
│   ├── data/
│   │   ├── build_catalog.py                  # 初始数据抓取脚本
│   │   └── patch_chinese_descriptions.py     # 52Poké 简体中文图鉴介绍清洗脚本
│   ├── model/
│   │   └── export_1025_onnx.py               # ViT-1025 模型导出与 INT8 动态量化脚本
│   └── verify_all.py                         # 全量数据、资产与模型审查脚本
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🛠️ 本地构建与测试

* **JDK 版本**：Java 17（推荐 OpenJDK 17）
* **Android SDK**：API Level 35（Android 15），最低支持 API Level 26（Android 8.0）
* **构建命令**：
  ```sh
  # 运行全部 JVM 单元测试
  ./gradlew testDebugUnitTest

  # 编译生成 Release APK（如需使用正式签名，可通过环境变量配置 IDEX_KEYSTORE_FILE 等）
  ./gradlew assembleRelease
  ```
  产物路径：`app/build/outputs/apk/release/app-release.apk`。

---

## 🙏 致谢、许可证与版权声明

1. **致谢原作者**：本项目衷心感谢 **[Zihang Huang (zihang-huang)](https://github.com/zihang-huang)** 创造了优秀的原版 **[iDex (iOS)](https://github.com/zihang-huang/iDex)** 开源项目（MIT License）。Android 版的交互设计、硬件拟物风格与芯片音效设计均源于原版项目。
2. **开源许可**：本仓库应用源码采用 **[MIT License](LICENSE)** 开源；内置第三方模型、数据集、音频与字体的完整许可及归属说明见 **[NOTICE.md](NOTICE.md)** 与 [`ATTRIBUTION.md`](app/src/main/assets/licenses/ATTRIBUTION.md)。
3. **宝可梦知识产权声明**：Pokémon（宝可梦）及相关角色名称、图像、精灵图与叫声均为 **Nintendo / Creatures Inc. / GAME FREAK inc. / The Pokémon Company** 的注册商标与版权所有。本项目为非商业、非官方粉丝开源学习项目，与上述公司无任何关联或授权背书。
