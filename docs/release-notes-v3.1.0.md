# cobblemon-modifier v3.1.0

评论区与私信里反复出现过同一个诉求："能不能改招式"。v3.0.0 先做了**学习表**（让宝可梦学得会哪些招式），
这一版补上**招式本身**的数值：威力 / 命中 / PP / 优先级 / 属性。

## 安装

1. 下载下面的 `cobblemonmodifier-3.1.0.jar`，放进整合包的 `mods` 目录
   （**先删掉旧版 jar**，同名模组放两份会让 Fabric 起不来）
2. 需要 **Minecraft 1.21.1 + Fabric Loader 0.18.4+ + Fabric API + Cobblemon**，Java 21
3. 进世界后按 `K` 打开修改器

## 新功能：招式数据修改

主界面右上角新增 **招式数据** 按钮，点进去可以改**招式本身**的数值：

- 搜索招式 id 或英文名（如 `flamethrower`、`whitealbum`）
- 修改 **威力 / 命中 / PP / 优先级 / 属性**
- 两种来源都能改：基础招式（`showdown/data/moves.js`，实测 952 条）与
  数据包自定义招式（`data/cobblemon/moves/<id>.js`，实测 435 条，赛尔号那些）
- 一键 **还原原版**

「技能修改」改的是**宝可梦学得到哪些招式**（学习表）；「招式数据」改的是**招式有多强**，两者互不影响。

## 实现要点

招式定义不是 JSON，而是"JSON + 内嵌函数"的脚本，直接做字符串替换会连函数体里的同名量一起改掉
（"预知未来"的 `onTry` 里就嵌着一份 `basePower`）。因此：

- 用**带括号深度跟踪的扫描器**只改最外层字段，函数与其它字段原样保留
- 覆盖层新增**文本通道**，把改好的脚本写进
  `config/cobblemonmodifier/overrides/data/cobblemon/moves/<id>.js`，再同步为世界数据包
- 统一写进 `cobblemon` 命名空间：招式以**文件名**为 id，世界数据包在模组之后加载，必定盖过所有来源
- 索引"一个压缩包只打开一次"：1387 个招式实测 285 ms
- 输入校验：威力 0~999、命中 1~100（必中填 `true`）、PP 1~200、优先级 -7~7、属性须为 18 种之一

## 数据安全

- **不改写任何原模组 JAR**，改动只落在覆盖目录，删文件即还原
- 保存时只写改动过的字段；实测 952 条基础招式全部能抽取成独立脚本（0 失败）
- 单元测试 254 → **277 个**，全部通过

## 重要限制

- **只对单机（本机世界）有效**：这是客户端工具，通过写本机存档的数据包生效，多人服务器上不生效
- 改动需要**重进世界**才生效：Cobblemon 的招式数据只在世界加载时读取，不支持运行时重载

## 文档

- [README](https://github.com/RUO-rr/cobblemon-modifier#readme)
- [架构演进与技术决策](https://github.com/RUO-rr/cobblemon-modifier/blob/main/ARCHITECTURE.md)（本次新增 C7：当"数据"不是 JSON）
- [需求驱动开发记录](https://github.com/RUO-rr/cobblemon-modifier/blob/main/docs/user-feedback.md)
- [更新日志](https://github.com/RUO-rr/cobblemon-modifier/blob/main/CHANGELOG.md)
