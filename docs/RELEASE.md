# 发布与更新说明

本文档说明「娅娅课表」如何发布一个新版本，以及更新功能涉及的关键约束。
客户端只在应用内通过 **GitHub Releases** 检查更新，不需要用户安装 GitHub App，也不需要自建更新服务器。

## 1. 版本号规则（必须遵守）

| 项目 | 位置 | 说明 |
| --- | --- | --- |
| versionName | `gradle.properties` → `timetable.versionName` | 形如 `3.0.0`，每段 0–99（3.x 起为正式版） |
| versionCode | 构建脚本自动推导 | `major*10000 + minor*100 + patch`，3.0.0 → 30000 |
| Release Tag | GitHub | 必须写成 `v3.0.0`，与 versionName 完全一致 |
| applicationId | `app/build.gradle.kts` | **永远不要改**，否则系统视为另一个应用，无法覆盖安装 |

发版时：

1. 修改 `gradle.properties` 中的 `timetable.versionName`（例如 `1.0.1`）；
2. 在 `docs/CHANGELOG.md` 顶部追加 `## [1.0.1] - 日期` 段落（工作流会把这段作为更新说明写进 Release 与 `version.json`）；
3. 提交后打 Tag 并推送：

   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```

工作流会校验：Tag ↔ gradle.properties ↔ 构建产物三者的 versionName/versionCode 完全一致，
并且 versionCode 必须大于上一个 Tag。任何一项不一致都会直接失败，不会产出 Release。

## 2. 首次配置（只需做一次）

### 2.1 生成正式签名密钥

**只能有一个正式签名密钥**，且必须长期保存。换密钥等于换应用身份：已安装用户无法覆盖升级，
只能卸载重装（会丢数据）。如果项目已有正式签名密钥，直接使用它，不要重新生成。

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -alias timetable \
  -keyalg RSA -keysize 2048 -validity 10000
```

把 `release.jks` 与口令离线保存两份以上（例如密码管理器 + 离线硬盘）。

> 已上架/已分发过旧版本的项目，必须复用旧版本使用的密钥，否则老用户无法覆盖升级。

### 2.2 配置 GitHub Secrets

在仓库 **Settings → Secrets and variables → Actions** 新建以下 Repository secrets：

| Secret | 值 |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` 的输出（Windows：`certutil -encode release.jks tmp.b64` 后去掉首尾行） |
| `KEYSTORE_PASSWORD` | 密钥库口令 |
| `KEY_ALIAS` | 别名，例如 `timetable` |
| `KEY_PASSWORD` | 别名口令 |

`GITHUB_TOKEN` 由 Actions 自动提供，无需手动配置；工作流已声明 `permissions: contents: write`。

> 这些值只注入构建环境，不会进入客户端，也不会写进仓库。
> 如果四个 Secret 缺任意一个，Release 工作流会直接失败并提示缺哪个，不会退回 debug 签名。

### 2.3 需要确认的仓库设置

- Actions 允许运行（Settings → Actions → General → Allow all actions）。
- 若开启了 "Read and write permissions" 限制，需允许 workflow 创建 Release（当前工作流已用 `permissions` 显式声明）。
- Tag 保护：建议只允许维护者推 `v*` Tag。

## 3. 自动发布流程（.github/workflows/release.yml）

推送 `vX.Y.Z` Tag 后依次执行：

1. 校验 Tag 格式、版本一致性、versionCode 单调递增；
2. 从 Secrets 还原 `keystore/release.jks` 与 `keystore.properties`，打印证书指纹（公开信息，便于审计）；
3. `./gradlew :app:testDebugUnitTest`；
4. `./gradlew :app:assembleRelease -Ptimetable.versionName=… -Ptimetable.versionCode=…`；
5. 用 `aapt2` 校验产物的包名/versionCode/versionName，用 `apksigner` 校验签名；
6. 计算 APK 的 SHA-256；
7. 生成 `version.json`（客户端读取的版本元数据）：

   ```json
   {
     "versionCode": 10001,
     "versionName": "1.0.1",
     "tagName": "v1.0.1",
     "apkName": "yaya-timetable-v1.0.1.apk",
     "apkUrl": "https://github.com/XiaozhuangXzsg/Schedule/releases/download/v1.0.1/yaya-timetable-v1.0.1.apk",
     "sha256": "…64 位小写十六进制…",
     "notes": "…更新说明…"
   }
   ```

8. `gh release create` 创建 Release，上传 APK、`*.sha256` 与 `version.json`。

> `version.json` 是应用内更新的**必要条件**：缺了它，即使 APK 上传成功，客户端也不会把这个
> Release 当成可更新版本（会显示「当前已是最新版本」）。手工发版时务必一并上传。

客户端逻辑（`com.university.timetable.update`）：

- 读取 `https://api.github.com/repos/<owner>/<repo>/releases?per_page=30`；
- 过滤 draft / prerelease，要求 Tag 可解析为 `x.y.z` 且带 https 的 `.apk`；
- **要求 Release 同时附带 `version.json`**（工作流自动上传）。没有该文件的 Release 一律不参与比较：
  3.0.0 之前的历史发布用的是旧版本号算法（v2.2.1 = 221），按新算法 Tag 会算成 20201，
  若允许从 Tag 推导版本号，这些旧包会永远排在新版本前面并诱导用户下载更旧的 APK；
- 用 **versionCode** 比较，取大于当前版本的最大者；
- 优先读取 Release 附件 `version.json` 里的 SHA-256；没有时回退到 GitHub 资产的 `digest` 字段；
- 两者都没有 → 拒绝应用内更新（提示去发布页手动下载），绝不安装无哈希的包。

## 4. 发布下一个版本（示例）

```bash
# 1) 改版本名
#    gradle.properties: timetable.versionName=1.0.1
# 2) 写更新说明
#    docs/CHANGELOG.md 顶部加 ## [1.0.1] - 2026-11-20
git add gradle.properties docs/CHANGELOG.md
git commit -m "v1.0.1"
git push origin main
git tag v1.0.1
git push origin v1.0.1
```

之后在仓库 Actions 里能看到构建过程；成功后 Releases 页面会出现 `v1.0.1`，
客户端「设置 → 软件更新 → 检查更新」即可看到新版本。

## 5. 如何验证覆盖安装后用户数据还在

> 前提：新旧 APK 使用**同一个签名密钥**、**同一个 applicationId**，且 versionCode 递增。
> 满足这三条，Android 才会把它当作「升级」而不是「另一个应用」，数据库与私有目录才会原样保留。

手工验证步骤（模拟器或真机）：

1. 安装旧版本（例如 v2.2.1 或更早的 3.x），录入若干课程、待办、收藏、修改主题，记下数量；
2. 用旧版本安装新版本同签名 APK：`adb install -r yaya-timetable-v1.0.1.apk`
   （**不要**先 `adb uninstall`，也不要加降级参数）；
3. 打开应用，逐项核对：课程、上课时段、待办、AI 对话、主题/背景、昵称头像、提醒设置；
4. 确认这次是**覆盖升级**而不是重装：
   `adb shell dumpsys package com.university.timetable | grep -E "versionCode|firstInstallTime"`
   —— `versionCode` 变大而 `firstInstallTime` 保持原值，说明系统把它当作升级处理。
   （正式包不可调试，`adb shell run-as` 不可用；要检查数据库文件请在应用内
   「设置 → 数据与备份 → 导出数据备份」，解压 zip 看 `database/university_timetable.db` 是否存在且行数正常。）
5. 若数据库结构有变更，确认迁移生效（旧数据仍在，且新字段可写入）。

应用内升级路径：

1. 旧版本打开「设置 → 软件更新 → 检查更新」，或在启动后等待自动检查；
2. 弹窗中确认版本、说明、大小 → 立即更新 → 下载进度 → 校验（SHA-256/包名/版本/签名）→ 系统安装界面；
3. 安装界面点「更新」而非「卸载」；完成后应用数据应保持不变。

## 6. 数据备份 / 恢复

设置页提供「导出数据备份」「导入数据恢复」：

- 导出的 zip 包含：`databases/university_timetable.db`（含 WAL/SHM）、
  `files/datastore/ui_preferences.preferences_pb`、`files/datastore/ai_settings.preferences_pb`、
  以及描述文件 `manifest.json`；
- 通过系统文件选择器保存到用户指定的位置（下载目录、网盘等），**不会只留在缓存目录**；
- 导入前会自动把当前数据快照到应用私有目录 `files/backup/pre-restore-*.zip`，导入后应用自动重启加载新数据；
- 备份文件绑定 applicationId，导入到别的应用会被拒绝。

备份是「意外情况」的兜底，不能替代签名一致与正确的迁移——**任何方案都无法承诺绝对零数据损失**。

## 7. 已知限制与风险

- 覆盖安装要求新包签名与已安装版本一致；密钥丢失则无法再让老用户升级。
- Google Play 分发的版本按 Play 政策应通过商店更新，客户端检测到安装来源为 `com.android.vending` 时会禁用应用内更新。
- 升级途中若系统杀进程/断网，下载文件会保留为 `.part`，重试时尝试 Range 续传；校验失败会删除坏包。
- 数据库从 v1–v3（无迁移路径的历史版本）升级时仍会触发 Room 的破坏性重建；升级前会把旧库复制到
  `files/backup/legacy-vN-*.db`，但这不等于用户数据一定可读，建议先导出备份再升级。
- 工作流依赖 GitHub 托管 Runner 与 Actions 配额；APK 体积较大时上传时间会更长。
