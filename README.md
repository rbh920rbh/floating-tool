# Floating Tool

Android 测试工程：半透明可拖动悬浮框，长按可打开菜单，用于添加快捷方式或选择小组件。后续可在此基础上扩展核心能力。

仓库：[rbh920rbh/floating-tool](https://github.com/rbh920rbh/floating-tool)

## 功能（当前版本）

- 主界面申请「在其他应用上层显示」权限
- 前台服务保持半透明悬浮面板（可拖动）
- 长按悬浮框：应用快捷方式、Android 小组件、关闭面板

## 本地开发

需要 JDK 17 与 Android SDK。首次构建前在仓库根目录创建 `key.properties`（或使用 CI 同款密钥）：

```properties
storePassword=floating-tool-sideload
keyPassword=floating-tool-sideload
keyAlias=floatingtool
storeFile=../ci/android/floating-tool-sideload.jks
```

将 `app/build.gradle.kts` 中 `storeFile` 路径按你的 `key.properties` 位置调整，或把 keystore 复制到 `app/` 目录。

```powershell
cd floating-tool
gradle :app:assembleRelease
```

安装包输出：`app/build/outputs/apk/release/app-release.apk`

## 覆盖安装（Overwrite）

CI 与本地 release 包使用固定 `applicationId`（`com.rbh920rbh.floatingtool`）和仓库内 sideload 密钥 `ci/android/floating-tool-sideload.jks` 签名，因此：

1. **同一签名**的后续版本可直接覆盖安装，无需卸载
2. 每次发布请 **递增** `app/build.gradle.kts` 中的 `versionCode`
3. 若手机上曾安装过 **其他签名** 的同包名 APK，需先卸载一次再安装 CI 构建的包

> `ci/android/floating-tool-sideload.jks` 仅用于个人 sideload / CI 产物，不是应用商店发布密钥。

## CI 构建

推送到 `main` 分支（或手动 `workflow_dispatch`）时，GitHub Actions 会：

1. 使用固定 sideload 密钥签名 release APK
2. 上传 artifact：`floating-tool-release-apk`

在 GitHub 仓库 **Actions** 页打开对应 workflow run，在 **Artifacts** 中下载 APK。

## 项目结构

- `app/src/main/java/com/rbh920rbh/floatingtool/MainActivity.kt` — 权限与启停悬浮框
- `app/src/main/java/com/rbh920rbh/floatingtool/FloatingOverlayService.kt` — 悬浮窗与长按菜单
- `.github/workflows/android-build.yml` — CI 构建

## 后续扩展建议

- 在悬浮框内展示已固定的快捷方式 / 小组件缩略图
- 使用 `ShortcutManager.requestPinShortcut` 固定应用内定义的快捷方式
- 持久化面板位置与尺寸
