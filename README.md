<h2 align="center"><a href="#zh">中文</a> · <a href="#en">English</a></h2>

---

<a id="zh"></a>

# Waypoint Menu（路径点集 / 地点菜单）

一个 **Fabric 客户端** Mod：记录地点坐标，为每个地点挂载一个「指令集」，并在世界中以 **菱形标记 + 名称/距离标签** 高亮显示目标地点。

- 🧭 记录当前位置，随手标记关键地点
- 🔷 世界内 3D 菱形标记 + 文字标签，无视遮挡、始终面向镜头
- 📡 超出视距也能看到远处地点的方位与距离（屏幕空间 HUD 标签）
- ⚡ 每个地点可携带一组指令，一键顺序执行（支持延时）
- 🗂 按维度筛选、拖拽排序、一键传送、自定义颜色

> 项目代码由 AI 生成。

---

## 目录

- [兼容版本](#兼容版本)
- [安装](#安装)
- [快速上手](#快速上手)
- [功能详解](#功能详解)
- [配置项](#配置项)
- [数据存储](#数据存储)
- [构建（开发者）](#构建开发者)
- [项目结构](#项目结构)

---

## 兼容版本

本 Mod 为 **1.20 ~ 1.21.11 的每个小版本**单独构建一个 Jar（共 19 个），每个 Jar 的 `mc_compat` 精确锁定单一版本，且均已实机验证：

| Minecraft | Fabric Loader | Fabric API | 状态 |
|-----------|---------------|------------|------|
| 1.20 | 0.19.3 | 0.83.0+1.20 | ✅ 已验证 |
| 1.20.1 | 0.19.3 | 0.92.11+1.20.1 | ✅ 已验证 |
| 1.20.2 | 0.19.3 | 0.91.6+1.20.2 | ✅ 已验证 |
| 1.20.3 | 0.19.3 | 0.91.1+1.20.3 | ✅ 已验证 |
| 1.20.4 | 0.19.3 | 0.97.3+1.20.4 | ✅ 已验证 |
| 1.20.5 | 0.19.3 | 0.97.8+1.20.5 | ✅ 已验证 |
| 1.20.6 | 0.19.3 | 0.100.8+1.20.6 | ✅ 已验证 |
| 1.21 | 0.19.3 | 0.102.0+1.21 | ✅ 已验证 |
| 1.21.1 | 0.19.3 | 0.116.15+1.21.1 | ✅ 已验证 |
| 1.21.2 | 0.19.3 | 0.106.1+1.21.2 | ✅ 已验证 |
| 1.21.3 | 0.19.3 | 0.114.1+1.21.3 | ✅ 已验证 |
| 1.21.4 | 0.19.3 | 0.119.4+1.21.4 | ✅ 已验证 |
| 1.21.5 | 0.19.3 | 0.128.2+1.21.5 | ✅ 已验证 |
| 1.21.6 | 0.19.3 | 0.128.2+1.21.6 | ✅ 已验证 |
| 1.21.7 | 0.19.3 | 0.129.0+1.21.7 | ✅ 已验证 |
| 1.21.8 | 0.19.3 | 0.136.1+1.21.8 | ✅ 已验证 |
| 1.21.9 | 0.19.3 | 0.134.1+1.21.9 | ✅ 已验证 |
| 1.21.10 | 0.19.3 | 0.138.4+1.21.10 | ✅ 已验证 |
| 1.21.11 | 0.19.3 | 0.141.4+1.21.11 | ✅ 已验证 |

> 说明：
> - **1.21.9**：Fabric API 的 `rendering-v1 16.0.1` 移除了世界渲染事件（`WorldRenderEvents`），该版本**无世界内 3D 菱形标记**，高亮地点全部退化为「超视距 HUD 标签」方式显示（其余功能正常）。
> - ✅ 所有版本均已实机验证。

---

## 安装

### 前置依赖

- **Minecraft** 与对应版本的 **Fabric Loader**（推荐 `0.19.3`）
- **Fabric API**（本 Mod 使用 `fabric-rendering-v1` 做世界高亮渲染，需安装对应 MC 版本的 Fabric API）
- **Mod Menu**（可选，仅用于在游戏内打开配置界面）
- **Java**：Minecraft 1.20.5+ 需 Java 21；1.20 ~ 1.20.4 用 Java 17

### 安装步骤

1. 按你的 MC 版本，从上表选择对应的 `waypointmenu-*.jar`。
2. 放入 `.minecraft/mods/`（需同装 Fabric Loader + Fabric API）。
3. 启动游戏，即可按 `G` 键打开地点列表。

---

## 快速上手

1. 按 **`G`** 打开地点列表。
2. 点击 **「添加」**，记录当前站立位置（自动打开编辑器）。
3. 在编辑器里设置名称、维度、颜色、坐标，可选填描述与指令集。
4. 保存后，点击该行或 `◆` 按钮开启高亮，世界内即可看到菱形 + 标签。
5. 点击行右侧 `▶` 执行该地点的指令集；右键行（若开启右键传送）可传送过去。

---

## 功能详解

### 地点列表

![地点列表](./images/list.png)

- 左侧**侧边栏**按维度筛选：`全部` / `主世界` / `下界` / `末地`。
- 每行显示：**颜色圆点**（青色 = 已高亮）、**名称**、**坐标 + 维度**、**描述摘要**。
- 行内操作按钮（右起）：
  - `◆` 切换高亮
  - `▶` 执行指令集
  - `✎` 编辑
  - `✕` 删除
- 行主体：**左键**切换高亮；**左键长按并拖动**移动列表项；**右键**传送（需开启右键传送）。
- 鼠标滚轮滚动列表。

### 拖拽排序

![拖拽排序](./images/drag_reorder.png)

**左键长按并拖动**列表项即可调整顺序：按住列表行不放、上下拖动到目标位置后松开；若只是单击（未拖动）则切换高亮。

### 编辑器

![地点编辑器](./images/editor.png)

- **名称**：最多 64 字符。
- **维度**：点击按钮在 主世界 / 下界 / 末地 间切换。
- **颜色**：8 个预设色块，点击选择高亮颜色。
- **坐标**：X / Y / Z 三个整数输入框。
- **指令集**：最多同时显示 3 行，可滚动；每行右侧 `✕` 删除、`＋` 在下方插入一行。
- **描述**：多行描述（1.21.5+ 支持自动换行与滚动；旧版本为单行）。

### 指令集语法

每行一条，按顺序执行：

| 写法 | 含义 |
|------|------|
| `/tp @p 100 64 100` | 以 `/` 开头 → 作为命令执行（自动去掉斜杠） |
| `你好` | 普通文本 → 作为聊天消息发送（超过 256 字符截断） |
| `#sleep 20` | 等待 20 tick 后再执行下一条（`#delay` 同义，`##sleep` 也可） |

### 传送

- 右键行主体即可传送（需在配置里开启「右键传送」）。
- **同维度**：直接 `/tp`。
- **跨维度**：`/execute in <维度> run tp`，且需同时开启「跨维度传送」，否则提示无法传送。
- **创造模式**：无视「右键传送」「跨维度传送」两个开关，始终可传送（含跨维度）。

### 世界内高亮

![世界内高亮](./images/world_marker.png)

开启高亮后，视距内会在该地点上方 2 格处绘制一个**半透明、始终面向镜头**的菱形，并在其上方绘制 **`名称 距离`** 文字标签，两者均**无视遮挡**（透墙可见）。

### 超视距 HUD 标签

![超视距 HUD 标签](./images/far_label.png)

超出渲染距离后，3D 标记会被引擎远平面裁剪，此时自动切换为**屏幕空间 HUD**，只绘制 `名称 距离` 文字，让你始终知道地点的方位与距离。

### 配置界面

![配置界面](./images/config.png)

从 Mod Menu 进入「地点菜单设置」即可在游戏内调整各项配置（详见下方 [配置项](#配置项)）。

---

## 配置项

从 Mod Menu 进入「地点菜单设置」，或点击列表/配置里的入口。所有修改点「保存」后才写入磁盘。

| 配置项 | 默认值 | 说明 | 范围 |
|--------|--------|------|------|
| 标签定屏距离 | 10 格 | 超过该距离后标签保持恒定屏幕大小（不随距离缩小） | 1 ~ 128，步进 1 |
| 菱形不透明度 | 35% | 菱形标记的透明度 | 5% ~ 100%，步进 5% |
| 菱形显示距离 | 128 格 | 超过后菱形消失、只留标签 | 16 ~ 视距×16，步进 16 |
| 菱形距离缩放 | 开 | 菱形是否像标签一样随距离放大 | 开 / 关 |
| 文字标签显示 | 开 | 是否显示名称 + 距离标签 | 开 / 关 |
| 跨维度传送 | 关 | 允许跨维度传送（需右键传送开启） | 开 / 关 |
| 右键传送 | 关 | 列表中右键地点可传送 | 开 / 关 |
| 按键绑定 | `G` | 打开列表的组合键 | 任意按键组合 |

> **按键绑定**：点击按钮进入录制，依次按下组合键的每个键；`Esc` 清空绑定（解绑）。

---

## 数据存储

- **地点列表**：`<游戏目录>/config/waypointmenu/waypoints_<世界标识>.json`
  - 每个世界单独一个文件，互不共享。
  - 世界标识：单人世界为 `sp_<世界名>`，服务器为 `mp_<服务器地址>`。
- **配置**：`<游戏目录>/config/waypointmenu/config.json`
- **高亮状态**：会话内临时状态，不持久化。

---

## 构建（开发者）

使用 [Stonecutter](https://stonecutter.kikugie.dev/) 多版本构建，一份源码编译出多个 MC 版本（Yarn 映射 + remapJar）。

1. 用 **IntelliJ IDEA** 打开本目录（自动识别 Gradle 项目，下载 Gradle 与依赖）。
2. 切换目标版本并构建，例如：
   ```bash
   ./gradlew :1.21.11:remapJar      # 构建单个版本
   ./gradlew :1.20.1:remapJar :1.21.1:remapJar  # 批量构建
   ```
3. 产物位于 `versions/<版本>/build/libs/waypointmenu-1.0.0+<mc_range>.jar`，已汇总副本在 `output/` 目录。

> 首次构建会下载 Minecraft、Yarn 映射与 Fabric API，耗时较长属正常现象。
> Java 版本：1.20 ~ 1.20.4 用 Java 17；1.20.5+ 用 Java 21。
> 各版本依赖（Yarn / Fabric API / Mod Menu）在 `stonecutter.properties.toml` 中集中配置。


## 项目结构

```
src/main/java/com/waypointmenu/
├── WaypointMenuClient.java          入口（组合键检测 + tick + 注册渲染）
├── command/CommandSetExecutor.java   指令集顺序执行 + #sleep 延迟
├── compat/ModMenuIntegration.java    Mod Menu 配置界面入口
├── config/WaypointConfig.java        配置（不透明度/标签距离/显示标签/组合键等）
├── data/Waypoint.java               地点数据模型
├── data/WaypointManager.java        列表存储 + 按世界 JSON 持久化 + 高亮状态
├── mixin/RenderLayerInvoker.java     访问 RenderLayer.of(String, RenderSetup)（1.21.11+）
├── mixin/RenderPipelinesAccessor.java 访问 position-color 渲染管线（1.21.5+）
├── render/WaypointRenderer.java     世界内高亮渲染 + 超视距 HUD 兜底
├── screen/WaypointListScreen.java   地点列表 UI
├── screen/WaypointEditScreen.java   地点编辑器（名称/维度/颜色/坐标/指令集/描述）
├── screen/WaypointConfigScreen.java 配置界面
└── ui/Ui.java                       通用 UI 绘制辅助
src/main/java/net/minecraft/client/render/
└── WaypointMenuHighlightLayer.java   渲染层辅助（<1.21.5 经典层；1.21.5~1.21.10 管线层）
```

---

<a id="en"></a>

# Waypoint Menu

A **Fabric client** mod that records locations, attaches a set of commands to each one, and highlights them in the world with a **diamond marker + name/distance label**.

- 🧭 Record your current position and mark key spots on the fly
- 🔷 In-world 3D diamond marker + text label, see-through and always facing the camera
- 📡 See the bearing and distance of far-off waypoints even beyond render distance (screen-space HUD label)
- ⚡ Each waypoint can carry a command set, run in order with one click (supports delays)
- 🗂 Filter by dimension, drag-to-reorder, one-click teleport, custom colors

> The project code is generated by AI.

---

## Table of Contents

- [Compatibility](#compatibility)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Features](#features)
- [Configuration](#configuration)
- [Data Storage](#data-storage)
- [Building (Developers)](#building-developers)
- [Project Structure](#project-structure)

---

## Compatibility

This mod builds one Jar for **every 1.20–1.21.11 minor version** (19 in total); each Jar's `mc_compat` locks to exactly one version, and all of them have been verified on real machines:

| Minecraft | Fabric Loader | Fabric API | Status |
|-----------|---------------|------------|--------|
| 1.20 | 0.19.3 | 0.83.0+1.20 | ✅ Verified |
| 1.20.1 | 0.19.3 | 0.92.11+1.20.1 | ✅ Verified |
| 1.20.2 | 0.19.3 | 0.91.6+1.20.2 | ✅ Verified |
| 1.20.3 | 0.19.3 | 0.91.1+1.20.3 | ✅ Verified |
| 1.20.4 | 0.19.3 | 0.97.3+1.20.4 | ✅ Verified |
| 1.20.5 | 0.19.3 | 0.97.8+1.20.5 | ✅ Verified |
| 1.20.6 | 0.19.3 | 0.100.8+1.20.6 | ✅ Verified |
| 1.21 | 0.19.3 | 0.102.0+1.21 | ✅ Verified |
| 1.21.1 | 0.19.3 | 0.116.15+1.21.1 | ✅ Verified |
| 1.21.2 | 0.19.3 | 0.106.1+1.21.2 | ✅ Verified |
| 1.21.3 | 0.19.3 | 0.114.1+1.21.3 | ✅ Verified |
| 1.21.4 | 0.19.3 | 0.119.4+1.21.4 | ✅ Verified |
| 1.21.5 | 0.19.3 | 0.128.2+1.21.5 | ✅ Verified |
| 1.21.6 | 0.19.3 | 0.128.2+1.21.6 | ✅ Verified |
| 1.21.7 | 0.19.3 | 0.129.0+1.21.7 | ✅ Verified |
| 1.21.8 | 0.19.3 | 0.136.1+1.21.8 | ✅ Verified |
| 1.21.9 | 0.19.3 | 0.134.1+1.21.9 | ✅ Verified |
| 1.21.10 | 0.19.3 | 0.138.4+1.21.10 | ✅ Verified |
| 1.21.11 | 0.19.3 | 0.141.4+1.21.11 | ✅ Verified |

> Notes:
> - **1.21.9**: Fabric API's `rendering-v1 16.0.1` removed the world render events (`WorldRenderEvents`), so that version has **no in-world 3D diamond marker**; highlighted waypoints all fall back to the "far-label HUD" display (other features work normally).
> - ✅ All versions have been verified on real machines.

---

## Installation

### Prerequisites

- **Minecraft** with a matching **Fabric Loader** (0.19.3 recommended)
- **Fabric API** (this mod uses `fabric-rendering-v1` for in-world highlighting; install the Fabric API that matches your MC version)
- **Mod Menu** (optional, only used to open the in-game config screen)
- **Java**: Minecraft 1.20.5+ requires Java 21; 1.20–1.20.4 uses Java 17

### Steps

1. Pick the `waypointmenu-*.jar` that matches your MC version from the table above.
2. Drop it into `.minecraft/mods/` (alongside Fabric Loader + Fabric API).
3. Launch the game and press `G` to open the waypoint list.

---

## Quick Start

1. Press **`G`** to open the waypoint list.
2. Click **"Add"** to record your current position (opens the editor automatically).
3. In the editor, set the name, dimension, color and coordinates; optionally add a description and a command set.
4. After saving, click the row or the `◆` button to enable highlighting and see the diamond + label in the world.
5. Click `▶` on the right of a row to run its command set; right-click the row (with right-click teleport enabled) to teleport to it.

---

## Features

### Waypoint List

![Waypoint list](./images/list.png)

- The left **sidebar** filters by dimension: `All` / `Overworld` / `Nether` / `End`.
- Each row shows: a **color dot** (cyan = highlighted), the **name**, **coordinates + dimension**, and a **description summary**.
- Per-row action buttons (right to left):
  - `◆` toggle highlight
  - `▶` run command set
  - `✎` edit
  - `✕` delete
- Row body: **left-click** toggles highlight; **hold left-click and drag** moves the item; **right-click** teleports (requires right-click teleport enabled).
- Scroll with the mouse wheel.

### Drag to Reorder

![Drag to reorder](./images/drag_reorder.png)

**Hold left-click and drag** a list item to reorder it: keep the row pressed, drag up/down to the target position and release; a plain click (no drag) toggles the highlight instead.

### Editor

![Waypoint editor](./images/editor.png)

- **Name**: up to 64 characters.
- **Dimension**: click the button to switch between Overworld / Nether / End.
- **Color**: 8 preset swatches; click one to pick the highlight color.
- **Coordinates**: three integer inputs for X / Y / Z.
- **Command set**: shows up to 3 lines at once, scrollable; `✕` deletes a line, `＋` inserts a line below.
- **Description**: multi-line (1.21.5+ supports word wrap and scrolling; older versions are single-line).

### Command Set Syntax

One command per line, run in order:

| Syntax | Meaning |
|--------|---------|
| `/tp @p 100 64 100` | Starts with `/` → run as a command (the slash is stripped automatically) |
| `hello` | Plain text → sent as a chat message (truncated past 256 characters) |
| `#sleep 20` | Wait 20 ticks before the next line (`#delay` is a synonym, `##sleep` also works) |

### Teleport

- Right-click the row body to teleport (requires "right-click teleport" in config).
- **Same dimension**: plain `/tp`.
- **Cross-dimension**: `/execute in <dimension> run tp`; requires "cross-dimension teleport" enabled, otherwise it reports it cannot teleport.
- **Creative mode**: always allowed, ignoring both toggles (including cross-dimension).

### In-World Highlight

![In-world highlight](./images/world_marker.png)

When highlighted, a **translucent, always camera-facing** diamond is drawn 2 blocks above the waypoint within render distance, with a **`name distance`** text label above it. Both are **see-through** (visible through walls).

### Far-Label HUD

![Far-label HUD](./images/far_label.png)

Past render distance the 3D marker is clipped by the far plane, so it automatically switches to a **screen-space HUD** that draws only the `name distance` text, so you always know a waypoint's bearing and distance.

### Config Screen

![Config screen](./images/config.png)

Open "Waypoint Menu Settings" from Mod Menu to adjust options in-game (see [Configuration](#configuration) below).

---

## Configuration

Open "Waypoint Menu Settings" from Mod Menu, or use the entry in the list/config screen. Changes are written to disk only after you click "Save".

| Option | Default | Description | Range |
|--------|---------|-------------|-------|
| Label fixed-size distance | 10 blocks | Past this distance the label keeps a constant on-screen size | 1 ~ 128, step 1 |
| Diamond opacity | 35% | Opacity of the diamond marker | 5% ~ 100%, step 5% |
| Diamond display distance | 128 blocks | Past this the diamond disappears, leaving only the label | 16 ~ view-distance×16, step 16 |
| Diamond distance scaling | On | Whether the diamond scales with distance like the label | On / Off |
| Show text label | On | Whether to show the name + distance label | On / Off |
| Cross-dimension teleport | Off | Allow teleporting across dimensions (requires right-click teleport) | On / Off |
| Right-click teleport | Off | Right-click a waypoint in the list to teleport | On / Off |
| Key binding | `G` | Key combo to open the list | Any key combo |

> **Key binding**: click the button to enter recording, then press each key of the combo in turn; `Esc` clears the binding (unbinds).

---

## Data Storage

- **Waypoint list**: `<game dir>/config/waypointmenu/waypoints_<world id>.json`
  - One file per world, not shared.
  - World id: `sp_<world name>` for single-player, `mp_<server address>` for servers.
- **Config**: `<game dir>/config/waypointmenu/config.json`
- **Highlight state**: temporary per-session state, not persisted.

---

## Building (Developers)

Built with [Stonecutter](https://stonecutter.kikugie.dev/) multi-version builds: one source tree compiles to many MC versions (Yarn mappings + remapJar).

1. Open this directory in **IntelliJ IDEA** (it auto-detects the Gradle project and downloads Gradle + dependencies).
2. Switch to a target version and build, for example:
   ```bash
   ./gradlew :1.21.11:remapJar      # build a single version
   ./gradlew :1.20.1:remapJar :1.21.1:remapJar  # batch build
   ```
3. Outputs go to `versions/<version>/build/libs/waypointmenu-1.0.0+<mc_range>.jar`, with copies collected in the `output/` directory.

> The first build downloads Minecraft, Yarn mappings and Fabric API, which takes a while — that's normal.
> Java version: 1.20–1.20.4 uses Java 17; 1.20.5+ uses Java 21.
> Per-version dependencies (Yarn / Fabric API / Mod Menu) are configured centrally in `stonecutter.properties.toml`.

---

## Project Structure

```
src/main/java/com/waypointmenu/
├── WaypointMenuClient.java           Entry point (key-combo detection + tick + render registration)
├── command/CommandSetExecutor.java   Runs the command set in order + #sleep delays
├── compat/ModMenuIntegration.java    Mod Menu config screen entry
├── config/WaypointConfig.java        Config (opacity / label distance / show label / key combo, etc.)
├── data/Waypoint.java                Waypoint data model
├── data/WaypointManager.java         List storage + per-world JSON persistence + highlight state
├── mixin/RenderLayerInvoker.java     Accesses RenderLayer.of(String, RenderSetup) (1.21.11+)
├── mixin/RenderPipelinesAccessor.java Accesses the position-color render pipeline (1.21.5+)
├── render/WaypointRenderer.java      In-world highlight rendering + far-label HUD fallback
├── screen/WaypointListScreen.java    Waypoint list UI
├── screen/WaypointEditScreen.java    Waypoint editor (name/dimension/color/coords/commands/description)
├── screen/WaypointConfigScreen.java  Config screen
└── ui/Ui.java                        Shared UI drawing helpers
src/main/java/net/minecraft/client/render/
└── WaypointMenuHighlightLayer.java   Render-layer helper (<1.21.5 classic layer; 1.21.5–1.21.10 pipeline layer)
```
