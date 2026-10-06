# v0.5.0 整体重构与验证说明

## 范围

保留用户已经确认的计时流程、纸墨界面、日/自然周/自然月/具体年份/使用以来统计、备注精确归类、排行和单条删除；重构内部架构，不增加桌面端、账号或云同步。本轮没有创建或委托智能体。

## 架构变化

| 层 | 原有问题 | 当前实现 |
| --- | --- | --- |
| 持久化 | 活动状态与完成记录分别查询 | Room 事务读取统一 `StudySnapshot`；表变更驱动 Flow |
| 领域 | 趋势和记录投影耦合在统计 ViewModel | `Statistics.kt` 提供纯函数；共享 `overlapDuration` 处理边界 |
| 首页 | 时钟刷新触发重复数据库读取 | 订阅快照，内存时钟刷新；今日汇总只在数据或日期变化时重算 |
| 统计 | 独立查询与请求结果存在覆盖风险 | 同一快照生成总览、趋势、详情；`collectLatest` 取消旧任务并核对选择 |
| 生命周期 | Compose 中手工构造 ViewModel | `viewModel()` + 工厂 + `viewModelScope`；Android 回收时自动取消任务 |
| 页面恢复 | 周期选择随重建丢失 | `SavedStateHandle` 保存模式、日期和是否跟随默认周期 |
| 删除 | 页面刷新与删除异步状态耦合 | 删除提交后由快照驱动页面；取消不误报失败，旧周期错误不污染新周期 |

首页离开或应用进入后台时暂停显示时钟，但实际计时仍由数据库中的开始时间恢复，不依赖后台定时服务。统计页离开时停止订阅，返回时重新读取；默认今日会跟随午夜，手动历史选择不自动跳回今日。

## 验证记录（2026-10-06）

执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
```

结果：`BUILD SUCCESSFUL`。

- `StudyTimerDomainTest`：21 项，0 失败、0 错误、0 跳过。
- `ViewModelRegressionTest`：16 项，0 失败、0 错误、0 跳过。
- Lint：0 Fatal、0 Error、49 Warning。类别为依赖升级建议（30）、Gradle 插件升级建议（3）、Kapt 提示（1）、Modifier 规范（2）、单色图标（2）、冗余 SDK 判断（1）和未使用资源（10）；本轮未盲目升级工具链。
- Debug APK 和 AndroidTest APK 均构建成功。
- 新增 2 项 Room 仪器测试使用独立内存数据库，不读取用户设备中的真实学习记录；仅完成编译。
- `adb devices` 没有连接设备，因此没有安装、真机交互、像素一致性或仪器测试通过的结论。

回归覆盖：跨日/月/年和夏令时交集、备注完全一致归类、空周期、年度 12 月趋势、总计与删除、活动恢复、时钟不轮询数据库、重复开始/结束、前后台刷新、快速周期切换、选中年份恢复、跨午夜、删除异常及取消、订阅释放和并发开始。

## 安装包

- 路径：`releases/ToutouTime-v0.5.0-debug.apk`
- 大小：9,960,828 字节
- 包名：`com.example.studenttimetotalnote`
- versionName / versionCode：`0.5.0` / `5`
- minSdk / targetSdk：`26` / `36`
- SHA-256：`B6054DF33056E65674191A983598A363E5985A870079C059CE804E7CFD63C8C5`
- `apksigner verify`：v2 签名校验通过，与仓库 v0.4.0 的签名证书一致。

未修改数据库名、版本或实体结构；未增加破坏性迁移。该证据支持旧版覆盖安装的兼容设计，但不等于已经进行真机升级测试。

## 交付与恢复

源码、历史 APK、设计预览、README、CHANGELOG 和交接说明保留在 `fplity/ToutouTime`。公开预览是设计图，不是本轮真机截图。签名私钥、本机 SDK 配置、学习数据库、缓存和内部工作记录不上传。

按用户原要求，在核对远程源码及 APK 后清理本地工程；若 Windows 或正在使用此工程的工具占用目录，删除结果以实际文件系统复核为准，不把失败说成完成。手机内学习数据不会因电脑工程删除而被清除。
