# Waypoint Menu（地点菜单）

一个 Fabric **客户端** Mod：记录地点坐标，为每个地点挂载一个「指令集」，在列表中点击坐标即可在世界上**高亮**目标地点。

指令集与列表 UI 的呈现方式参考了 [quick-menu](https://github.com/tenkun0317/quick-menu)。

代码由AI生产。

## 功能

| 需求                    | 实现 |
|-----------------------|------|
| 1. 记录地点坐标，点击坐标后高亮目标地点 | `G` 键打开列表，「添加」记录当前坐标；点击行 / 坐标 → 在世界上绘制菱形标记 + 名称/距离标签 |
| 2. 每个地点携带一个指令集        | 编辑器内可增删指令，行右侧 `▶` 一键执行整组指令 |
| 3. 每个坐标以列表项形式呈现       | 可滚动的列表，每行显示名称、坐标、维度，支持执行/编辑/删除/高亮 |

## 前置（依赖）

- **JDK 21**
- **Fabric Loader** `0.19.3`
- **Fabric API** `0.141.6+1.21.11`
- **Minecraft** `1.21.11`

本 Mod 只依赖 Fabric API（使用 `fabric-rendering-v1` 做世界高亮渲染），无需额外的库 Mod。
已集成 **Mod Menu**（可选）作为游戏内配置界面入口，配置界面为自绘，不依赖 Cloth Config。

> **如何根据 Mod 需要寻找前置：** 在 <https://fabricmc.net/develop> 可查到当前 MC 版本对应的
> Loader / Yarn / Fabric API 版本；Fabric API 各版本见
> <https://modrinth.com/mod/fabric-api/versions?g=1.21.11>。版本校验接口：
> `https://meta.fabricmc.net/v2/versions/yarn/1.21.11`。

## 构建

1. 用 **IntelliJ IDEA** 打开本目录（会自动识别 Gradle 项目，下载 Gradle 9.5.1 与依赖）。
2. 运行 `./gradlew build`（Windows 用 `gradlew.bat`），或点击右侧 Gradle 面板的 `build`。
3. 产物在 `build/libs/waypointmenu-1.0.0.jar`，放入 `.minecraft/mods` 即可（需同装 Fabric Loader + Fabric API）。

> 首次构建会下载 Minecraft、映射与 Fabric API，耗时较长属正常现象。


## 使用

- 默认 **`G`** 键打开地点列表；可在配置界面「按键绑定」里改成其他组合键。
- 列表项左侧圆点表示高亮状态（青色 = 已高亮）；点击行或坐标切换高亮。
- 行右侧按钮：`▶` 执行指令集、`✎` 编辑、`✕` 删除。
- 「添加」记录当前站立位置并打开编辑器；编辑器可修改名称、维度、坐标，并增删指令。

## 指令集语法

每行一条，顺序执行：

| 写法 | 含义 |
|------|------|
| `/tp @p 100 64 100` | 以 `/` 开头 → 作为命令执行（自动去掉斜杠） |
| `你好` | 普通文本 → 作为聊天消息发送 |
| `#sleep 20` | 等待 20 tick 后再执行下一条（`#delay` 同义） |

## 数据存储

- 地点列表：`<游戏目录>/config/waypointmenu/waypoints_<世界标识>.json`（每个世界单独一个文件，标识为单人世界名或服务器地址）
- 配置：`<游戏目录>/config/waypointmenu/config.json`
- 高亮状态为会话内临时状态，不持久化。

## 目录结构

```
src/main/java/com/waypointmenu/
├── WaypointMenuClient.java          入口（组合键检测 + tick + 注册渲染）
├── command/CommandSetExecutor.java   指令集顺序执行 + #sleep 延迟
├── compat/ModMenuIntegration.java    Mod Menu 配置界面入口
├── config/WaypointConfig.java        配置（不透明度/标签距离/显示标签/组合键）
├── data/Waypoint.java               地点数据模型
├── data/WaypointManager.java        列表存储 + 按世界 JSON 持久化 + 高亮状态
├── mixin/RenderLayerInvoker.java     访问 RenderLayer 构造函数
├── mixin/RenderPipelinesAccessor.java  访问 position-color 渲染管线
├── render/WaypointRenderer.java     世界内高亮渲染（WorldRenderEvents）
├── screen/WaypointListScreen.java   地点列表 UI
├── screen/WaypointEditScreen.java   地点编辑器（名称/维度/坐标/指令集）
├── screen/WaypointConfigScreen.java 配置界面（不透明度/标签距离/显示标签/组合键）
└── ui/Ui.java                       通用 UI 绘制辅助
```
