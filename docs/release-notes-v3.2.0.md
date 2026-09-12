# cobblemon-modifier v3.2.0

这一版有两件事：**新增"属性修改"**，以及**修好一批魔改 Mega 石宝可梦（喷火龙Z / 烈咬陆鲨M / 莱希拉姆）改不了的问题**。

## 安装

1. 下载下面的 `cobblemonmodifier-3.2.0.jar`，放进整合包的 `mods` 目录
   （**先删掉旧版 jar**，同名模组放两份会让 Fabric 起不来）
2. 需要 **Minecraft 1.21.1 + Fabric Loader 0.18.4+ + Fabric API + Cobblemon**，Java 21
3. 进世界后按 `K` 打开修改器

## 新功能：属性修改

插件下拉框新增 **宝可梦属性修改**：主属性 / 副属性都是下拉框（18 种属性），
副属性选「（无副属性）」即变成单属性。

- 例如把喷火龙从 火/飞 改成 龙/飞
- **形态单独编辑**：形态各自带属性（喷火龙 Mega-X 是 火/龙），每个形态一组字段
- 整合包里的自定义属性名会原样保留并出现在下拉框里，不会被回退成 Normal 把数据改坏
- 形态没有 `primaryType`（表示继承本体）时不生成字段、也不写回；
  只覆盖招式的 `species_additions` 文件里不会被误改属性

## 重要修复：魔改 Mega 石宝可梦改不了

玩家反馈"喷火龙进化石Z、烈咬陆鲨进化石M、莱希拉姆进化石这些改不了"。查下来是三个叠在一起的问题：

**1. 扫描范围漏了魔改模组的命名空间。** 这些形态写在模组自己的命名空间里
（mushiromega 的 `data/newsmega/species_additions/**`，共 150 个文件），
而扫描只认 `data/cobblemon/...`，它们连列表都进不去。
现在任何 `data/<命名空间>/species` 与 `species_additions` 都会收录。

**2. "只带 forms"的覆盖文件被过滤掉。** 这类文件顶层只有 `target` + `forms`，
种族值/属性/特性全写在形态里；以前只认顶层 `moves`/`abilities`/`baseStats`，
于是莱希拉姆这种被整份跳过。

**3. 选中后只看到一排 0。** 这类文件没有顶层 `baseStats`，
插件却仍生成 6 个"基础形态"字段并显示成 0，把真正的 `Mega-Z - HP = 78` 挤到了第二页，
看起来像"没读到数据"。现在没有顶层 `baseStats` 的文件不再生成基础形态字段。

实测（同一份 5.9.2 整合包）：

```
种族值插件列表   1224 → 1374（新增 150 个魔改形态，丢失 0 个）
全量加载         2401（候选 3517，0.3 秒）
```

另外引入了"扫描规则版本号"：规则变化时启动会清掉旧的插件缓存，
否则升级后界面仍在用旧列表，看起来像"还是没加载进来"。

## 单元测试

277 → **307 个**，全部通过。

## 重要限制

- **只对单机（本机世界）有效**：客户端工具，通过写本机存档的数据包生效，多人服务器上不生效
- 改动需要**重进世界**才生效（Cobblemon 的物种数据只在世界加载时读取）

## 文档

- [README](https://github.com/RUO-rr/cobblemon-modifier#readme)
- [架构演进与技术决策](https://github.com/RUO-rr/cobblemon-modifier/blob/main/ARCHITECTURE.md)
- [需求驱动开发记录](https://github.com/RUO-rr/cobblemon-modifier/blob/main/docs/user-feedback.md)
- [更新日志](https://github.com/RUO-rr/cobblemon-modifier/blob/main/CHANGELOG.md)
