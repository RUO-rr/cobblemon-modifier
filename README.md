# cobblemon-modifier · 游戏内的 Cobblemon 数据修改器

[![CI](https://github.com/RUO-rr/cobblemon-modifier/actions/workflows/ci.yml/badge.svg)](https://github.com/RUO-rr/cobblemon-modifier/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62b47a)
![Fabric](https://img.shields.io/badge/Fabric-Loader%200.18.4%2B-dbb69c)
![Java](https://img.shields.io/badge/Java-21-orange)

一个 Fabric 客户端模组：**在游戏里直接修改 Cobblemon 宝可梦数据（种族值 / 特性 / 技能）**。

所有改动都写入独立覆盖文件并同步为世界数据包，**不改写任何原模组 JAR**，随时可以一键还原。
项目最初是一个 Swing 桌面程序，后完整迁移进 Minecraft 生态（见 [ARCHITECTURE.md](ARCHITECTURE.md)）。

## 核心亮点

- **数据源补全**：不只读 `mods/*.jar`，还会读取整合包的 `global_packs` / `resourcepacks` 数据包，
  因此那些"只存在于数据包里"的魔改宝可梦（赛尔号、真·方可梦、Extra Paradox…）同样可以编辑
- **覆盖写入，不碰原 JAR**：改动落到 `config/cobblemonmodifier/overrides/`，再同步为世界数据包，
  删除文件即还原，天然可回滚
- **扇出同步（本项目最重要的一次修复）**：整合包常用 `data/<ns>/species_additions/` **整体替换**物种字段，
  而多个覆盖文件谁最后生效在 Cobblemon 里是 **HashMap 顺序决定的、和包优先级无关**。
  修改器会把改动同步写进该宝可梦的**所有**来源文件，从根上消灭"改了没效果 / 必须改某个特定包"
- **插件化**：`ServiceLoader` 自动发现"种族值 / 特性 / 技能"三个编辑器加上扫描与查找两个辅助插件，加插件零改动主流程
- **输入校验**：技能需要命中 1386 条已知招式表、特性需在白名单内，拼错直接拒绝保存（避免"改完进不去世界"）
- **性能**：扫描改为"一个压缩包只打开一次"的批量读取，实测 **35~126 秒 → 0.41 秒**
- **254 个单元测试**，覆盖 model / plugin / service / repository 四层

## 技术栈

| 类别 | 技术 |
|------|------|
| 平台 | Minecraft 1.21.1、Fabric Loader 0.18.4+、Fabric API |
| 依赖模组 | Cobblemon 1.7.3（必需）、mega_showdown（可选，提供多 Mega 开关） |
| 语言 / 构建 | Java 21（开发用 JDK 25）、Gradle 9.5.1、Fabric Loom 1.8.13、Yarn 映射 |
| JSON / 配置 | Gson（读写物种与数据包）、反射热重载（BestSpawner、MegaShowdownConfig） |
| UI | Minecraft `Screen` + Widget 体系，View 层是**纯 POJO 状态模型**，由渲染线程轮询同步 |
| 日志 | SLF4J（模组内不再自带 logback 配置，交给游戏统一管理） |
| 测试 | JUnit 4.13.2 + Mockito 5.17 + `TemporaryFolder` 真实文件/压缩包夹具 |
| 工程 | Maven 风格的目录分层、`.gitattributes` 统一行尾、GitHub Actions 自动构建与测试 |

## 架构概览

```
                 Minecraft Client（渲染线程）
                          │  轮询视图快照 / 分发点击事件
                          ▼
     client/ ModifierScreen · FieldSearchScreen · BucketConfigScreen
                          │
                          ▼
     ui/ MainFrame（纯 POJO 视图状态）      controller/ MainController（唯一调度者）
                          │                              │
                          │                              ▼
                          │            service/ ScanService · ModifyService
                          │                     SpawnConfigService · MoveCatalog
                          │                     SpeciesOverrideIndex · MegaLimitService
                          ▼                              │
     plugin/ 种族值 / 特性 / 技能（ServiceLoader SPI）    ▼
                          │            repository/ JarRepository（jar/zip I/O）
                          │                        OverrideRepository（覆盖文件 + 同步数据包）
                          │                        ConfigRepository（本地缓存）
                          ▼                              ▼
     model/ PokemonStats · FormInfo · StatField · JarResourcePath · SpawnEntry
                          │
                          ▼
  数据源：mods/*.jar  →  global_packs/required_data  →  resourcepacks  →  jostar_overrides
          （优先级按 level.dat 里真实的 Enabled 顺序，越靠后越高）
```

详细的架构演进、技术决策与被推翻的假设见 [ARCHITECTURE.md](ARCHITECTURE.md)；
运行时流程（开界面 → 加载数据 → 编辑 → 保存 → 扇出 → 重进世界生效）见
[project-flow.mermaid](project-flow.mermaid)；社区反馈如何变成代码见
[docs/user-feedback.md](docs/user-feedback.md)。

## 目录结构

```
src/main/java/com/cobblemon/modifier/
├── CobblemonModifier.java        # Fabric 主入口（装配 DI 容器）
├── client/                       # 客户端入口、按键、Minecraft Screen、数据包同步
├── controller/                   # MainController：唯一接收视图事件并调度服务的地方
├── core/                         # JsonModifier 接口、DI 容器、通用工具
├── model/                        # 不可变值对象（PokemonStats / FormInfo / JarResourcePath…）
├── plugin/                       # 5 个插件：种族值 / 特性 / 技能 / 魔改扫描 / 字段查找
├── repository/                   # JarRepository、OverrideRepository、ConfigRepository
├── service/                      # 扫描、修改、招式表、来源索引、Mega 开关、覆盖率配置
└── ui/                           # 视图状态模型（纯 POJO，无任何 Swing/AWT 依赖）
src/main/resources/
├── fabric.mod.json               # 模组元数据（environment=client）
├── META-INF/services/…JsonModifier   # SPI：插件清单
└── assets/cobblemonmodifier/     # 图标与按键汉化
docs/user-feedback.md             # 反馈 → 改动 的对应记录
```

## 安装

1. 把 `cobblemonmodifier-3.0.0.jar` 放进整合包的 `mods` 目录
2. 确认已安装 **Cobblemon** 与 **Fabric API**（多人服务器上无效，见下）

## 使用

1. 进世界后按 **K** 打开修改器（按键可在"选项 → 按键控制"里改）
2. **模组目录**默认是 `<游戏目录>/mods`，点 **应用目录**
3. 点 **加载宝可梦数据**（扫描 mods 与整合包数据包，整合包越大首次越慢）
4. 在左侧列表搜索并选中宝可梦；或用 **字段查找** 按图鉴编号 / 名字 / 特性搜索
5. 用下拉框切换插件：**种族值修改 / 宝可梦特性修改 / 技能修改**
6. 改完点 **保存修改**，然后 **重进世界** 生效（不用重启游戏）

常见操作：

- **给宝可梦学本来学不会的招式**：技能修改 → 第一页最上面 4 个空槽填 `1:dragondance`（1 级学龙之舞）
- **改 Mega / 地区形态的种族值**：先搜到带那个形态的条目（如 `absol_mega`）再改
- **全局稀有等级占比**：点 **稀有等级权重**（改完立即热重载）
- **一次允许多只 Mega**：点底部 **多Mega** 开关（需要 mega_showdown）
- **还原原版**：选中后点 **还原原版**，或直接删掉覆盖目录里的文件

## 重要限制

- **只对单机（本机世界）有效**：这是客户端工具，通过写本机存档的数据包生效，**多人服务器上不生效**
- 改动需要**重进世界**才生效：Cobblemon 的物种数据只在世界加载时读取
  （我们验证过它明确不支持运行时重载：日志里那句
  `Cobblemon data registries are only loaded once per server instance as Pokémon species are not safe to reload`）
- 技能与形态是整合包最常覆盖的两类字段，种族值/特性一般不会被覆盖——这也是"改了没效果"通常只出现在前两者的原因

## 开发与构建

```bash
git clone git@github.com:RUO-rr/cobblemon-modifier.git
cd cobblemon-modifier

./gradlew build      # 编译 + 254 个单元测试
./gradlew test       # 只跑测试
```

产物：`build/libs/cobblemonmodifier-<版本>.jar`

CI 在 Ubuntu + Temurin JDK 21 上跑同一套 `./gradlew build`（见 [.github/workflows/ci.yml](.github/workflows/ci.yml)）。

> 开发环境用 JDK 25 构建、目标字节码 21；`gradle-wrapper.properties` 里用的是腾讯云镜像，
> 若在国外网络可换回 `https://services.gradle.org/distributions/`。

## 版本

见 [CHANGELOG.md](CHANGELOG.md)。当前版本 **3.0.0**。

## 许可

[MIT](LICENSE)
