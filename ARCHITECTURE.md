# Cobblemon 数据修改器 —— 架构演进文档

## 项目定位

一个把"修改 Cobblemon 宝可梦数据"这件事从**桌面工具**变成**游戏内模组**的项目。

它要解决的核心问题是：整合包里的宝可梦数据分散在 `mods/*.jar`、`global_packs`、`resourcepacks`
三类来源里，其中还有大量互相覆盖的 `species_additions`；手工改 JSON 既容易改错、也容易"改了不生效"。

项目从头到尾经历了三轮演进：

1. **桌面版重构**（Swing + 四阶段分层改造），2026-02 发布 v1.0.0（未建仓库，靠网盘 / 群分发）
2. **迁移进 Minecraft**（Fabric 模组，UI 从 Swing 换成游戏内界面）；2.x 系列未公开发布
3. **上线后按玩家反馈迭代**（C1 ~ C9 共 9 个阶段，包含若干"被真实数据推翻的假设"）

> 版本与仓库的对应关系：**本仓库只收录 v3.0.0 及以后的演进**（v3.0.0 起以 MIT 开源、走 GitHub Release 分发）。
> v1.x 桌面版与未公开的 2.x 属于建仓库之前的阶段，只在本节与「演进路线」里作为背景出现。

---

## 一、架构演进路线

### A1 桌面版：1643 行的巨类 → 分层

```
改造前                                  改造后
─────────────────────────               ─────────────────────────
MainFrame.java (1643 行)                 ui/      MainFrame（视图，554 行）
  ├── UI 构建                             controller/ MainController（事件调度）
  ├── 扫描逻辑          ──────────►       service/   ScanService / ModifyService
  ├── JSON 读写                           repository/ JarRepository / ConfigRepository
  ├── 业务规则                            plugin/    JsonModifier 插件
  └── 直接 new Thread()                   core/      ModifierContainer（DI 装配）
```

**动机**：桌面版把"界面、扫描、JSON 读写、业务规则"全塞在一个类里，
任何一个需求（比如新增一种可修改字段）都要动这个 1643 行的文件。

### A2 桌面版：DTO 提取（消灭"弱类型 Map"）

```
改造前                                  改造后
─────────────────────────               ─────────────────────────
Map<String, Object> 到处传               model/PokemonStats   6 项种族值，不可变
先 get 再强转                            model/FormInfo       形态名 → 字段后缀
字段名字符串散落各处                     model/StatField      字段元信息 + 类型转换
自定义内部类重复定义                     model/JarResourcePath [jar]路径 的解析与反解析
```

**动机**：`Map<String, Object>` 让编译器无法帮忙——字段名写错、类型转错都只能在运行时炸。

### A3 桌面版：依赖注入

```
改造前                                  改造后
─────────────────────────               ─────────────────────────
Main 里 10 处 new                        core/ModifierContainer（组合根）
插件在 MainController 里硬编码注册        ServiceLoader<JsonModifier> 自动发现
```

**动机**：把"谁依赖谁"集中到一个地方，测试可以只装配自己需要的部分。

### A4 桌面版：测试与收尾

- 补 147 个单元测试（model / plugin / service / repository）
- 消除全部 `System.out/err`，接入 SLF4J
- 危险路径 `JarRepository.writeJson()`（整包重写 JAR）从生产调用中移除

---

### B1 迁移：构建与环境

```
桌面版                                  模组版
─────────────────────────               ─────────────────────────
Maven + JDK 17                           Gradle 9.5.1 + Fabric Loom 1.8.13
独立可执行 jar                            Fabric Mod（fabric.mod.json）
main() 启动                              ModInitializer / ClientModInitializer
```

**关键约束**：模组源码里出现任何 `javax.swing` / `java.awt` 都会在客户端加载期直接崩，
所以这一阶段先把 UI 层"掏空成纯 POJO"，让 34 个源文件先能在 Fabric 工程里编译通过。

### B2 迁移：入口整合

```
桌面版                                  模组版
─────────────────────────               ─────────────────────────
Main.main()                              CobblemonModifier.onInitialize() → 装配容器
（无）                                   按 K 键 → 打开修改器界面（KeyBinding）
配置文件目录自定                          FabricLoader.getConfigDir() / getGameDir()
```

### B3 迁移：UI 重建（Swing → Minecraft Screen）

```
桌面版                                  模组版
─────────────────────────               ─────────────────────────
JFrame / JPanel / JList                  Screen + EntryListWidget + ButtonWidget
EDT 上直接改控件                          View 变成纯 POJO 状态模型
Controller 里 SwingUtilities.invokeLater 渲染线程每 tick 轮询快照并重建控件
```

**为什么改成"轮询"**：后台线程（扫描/保存）不能碰 Minecraft 的控件对象，
否则会在渲染线程外修改 UI 状态。让 View 只保存**可被任意线程安全读写的状态**，
由渲染线程在 `tick()` 里消费，是最省事也最不容易出线程 bug 的做法。

### B4 迁移：安全文件 I/O

```
桌面版                                  模组版
─────────────────────────               ─────────────────────────
直接改写 mods/*.jar                      写 config/cobblemonmodifier/overrides/<原路径>
（游戏运行中改 JAR = 崩溃/损坏）          再同步为 <存档>/datapacks/cobblemonmodifier/
```

覆盖文件**镜像原始资源路径**，所以"哪来的、写哪去"一目了然；同步时是"删除目标 + 整份拷贝"的镜像同步，
覆盖目录里删掉文件，世界数据包下次同步就会跟着消失。

### B5 迁移：为什么最终没有走 Mixin

原计划用 Mixin 直接改内存里的物种数据。实际调研后发现：
**数据包覆盖（世界数据包优先级最高）已经能完整达成目标，且天然可回滚**，
而 Mixin 方案需要跟着 Cobblemon 内部实现走、升级即碎。于是放弃 Mixin，走数据包路线。

---

### C1（P1）形态数据被写坏 —— 一次真实事故的修复

线上反馈：修改某些宝可梦后，"进对战就变得更弱 / 大扣血"。

定位到根因：Cobblemon 的数据格式里，**形态不带 `baseStats` 表示"继承本体"**。
而原实现把"缺字段"当成"空值"，于是给这种形态补上了一串 `0`：

```
本体 venusaur.json        forms[1].Gmax 没有 baseStats（继承本体）
修复前的覆盖文件           forms[1].baseStats = {0,0,0,0,0,0}   ← 凭空写入
```

影响面实测：**366 个形态里有 223 个没有 `baseStats`，涉及 105 个物种**（Mega / Gmax / 阿罗拉 / 伽勒尔 / 洗翠形态）。
种族值全 0 的形态在对战里 HP 只剩"等级+10"、其余属性取最低值——这正好解释了那批"变弱"的反馈。

修复要点：

- 形态没有 `baseStats` 时**保持继承**，只有用户确实改了其中某一项才落地
- 界面改为显示"继承来的值"而不是 0（否则用户一看就是 0，自然会去改）
- 加回归测试，把"不能凭空写入 baseStats"钉死

同一类问题在特性插件里也存在（形态没有 `abilities` 时会被写成空数组，
导致"改索罗亚克把洗翠形态的特性清掉"），一并修复。

### C2（P0）被推翻的假设：战斗引擎能热推送

有一类反馈是"图鉴显示改了，但对战没变"。经过反编译发现 Cobblemon 1.7.3 的战斗引擎是
**内置的 Pokémon Showdown（跑在 GraalJS 里）**，物种数据由 `ShowdownService.sendRegistryData(...)`
推送过去，并且存在 `/reloadshowdown` 命令。

基于此做了一个"保存后自动 `/reload` + `/reloadshowdown`"的方案，**结果被真实日志推翻**：

```
Cobblemon data registries are only loaded once per server instance
as Pokémon species are not safe to reload.
```

即 Cobblemon 的物种注册表**根本不参与热重载**（源码里用 `register(registry, reloadable=false)` 标记，
重载监听器会跳过），而且那次 `/reload` 让游戏卡了 **13.6 秒**却毫无收益。

处理方式：**撤销这个功能**，并把提示改成实话——"重进世界后生效（不用重启游戏）"。
同时确认物种数据在**每次世界加载**时都会重新读取并推送给战斗引擎，
所以"重进世界"这条路径本来就是通的。这是一次典型的"先承认假设错了，再删掉错误代码"。

### C3（P2）数据源补全

```
改造前                                  改造后
─────────────────────────               ─────────────────────────
只扫 mods/*.jar                          mods/*.jar
（看不到数据包里的魔改宝可梦）            + global_packs/required_data
                                          + resourcepacks
                                          + global_packs/jostar_overrides
                                        按 JSON 路径去重，优先级取游戏真实加载顺序
```

顺带修正了两件事：

- 数据包里只有 `data/<ns>/species/**` 与 `species_additions/**` 才是目标，
  其它（图鉴条目、骑乘参数、配方…）不再逐个解析
- "是不是宝可梦"的判定收紧为**必须有 `baseStats`**：原来的宽松规则会把 1214 条图鉴条目当成宝可梦收进列表

### C4（P3）Mega 数量开关

玩家反馈的"解除 mega 限制"，原意是**解除"同时只能有一只 Mega"**。
调研发现 mega_showdown 自带这个开关（`config/mega_showdown/config.json` 的 `multipleMegas`），
而且它的配置类有公开的 `load()`，可以在写完文件后反射热重载、立即生效。

注意：**真正生效的是 JSON，不是同目录下那份看起来像配置的 `mega_showdown-common.toml`（旧版遗留）**——
照着 toml 改会完全没效果，这是踩过的坑。

### C5（P4）技能修改

```
数据格式（实测 1394 个物种文件 / 15.7 万条记录）
  "1:blitzstrike"      按等级学
  "tm:aerialace"       TM        egg: / tutor: / legacy: / special: / form_change:

编辑器
  第一页最上面 4 个空槽 = 新增技能；后面按原顺序列出已有技能；清空 = 删除

校验
  已知招式表 = Showdown 基础招式 951 + 数据包自定义招式 435 = 1386 条
  拼错直接拒绝保存并指出是哪个词
```

自定义招式在数据包里是 `.js`（"JSON + 内嵌函数"，形如 `data/cobblemon/moves/<包>/<招式>.js`），
所以扫描层专门加了"列出非 JSON 条目"的能力。

### C6（方案 A / B1）species_additions 与扇出写入

**问题**：玩家反馈"给地龙加了 `tm:dragondance`、TM 也用了，还是学不会"。
排查发现地龙的招式表被数据包整体替换了：

```
resourcepacks/§eJoStar§c大集合.zip
  └─ data/move_calibration/species_additions/garchomp_move.json
       { "target": "cobblemon:garchomp", "moves": [ 79 条 ] }   ← 没有龙之舞
我们改的 data/cobblemon/species/generation4/garchomp.json（140 条）被它盖掉
```

**机制**：`SpeciesAdditions$Addition` 就是 `(物种的某个可变属性, 新值)`，应用时直接赋值——
**是整体替换，不是合并**。而 `SpeciesAdditions.reload()` 是遍历一个 `HashMap` 应用的，
**谁最后生效由哈希顺序决定，与数据包优先级无关**。

**规模**：整合包里 **4693 个覆盖文件、覆盖 1074 个物种**；`moves` 被覆盖 **2082 次**，
其中 **1025 个物种的招式表被 ≥2 个文件同时接管**。这就是"改哪个包才有效"这种经验说法的来源——
它不是规则，是运气。

**对策（两步）**：

1. **方案 A**：把 `species_additions` 也纳入列表，玩家能直接编辑覆盖文件（`PokemonJsonFilter` 区分"本体"与"可编辑的覆盖文件"）
2. **方案 B1 扇出写入**：保存时计算"值变过的顶层字段"，把它们同步写进该物种的**所有**来源文件
   （本体 + 每个覆盖文件），于是无论谁最后生效都是玩家的值；`还原原版` 也改为把所有相关覆盖一起删除，
   避免"半还原"状态

**唯一偏离"整字段替换"的地方**：`forms` 按**形态名逐个替换**。
否则把本体形态同步进 ZA 的覆盖文件时，会把 ZA 独有的超进化形态整段删掉。

### C7（P4 第二档）招式数据修改 —— 当"数据"不是 JSON

**需求**：评论区和私信反复出现"能不能改招式"。C5 做的是**学习表**（让宝可梦学得会哪些招式），
玩家真正想要的还有**招式本身**的强度：威力、命中。这一档补上它。

**第一步是承认它不是 JSON**。招式定义有两种来源，而且都是脚本：

```
showdown/data/moves.js            952 条基础招式（Showdown 编译产物）
data/cobblemon/moves/<id>.js      435 条数据包自定义招式（赛尔号那些）
```

后者长这样——**是 JavaScript 对象字面量，不是 JSON**：

```js
{
  num: 10001, accuracy: 100, basePower: 100,
  name: "White Album", pp: 5, priority: 0,
  onModifyMove(move, pokemon) { ... },     ← 内嵌函数
  type: "Steel"
}
```

于是有两个技术问题：

**问题 1：不能直接做字符串替换。** "预知未来"的 `onTry` 函数体内嵌了一整份
`accuracy/basePower/moveData`，朴素替换会把它们一起改掉，破坏招式行为。
解法是 `MoveScriptUtil` 里的**带括号深度跟踪的扫描器**：跳过字符串与注释，
只认最外层对象（深度 1）上的 `字段: 值`。基础招式表还需要按"恰好缩进两空格"定位入口
（否则 `flags: { ... }` 这类内层对象会被误判成招式）。

**问题 2：现有覆盖层只会写 JSON。** 为此给 `OverrideRepository` 加了一条**文本通道**
（`readOverrideText` / `writeOverrideText`），并把仓储的批量读取扩展成
`readTextBatch` —— 一个压缩包只打开一次，1387 个招式的索引构建实测 285 ms。

**覆盖路径**：统一写进 `data/cobblemon/moves/<id>.js` 的**cobblemon 命名空间**。
因为 Cobblemon 的招式以**文件名**为 id，而世界数据包在模组之后加载，
这一份一定盖过所有来源——包括那些"三个包同时定义了同一个招式"的情况
（实测 437 个自定义招式里有 392 个被多个包重复定义）。

**效果**：实测 952 条基础招式**全部**能被抽取成独立脚本（0 失败），
保存只写改动过的字段，函数体与其它字段原样保留；点"还原原版"删除覆盖即回到原始数值。

### C8（P5）属性修改 —— 一个插件要处理 1025 个物种 + 366 个形态

**需求**：把喷火龙从 火/飞 改成 龙/飞。

**数据事实**（扫过本体全部物种后确认）：

```
"primaryType": "fire",         1025 个物种全部有（值为小写）
"secondaryType": "flying",     499 个单属性物种没有这个字段
forms[]: 366 个形态全部自带属性（喷火龙 Mega-X 是 fire/dragon）
```

**实现上的三个决定**：

1. **下拉框而不是输入框**：18 种属性做成下拉选项，写入时统一转小写。
   顺带约束了取值，避免把 `fire` 打成 `frie` 让数据包加载失败。
2. **未知属性名必须原样保留**：整合包里可能存在自定义属性名，
   若下拉框里没有它，控件会回退到第一项（Normal）——保存就等于悄悄改坏了数据。
   因此读取时把"当前值"临时插进选项列表，回写时也允许保留。
3. **形态没有 `primaryType` 就不生成字段**：这是 C1 那次事故的同一条原则——
   形态缺少某字段表示"继承本体"，插件绝不能凭空补一个空值。
   同时"只覆盖 moves 的 `species_additions`"文件也不会有属性字段，因此在那里不会被误改。

复用清单：插件的注册（`META-INF/services`）、覆盖写入、扇出同步、一键还原、
表单渲染（`FormInfo` + `StatField`）**全部沿用**种族值/特性插件那一套，新增代码只有一个插件类。

### C9（P6）"我改了但列表里没有"：命名空间无关的物种数据扫描

**反馈**："喷火龙进化石Z / 烈咬陆鲨进化石M / 莱希拉姆进化石 这些宝可梦改不了。"

**第一步是分清两种"找不到"**：是数据不在硬盘上，还是我们的列表没收录？
把整合包 476 个压缩包全扫一遍后，答案很明确——数据在，只是路径不在我们的扫描范围内：

```
mods/mushiromega-fabric-1.4.8-SNAPSHOT.jar
   └── data/newsmega/species_additions/generation1/charizard.json      forms: [Mega-Z]
   └── data/newsmega/species_additions/generation4/garchomp_change.json forms: [Mega-M]
   └── data/newsmega/species_additions/generation5/reshiram.json        forms: [Mega]
```

**三个独立的坑**，任何一个都会让这些宝可梦改不了：

1. **命名空间被写死**。扫描沿用了各插件的 `getTargetJsonPaths()`（`data/cobblemon/species`），
   于是 `data/newsmega/...` 连候选都算不上。
   改为按**路径规则**收集：`data/<任意命名空间>/(species|species_additions)/**`。
   用正则的目录位置（命名空间后紧接 `species`）同时排掉了
   `data/cobblemonresearchtasks/rewards/species/...` 这种"名字里有 species 但不是物种数据"的路径——
   顺带把候选数从 11058 降到 3517，加载更快。
2. **"只带 forms"的覆盖文件被过滤掉**。`PokemonJsonFilter` 原来只认顶层
   `moves` / `abilities` / `baseStats`，而魔改 Mega 石的种族值、属性、特性**全写在形态里**
   （莱希拉姆那份顶层只有 `target` + `forms`）。现在形态里带这些字段也算可编辑。
   对应地，三个插件的 `hasValidStatsFields` 也一起放开，否则选中文件后会显示"没有字段"。
3. **旧缓存让修复失效**。插件列表会缓存在 `~/.cobblemon-modifier/config.properties` 里，
   升级后仍然用升级前的列表，用户看到的现象和没修一样。
   于是引入 `SCAN_RULE_VERSION`：规则变化时启动即清缓存（版本号留在配置里，不会每次启动都清）。

**验证方式**：写了一个"用旧规则和新规则各跑一遍、比较有效集合"的对比程序，
结果是 **+150 / -0**——新增的全是 `data/newsmega/**` 的魔改形态，没有任何原来能改的条目丢失。
这条"先证明没弄丢东西"的检查，比单看新增数量更有意义。

---

## 二、核心技术决策与技术亮点

### 2.1 覆盖写入而非改写 JAR

**决策**：任何修改都不碰 `mods/*.jar`，而是写 `config/cobblemonmodifier/overrides/<原路径>`，
再同步为世界数据包。

**为什么**：游戏运行中改写正在使用的 JAR 既可能损坏文件，也可能让后续读取拿到半截数据；
覆盖方案还能顺带获得"删文件即还原"的能力。

### 2.2 插件体系：ServiceLoader + 统一接口

```java
public interface JsonModifier {
    String getPluginName();
    List<String> getTargetJsonPaths();
    List<String> getFieldNames();
    String getFieldLabel(String fieldName);
    List<String> getFieldChoices(String fieldName);   // 需要下拉框的字段
    Map<String, Object> parseJson(JsonObject json);   // JSON → 编辑器字段
    JsonObject modifyJson(JsonObject json, Map<String, Object> newValues);
    boolean usesCustomSave();                          // 少数插件走自定义保存
    boolean hasValidStatsFields(JsonObject json);      // 该文件是否适用本插件
}
```

新增一个"可编辑字段"只需实现这个接口并在
`META-INF/services/com.cobblemon.modifier.core.JsonModifier` 里注册一行，
主流程、UI、扫描、保存全都不用改。

### 2.3 View 是纯 POJO，由渲染线程轮询

后台线程只写"状态"（列表、进度、日志行），渲染线程在 `tick()` 里读取快照并更新控件。
这样扫描/保存可以在工作线程里放心跑，不需要到处 `invokeLater`。

### 2.4 数据源优先级以"游戏真实加载顺序"为准

优先级不是猜的，而是以 `saves/<世界>/level.dat` 里 `DataPacks.Enabled` 的顺序为依据
（越靠后优先级越高，我们的 `file/cobblemonmodifier` 永远排最后），得到：

```
我们的覆盖 > global_packs/jostar_overrides > global_packs/required_data > resourcepacks > mods
```

另外实测同一个 zip 在 `resourcepacks` 与 `required_data` 各放一份时游戏只加载一份，
所以扫描时按文件名去重，避免重复劳动。

### 2.5 扇出写入（见 C6）

### 2.6 扫描性能：一个压缩包只打开一次

```
改造前                                  改造后
─────────────────────────               ─────────────────────────
每个候选文件 new JarFile(...)            按压缩包分组
8968 个候选 = 8968 次 open               每个包 open 一次 + 批量读取
实测 35 ~ 126 秒                         实测 0.41 秒
```

### 2.7 输入校验：让错误在保存前暴露

技能 id 必须命中 1386 条已知招式表，特性 id 必须在白名单内；
校验失败直接拒绝保存并给出"是不是多打了个减号"这类具体提示。
这条直接对应线上反馈里"改完进不去世界"的事故（玩家把特性单词打错）。

### 2.8 反射热重载，避免"改完必须重启"

`BestSpawner.reloadConfig()`（全局稀有等级权重）与 `MegaShowdownConfig.load()`（多 Mega 开关）
都是公开静态方法，写完配置文件后反射调用即可立即生效。

### 2.9 测试策略：把真实数据当夹具

- Service/Repository 层用 `TemporaryFolder` 造**真实文件与真实压缩包**（`JarOutputStream` / `ZipOutputStream`）
- 关键 bug 都配回归测试（形态写 0、扇出、还原、压缩包去重、招式校验…）
- 除了单测，还用"离线跑真实整合包"的方式验证：例如扫描真实 218 个 mods + 50 个数据包，
  确认 152 个数据包物种一个不漏、`garchomp` 能被扇出到 3 份文件

---

## 三、代码质量改进

| 项 | 改进 |
|---|---|
| 线程 | 所有后台任务走统一 `ExecutorService`，不再 `new Thread()` |
| 异常 | 扫描任务改为 `catch (Throwable)` + `finally`：即使抛 `Error` 也会复位进度条与按钮，不再"永远卡住" |
| 并发 | 加入扫描闸门（同一时间只允许一个扫描），避免重复点击把任务堆进 2 线程池 |
| 日志 | 全部走 SLF4J；删除模组内置 `logback.xml`（Fabric 会覆盖它，留着只会误导人） |
| 死代码 | 删除桌面版遗留的 `JarManager` / `DefaultJarManager` / `PersistenceUtil` / `PokemonNameMappingUtil` / `ModifyRecord`、未使用的图标与映射表 |
| 仓库卫生 | `.gitignore` 屏蔽构建产物、日志、本地配置、运行数据；`.gitattributes` 统一行尾 |

---

## 四、关键指标

| 指标 | 数值 |
|---|---|
| 主源码 | 47 个文件 / 约 6100 行 |
| 测试代码 | 23 个文件 / 约 3250 行，**254 个用例全部通过** |
| 分层 | model / repository / service / plugin / controller / ui / client / core 共 8 个包 |
| 插件 | 5 个（种族值 / 特性 / 技能 / 魔改扫描 / 字段查找），SPI 自动发现 |
| 招式表 | 1386 条（Showdown 951 + 数据包自定义 435） |
| 招式数据索引 | 1387 条（showdown 952 + 数据包自定义 435），构建 285 ms；952 条基础招式全部可抽取成独立脚本（0 失败） |
| 扫描规模（实测） | 候选 11058 → 有效 2210（物种本体 1153 + 可编辑覆盖文件 1057），耗时 0.41 秒 |
| 覆盖数据规模（实测） | 4693 个 `species_additions` 覆盖 1074 个物种；`moves` 被覆盖 2082 次 |
| 形态数据修复面 | 366 个形态中 223 个受影响，涉及 105 个物种 |
| 性能提升 | 扫描 35~126 秒 → 0.41 秒（约 80 倍） |

---

## 五、决策记录（Why / Why Not）

### D-01 修改方案：覆盖文件而非改写 JAR
> 见 2.1。否决"直接改 JAR"：运行中改写正在使用的文件风险不可控，且无法回滚。

### D-02 插件发现：ServiceLoader 而非硬编码列表
> 见 2.2。硬编码列表每加一个插件都要改主流程；SPI 只加一行配置。

### D-03 View 层：纯 POJO 轮询而非 EDT/invokeLater
> 见 2.3。迁移到 Minecraft 后没有 Swing 的 EDT，跨线程直接改控件会崩。

### D-04 迁移实现：数据包覆盖而非 Mixin
> 见 B5。能达成同样效果、且可回滚；Mixin 要跟着 Cobblemon 内部实现走。

### D-05 撤销"保存后热推送战斗数据"
> 见 C2。被 Cobblemon 源码与日志明确推翻（物种注册表不参与重载），且代价是 13.6 秒卡顿。
> **结论：宁可给用户一句准确提示，也不留一个看起来聪明但无效的功能。**

### D-06 放�弃"单只宝可梦刷新率编辑"
> 该功能依赖往世界数据包里写 spawn_pool 覆盖，但在真实整合包里
> （有 8 个全局数据包自带 `spawn_pool_world`，其中一个还含同名文件）基线来源不一致，
> 且必须重进世界才生效，实测无法稳定生效。
> **结论：下架该编辑器，只保留"全局稀有等级权重"（配置文件 + 热重载，验证有效）。**

### D-07 删除模组内置 logback.xml
> Fabric 自己的 logback 配置优先级更高，模组内的配置不会生效，
> 结果只会在用户目录留下一个 0 字节的日志文件误导排查。删掉。

### D-08 `forms` 同步：按形态名合并而非整段替换
> 见 C6。整段替换会在把本体形态写入 ZA 覆盖文件时删掉 ZA 的超进化形态。

### D-09 扇出方向：本体 ⇄ 覆盖文件双向同步
> 起初只想"改本体时同步到覆盖文件"。但玩家完全可能直接编辑覆盖文件，
> 因此改为对称：改任何一份，其余相关来源一起更新。

### D-10 撤回 `resourcepacks` 扫描
> 一度考虑去掉（那里面有 2.3 GB 材质包）。实测列条目只要 63 毫秒、影响不大，
> 且保留能覆盖"只在 resourcepacks 里存在"的情况，因此保留，仅做按名去重。

### D-11 版本号：2.0.0 → 3.0.0
> 中间没有公开发过 2.x；本次是**形态变化**（桌面程序 → 游戏内模组）＋大量新能力，
> 按语义化版本应当跳大版本。

---

## 六、需求驱动的开发过程（用户反馈 → 迭代）

这个项目最特别的一点是：**几乎每一个阶段都不是"我想做什么"，而是"玩家遇到了什么"**。
反馈来自项目作者的视频评论区与私信（Cobblemon 整合包玩家），我把它们逐条落成了代码。

### 6.1 反馈如何进入开发

```
评论区 / 私信
    │  收集原文（含截图与日志）
    ▼
归类：数据源 / 形态 / 输入校验 / 概念误解 / 新功能
    │
    ▼
先验证"到底发生了什么"（读日志、扫真实整合包、反编译 Cobblemon）
    │   例：伊布改不生效 → 日志里根本没有伊布记录 → 真凶是形态被写成 0
    ▼
改代码 + 加回归测试 + 交付给作者实测
    │
    ▼
把这一条写进文档（就是本文件与 docs/user-feedback.md）
```

### 6.2 典型例子

| 玩家反馈 | 调查结论 | 代码上的结果 |
|---|---|---|
| "改了某些宝可梦，进对战变得更弱/大扣血" | 223 个形态的 `baseStats` 被写成 0 | P1 修复 + 回归测试 |
| "图鉴显示改了，对战没变" | Cobblemon 物种数据不支持热重载 | 改提示为"重进世界生效"，撤销无效的热推送 |
| "喷火龙 / 蒂安希改不了" | 这些物种被数据包覆盖 | P2 数据源补全 |
| "给地龙加龙之舞学不会" | 招式表被 `species_additions` 整体替换 | 方案 A + B1 扇出写入 |
| "想解除 mega 限制" | 指"同时只能有一只 Mega" | P3 多 Mega 开关 |
| "能不能改招式" | 招式表格式与校验来源 | P4 技能修改（1386 条招式表 + 输入校验） |

详细逐条记录（含原文与对应提交）见 [docs/user-feedback.md](docs/user-feedback.md)。

### 6.3 这条理念如何影响了架构

- **"改了没效果"这一条直接改变了架构**：从"只读 mods JAR"扩到多数据源，
  再引入来源索引与扇出写入——如果只按技术直觉做，根本不会想到要做这些
- **"改完进不去世界"催生了输入校验层**
- **玩家想要"更简单的开关"**（不想记指令、不想自己找包）推动我们把命令封装成按钮与下拉框
- 反向也成立：**玩家的说法不一定等于需求**。"解除 mega 限制"原以为是形态问题，
  澄清后才发现是数量限制；先确认再动手，省下了一整轮返工

---

## 附：相关文件

- [README.md](README.md) —— 安装与使用
- [project-flow.mermaid](project-flow.mermaid) —— 运行时流程与时序
- [docs/user-feedback.md](docs/user-feedback.md) —— 反馈驱动开发逐条记录
- [CHANGELOG.md](CHANGELOG.md) —— 版本变更
