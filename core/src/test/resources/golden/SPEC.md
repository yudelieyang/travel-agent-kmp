# Kotlin golden fixtures 规格

## 范围和复现

从仓库根目录运行 `.venv\Scripts\python.exe scripts/export_golden_fixtures.py`。脚本显式构造 `DeterministicTestPlanner`，关闭语义增强，使用本地 `data/travel/snapshots/openstreetmap/migration_manifest.json`，不读取 `.env`，不创建 OpenAI 客户端。每次调用 `TravelService.run`；按数据集顺序选择前 20 条 `status=success` 且有行程的输入。预设按当前 `presets.ts` 提取。`repair-failure` 和 `clarification` 是补充输入。每个 e2e JSON 的 candidates 是最后一次工具结果的 selected/alternatives 投影；repair 保存前次行程、触发违规和最终预算，失败时最终行程已按业务逻辑清空。requirements 每条记录为当前解析与覆盖结果；`dataset-gold.json` 保留输入文件中的原始 expected，未注入默认字段。

金额字段以字符串表示现有 Python 结果的十进制文本；坐标、置信度等非金额数字仍为数字。重要限制：源业务模型将金额定义为 float，`money()` 的 Decimal 量化结果立即转为 float，因此原 Decimal 的指数、尾随零以及此前丢失的精度在 `TravelService.run` 返回前已经消失，导出无法恢复。字符串例如 `317.0` 表示 Python 当前可见数值，不代表保留了原始 Decimal 精度。dataset-gold.json 则忠实保存原始 gold 的数值类型，作为源数据对照。比较时按十进制数值比较金额，不按字符串尾零比较。

## Decimal 及舍入

唯一的显式 `Decimal.quantize` 在 `backend/app/tools/mock.py:money`：`quantize(Decimal("0.01"))`，没有显式 rounding 参数，使用当前 decimal context；Python 默认 context 为 `ROUND_HALF_EVEN`，若调用方修改 context 则行为可能不同。结果立即 `float(...)`。导出脚本不修改 context。

调用位置：`mock.py:calculate_budget` 对每人费用、总额、各分类乘人数后的金额、比较金额和剩余预算调用 money；`itinerary.py:compose_draft` 对每项费用和每日小计调用；`itinerary.py:validate_itinerary` 对重算的小计、总额、约束 actual、expected、excess 调用；`live_evaluation.py` 用同一函数核对活动费用。`service.py` 的公开 repair savings 另用 Python `round(initial_total - final_total, 2)`，其输入为 float，非 Decimal quantize；本导出以 run 的 repair 上下文与最终预算呈现，不重新计算该字段。模型和 trace 的耗时 round 不属于金额，也不导出。

## 排序和选择

`candidate_data.py:candidates_for`：先过滤 `price > max_price`，偏好和标签以 `preference_key` 规范化（小写词、`zoos`→`zoo`），精确匹配。排序键 `(-len(preference_matches), price, id)` 升序。正常 `select_option` 按排序后列表 `(day - 1) % len(options)` 选择；酒店与交通的 day 固定为 1。低价修复使用 `min(options, key=lambda option: (option.price, option.id))`，忽略偏好排序；每类别重复该选择。酒店硬上限可预先按总额 / 人数 / 晚数过滤。

## 状态转移表

| 当前节点 | 条件 | 下一节点 |
|---|---|---|
| START | 总是 | preflight |
| preflight | requirements sufficient | planner |
| preflight | insufficient | clarification |
| clarification | 总是 | END |
| planner | 总是（包括错误） | tools |
| tools | 总是 | validate |
| validate | replan_attempts < 1 且 `is_replan_eligible` | replan |
| validate | 否则 | finalize |
| replan | 总是 | planner |
| finalize | 总是 | END |

eligible 要求验证已执行、失败、至少一个 violation、所有 violation 均为总预算或酒店预算超额，且 errors 与 violation messages 完全相等。planner/tools 出错也经过 validation，通常因 prior_errors 跳过验证而直接 finalize。finalize 出错时清空行程与预算；repair 后重新搜索和验证，最多一次。

## 需求解析结构

物理行数（含空行和注释）：`backend/app/agent/extractor.py` 14 行，`requirements.py` 779 行，`semantic_coverage.py` 269 行。`candidate_data.py` 提供城市/别名解析。规则数量没有唯一语义定义：按 Python AST 静态统计直接 `re.*` 调用点，requirements 有 71 个（compile 7、sub 4、search 44、findall 1、split 1、match 8、finditer 3、fullmatch 2、escape 1）；coverage 有 20 个（compile 14、split 1、search 3、findall 1、match 1）；extractor 无。编译后对象的方法调用不在此统计中，正则替代分支也没有逐个计数；调用点数量不是独立业务规则数。

处理顺序：`RuleBasedRequirementsExtractor.extract` 调用 `parse_requirements`；先规范空白、标点和少量拼写，再识别目的地及歧义、出发地、天数、人数和 ISO 日期；识别货币表达，解析硬预算范围/强度/歧义及兼容字段；解析目标、兴趣、约束、具体和预算偏好；去重并构造 `TravelRequirements`，由 Pydantic 规范化地点、校验日期及投影 V2 字段。随后 `SemanticCoverageAnalyzer.analyze` 按阻断歧义、tradeoff、否定、偏好、酒店软预算、货币缺口、修正缺口及酒店歧义等规则累积原因、证据和信号。覆盖分析不会改变需求；本 fixtures 不执行模型或语义合并。


## 补充：e2e 金额字段逐项审计

通配符 `[*]` 表示数组中每一项。下表中“已量化”指产生该字段的业务计算确实通过 `backend/app/tools/mock.py:money` 的 `Decimal.quantize(0.01)`；其 rounding 使用当前 Decimal context，默认 `ROUND_HALF_EVEN`，随后转换为 float。A 类可在复制相同舍入语义后用分严格比较；B 类应保留源十进制值并单独比较，不可无条件按分舍入。检查范围为本次 28 个 `golden/e2e/*.json` 的非空金额字符串；实际超过两位小数的条目：**0 个**。这只是当前样本事实，并非业务输入和快照的精度上限。

| JSON 路径 | 产生前是否已 quantize；位置及模式 | 当前超过两位 | 类别 |
|---|---|---|---|
| `$.candidates[*].selected[*].price`、`$.candidates[*].alternatives[*].price` | 否；快照/fixture 输入经 Pydantic float，无 quantize、无 rounding 模式 | 否 | B：原始价格可能含更细精度 |
| `$.requirements.budget_amount` | 否；`requirements.py:parse_requirements` 数值解析/投影，float，无量化 | 否 | B：输入可含更细精度 |
| `$.requirements.requirements_v2.constraints[*].value` | 否；`requirements.py:extract_budget_constraints` 解析为 float，无量化 | 否 | B：原始硬约束需原样比较 |
| `$.itinerary.daily_plan[*].activities[*].estimated_cost`、`$.repair.initial_itinerary.daily_plan[*].activities[*].estimated_cost` | 是；`itinerary.py:compose_draft` 调用 `mock.py:money`，默认 HALF_EVEN | 否 | A |
| `$.itinerary.daily_plan[*].estimated_cost`、`$.repair.initial_itinerary.daily_plan[*].estimated_cost` | 是；`itinerary.py:compose_draft` 调用 `mock.py:money`，默认 HALF_EVEN | 否 | A |
| `$.itinerary.estimated_total_cost`、`$.repair.initial_itinerary.estimated_total_cost` | 是；`mock.py:calculate_budget` 调用 `money`，默认 HALF_EVEN | 否 | A |
| `$.budget.per_traveler_cost`、`$.repair.final_budget.per_traveler_cost` | 是；`mock.py:calculate_budget` 调用 `money`，默认 HALF_EVEN | 否 | A |
| `$.budget.estimated_total_cost`、`$.repair.final_budget.estimated_total_cost` | 是；`mock.py:calculate_budget` 调用 `money`，默认 HALF_EVEN | 否 | A |
| `$.budget.breakdown.attractions`、`$.budget.breakdown.food`、`$.budget.breakdown.hotel`、`$.budget.breakdown.transport`，及 `$.repair.final_budget.breakdown` 下同名四项 | 是；`mock.py:calculate_budget` 调用 `money`，默认 HALF_EVEN | 否 | A |
| `$.budget.comparison_cost`、`$.repair.final_budget.comparison_cost` | 是；`mock.py:calculate_budget` 调用 `money`，默认 HALF_EVEN；可能为 null | 否 | A |
| `$.budget.remaining_budget`、`$.repair.final_budget.remaining_budget` | 是；`mock.py:calculate_budget` 调用 `money`，默认 HALF_EVEN；可能为 null | 否 | A |
| `$.budget.provided_limit`、`$.repair.final_budget.provided_limit` | 否；直接复制解析的 float 上限，`mock.py:calculate_budget` 不量化；可能为 null | 否 | B：输入可含更细精度 |
| `$.validation.violations[*].actual`、`$.validation.violations[*].expected`、`$.validation.violations[*].excess` | 是；`itinerary.py:validate_itinerary` 分别调用 `money`，默认 HALF_EVEN | 否 | A：这是量化后的验证投影，非原始预算 |
| `$.repair.trigger[*].actual`、`$.repair.trigger[*].expected`、`$.repair.trigger[*].excess` | 是；前次 `itinerary.py:validate_itinerary` 调用 `money`，默认 HALF_EVEN | 否 | A |

最终行程总额和 breakdown 虽来自分舍入，但复制流程时还须遵守 Python 先以 `Decimal(str(float))` 累加再量化的顺序；浮点输入已有的信息损失不能恢复。null 需单独与 null 比较。此清单只覆盖 e2e 实际 schema；不将数据集原始 gold 值列入。

## 补充：解析器调用图和迁移行为

以下行数为函数定义的物理行数（包含定义行）；分支和循环会导致实际调用次数不同，顺序是外层执行顺序，不代表每条输入都会走所有分支。`backend/app/agent/extractor.py`：`RuleBasedRequirementsExtractor.extract`（12–14，3 行）→ `requirements.py:parse_requirements`（218–385，168 行）。解析中按需调用 `candidate_data.py:city_at_start`（93–106，14）→ `location_key`（70–71，2）和 `load_candidate_dataset`（80–82，3）；`number_value`（778–779，2）；`recognized_money_expressions`（509–515，7）→ `has_budget_intent`（496–506，11）→ `budget_context`（469–493，25）；`extract_budget_constraints`（518–584，67）→ `budget_context`、`monetary_scope`（601–628，28）、`unsupported_money_scope`（640–652，13）、`has_unsupported_money_scope`（655–656，2）、`ambiguous_hotel_budget_phrase`（631–637，7）、`budget_strength`（686–691，6）、`budget_currency`（694–706，13）、`is_bounded_scope_correction`（587–598，12）、`merge_budget_constraints`（659–664，6）；`legacy_budget_projection_match`（667–683，17）、`invalid_budget_value`（460–466，7）；`extract_specific_preferences`（709–732，24）→ `preference_category`（762–775，14）；`extract_budget_preferences`（735–759，25）复用预算辅助函数。具体子调用见源代码，部分函数可在不同分支重复调用。模型构造还调用 `TravelRequirements.normalize_place` → `candidate_data.py:resolve_city`（85–90，6）→ `location_key/load_candidate_dataset`，以及日期和 V2 投影验证器；`BudgetConstraint.validate_hard_budget_contract`、`RequirementsV2.reject_duplicate_budget_scopes` 也参与校验。

接着调用 `semantic_coverage.py:SemanticCoverageAnalyzer.analyze`（97–184，88 行）及按需 `unrepresented_exclusion`（265–269，5）、`unrepresented_hard_money`（206–229，24）→ `budget_like_amounts`（187–203，17）、`unresolved_correction`（232–262，31）；最后再次用 `budget_like_amounts` 产生信号。输入 `query` 在覆盖分析中是原始文本，解析器内部归一化后的文本不会覆盖它。Pydantic 模型、`core/budget.py:BudgetScope` 和 `data/travel/us/cities.json` 是额外的类型/城市数据依赖。

正则和关键词总量较大，不逐项复制全部表达式；以下是定位清单。`requirements.py` 有 71 个直接 `re.*` 静态调用点（上文已分方法计数），另有已编译对象的 `.search/.finditer/.split/.sub` 调用；正则覆盖输入规范化、地点、人数、日期、金额、否定、偏好与类别。命名模式和表在 388–458 行：`NUMBER_WORDS` 20 项、`NUMBER`、`NEGATOR`、`NEGATIVE_TERMS` 4 项、7 个编译模式 `BUDGET_PATTERN`、`PREFERENCE_PATTERN`、`PREFERENCE_CONNECTOR`、`PREFERENCE_PREFIX`、`BUDGET_INTENT`、`BUDGET_HARD`、`BUDGET_SOFT`，以及 `GENERIC_PREFERENCE_TERMS` 15 项。`parse_requirements` 334–339 的四组 vocabulary 合计 15 项。`semantic_coverage.py` 38–94 行有 14 个 `re.compile` 模式，分别覆盖金额、条件取舍、修正、否定、偏好提示和酒店金额等；其余直接调用定位于 97–269 行。`candidate_data.py:location_key` 的 1 个正则及 `city_at_start` 的动态前缀正则用于城市别名。所有表达式的**原文和确切用途以标注行的源代码为准**，同一正则的替代分支不可简单计作一条业务规则；城市关键词表是 `data/travel/us/cities.json` 中的城市及 aliases。迁移前应读取上述各行及函数内的内联模式，不要仅移植命名模式。

Python 行为：`re` 默认 Unicode 的 `\b`、`\w`、`\d`、`\s` 和 `re.I` 与 JVM 默认不必相同；命名组 `(?P<name>...)` 和反向替换 `\1` 语法需转换。`lower()` 和 `casefold()` 是 Unicode 相关操作，`casefold` 并不等同于 JVM 默认 locale 的 lowercase；城市规范化之后只保留 `[a-z0-9]`。Python 字典保持插入顺序，`dict.fromkeys` 保留首次出现顺序；集合在此处主要用于去重/成员测试，但不应假设其迭代顺序，城市同长度匹配以数据文件原顺序的第一个为准。Python 列表按顺序、Pydantic model 验证与浮点转换亦是行为契约。`date.fromisoformat` 仅解析输入匹配到的 ISO 日期，结束日期包含在天数内；遇到无效日期会抛错。所审计的解析、覆盖与城市解析路径未发现 `today()` 或 `now()`；导出器未固定日期，但这些路径不依赖今天。不能据此保证其他未来扩展路径没有时间依赖。

## 补充：各状态节点读写

下表列节点本身显式状态访问，写入按所有可能分支取并集；不是每次都会写所有字段。

| 节点 | 读取 | 写入 |
|---|---|---|
| preflight | requirements | requirement_status、missing_fields、tool_requests、tool_results、itinerary、budget_summary、warnings、errors、clarification_question、validation_status、approved_tool_requests、public_validation、replan_attempts、replan_context、budget_repair、selected_candidate_ids、execution_stages（包装器） |
| clarification | missing_fields、execution_stages | clarification_question、messages、execution_stages |
| planner | requirements、replan_context、execution_stages | tool_requests、approved_tool_requests、warnings、budget_repair、errors、execution_stages |
| tools | errors、tool_requests、requirements、budget_repair、warnings、execution_stages | tool_requests、tool_results、selected_candidate_ids、itinerary、budget_summary、warnings、errors、execution_stages |
| validate | errors、itinerary、budget_summary、requirements、execution_stages | errors、validation_status、public_validation、execution_stages |
| replan | replan_attempts、public_validation、itinerary、execution_stages | errors、replan_attempts、replan_context、budget_repair、execution_stages |
| finalize | errors、execution_stages | itinerary、budget_summary、messages、execution_stages |

`observed` 包装器还在 tools 节点读取原 `errors` 以确定 skipped，并在各节点追加 `execution_stages`；preflight 从空列表开始。validate 后的条件边读取 `replan_attempts`、`public_validation`、`errors`；preflight 后的条件边读取 `requirement_status`。messages 使用 LangGraph 的 `add_messages` reducer，不是普通覆盖。

## 附录：正则原文及关键词定义索引

下面列出解析/覆盖/城市解析源文件中直接 `re` 调用的首个参数原文，动态参数保留源代码表达式；其所在函数或模块就是作用上下文。命名已编译模式在定义处列出，后续 `.search` 等引用同一原文。`re.escape` 本身用于构造字面正则而非独立模式，也保留以免漏掉动态构造。flags 和调用细节以源文件对应行为准。

### `backend/app/agent/requirements.py`：71 个直接调用点

- 第 221 行 `parse_requirements` / `sub`：

```python
r"\b(whole|entire|full)-trip\b"
```

- 第 222 行 `parse_requirements` / `sub`：

```python
r"\bmore\s+then\b"
```

- 第 223 行 `parse_requirements` / `sub`：

```python
r"\b([A-Za-z])\.([A-Za-z])\."
```

- 第 224 行 `parse_requirements` / `sub`：

```python
r"\b([A-Za-z][A-Za-z ]*),\s*([A-Z]{2})\b"
```

- 第 226 行 `parse_requirements` / `search`：

```python
r"\b(?:to|in|visit)\s+([A-Za-z][A-Za-z ]*?)"
        r"(?=\s+(?:under|for|with|from|on|starting|between|around|but|budget)\b|[.,!?;]|$)"
```

- 第 242 行 `parse_requirements` / `match`：

```python
r"\s+or\b"
```

- 第 246 行 `parse_requirements` / `search`：

```python
r"\b(?:\d+|" + NUMBER + r")\s*-\s*days?\s+"
                r"(?P<place>[A-Z][A-Za-z]*(?:\s+[A-Z][A-Za-z]*)*)\s+trip\b"
```

- 第 254 行 `parse_requirements` / `search`：

```python
r"\bor\b"
```

- 第 257 行 `parse_requirements` / `match`：

```python
r"(?:somewhere|anywhere|rent|eat|stay|book)\b"
```

- 第 261 行 `parse_requirements` / `search`：

```python
r"\bfrom\s+([A-Za-z][A-Za-z ]*?)\s+to\b"
```

- 第 264 行 `parse_requirements` / `search`：

```python
rf"(?<![\w.])({NUMBER})\s*[- ]\s*days?\b"
```

- 第 267 行 `parse_requirements` / `search`：

```python
rf"(?<![\w.])({NUMBER})\s+(?:travelers?|people|persons?|adults?)\b"
```

- 第 271 行 `parse_requirements` / `search`：

```python
rf"\bfamily of\s+({NUMBER})\b"
```

- 第 273 行 `parse_requirements` / `search`：

```python
rf"\bfor\s+({NUMBER})(?=\s*(?:[.,;!?]|$|under\b|with\b))"
```

- 第 278 行 `parse_requirements` / `search`：

```python
r"\b(?:traveling|travelling) alone\b"
```

- 第 280 行 `parse_requirements` / `search`：

```python
r"\bjust me\b"
```

- 第 282 行 `parse_requirements` / `search`：

```python
r"\bme and my partner\b"
```

- 第 284 行 `parse_requirements` / `findall`：

```python
r"\b\d{4}-\d{2}-\d{2}\b"
```

- 第 306 行 `parse_requirements` / `match`：

```python
r"(?:per (?:person|traveler)|each)\b"
```

- 第 308 行 `parse_requirements` / `match`：

```python
r"(?:total|(?:for|on) (?:the )?(?:(?:whole|entire) )?trip)\b"
```

- 第 312 行 `parse_requirements` / `search`：

```python
r"\b(?:total budget|whole trip|entire trip)\b"
```

- 第 328 行 `parse_requirements` / `search`：

```python
r"\bspend\s+as\s+much\b[^.!?;]{0,80}\bbudget\s+as\s+(?:reasonably\s+)?possible\b"
```

- 第 340 行 `parse_requirements` / `split`：

```python
r"[.!?;]|\b(?:but|however)\b"
```

- 第 346 行 `parse_requirements` / `search`：

```python
r"\b" + re.escape(term) + r"\b"
```

- 第 346 行 `parse_requirements` / `escape`：

```python
term
```

- 第 347 行 `parse_requirements` / `search`：

```python
r"budget hotels?"
```

- 第 349 行 `parse_requirements` / `search`：

```python
r"\b(?:city|urban|neighborhood)\s+walking\b"
```

- 第 351 行 `parse_requirements` / `search`：

```python
NEGATOR
```

- 第 357 行 `parse_requirements` / `search`：

```python
term
```

- 第 358 行 `parse_requirements` / `search`：

```python
NEGATOR
```

- 第 360 行 `parse_requirements` / `search`：

```python
r"\bwheelchair\b"
```

- 第 362 行 `parse_requirements` / `search`：

```python
r"\bmust\s+.+"
```

- 第 365 行 `parse_requirements` / `search`：

```python
r"\b(?:cheap|affordable)\b"
```

- 第 403 行 `模块定义` / `compile`：

```python
r"(?:(?P<intro>under|below|up to|around|max(?:imum)?|at most|no more than|don't spend more than|"
    r"(?:don't\s+let\s+(?:the\s+)?(?:total|trip|whole\s+trip)\s+go\s+over)|"
    r"(?:cap\s+(?:the\s+)?(?:whole\s+)?trip\s+at)|"
    r"(?:my\s+)?(?:total\s+)?budget(?:\s+(?:of|is))?)\s*)?"
    r"(?:(?P<code>USD|EUR|GBP)\s*|(?P<symbol>[$€£])\s*)?"
    r"(?P<amount>-?\d(?:[\d,]*\d)?(?:\.\d+)?)(?P<multiplier>[kK])?\s*"
    r"(?P<currency>USD|EUR|GBP|dollars?|euros?|pounds?)?"
```

- 第 413 行 `模块定义` / `compile`：

```python
r"\bi\s+(?:also\s+)?(?:like|enjoy|prefer|love|need)\s+(?P<values>[^.!?;]+)"
```

- 第 416 行 `模块定义` / `compile`：

```python
r"\s*(?:,|\b(?:and|but)\b)\s*"
```

- 第 417 行 `模块定义` / `compile`：

```python
r"^(?:(?:i\s+)?(?:also\s+)?(?:like|enjoy|prefer|love|need)\s+|also\s+)"
```

- 第 420 行 `模块定义` / `compile`：

```python
r"\b(?:budget|spending\s+limit|spend|afford|under|up\s+to|max(?:imum)?|"
    r"at\s+most|no\s+more\s+than|around|about)\b"
```

- 第 425 行 `模块定义` / `compile`：

```python
r"\b(?:under|below|up\s+to|max(?:imum)?|at\s+most|no\s+more\s+than|"
    r"(?:do not|don't)\s+spend\s+more\s+than|spending\s+limit|"
    r"(?:do\s+not|don't)\s+let\s+(?:the\s+)?(?:total|(?:total\s+)?trip)\s+go\s+over|"
    r"cap\s+(?:the\s+)?(?:whole\s+)?trip\s+at|(?:total\s+)?budget|"
    r"(?:must|should)\s+(?:not\s+)?(?:exceed|be\s+more\s+than)|"
    r"cannot\s+(?:exceed|go\s+above)|(?:absolute\s+)?ceiling|"
    r"(?:capped?|cap)(?:\s+is)?(?:\s+at)?|\bwithin\b|\bonly\s+have\b)\b"
```

- 第 435 行 `模块定义` / `compile`：

```python
r"\b(?:around|about|roughly|near|ideally|ideal|if\s+possible|would\s+(?:like|be\s+ideal)|flexible)\b"
```

- 第 461 行 `invalid_budget_value` / `search`：

```python
r"\b(?:maximum\s+|total\s+|my\s+)?budget\s*(?:is|=|of)?\s*(?P<value>[A-Za-z]+)\b"
```

- 第 472 行 `budget_context` / `finditer`：

```python
r"(?:\b(?:but|however)\b|\b(?:while|and|with)\s+(?:the\s+|a\s+)?(?="
            r"(?:hotel|hotels|lodging|accommodation|food|transport|flight|attractions?|"
            r"(?:whole|entire|full)\s+trip|total)\b)|"
            r"\band\s+(?:would|i|spend|keep)\b|,\s*(?:hotel|hotels|lodging|accommodation|"
            r"food|transport|flight|attractions?|while\s+(?:the\s+)?(?:whole|entire|full)\s+trip|"
            r"with\s+(?:a\s+|the\s+)?(?:hotel|lodging|accommodation))\b)"
```

- 第 499 行 `has_budget_intent` / `search`：

```python
r"\d"
```

- 第 501 行 `has_budget_intent` / `finditer`：

```python
r"\bhave\b"
```

- 第 504 行 `has_budget_intent` / `search`：

```python
r"\d"
```

- 第 505 行 `has_budget_intent` / `match`：

```python
r"(?:for|on) (?:the )?trip\b"
```

- 第 537 行 `extract_budget_constraints` / `search`：

```python
r"\bcan't\s+go\s+above\b"
```

- 第 590 行 `is_bounded_scope_correction` / `fullmatch`：

```python
r"\s*(?:(?:total|maximum|ceiling)\s*)*(?:[.;]\s*|\s+(?:and|but)\s+)"
            r"(?:(?:actually|sorry)(?:,\s*)?)?\s*"
            r"(?:i\s+meant|(?:make|change)\s+(?:that|it|the\s+(?:total\s+trip|trip\s+total|total|trip|hotel))|"
            r"(?:no,\s*)?use|instead)(?:\s+to)?\s*"
```

- 第 604 行 `monetary_scope` / `match`：

```python
r"\s*(?:per\s+(?:person|traveler)|each)\b"
```

- 第 606 行 `monetary_scope` / `search`：

```python
r"\b(?:hotel|hotels|lodging|accommodation)\b"
```

- 第 606 行 `monetary_scope` / `search`：

```python
r"\b(?:hotel|hotels|lodging|accommodation)\b"
```

- 第 609 行 `monetary_scope` / `search`：

```python
r"\bper\s+night\b"
```

- 第 612 行 `monetary_scope` / `search`：

```python
r"\b(?:total(?:\s+budget)?|keep\s+(?:the\s+)?trip|(?:whole|entire|full)\s+(?:the\s+)?trip|"
        r"everything|don't\s+let\s+(?:the\s+)?(?:total|trip|whole\s+trip)\s+go\s+over|"
        r"do\s+not\s+let\s+(?:the\s+)?(?:total|(?:total\s+)?trip)\s+go\s+over|"
        r"cap\s+(?:the\s+)?(?:whole\s+)?trip\s+at|(?:the\s+)?trip\s+budget|"
        r"(?:whole|entire|full)\s+trip\s+cap|"
        r"(?:the\s+)?trip\s+(?:(?:must|has\s+to|needs\s+to)\s+stay\s+)?"
        r"(?:under|below|up\s+to|max(?:imum)?|at\s+most|no\s+more\s+than|cannot\s+exceed|can't\s+go\s+above)|"
        r"\btrip\b[^.!?;]{0,40}\b(?:cannot\s+exceed|can't\s+go\s+above)\b|"
        r"\b(?:absolute\s+)?ceiling\b[^.!?;]{0,40}\btrip\b|\boverall\b)\b"
```

- 第 624 行 `monetary_scope` / `match`：

```python
r"\s*(?:total|overall|for (?:the )?(?:(?:whole|entire|full) )?trip)\b"
```

- 第 635 行 `ambiguous_hotel_budget_phrase` / `search`：

```python
r"\b(?:my\s+)?(?:hotel|lodging|accommodation)\s+budget\s*(?:is|of|:)?\b"
```

- 第 636 行 `ambiguous_hotel_budget_phrase` / `search`：

```python
r"\b(?:total|per\s+night|under|below|at\s+most|no\s+more)\b"
```

- 第 642 行 `unsupported_money_scope` / `search`：

```python
r"\b(?:hotel|lodging|accommodation)\b"
```

- 第 642 行 `unsupported_money_scope` / `search`：

```python
r"\bper\s+night\b"
```

- 第 646 行 `unsupported_money_scope` / `search`：

```python
r"\b(?:food|meals?|restaurants?)\b"
```

- 第 648 行 `unsupported_money_scope` / `search`：

```python
r"\b(?:transport(?:ation)?|transit|flight)\b"
```

- 第 650 行 `unsupported_money_scope` / `search`：

```python
r"\b(?:attractions?|activities?|museum\s+tickets?)\b"
```

- 第 725 行 `extract_specific_preferences` / `finditer`：

```python
r"\b(?:want\s+to\s+)?visit\s+(?P<value>zoos?|museums?|parks?)\b"
```

- 第 743 行 `extract_budget_preferences` / `search`：

```python
r"\b(?:hotel|hotels|lodging|accommodation)\b"
```

- 第 745 行 `extract_budget_preferences` / `search`：

```python
r"\b(?:food|meal|restaurant)\b"
```

- 第 752 行 `extract_budget_preferences` / `search`：

```python
r"\bper\s+night\b"
```

- 第 763 行 `preference_category` / `fullmatch`：

```python
r"(?:walking|public transit|taxi)"
```

- 第 765 行 `preference_category` / `search`：

```python
r"\b(?:city|urban|neighborhood)\s+walking\b"
```

- 第 767 行 `preference_category` / `search`：

```python
r"\b(?:zoos?|museums?|parks?)\b"
```

- 第 769 行 `preference_category` / `search`：

```python
r"\b(?:food|cuisine|meal|dish|coffee)\b"
```

- 第 769 行 `preference_category` / `match`：

```python
r"(?:fried|grilled|baked|roasted|steamed)\b"
```

- 第 773 行 `preference_category` / `search`：

```python
r"\b(?:hotel|lodging|accommodation)\b"
```

### 关键词表原文

```python
NUMBER_WORDS = dict(
    zip(
        "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty".split(),
        range(1, 21),
        strict=True,
    )
)

NEGATIVE_TERMS = {
    r"\bnightlife\b": "no nightlife",
    r"\bmeat\b": "no meat",
    r"\b(?:rent a car|rental cars?)\b": "no rental car",
    r"\bexpensive restaurants?\b": "avoid expensive restaurants",
}

GENERIC_PREFERENCE_TERMS = frozenset(
    {
        "museums",
        "food",
        "parks",
        "history",
        "art",
        "quiet",
        "central",
        "budget",
        "luxury",
        "vegetarian",
        "vegan",
        "seafood",
        "walking",
        "public transit",
        "taxi",
    }
)

vocabulary = {
        "interests": ["museums", "food", "parks", "history", "art"],
        "hotel_preferences": ["quiet", "central", "budget", "luxury"],
        "food_preferences": ["vegetarian", "vegan", "seafood"],
        "transport_preferences": ["walking", "public transit", "taxi"],
    }

```

其他关键词直接嵌入上述正则；城市和别名为 `data/travel/us/cities.json` 数据表，随快照一起移植。
### `backend/app/agent/semantic_coverage.py`：20 个直接调用点

- 第 38 行 `模块定义` / `compile`：

```python
r"(?:[$€£]\s*\d|\b(?:USD|EUR|GBP)\s+\d)"
```

- 第 39 行 `模块定义` / `compile`：

```python
r"\b(?:if\s+necessary|okay\s+spending\s+more|"
    r"prioriti[sz]e\b[^.!?;]{0,60}\bover|rather\s+(?:spend|pay)|"
    r"(?:save|spend\s+less)\b[^.!?;]{0,60}\bso\b|"
    r"(?:spend|pay)\s+(?:a\s+little\s+)?(?:more|extra)\b[^.!?;]{0,60}\b(?:if|while)|"
    r"(?:cheaper|simpler)\b[^.!?;]{0,60}\bif|value\b[^.!?;]{0,60}\bover)\b"
    r"|\bstretch\b[^.!?;]{0,40}\bbudget\b[^.!?;]{0,40}\bif\b"
```

- 第 48 行 `模块定义` / `compile`：

```python
r"\b(?:if necessary|okay spending more|rather spend|rather pay)\b"
```

- 第 51 行 `模块定义` / `compile`：

```python
r"(?<![\w])(?:[$€£]\s*|(?:USD|EUR|GBP)\s+)?(?P<value>\d[\d,]*)"
```

- 第 52 行 `模块定义` / `compile`：

```python
r"\b(?:budget|budgt|money|spend(?:ing)?|cost|trip|hotel|lodg\w*|ac+om+odation|"
    r"food|dining|restaurants?|transport|transit|flights?|airfare|activities?|attractions?|total)\b"
```

- 第 57 行 `模块定义` / `compile`：

```python
r"\b(?:under|below|max(?:imum)?|cap(?:ped)?|ceiling|within|must|should|shud|"
    r"cannot|can't|cant|no\s+more|not\s+more|cost\s+less)\b"
```

- 第 62 行 `模块定义` / `compile`：

```python
r"\b(?:actually|sorry|make|change|instead|use)\b"
```

- 第 63 行 `模块定义` / `compile`：

```python
r"\bnot\s+(?:a\s+)?strict\s+cap\b"
```

- 第 64 行 `模块定义` / `compile`：

```python
r"\b(?:around|about|roughly|ideally|ideal|flexible|target|if\s+possible)\b"
```

- 第 67 行 `模块定义` / `compile`：

```python
r"(?:[$€£]\s*\d[\d,]*[^.!?;]{0,40}\b(?:hotel|place\s+to\s+stay|lodging|accommodation)\b|"
    r"\b(?:hotel|place\s+to\s+stay|lodging|accommodation)\b[^.!?;]{0,40}[$€£]\s*\d)"
```

- 第 72 行 `模块定义` / `compile`：

```python
r"(?:\bhotel\b[^.!?;]{0,30}\b(?:around|about)\b[^.!?;]{0,20}[$€£]\s*\d|"
    r"\b(?:around|about)\b[^.!?;]{0,20}[$€£]\s*\d[^.!?;]{0,30}\bhotel\b)"
```

- 第 77 行 `模块定义` / `compile`：

```python
r"\b(?:(?:do\s+not|don't)\s+(?:include|need|schedule|care\s+about)|"
    r"(?:please\s+)?exclude|avoid|without|no(?!\s+more\s+than))\b"
```

- 第 82 行 `模块定义` / `compile`：

```python
r"\b(?:zoos?|nightlife|luxury\s+hotels?|restaurants?|museums?|parks?|seafood|"
    r"casino|hotel\s+areas?|attractions?|activities?)\b"
```

- 第 87 行 `模块定义` / `compile`：

```python
r"\b(?:find\s+me\s+(?:zoos?|museums?|parks?)|"
    r"i\s+(?:like|enjoy|prefer|love)\b|i\s+want\s+(?!to\s+spend|\d)|"
    r"(?:public\s+transit)\s+is\s+preferred|i'd\s+rather\s+stay|"
    r"(?:zoos?|museums?|parks?)[^.!?;]{0,50}\bplease|"
    r"(?:quiet|downtown)\s+hotel)\b"
```

- 第 214 行 `unrepresented_hard_money` / `findall`：

```python
r"\b\d[\d,]*\b"
```

- 第 243 行 `unresolved_correction` / `match`：

```python
r"-?\s*days?\b|\s*stars?\b"
```

- 第 244 行 `unresolved_correction` / `search`：

```python
r"\bnot\s*$"
```

- 第 252 行 `unresolved_correction` / `search`：

```python
r"\b(?:trip|total)\b"
```

- 第 254 行 `unresolved_correction` / `search`：

```python
r"\b(?:hotel|lodging|accommodation)\b"
```

- 第 266 行 `unrepresented_exclusion` / `split`：

```python
r"[.!?;]"
```

### `backend/app/tools/candidate_data.py`：2 个直接调用点

- 第 71 行 `location_key` / `findall`：

```python
r"[a-z0-9]+"
```

- 第 100 行 `city_at_start` / `match`：

```python
pattern
```
