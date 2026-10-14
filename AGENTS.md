# AGENTS.md — magconch-engine 核心开发规范与执行指令

## 1. 交互与决策规范 (Interaction & Decisions)
- **【语言要求】**：始终使用**简体中文**进行所有交互、思考、回复与代码注释。
- **【方案先行】**：**修改任何代码前，必须先提供具体方案与改动范围，获得用户明确确认后方可落盘执行。**
- **【暴露假设】**：遇到需求歧义、多种技术路径或隐性权衡时，**必须列出选项供用户决策**，严禁擅自做主。
- **【精炼沟通】**：日常答复 ≤ 3 行；方案陈述直奔核心要点与改动范围，拒绝客套与冗余铺垫。

---

## 2. Minecraft 插件专项工程准则 (Minecraft Engineering Principles)

### 2.1 兼容性与服务端标准
- **【Paper API 优先】**：
  - 以 **Paper API 1.21.4+** 作为核心依赖底座，**保证在 Paper、Purpur（含 26.x 现代版本）上 100% 通用无报错**。
  - 优先使用 Paper 提供的 Adventure API（`net.kyori.adventure.*`）处理文本组件与交互，避免使用过时的 BungeeCord Chat API。
  - Paper API 是 Spigot API 的超集，向后兼容标准 Bukkit/Spigot API，但**不保证在纯 Spigot 服务端上运行**（1.20.5+ 后 Paper 引入的 Data Components 等特性在纯 Spigot 上不可用）。
- **【运行时与编译目标】**：统一基于 **Java 21** 编译目标，严格适配现代 JVM 运行环境。
- **【物品组件保真 (Data Components)】**：
  - 自 1.20.5 及后续 26.x 版本起，物品数据已彻底改版为 Data Components。
  - 临时性操作严禁使用过时的 Java 原生对象流或第三方旧序列化库拆解 `ItemStack`，优先直接引用对象，防止物品组件丢失、损坏或附魔失真。

### 2.2 性能与生命周期守则 (Life Cycle & Zero Leak)
- **【持久化审慎原则 (严禁 NBT 泄漏)】**：
  - 明确区分“持久化数据”与“瞬态业务状态”。
  - 持续时间极短的业务状态（如超时撤回、临时冷却、战斗标记）**严禁写入 Chunk PDC / NBT**，杜绝世界存档暴涨与保存卡顿；强制使用纯内存模型（如基于 ConcurrentHashMap + 定时淘汰）。
- **【命令与监听器安全注销】**：
  - 严禁在代码中通过反射侵入服务端的私有字段（如 `CraftServer.commandMap`）。
  - 执行插件重载（`/mcore reload`）时，严禁重复向服务端注册命令实例，杜绝命令映射表内存泄漏。
- **【异步调度与主线程安全】**：
  - 任何调用 Bukkit API 修改世界（设置方块、给予物品、触发事件）的操作，**必须且只能在主线程执行**。
  - 异步任务在插件 `onDisable()` 时必须完全取消与注销。

### 2.3 安全防刷与边界防御 (Security & Exploit Prevention)
- **【方块实体保护 (TileState)】**：
  - 任何方块回滚/替换逻辑，必须主动检测被替换方块是否包含方块实体（`TileState`，如箱子、熔炉、潜影盒、告示牌等）。
  - 严禁对未知或未做深度 NBT 序列化的容器方块进行强制复原，杜绝物品凭空蒸发或复制刷物品漏洞。
- **【物品归还防丢机制】**：
  - 归还物品给玩家时，必须优先使用 `player.getInventory().addItem(item)`。
  - 仅当玩家背包满载时，才可使用 `world.dropItemNaturally` 安全掉落，杜绝直接硬掉落导致被周围漏斗吸走或他人捡拾。

---

## 3. 架构设计规范 (Modular Engine Architecture)

### 3.1 模块化微内核模式
- **【职责单一】**：
  - 主类 `MagconchEngine` 仅负责插件生命周期引导、核心管理器初始化，**严禁在主类堆砌具体业务逻辑与事件监听器**。
  - 每个功能特性独立为一个模块包（`com.magconch.engine.modules.<feature>`），并实现 `EngineModule` 统一契约接口。
- **【配置与多语言完全解耦】**：
  - 主配置 `config.yml` 采用结构化分段：全局设置（语言、debug） + 各模块专属配置段（`modules.<feature-id>.*`）。
  - 所有面向玩家/控制台的提示文本，必须统一抽离至 `languages/<lang>.yml`（支持中英双语切换与占位符动态插值），严禁在 Java 代码中硬编码中文字符串。
- **【统一子命令总线】**：
  - 所有功能命令统一挂载在主指令 `/magconch`（别名 `/mcore`, `/mce`）下，由 `MainCommand` 统一分发并提供自动 Tab 补全与权限控制。

---

## 4. 工具认知与 CLI 执行边界 (CLI Workflow Boundaries)
- **【零测试套件 & 原生编译必过】**：
  - **严禁生成任何 Mock 夹具或测试类（src/test/**）**。
  - 每次代码变更后，**必须通过终端执行 `mvn clean package` 验证**，确保编译零报错、类型检查完全通过。
- **【外科手术式编写】**：
  - 严禁无意义的“顺手优化”或破坏已有文件格式。
  - 及时清理孤儿 import 与无用变量。
  - 具备清晰的结构化导航注释（模块职能说明、核心代码区块划分、关键业务意图）。
- **【Git 提交规范】**：
  - Commit Message 必须严格遵循 `<type>: <中文描述>` 格式。
  - 常用类型标记：
    - `init: ` 初始化工程或核心架构
    - `feat: ` 新增业务特性或模块
    - `fix: ` 修复 Bug 或逻辑缺陷
    - `docs: ` 文档修改（README、规范等）
    - `style: ` 代码格式、文案或注释微调
    - `refactor: ` 重构与逻辑优化（不改变外部行为）
    - `chore: ` 构建配置、依赖或环境工程变动

