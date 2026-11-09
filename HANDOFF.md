# Travel Agent KMP 移植 Handoff（P1–P5）

> 给 Codex 的执行文档。每次只执行一个阶段，阶段结束后停下并按第 9 节格式汇报，等待确认后再进入下一阶段。

## 0. 背景与目标

把 `Travel-Agent-Project/travel-planning-agent`（Python + FastAPI + LangGraph + Vue）的**确定性规划核心**移植成一个可离线运行的 Kotlin 应用，目标平台为 Android 和 Desktop，最终作为独立开源项目发布到 GitHub。

- **Python 仓库（只读标准答案）**：`C:/Users/24832/Desktop/Travel-Agent-Project/travel-planning-agent`，以 tag `golden-v1`（commit `0f498003c365c8a6d1bb1f066dc3afbd3bdb23fd`）为准。
- **新仓库**：`C:/Users/24832/Desktop/Travel-Agent-Project/travel-agent-kmp`（新建，独立 git 仓库）。
- **规格**：Python 仓库内的 `golden/SPEC.md`。本文与 SPEC 冲突时，以 Python 源码在 `golden-v1` 上的实际行为为准，并在汇报中指出冲突。

### 非目标（任何阶段都不做）

- 不接入 LLM、OpenAI 或任何网络请求；应用运行时完全离线。
- 不做 FastAPI 服务端，不保留 HTTP 接口。
- **P1–P4 期间不修复 Python 的任何已知问题。** 移植目标是行为一致，不是行为正确。改进统一放到 P5。
- 不修改 Python 仓库中的任何文件。

## 1. 移植总原则

1. **Python 是唯一的标准答案。** 判断 Kotlin 实现是否正确，唯一依据是 `golden/` 里的 fixtures 与 `golden-v1` 的源码行为。
2. **逐函数对照移植。** Kotlin 文件结构尽量对应 Python 模块；每个移植的函数上方用注释写明来源，例如 `// Port of backend/app/agent/requirements.py:parse_requirements (L218–385)`。
3. **读源码，不要只读 SPEC。** SPEC 附录里的正则只是定位索引，内联模式、flags、调用细节一律以源码为准。
4. **偏差必须登记。** 无法做到一致的地方，写进新仓库的 `PARITY_EXCEPTIONS.md`，包括用例 ID、字段、Python 值、Kotlin 值、原因。登记后在阶段汇报中单独列出，**等确认后才能算通过**。
5. **小步提交。** 每完成一个可测试的子任务就提交一次，commit message 用 Conventional Commits（`feat:`、`test:`、`chore:` 等）。不要 push，除非汇报后得到确认。

## 2. 工程结构与技术选型

```text
travel-agent-kmp/
├── core/                 # 纯 Kotlin/JVM 库：模型、数据加载、解析器、规划、预算、验证、状态机
│   ├── src/main/kotlin/
│   ├── src/main/resources/data/      # OSM 快照、manifest、cities.json、NOTICE.md
│   └── src/test/kotlin/              # golden 一致性测试
│   └── src/test/resources/golden/    # 从 Python 仓库复制的 golden/（原样，不修改）
├── composeApp/           # Compose Multiplatform 共享 UI（KMP 库）：commonMain + androidMain + desktopMain
├── androidApp/           # 独立 Android 应用模块（com.android.application），依赖 composeApp，用于生成 APK（AGP 9 要求）
├── tools/                # 同步脚本（复制 golden 与数据）
├── PARITY_EXCEPTIONS.md
├── NOTICE.md             # OSM 署名与 ODbL 许可，原样保留
└── README.md
```

**为什么 `core` 是纯 JVM 模块而不是 KMP `commonMain`**：Android 和 Desktop 都运行在 JVM 上，`core` 用 JVM 库可以直接使用 `java.math.BigDecimal` 和 `java.util.regex`，不需要第三方多平台十进制库，也能保证两端行为一致。代价是将来若要支持 iOS，`core` 需要重构。这个取舍已经确认。

- Kotlin 2.x，JDK 17 工具链（如需更高版本请在汇报中说明）。
- 序列化：`kotlinx.serialization`（JSON）。
- UI：Compose Multiplatform，Material 3。参考 `kline-kmp` 仓库（https://github.com/yudelieyang/kline-kmp）的 Gradle 配置、模块划分和 UI 约定，保持风格一致。
- 测试：JUnit 5 或 kotlin.test；golden 测试用参数化测试，每个 fixture 一个测试用例。

## 3. 金额规则（必须严格遵守）

Python 在 `backend/app/tools/mock.py:money` 中执行 `Decimal(...).quantize(Decimal("0.01"))`，使用默认 context（`ROUND_HALF_EVEN`，精度 28），结果立即转为 `float`。Kotlin 侧规则如下：

1. **所有金额在 Kotlin 内部一律用 `BigDecimal`。** 禁止用 `Double` 表示或中转金额。
2. **禁止使用 `BigDecimal(Double)` 构造器**（例外只有第 8 条的 D5 和 UI 显示格式化）。 金额只能从字符串构造：JSON 里的价格读成文本再转 `BigDecimal`，解析器从正则捕获的文本直接转 `BigDecimal`。
3. **量化点必须与 Python 一一对应。** 只在 SPEC“Decimal 及舍入”一节列出的调用位置（`calculate_budget`、`compose_draft`、`validate_itinerary`）执行 `setScale(2, RoundingMode.HALF_EVEN)`，不多加也不少加。
4. **累加和除法的顺序照搬 Python。** 先读 `mock.py:calculate_budget` 和酒店上限预过滤的代码，确认每一步运算是用 `Decimal` 还是 `float` 完成的：
   - 若为 `Decimal` 除法，Kotlin 使用 `MathContext(28, RoundingMode.HALF_EVEN)`。
   - 若某一步确实在 `float` 上运算，在汇报中列出该步骤，不要自行决定替代方案。
5. **测试比较规则**（实现在 golden 比较器中）：
   - SPEC“e2e 金额字段逐项审计”中的 **A 类字段**：两边都 `setScale(2, HALF_EVEN)` 后 `compareTo == 0`。
   - **B 类字段**（候选 `price`、`budget_amount`、`constraints[*].value`、`provided_limit`）：按十进制数值 `compareTo == 0`，不舍入。
   - `null` 只与 `null` 相等。
   - 不按字符串比较（`"317.0"` 与 `317.00` 视为相等）。
6. 非金额数字（经纬度、`confidence`、`rating`、`review_count`）按 `Double`/`Int` 精确比较，因为两边读的是同一份 JSON 文本。
7. **例外：Python 本身用 `float` 计算的步骤，Kotlin 用 `Double` 逐位复刻。** 一致性优先于第 1 条。已确认的例子是 `hotel_price_ceiling`（float 除法）。规则如下：
   - 运算顺序必须与 Python 完全相同。例如 `limit / travelers / nights` 和 `limit / (travelers * nights)` 在浮点下结果可能不同，不能互换。
   - 输入从字符串用 `toDouble()` 得到，int 用 `toDouble()` 转换。
   - 比较也在 `Double` 域内进行。例如 Python 是 `price > ceiling`（两边都是 float），Kotlin 就写成 `price.toDouble() > ceiling`，不要把 ceiling 转回 `BigDecimal` 再比较。
   - 这类函数统一命名为 `pyFloat...` 前缀并集中放在一个文件中，注释写明 Python 源位置。
   - P1b 开始时，先审计金额路径上所有在 `float` 上运算的步骤，在汇报中列出完整清单（已完成，见 `docs/P1B_FLOAT_AUDIT.md`）。

8. **float 审计后的决定（D1–D6）**

   **D1：不复刻 `repr(float)` 的字符串格式。** Kotlin 没有 JSON 接口，float 结果只以 `Double` 存在于模型中，再由 UI 格式化显示。golden 比较时，把 fixture 中的 Python `repr` 字符串用 `toDouble()` 解析，再与 Kotlin 的 `Double` 用 `toRawBits()` 比较是否相等。
   *依据*：Python `repr(float)` 输出的是能还原到同一个 binary64 的最短十进制串，`toDouble()` 是正确舍入的解析，因此会得到与 Python 完全相同的二进制值。比较值本身已经足够，不需要比较格式。

   **D2：`Decimal → float → Decimal(str(float))` 这段往返，Kotlin 不做 float 中转，全程保持 `BigDecimal`。**
   *依据*：设 q 是有效数字不超过 15 位的十进制数（binary64 的 DBL_DIG = 15），并且 **q 落在 binary64 的正规数范围内**，即 q = 0，或 DBL_MIN（约 2.225e-308）≤ |q| ≤ DBL_MAX（约 1.798e308）。在这个范围内，double 的相对间距不超过 2^-52，比 15 位十进制数的相对间距更密，所以 q 转成 double 后再取 15 位有效数字，一定能还原出 q；任何不超过 15 位、且与 q 不同的十进制数，都不会和 q 映射到同一个 double。`repr` 取的是能还原到该 double 的最短串，它必然在数值上等于 q。因此 `Decimal(str(float(q)))` 在数值上等于 q，float 中转没有损失。
   正规数范围这个条件是必需的：在非正规数区间，有效位数随数值变小而减少，不同的短十进制数会映射到同一个 double。例如 `4e-324` 和 `5e-324` 都映射到最小的非正规数，Python 会把 `4e-324` 变成 `5e-324`。超出 DBL_MAX 的值则会变成 `inf`。
   *适用范围*：取保守的十进制边界，要求 q = 0 或 1e-307 ≤ |q| ≤ 1e308，并且有效数字不超过 15 位。量化到 2 位小数的金额只要绝对值小于 10^13 就满足条件（非零的 2 位小数金额至少是 0.01，不会落入下溢区间）。B 类输入（JSON 价格、查询中的金额文本）需要同时满足有效数字和数值范围两个条件。
   *不满足条件时*：作为**已接受的例外**登记到 `PARITY_EXCEPTIONS.md`，分三类：有效数字超过 15 位；0 < |q| < 1e-307（非正规数和下溢）；|q| > 1e308（溢出）。为每一类写一个测试，固定 Kotlin 在这种情况下的行为。不要为此实现最短 repr 算法，也不要模拟非正规数的舍入。

   **D3：Python 在 float 上做的比较、筛选、排序（`candidates_for`、`select_option`、`validate_itinerary` 中的比较），Kotlin 用 `pyFloat` 函数在 `Double` 上做**，操作数用 `BigDecimal.toDouble()` 或字符串 `toDouble()` 得到。比较结果决定的是后续分支，输出的金额仍用 D2 的 `BigDecimal`。

   **D4：`hotel_price_ceiling`（`planner.py:139`）**：Python 是 `constraint.value / (travelers * nights)`，其中乘法是整数运算（精确），然后做一次 float 除法。Kotlin 写成 `value.toDouble() / (travelers.toLong() * nights).toDouble()`，结果以 `Double` 存入工具记录的 `maxPrice`。验证用例：760、1 人、3 晚，应得到 `253.33333333333334` 对应的 binary64。

   **D5：`savings`（`service.py:201`）**：Python 是 `max(round(initial_total - final_total, 2), 0)`。Kotlin 实现如下：
   1. `d = initialTotal.toDouble() - finalTotal.toDouble()`（float 减法，与 Python 一致）；
   2. `r = BigDecimal(d).setScale(2, RoundingMode.HALF_EVEN).toDouble()`。这里**必须**用 `BigDecimal(Double)` 构造器（取 double 的精确二进制值），这是全项目唯一允许使用这个构造器的地方。CPython 的 `round(float, 2)` 也是对精确二进制值做正确舍入、平局取偶，然后取最接近的 double，所以结果相同；
   3. 若 `r < 0.0`，结果为 `0.0`；否则为 `r`。（Python 在 r 为负时返回整数 `0`，数值上等于 `0.0`。`-0.0` 的情况在这里不会出现：两个总额若是同一个 double，相减得 `+0.0`；若不同，它们相差至少约 0.01，舍入后不可能是 `-0.0`。）

   **D6：补充 golden（`golden-v1.1`）。** 现有 e2e fixtures 不包含工具记录里的 `max_price`，也不包含 `plan()` 响应中的 `execution.repair.savings`，所以 D4、D5 目前没有标准答案可对照。在 Python 仓库中**只新增文件**：扩展导出脚本，输出到新目录 `golden/e2e-ext/`（现有 `golden/` 文件一律不改）。对 28 个用例，以及至少一个触发酒店上限且除不尽的用例（例如 760 美元、1 人、3 晚），导出公开工具记录（含 `max_price`）和 `plan()` 的 `execution.repair`（含 `savings`）。float 值存为 Python `repr` 字符串，并在 SPEC 中注明该目录的 float 字段格式。完成后单独提交，打 tag `golden-v1.1`，再用 `tools/sync_golden` 同步到新仓库。

   **UI 显示**（不属于一致性测试）：金额统一显示为 2 位小数。`Double` 值先用 `BigDecimal(d)` 取精确值，再 `setScale(2, HALF_EVEN)`，与 Python 的 `format(x, ".2f")` 一致；`BigDecimal` 值直接 `setScale(2, HALF_EVEN)`。

   **为什么这样能逐位一致**：Python `float` 和 JVM `double` 都是 IEEE 754 binary64，`/`、`*`、`+`、`-` 都按 round-to-nearest-even 正确舍入；Python `float(str)` 和 Java `Double.parseDouble` 都是正确舍入的十进制转二进制；绝对值小于 2^53 的整数转浮点是精确的。因此在输入相同、运算顺序相同的前提下，两边每一步的结果在二进制上完全相同。

## 4. 正则移植规则

Python `re` 与 Java 正则的差异必须逐条处理。建议写一个小的 `PyRegex` 辅助层集中处理，所有解析器代码都通过它调用，不要散落各处。

| Python | Kotlin/Java | 说明 |
|---|---|---|
| `(?P<name>...)` | `(?<name>...)` | 命名组语法 |
| `(?P=name)` | `\k<name>` | 若出现 |
| 替换串 `\1`、`\g<name>` | `$1`、`${name}` | `re.sub` 的替换模板 |
| `re.match` | `lookingAt()` / `matchAt(0)` | 只锚定开头 |
| `re.fullmatch` | `matches()` | 整串匹配 |
| `re.search` | `find()` | |
| `re.split` | `Regex.split`（**不要**用 `String.split`，它会丢掉末尾空串）| 带捕获组的 split，Python 会把组内容插入结果，逐个核对 |
| `re.I` | `RegexOption.IGNORE_CASE` + Unicode case | Python str 模式默认 Unicode 大小写 |
| `\w` `\b` `\d` `\s`（Python 默认 Unicode）| 桌面 JVM 默认是 ASCII | 见下方说明 |
| `str.lower()`、`str.casefold()` | `PyStr.lower()`、`PyStr.casefold()`（查表实现，见下方）| 不要直接用 `lowercase()` |
| `dict.fromkeys` 去重、`dict` 遍历 | `LinkedHashSet` / `LinkedHashMap` | 保持插入顺序 |
| `set` | 只用于成员判断；需要遍历时找出 Python 的实际顺序依据 | |
| `date.fromisoformat` | `LocalDate.parse` | 无效日期 Python 抛错，Kotlin 要以相同方式失败 |

**大小写转换说明**：Kotlin/Java 没有 `casefold`，而且 `lowercase()` 使用 JDK 自带的 Unicode 版本（JDK 17 为 Unicode 13），Python 3.11 为 Unicode 14，两者的映射表不同（例如 `ſ` 经 `casefold` 得到 `s`，`lowercase` 则保持不变）。做法是：
- 在 `tools/` 中写一个 Python 脚本，用 Python 仓库 `.venv` 的同一个解释器遍历全部码位（`0` 到 `0x10FFFF`），对每个码位 `c` 记录 `chr(c).lower()` 和 `chr(c).casefold()` 中结果与原字符不同的映射（结果可能是多个字符），生成 `core/src/main/resources/unicode/lower.tsv` 和 `casefold.tsv`，文件头写明 Python 版本和 `unicodedata.unidata_version`。
- `PyStr.lower()` 和 `PyStr.casefold()` 按码位查表逐个替换；表里没有的码位保持原样。
- 所有对应 Python `lower()` 和 `casefold()` 的地方都改用 `PyStr`。正则的 `IGNORE_CASE` 不在此列，仍按上表处理。
- 测试：`Boſton` 应解析为 `Boston`，并加入 `İstanbul`、`STRASSE`/`straße` 等多字符映射的用例，期望值用 Python 生成。

**Unicode 字符类说明**：Python 的 `\w`、`\b`、`\d` 默认匹配 Unicode；桌面 JVM 默认只匹配 ASCII，需要 `Pattern.UNICODE_CHARACTER_CLASS`（或模式前缀 `(?U)`）。**Android 的 `java.util.regex` 由 ICU 实现，默认语义又与桌面 JVM 不同。** 因此：

- `PyRegex` 在桌面 JVM 上统一启用 Unicode 字符类，使行为对齐 Python。
- P4 必须在 Android 设备或模拟器上跑一遍解析器 golden 测试（instrumented test，不能用 Robolectric，它用的是桌面 JVM 的正则），并额外加入一组包含非 ASCII 字符的输入（如 `Café`、`São Paulo`、全角标点），对比 Python 的实际输出（用一个小脚本在 Python 仓库里生成，不提交到 Python 仓库）。

## 5. 必须保留的 Python 行为（不要“修好”）

以下行为已在 fixtures 中出现，Kotlin 必须原样复现：

- `SEM200-001`：查询 `"Please plan a 2-day visit to Boston for one traveler."` 解析出的 `destination` 是 **`"to Boston"`**（`visit` 被目的地正则当作引导词，后面的 `to` 被捕获进地名，且 `normalize_place` 没有解析成功时保留原值）。
- 候选排序：`(-len(preference_matches), price, id)` 升序；常规选择按 `(day - 1) % len(options)` 轮换；酒店与交通的 day 固定为 1。
- 低价修复：每个类别用 `min(options, key=(price, id))`，忽略偏好排序。
- 报价约定：每天三餐，住宿晚数为天数减一，价格按人计（见 `preset-repair.json` 的 warnings 文本）。
- 修复最多一次；修复资格条件见 SPEC“状态转移表”下方的 eligible 定义，包括“errors 与 violation messages 完全相等”这一条。
- 修复失败时最终行程按业务逻辑清空（见 `repair-failure.json`）。
- warnings 文本、clarification 问题文本逐字一致。

发现其他“看起来像 bug”的行为，一律照搬，并记录到 `PARITY_EXCEPTIONS.md` 的“已知 Python 问题（P5 处理）”一节，不要修改。

## 6. 阶段计划

### P1a：工程骨架、数据与测试框架

1. 新建仓库与第 2 节的目录结构，Gradle 能在 Windows 上构建 `core` 与 `composeApp`（Android debug + Desktop run）。
2. `tools/sync_golden`：从 Python 仓库的 `golden-v1` 复制 `golden/`、OSM 快照目录（含 manifest、分类映射、`NOTICE.md`）、`data/travel/us/cities.json`、`frontend/src/presets.ts` 中的预设到新仓库。复制时校验文件数和 SHA-256，并把源 commit hash 写入 `core/src/test/resources/golden/SOURCE.txt`。
3. 用 `kotlinx.serialization` 定义所有模型：`TravelRequirements`、`RequirementsV2`、`BudgetConstraint`、`SpecificPreference`、`RequirementAmbiguity`、`TravelOption`、`Itinerary`、`DayPlan`、`Activity`、`BudgetSummary`、`ValidationResult`、`Violation`、`RepairSummary`、`PlanResult`、`CoverageResult`。字段名、可空性与 fixtures JSON 完全对应；金额字段用自定义 serializer 读写为 `BigDecimal`。
4. 快照加载器：照搬 `RealSnapshotProvider` 与 manifest 的城市、类别映射，以及 `cities.json` 的别名表。
5. golden 比较器：实现第 3 节的比较规则，失败时输出 JSON 路径、期望值与实际值。

6. 新增 `androidApp` 模块（见第 2 节），生成 debug APK，并通过 adb 安装到已连接的真机（iQOO 15，已开启 USB 调试）。此阶段的应用只需能启动，显示一个占位页面，列出加载到的城市数和候选数即可。
7. 用第 4 节的查表方式实现 `PyStr`，替换加载器中的 `lowercase()`，消除 `Boſton` 偏差。
8. 补齐 `RealSnapshotProvider` 的全部输入与快照校验行为（对照 Pydantic 模型和 provider 源码），对无效快照的失败方式与 Python 一致。

**验收**
- 加载到 12 个城市、114 条 OSM 候选，外加 `cities.json` 中的 mock 候选，数量与 Python 一致（在 Python 仓库里用只读脚本统计后对照）。
- `adb devices` 能看到真机；`androidApp` 的 debug APK 安装到真机后能启动，并显示与 JVM 测试相同的城市数和候选数。
- Desktop 应用能启动并显示同样的信息。
- `PARITY_EXCEPTIONS.md` 中的 `Boſton` 条目已关闭；`PyStr` 测试全部通过。
- 全部 28 个 e2e fixture 和 200 个 requirements fixture 能无损反序列化，再序列化后经比较器对比与原文件相等。

### P1b：规划核心与状态机（绕过解析器）

1. 移植 `candidate_data.py:candidates_for`、`preference_key`、`select_option`。
2. 移植 `itinerary.py:compose_draft`、`validate_itinerary`，`mock.py:calculate_budget`、`money`。
3. 移植低价修复和酒店硬上限预过滤。
4. 用一个显式状态机替代 LangGraph：节点为 `preflight`、`clarification`、`planner`、`tools`、`validate`、`replan`、`finalize`，转移条件照抄 SPEC 状态转移表，每个节点读写的字段照抄 SPEC“各状态节点读写”。`execution_stages` 要按节点顺序记录，供 UI 播放。
5. 入口 `TravelEngine.runWithRequirements(requirements)`：直接接收 fixture 里的 `requirements`，跳过解析器。

**验收**
- 28 个 e2e fixture 中，以 fixture 的 `requirements` 为输入，Kotlin 输出的 `status`、`missing_fields`、`clarification_question`、`candidates`、`itinerary`、`budget`、`validation`、`repair`、`warnings` 全部通过比较器，即 **28/28**。
- 单元测试覆盖 `HALF_EVEN` 边界（如 `0.125`、`0.135`、`2.675`）以及价格并列时按 id 排序。

### P2：需求解析器与覆盖分析

1. 移植 `requirements.py`（779 行）、`semantic_coverage.py`（269 行）、`extractor.py`，以及 `candidate_data.py` 中的 `location_key`、`city_at_start`、`resolve_city`、`load_candidate_dataset`。
2. 按 SPEC“解析器调用图”的顺序逐函数移植，函数名用 camelCase 对应。Pydantic 验证器（`normalize_place`、日期校验、V2 投影、`validate_hard_budget_contract`、`reject_duplicate_budget_scopes`）要显式实现为构造后的校验步骤，触发顺序与 Python 一致。
3. 所有正则通过第 4 节的 `PyRegex` 调用；每个正则常量上方注释写明 Python 源行号。
4. 入口 `TravelEngine.run(query)`：解析、覆盖分析，然后进入 P1b 的状态机。
5. 生成一份对照报告（不作为通过门槛）：Kotlin 解析结果与 `dataset-gold.json` 的符合率，应与 Python 当前解析结果的符合率完全相同。

**验收**
- 200 个 requirements fixture 的 `requirements` 与 `coverage` 全部一致，即 **200/200**。
- 28 个 e2e fixture 改为从原始 `query` 开始跑完整流程，**28/28** 一致。
- 对照报告中 Kotlin 与 Python 的 gold 符合率相同。

### P3：Compose UI

先读 Vue 前端的 `App.vue`、`components/`、`composables/useStagePlayback`、`OfflineRouteMap.vue`、`presets.ts`，理解现有信息结构和阶段播放节奏，再用 Compose 重新设计。目标是“一个像样的移动应用”，不是网页的逐像素复刻。

页面与组件：
1. **首页**：查询输入框，6 个预设场景卡片（标题、一句话说明），以及“离线运行、数据日期”提示。
2. **执行页**：五阶段时间线（request、requirements、candidate retrieval、validation、repair/final）。引擎在 `Dispatchers.Default` 上一次算完，再按 `useStagePlayback` 的节奏逐阶段展开；提供“跳过动画”按钮。
3. **需求卡片**：目的地、天数、人数、预算约束（含 HARD/SOFT、范围）、偏好、歧义提示。
4. **候选卡片**：按类别分组，区分 selected 与 alternatives，显示价格、单位、标签、偏好匹配、数据来源（OSM 或 mock）。
5. **验证与修复**：违规列表（actual、expected、excess）；修复前后的行程和总价对比。
6. **每日行程与预算**：按天展开的活动列表，预算分类条形图，剩余预算。
7. **离线示意图**：用 Canvas 画点和直线，并明确标注“示意图，不代表实际路线”。
8. **澄清流程**：`needs_clarification` 时显示问题，允许用户修改查询后重新运行。
9. **关于页**：OSM 署名与 ODbL 许可（来自 `NOTICE.md`）、快照抓取日期、能力边界（无营业时间、无路线、价格为估算、无实时数据）。

布局要求：手机竖屏为单栏；平板和 Desktop 宽屏为双栏（左侧时间线，右侧详情）。支持深色模式。

**验收**
- 6 个预设在 Android 模拟器和 Desktop 上都能完整运行，结果与对应 e2e fixture 一致。
- 每个页面截图存到 `docs/screenshots/`，汇报时附上。

### P4：Android 一致性、发布与文档

1. Android instrumented test：在真机（iQOO 15）上运行，有条件时再加一个模拟器，运行 200 个 requirements fixture 和 28 个 e2e fixture，以及第 4 节的非 ASCII 输入集。
2. GitHub Actions：在 push 时运行 `core` 的 JVM golden 测试，并构建 Android debug APK。
3. 生成 release APK（签名配置不要提交到仓库，说明本地如何配置）。
4. `README.md`（英文）：项目简介、截图、架构图、“Python 作为标准答案的一致性移植方法”说明、测试结果数字、如何运行、能力边界、数据与许可。
5. 确认仓库中没有密钥、`.env`、个人路径等敏感内容。

**验收**
- Android 上 golden 测试全部通过；不能通过的项已登记并得到确认。
- CI 通过，release APK 能安装运行。

### P5（可选，P4 完成后再讨论）：修复已知问题

在 Python 仓库和 Kotlin 仓库中同步修复 `PARITY_EXCEPTIONS.md` 里登记的 Python 问题（例如 `"to Boston"`），重新导出 `golden-v2`，两边重新验证一致。此阶段开始前需要单独确认范围。

## 7. 运行与测试命令（需在 P1a 中落实）

在 README 和本节中补全以下命令的实际写法：
- 运行全部 JVM golden 测试
- 只运行某一个 fixture 的测试（排查用）
- 运行 Desktop 应用
- 安装 Android debug 版
- 运行 Android instrumented test

## 8. 注意事项

- Windows 环境，命令使用 PowerShell 写法。
- `golden/` 复制到新仓库后视为只读；如需更新，只能通过 `tools/sync_golden` 从 Python 仓库的新 tag 重新同步。
- fixtures 只冻结了最后一轮候选投影，初始候选和完整执行轨迹没有全部冻结。排查修复相关问题时，可在 Python 仓库用只读脚本临时打印中间状态，但不要提交到 Python 仓库。
- golden 全部通过只说明与 Python 一致，不能证明真实行程可执行；README 中要如实说明。

## 9. 每个阶段的汇报格式

1. 完成了什么（对照本阶段任务逐条列出）
2. 验收结果：通过数 / 总数，失败项的用例 ID 与字段
3. `PARITY_EXCEPTIONS.md` 新增条目
4. 对本文档或 SPEC 的疑问和冲突
5. 提交列表（hash 和 message）
6. 下一阶段开始前需要确认的事项
