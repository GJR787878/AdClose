# AdClose

> 基于 Xposed/LSPosed 的 Android 广告屏蔽模块，阻止应用内广告 SDK 初始化并拦截广告网络请求。

## 0. 项目快照

| 项 | 值 |
|---|---|
| 类型 | Xposed / LSPosed 模块 |
| 语言 | Kotlin + C/C++（shadowhook / nghttp2） |
| 包名 | `com.close.hook.ads` |
| 版本 | `4.3.7`（versionCode 按日期自动生成） |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| ABI | armeabi-v7a、arm64-v8a |
| UI 语言 | 中文（简体/繁体）、英语、土耳其语 |
| 更新来源 | 本仓库 Releases + LSPosed 模块仓库 + `latest_version.txt` |

## 1. 主要特性

- **精准处理**：阻止应用内广告 SDK 初始化加载。
- **广告请求拦截**：拦截屏蔽应用的网络广告请求（含 HTTP/2 原生层拦截）。
- **截图录屏限制移除**：允许在应用中自由截图和录屏。
- **VPN 与代理检测移除**：去除应用内的 VPN 和系统代理检测。
- **传感器监听移除**：禁用摇一摇等基于传感器的广告跳转。
- **Root 检测规避**：去除应用内的一般性 Root、Magisk 和 Xposed 框架检测。
- **订阅规则支持**：支持订阅源与自定义规则导入。

## 2. 支持的应用范围

包括但不限于：视频播放、阅读新闻、工具便捷、商务办公、社交通讯等应用。请在 LSPosed 框架环境中使用。

## 3. 使用指南

1. 设备安装并激活 LSPosed 框架。
2. 下载并安装 AdClose 模块。
3. 在 LSPosed 模块管理器中激活本模块。
4. 在模块作用域中选择需要去除广告的应用。
5. 启动模块，在应用列表中选择并启用对应的 Hook 功能。

## 4. 构建与 CI

- 工程基于 Gradle Kotlin DSL，本地与 CI 均可构建：
  `./gradlew :app:assembleRelease`
- 含 C++ 原生层，需 Android SDK 的 NDK/CMake（`ndkVersion 28.2.13676358`）。
- CI（`.github/workflows/android.yml`）：
  - `push` / `pull_request`：仅**验证构建**。
  - `workflow_dispatch`（手动）：构建产物并发布 pre-release，同时更新 `latest_version.txt`。
  - 发布需在仓库 Actions 页**手动触发**，不会自动发布。

### 签名（重要）

- 签名密钥**不落仓库**，仅通过 CI secrets 注入：
  `SIGN_KEYSTORE_BASE64` / `SIGN_KEYSTORE_PASSWORD` / `SIGN_KEY_ALIAS` / `SIGN_KEY_PASSWORD`。
- 本地无 secret 时回退到 debug 签名，仅用于本地调试；**对外发布产物一律由 CI 用 release 签名**。
- 仓库内**不含任何 keystore 文件或明文口令**（见 `.gitignore` 的 keystore 忽略项）。

## 5. 升级与更新

- 版本文件 `latest_version.txt`（仓库根，内容即版本号）在每次手动发布时由 CI 自动更新。
- 升级通道：本仓库 Releases / LSPosed 模块仓库 / 版本文件检测，多源取最大版本号。
- 若安装提示「签名不一致」或「重新安装」：旧版为上一套签名，需先卸载旧版再安装本轮重签名产物。

## 6. 贡献与支持

- 改进建议或 Bug 请通过 Issues 反馈。
- 若 AdClose 对你有帮助，欢迎 Star 支持。
- 更新与帮助请关注 Telegram 频道：**@AdClose**

## 7. 发布核对清单

- [ ] `versionName` / `versionCode` 已递增
- [ ] 手动触发 CI，产物构建成功且验签指纹匹配
- [ ] 本仓库 `latest_version.txt` == 新版本号
- [ ] 分发/模块仓库 `releases/latest` 已同步新 tag
- [ ] 发布正文 / 变更说明已写好
