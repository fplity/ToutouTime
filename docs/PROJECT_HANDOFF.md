# 偷偷时间项目交接说明

## 最终交付基线

- 应用名称：偷偷时间
- 仓库：`https://github.com/fplity/ToutouTime`
- Android 版本：`0.4.0`（versionCode `4`）
- 包名：`com.example.studenttimetotalnote`
- 最低系统：Android 8.0（API 26）
- 推荐安装包：`releases/ToutouTime-v0.4.0-debug.apk`
- APK SHA-256：`84CF256A9988088270B64FBA32123AE5B0E1EBB9E0A5C4A4F6659A56684E9F07`

当前完成范围是 Android 本地版。电脑端和跨设备同步没有实现，不能把 README 中的后续方向当作现有功能。

## 功能边界

### 已实现

- 带备注的开始/结束计时。
- 活动计时状态持久化与应用重开恢复。
- 单条学习记录持久化。
- 今日、任意日期、自然周、自然月、自然年和使用以来统计。
- 日/周/月/年周期前后切换。
- 趋势图、完全相同备注聚合、累计时长排序。
- 查看备注下的每次学习记录并删除指定记录。
- 删除后重新计算所有周期和总学习时间。
- 极简纸墨视觉、自适应启动图标和 Android 深浅系统栏处理。

### 尚未实现

- 正式 release 签名和应用商店发布。
- 数据导入、导出、备份恢复和云同步。
- 用户账号、设备配对或服务端。
- Windows/macOS/Linux 电脑端。
- Android 仪器测试的真机执行与当前版本真机截图验收。

## 关键业务规则

1. 开始计时前只对备注执行 `trim()`；内部空格和大小写保持不变。
2. 活动计时是单例，同一时刻只能有一条。
3. 结束时在 Room 事务中写入完成记录并清除活动计时。
4. 自然周从周一开始，到周日结束。
5. 周和月模式默认进入上一完整自然周、上一完整自然月；年度默认今年，日模式默认今日。
6. 记录跨周期时按 `[开始时间, 结束时间)` 与目标周期的实际交集计时，避免边界重复统计。
7. 备注必须文本完全一致才合并；结果按总时长降序、备注文字升序排列。
8. “使用以来”覆盖所有现存完成记录，没有前后周期，也不显示趋势图。
9. 删除按记录 ID 精确执行；删除成功后所有统计重新从剩余记录计算。

## 代码导航

| 位置 | 职责 |
| --- | --- |
| `data/local/StudyTimerDatabase.kt` | Room 实体、DAO、事务和数据库 |
| `data/StudyTimerStore.kt` | 持久化接口 |
| `domain/DefaultStudyTimerRepository.kt` | 计时与报告仓库实现 |
| `domain/Reports.kt` | 周期边界、交集、聚合和排序规则 |
| `ui/home/HomeViewModel.kt` | 首页计时状态 |
| `ui/home/HomeScreen.kt` | 首页与备注弹窗 |
| `ui/statistics/StatisticsViewModel.kt` | 周期选择、趋势、记录和删除状态 |
| `ui/statistics/StatisticsScreen.kt` | 统计页视觉和交互 |
| `navigation/StudyTimerNavHost.kt` | 首页/统计页导航 |
| `StudyTimerDomainTest.kt` | 领域规则与删除回归测试 |
| `StudyTimerNavigationSmokeTest.kt` | Compose 导航与周期入口测试 |

## 构建与验证

标准命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

2026-10-06 强制重跑验证：21 项 JVM 测试通过且 0 失败；Lint 为 0 Fatal / 0 Error / 46 Warning；Debug 与 AndroidTest APK 构建通过；公开的 v0.4.0 APK 包名、版本号、SHA-256 和 v2 签名均已复核。

Windows 本机曾使用以下环境：

```powershell
$env:JAVA_HOME='D:\app\Android\jbr'
$env:ANDROID_HOME='D:\app\AndroidStudio'
$env:ANDROID_SDK_ROOT='D:\app\AndroidStudio'
$env:GRADLE_USER_HOME=(Resolve-Path -LiteralPath '.tmp-build\gradle-real').Path
```

Gradle Wrapper 的发行地址已经指向腾讯镜像，避免首次下载被 GitHub 网络连接阻塞。`local.properties`、构建缓存和签名材料均被 `.gitignore` 排除。

## 从远端恢复

本地项目被删除后，可以从 GitHub 完整恢复源码和公开交付物：

```powershell
git clone https://github.com/fplity/ToutouTime.git
Set-Location ToutouTime
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

仓库内 `releases/` 保存历史测试 APK；`docs/assets/` 和 `docs/screenshots/` 保存公开图标与界面预览；`CHANGELOG.md` 保存版本变化。

## 后续双端同步建议

若未来重启电脑端计划，建议先冻结同步规则再编码：

- 桌面端可考虑 Compose Multiplatform Desktop，以复用 Kotlin、Compose 和领域统计逻辑。
- 两端各自保留本地数据库并离线可用，通过服务端同步记录，不直接同步数据库文件。
- 为完成记录增加跨设备稳定 UUID、更新时间/版本、删除标记和同步游标。
- 第一阶段只同步已经结束的记录；活动计时由发起设备负责，避免跨设备计时冲突。
- 验收必须覆盖重复提交、离线重试、双端删除、时钟偏差和冲突解决。

这些只是后续建议，不属于 v0.4.0 已实现能力。
