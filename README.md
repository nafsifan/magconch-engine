# Magconch Engine

> 高性能、微内核模块化的 Minecraft 服务器专属核心插件底座。

---

## 🛠️ 技术选型与运行环境

- **运行平台**：Spigot / Paper / Purpur (1.20+)
- **构建工具**：Maven 3.8+
- **编译环境**：Java 21 (LTS)
- **依赖标准**：`spigot-api` 1.20.4-R0.1-SNAPSHOT（无缝兼容现代服务端生态）

---

## 🚀 构建与打包

进入项目根目录执行以下 Maven 打包指令：

```bash
mvn clean package
```

编译产物将生成在 `target/` 目录下：
- `target/magconch-engine-1.0.0-SNAPSHOT.jar`

---

## 📋 指令与权限

### 主命令
- `/magconch` (别名: `/magc`)

### 子命令列表

| 指令 | 权限节点 | 默认分配 | 职能说明 |
| :--- | :--- | :--- | :--- |
| `/magc help` | `magconch.command.use` | 所有人 | 查看当前具备执行权限的指令清单 |
| `/magc reload` | `magconch.command.reload` | OP | 安全热重载配置文件、多语言系统及各运行模块 |
| `/magc modules` | `magconch.command.modules` | OP | 查看当前已挂载所有系统模块的运行状态 |

*超级权限节点：`magconch.admin`（默认分配给 OP，继承上述所有子权限）。*

---

## 🧩 微内核模块接入规范

所有子功能模块均通过实现 `EngineModule` 接口进行解耦集成：

```java
package com.magconch.engine.api;

public class SampleModule implements EngineModule {
    @Override
    public String getId() { return "sample"; }

    @Override
    public void onEnable() {
        // 注册事件监听器、任务调度等
    }

    @Override
    public void onDisable() {
        // 数据持久化与资源卸载
    }

    @Override
    public void onReload() {
        // 模块级配置热重载
    }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    public void setEnabled(boolean enabled) {}
}
```

在插件初始化逻辑中注册：
```java
MagconchEngine.getInstance().getModuleManager().registerModule(new SampleModule());
```

---

## 🌐 国际化与视觉设计

语言文件位于 `plugins/magconch-engine/languages/`：
- `zh_CN.yml`：默认简体中文
- `en_US.yml`：英文对照语言包

支持标准 `&` 颜色代码与 `&#RRGGBB` 现代十六进制渐变色渲染。
