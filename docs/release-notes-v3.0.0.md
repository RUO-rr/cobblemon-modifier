# cobblemon-modifier v3.0.0

在游戏里直接修改 Cobblemon 宝可梦数据（**种族值 / 特性 / 技能**）的 Fabric 客户端模组。

**不改写任何原模组 JAR**：所有改动写入独立覆盖文件并同步为世界数据包，删掉文件即还原。

这是本项目的首次公开发布。`2.x` 是未公开的桌面版内部迭代，因此这一版直接进入 `3.0.0`——
它同时也是一次形态大变更：项目从独立的 Swing 桌面程序完整迁移进了 Minecraft 生态。

## 安装

1. 下载下面的 `cobblemonmodifier-3.0.0.jar`，放进整合包的 `mods` 目录
2. 需要 **Minecraft 1.21.1 + Fabric Loader 0.18.4+ + Fabric API + Cobblemon**，Java 21
3. 可选安装 **mega_showdown**，以启用底部的「多 Mega」开关
4. 进世界后按 `K` 打开修改器

## 本版亮点

- **游戏内界面**：按 `K` 打开，插件切换 / 宝可梦列表 / 字段编辑 / 进度与日志全部在游戏内完成
- **数据源补全**：除 `mods/*.jar` 外，还会读取整合包的 `global_packs/required_data`、`resourcepacks`、
  `global_packs/jostar_overrides`，并按 `level.dat` 里真实的加载顺序确定优先级，
  所以"只存在于数据包里"的魔改宝可梦（赛尔号、真·方可梦、Extra Paradox…）同样能编辑
- **扇出写入（本项目最重要的一次修复）**：整合包常用 `data/<ns>/species_additions/` **整体替换**物种字段，
  而多个覆盖文件谁最后生效在 Cobblemon 里由 **HashMap 顺序**决定、与包优先级无关。
  修改器会把改动同步写进该宝可梦的**所有**来源文件，从根上消灭"改了没效果 / 必须改某个特定包"
- **技能修改**：新增 / 改等级 / 删除招式，基于 1386 条已知招式表做输入校验
- **多 Mega 开关**：一键解除"同时只能有一只 Mega"的限制，立即生效（需要 mega_showdown）
- **字段查找**：按图鉴编号 / 名字 / 特性搜索并跳转到对应宝可梦
- **性能**：扫描改为"一个压缩包只打开一次"，实测 **35 ~ 126 秒 → 0.41 秒**

## 重要修复

- **形态数据被写坏（严重）**：形态没有 `baseStats` 时本应"继承本体"，旧实现会写入一串 `0`，
  导致 223 个形态（涉及 105 个物种）在对战里变得极弱
- "还原原版"只删除一份覆盖、导致半还原状态
- 同名数据包在 `resourcepacks` 与 `global_packs` 各有一份时的重复扫描与重复写入
- 图鉴条目等 1214 条"不是宝可梦的数据"被误收进列表

## 重要限制

- **只对单机（本机世界）有效**：这是客户端工具，通过写本机存档的数据包生效，多人服务器上不生效
- 改动需要**重进世界**才生效：Cobblemon 的物种数据只在世界加载时读取，它明确不支持运行时重载
- 每次修改后请重进世界再验证；"文件已写"与"游戏内已生效"是两件事

## 文档

- [README](https://github.com/RUO-rr/cobblemon-modifier#readme)
- [架构演进与技术决策](https://github.com/RUO-rr/cobblemon-modifier/blob/main/ARCHITECTURE.md)
- [需求驱动开发记录：玩家反馈 → 代码改动](https://github.com/RUO-rr/cobblemon-modifier/blob/main/docs/user-feedback.md)
- [更新日志](https://github.com/RUO-rr/cobblemon-modifier/blob/main/CHANGELOG.md)
- [运行时流程图](https://github.com/RUO-rr/cobblemon-modifier/blob/main/project-flow.mermaid)

## 工程

- 254 个单元测试，覆盖 model / plugin / service / repository 四层
- GitHub Actions 自动构建与测试（Ubuntu + Temurin JDK 21）
- 许可证：MIT
