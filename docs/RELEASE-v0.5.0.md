# 偷偷时间 v0.5.0：整体架构重构

保留已有纸墨界面与全部计时/统计功能，重新组织数据流、领域计算、ViewModel、导航和生命周期。

- 统一 Room 事务快照，首页/统计自动响应记录变化。
- 首页显示时钟不再每秒查询数据库，后台暂停刷新并可恢复活动计时。
- 总览、趋势和详情统一计算，快速切换周期不会被旧结果覆盖。
- 周期选择支持页面重建恢复；默认日期跨午夜更新，历史日期保持不变。
- 重复操作、删除失败、取消和订阅释放增加回归覆盖。
- 保留日/周/月/年/使用以来、精确备注合并、排行和单条删除。

37 项 JVM 测试通过；Debug Lint 为 0 Fatal、0 Error、49 Warning；Debug 与 AndroidTest APK 构建成功。没有连接设备，未进行真机安装、升级和仪器测试，不是商店正式签名版本。

安装附件 `ToutouTime-v0.5.0-debug.apk`，Android 8.0 及以上。与 v0.4.0 同证书，数据库结构保持不变；保留数据请覆盖安装，不要先卸载或清除应用数据。

- versionName / versionCode：0.5.0 / 5
- 大小：9,960,828 字节
- SHA-256：`B6054DF33056E65674191A983598A363E5985A870079C059CE804E7CFD63C8C5`

完整项目介绍见 [README](https://github.com/fplity/ToutouTime#readme)，架构与验证见 [重构说明](https://github.com/fplity/ToutouTime/blob/main/docs/REFACTOR.md)，恢复与后续维护见 [交接说明](https://github.com/fplity/ToutouTime/blob/main/docs/PROJECT_HANDOFF.md)。
