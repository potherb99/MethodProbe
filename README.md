# MethodProbe

轻量级 Java 方法性能分析 Agent，基于 ByteBuddy 字节码增强技术，提供方法计时、调用树追踪、异常捕获和参数快照功能。

## 特性

- **两种计时模式**：Flat（单方法计时）和 Tree（调用树）可独立开关
- **三级粒度配置**：支持包/类/方法级别的监控配置
- **触发模式**：支持超时触发、异常触发，或两者同时触发
- **方法快照**：超阈值/异常时捕获方法参数和异常堆栈，支持 Kryo 序列化
- **异步日志输出**：支持控制台和异步文件输出，带日期滚动
- **字节码增强**：使用 ByteBuddy Advice 机制直接修改字节码，非代理模式
- **阈值过滤**：只打印执行时间超过阈值的方法调用
- **动态配置**：通过 HTTP 接口动态添加监控类
- **JDK 8+ 兼容**

---

## 1. 快速开始

### 1.1 编译

```bash
mvn clean package -DskipTests
```

编译成功后在 `target/` 目录生成 `methodprobe-agent-1.0.0.jar`

### 1.2 使用

```bash
java -javaagent:methodprobe-agent-1.0.0.jar=config=/path/to/agent.properties \
     -jar your-application.jar
```

### 1.3 最小配置示例

```properties
# 启用 Flat 模式，监控 com.example 包下所有方法
probe.flat.enabled=true
probe.flat.packages=com.example
probe.flat.threshold=100
```

### 1.4 Demo 测试

```bash
# 控制台输出
./run-demo.sh console

# 文件输出
./run-demo.sh file
```

---

## 2. 配置说明

### 2.1 Flat 模式（单方法计时）

打印每个方法的独立执行时间，适合性能热点分析、高并发场景。

| 参数                   | 默认值    | 说明                                                                      |
| ---------------------- | --------- | ------------------------------------------------------------------------- |
| `probe.flat.enabled`   | `false`   | 是否启用 Flat 模式                                                        |
| `probe.flat.packages`  | 空        | 监控的包名列表（逗号分隔），如 `com.example.service,com.example.dao`      |
| `probe.flat.classes`   | 空        | 监控的类名列表（逗号分隔），如 `com.example.Controller`                   |
| `probe.flat.methods`   | 空        | 监控的方法列表（格式：`类名.方法名`），如 `com.example.Service.doProcess` |
| `probe.flat.threshold` | `100`     | 触发阈值（毫秒），仅打印超过此值的方法                                    |
| `probe.flat.trigger`   | `timeout` | 触发模式：`timeout`/`exception`/`timeout,exception`                       |

> **粒度说明**：packages/classes/methods 是 **OR** 关系，命中任一即计时

**输出示例：**

```
[2026-01-12 09:27:00] [main] com.example.Service.doProcess - 317.00 ms
```

---

### 2.2 Tree 模式（调用树）

打印方法调用链的树形结构，适合请求链路分析、方法调用关系追踪。

| 参数                        | 默认值       | 说明                                                                |
| --------------------------- | ------------ | ------------------------------------------------------------------- |
| `probe.tree.enabled`        | `false`      | 是否启用 Tree 模式                                                  |
| `probe.tree.entry.methods`  | 空           | 入口方法列表（逗号分隔），如 `com.example.Controller.handleRequest` |
| `probe.tree.packages`       | 空           | 包含的包名列表，入口方法调用的这些包下的方法会被纳入调用树          |
| `probe.tree.threshold`      | `100`        | 触发阈值（毫秒），整棵调用树耗时超过此值才打印                      |
| `probe.tree.trigger`        | `timeout`    | 触发模式：`timeout`/`exception`/`timeout,exception`                 |
| `probe.tree.snapshot.probe` | `entry_only` | 快照范围：`entry_only`=仅入口方法；`all`=树中所有方法               |

**输出示例：**

```
╔══════════════════════════════════════════════════════════════════════════════
║ [2026-01-11 21:26:32.543] [main] Method Call Tree
╠══════════════════════════════════════════════════════════════════════════════
║ └── com.example.Controller.handleRequest - 277.64 ms
║     ├── com.example.Service.loadData - 87.41 ms
║     │   └── com.example.Dao.query - 55.07 ms
║     └── com.example.Service.process - 91.62 ms
╚══════════════════════════════════════════════════════════════════════════════
```

---

### 2.3 触发模式

配置何时触发日志输出：**超时触发** 或 **异常触发**，两者可同时启用。

| 参数                     | 适用于   | 说明                |
| ------------------------ | -------- | ------------------- |
| `probe.flat.trigger`     | Flat     | Flat 模式的触发条件 |
| `probe.tree.trigger`     | Tree     | Tree 模式的触发条件 |
| `probe.snapshot.trigger` | Snapshot | 快照捕获的触发条件  |

| 值                            | 含义                     |
| ----------------------------- | ------------------------ |
| `timeout`                     | 仅超过阈值时触发（默认） |
| `exception`                   | 仅方法抛出异常时触发     |
| `timeout,exception` 或 `both` | 超时或异常都触发         |

**异常触发输出示例：**

```
[2026-01-12 12:25:42] [main] handleRequest - 40.68 ms [EXCEPTION: RuntimeException]

╔══════════════════════════════════════════════════════════════════════════════
║ [2026-01-12 12:25:42] [main] Method Call Tree
║ ⚠ Exception: java.lang.RuntimeException
╠══════════════════════════════════════════════════════════════════════════════
║ └── handleRequest - 41.17 ms [EXCEPTION: RuntimeException]
╚══════════════════════════════════════════════════════════════════════════════
```

---

### 2.4 异常过滤

配置只捕获特定类型的异常，或忽略某些异常。**仅当 trigger 包含 `exception` 时生效**。

| 参数                          | 默认值 | 说明                                                                   |
| ----------------------------- | ------ | ---------------------------------------------------------------------- |
| `probe.exception.include`     | 空     | 只捕获的异常类型（逗号分隔），为空表示捕获所有。支持简单类名或完整类名 |
| `probe.exception.exclude`     | 空     | 忽略的异常类型（逗号分隔），**优先级高于 include**                     |
| `probe.exception.stack.depth` | `10`   | 堆栈深度限制，只保存前 N 帧堆栈（减少性能开销）                        |

**过滤逻辑**：`exclude` 优先 → 检查 `include`（为空则全部通过）→ 捕获异常

**示例**：

```properties
# 只捕获 SQLException 和 IllegalAccessException
probe.exception.include=SQLException,IllegalAccessException

# 忽略 CancellationException（即使在 include 中也会被排除）
probe.exception.exclude=CancellationException

# 只保存前 5 帧堆栈（提高性能）
probe.exception.stack.depth=5
```

---

### 2.5 日志输出

| 参数                          | 默认值         | 说明                                                              |
| ----------------------------- | -------------- | ----------------------------------------------------------------- |
| `probe.output.mode`           | `console`      | 输出模式：`console`=控制台（同步阻塞）；`file`=文件（异步非阻塞） |
| `probe.output.dir`            | `./probe-logs` | 日志文件目录（仅 `file` 模式）                                    |
| `probe.output.buffer.size`    | `10000`        | 异步队列大小（仅 `file` 模式）                                    |
| `probe.output.flush.interval` | `1000`         | 刷盘间隔（毫秒，仅 `file` 模式）                                  |

> **生产建议**：使用 `probe.output.mode=file`，控制台输出会阻塞业务线程（5-10ms）。

---

### 2.6 快照模式（方法参数捕获）

当方法触发条件满足时，捕获方法参数并持久化到文件，用于"现场还原"。

| 参数                             | 默认值              | 说明                                                                |
| -------------------------------- | ------------------- | ------------------------------------------------------------------- |
| `probe.snapshot.enabled`         | `false`             | 是否启用快照功能                                                    |
| `probe.snapshot.dir`             | `./probe-snapshots` | 快照文件存储目录                                                    |
| `probe.snapshot.trigger`         | `timeout`           | 触发模式：`timeout`/`exception`/`timeout,exception`                 |
| `probe.snapshot.max.object.size` | `1048576`           | 单个参数对象最大序列化字节数（默认 1MB）                            |
| `probe.snapshot.serialize.mode`  | `sync`              | 序列化模式：`sync`=业务线程（数据一致）；`async`=异步线程（低延迟） |
| `probe.snapshot.retention.days`  | `7`                 | 快照文件保留天数                                                    |

**日志与快照关联：**

```
日志输出:
[2026-01-12 09:27:00] [main] handleRequest - 317.00 ms [snap:20260112-092700-408-00009]

快照文件:
probe-snapshots/2026-01-12/20260112-092700-408-00009.snapshot
```

**读取快照（支持通配符）：**

```bash
# 单个文件
java -cp agent.jar com.probe.agent.snapshot.SnapshotReader ./xxx.snapshot

# 通配符 - 所有快照
java -cp agent.jar com.probe.agent.snapshot.SnapshotReader "./probe-snapshots/**/*.snapshot"

# 匹配特定 ID
java -cp agent.jar com.probe.agent.snapshot.SnapshotReader "./probe-snapshots/**/*00031*.snapshot"
```

**快照输出示例：**

```
╔══════════════════════════════════════════════════════════════
║ Method Snapshot
╠══════════════════════════════════════════════════════════════
║ ID:       20260112-092700-089-00001
║ Method:   TreeprobeDemo.handleRequest
║ Duration: 318.80 ms
╠══════════════════════════════════════════════════════════════
║ Arguments:
║   [0] java.lang.String = user123
╚══════════════════════════════════════════════════════════════
```

---

### 2.7 通用配置

| 参数              | 默认值 | 说明                                       |
| ----------------- | ------ | ------------------------------------------ |
| `probe.http.port` | `9876` | HTTP 动态配置接口端口                      |
| `probe.exclude`   | 空     | 排除的类模式（逗号分隔），如 `*Test,*Mock` |

---

## 3. HTTP 动态配置接口

Agent 启动后会在配置的端口启动 HTTP 服务（默认 9876）：

| 接口             | 方法 | 说明                               |
| ---------------- | ---- | ---------------------------------- |
| `/class/add`     | POST | 添加 Flat 监控类 `className=xxx`   |
| `/package/add`   | POST | 添加 Flat 监控包 `packageName=xxx` |
| `/threshold/set` | POST | 设置 Flat 阈值 `threshold=xxx`     |
| `/config`        | GET  | 查看当前配置                       |

```bash
curl -X POST http://localhost:9876/class/add -d "className=com.example.NewClass"
curl http://localhost:9876/config
```

---

## 4. 完整配置文件示例

```properties
# ==================== Flat Mode ====================
probe.flat.enabled=true
probe.flat.packages=com.example.service,com.example.dao
probe.flat.classes=com.example.Controller
probe.flat.threshold=100
probe.flat.trigger=timeout

# ==================== Tree Mode ====================
probe.tree.enabled=true
probe.tree.entry.methods=com.example.Controller.handleRequest
probe.tree.packages=com.example
probe.tree.threshold=50
probe.tree.trigger=timeout,exception

# ==================== Snapshot ====================
probe.snapshot.enabled=true
probe.snapshot.dir=./probe-snapshots
probe.snapshot.trigger=timeout,exception
probe.tree.snapshot.probe=all

# ==================== Exception Filter ====================
probe.exception.include=
probe.exception.exclude=
probe.exception.stack.depth=10

# ==================== Log Output ====================
probe.output.mode=file
probe.output.dir=./probe-logs
probe.output.buffer.size=10000

# ==================== General ====================
probe.http.port=9876
```

---

## 5. 性能开销

| 模式 | 每调用开销 | 适用场景                  |
| ---- | ---------- | ------------------------- |
| Flat | ~100-200ns | 高并发接口，性能影响 <1%  |
| Tree | ~200-400ns | 调用链分析，性能影响 1-3% |

| 快照模式 | 序列化线程 | 延迟影响 | 数据一致性    |
| -------- | ---------- | -------- | ------------- |
| sync     | 业务线程   | 较高     | ✅ 完全一致   |
| async    | 异步线程   | 极低     | ⚠️ 可能不一致 |

> **建议**：生产环境使用 `threshold` 过滤低耗时方法可大幅减少日志量

---

## 6. 注意事项

1. **Tree 优先原则**：当方法同时匹配 Flat 和 Tree，且在 Tree 调用链中时，只打印 Tree 输出
2. **Entry Methods 自动插桩**：`probe.tree.entry.methods` 中的类会自动被插桩
3. **Console 性能影响**：控制台输出是同步阻塞 I/O，生产环境建议用 `file` 模式
4. **端口冲突**：HTTP 端口被占用时 Agent 仍正常工作，仅动态配置不可用
5. **类排除**：默认排除 `java.*`, `javax.*`, `sun.*`, `jdk.*`, `net.bytebuddy.*`

---

## 7. 实现原理

### 7.1 技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        JVM 启动流程                              │
├─────────────────────────────────────────────────────────────────┤
│  JVM 启动 → Agent premain → 读取配置 → ByteBuddy Instrumentation │
│                                              ↓                   │
│                                    ClassFileTransformer          │
│                                              ↓                   │
│                                        匹配目标类                 │
│                                              ↓                   │
│                                        字节码增强                 │
│                                         ↙    ↘                   │
│                            方法入口: 记录时间   方法出口: 计算耗时  │
│                                         ↘    ↙                   │
│                                        日志输出                   │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 核心组件

| 组件                 | 职责                                                 |
| -------------------- | ---------------------------------------------------- |
| **MethodProbeAgent** | Agent 入口，初始化 ByteBuddy 转换器                  |
| **probeAdvice**      | 定义方法前后的增强逻辑（@Advice.OnMethodEnter/Exit） |
| **CallTreeContext**  | 管理方法调用树上下文（ThreadLocal）                  |
| **AgentConfig**      | 配置读取与管理                                       |
| **LogOutputFactory** | 日志输出工厂（Console/AsyncFile）                    |

### 7.3 字节码增强原理

> 使用 ByteBuddy 的 `Advice` 机制直接操作字节码，而非创建代理子类。可以增强 `final` 类和方法。

```java
// 原始方法
public void targetMethod() {
    // 业务逻辑
}

// 增强后（概念上）
public void targetMethod() {
    long startTime = System.nanoTime();
    try {
        // 业务逻辑
    } finally {
        long duration = System.nanoTime() - startTime;
        // 输出计时日志
    }
}
```

### 7.4 异常观察机制

```java
@Advice.OnMethodExit(onThrowable = Throwable.class)
public static void onExit(@Advice.Thrown Throwable thrown) {
    // thrown 只是引用，JVM 观察后继续正常传播异常
    if (thrown != null) {
        log("[EXCEPTION: " + thrown.getClass().getSimpleName());
    }
}
```

> **重要**：异常捕获对业务代码是**零侵入**的，异常信息和堆栈跟踪完全不变。

### 7.5 动态 Retransform 机制

通过 HTTP 接口动态添加配置后，已加载的类会被重新增强：

```
HTTP 请求 /class/add
        ↓
AgentConfig.addFlatClass()
        ↓
instrumentation.retransformClasses()  ← 触发字节码重新增强
        ↓
下次方法调用使用新字节码
```

**JVM 保证安全的机制：**

| 机制                     | 说明                                           |
| ------------------------ | ---------------------------------------------- |
| **Safepoint**            | JVM 在所有线程到达安全点时才执行替换           |
| **原子替换**             | JVM 只替换方法表中的指针，不影响执行中的方法   |
| **On-Stack Replacement** | 正在执行的方法继续用旧代码，下次调用才用新代码 |

**Retransform 限制：**

- ✅ 可以修改方法体
- ❌ 不能添加/删除/重命名方法
- ❌ 不能添加/删除字段

### 7.6 快照捕获流程

```
┌─────────────────────────────────────────────────────────────────┐
│                        快照捕获流程                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  方法执行 ─▶ 超过阈值? ─▶ 生成唯一ID ─▶ 序列化参数 ─▶ 异步写入      │
│                  │              │            │           │       │
│                  ▼              ▼            ▼           ▼       │
│              日志输出      [snap:ID]     Kryo序列化   .snapshot文件 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

| 组件                    | 职责                                         |
| ----------------------- | -------------------------------------------- |
| **SnapshotIdGenerator** | 生成唯一 ID（格式：yyyyMMdd-HHmmss-SSS-seq） |
| **SnapshotSerializer**  | 使用 Kryo 序列化（支持非 Serializable 对象） |
| **SnapshotWriter**      | 异步写入快照文件                             |
| **SnapshotReader**      | 命令行工具，支持通配符读取                   |
