# P1b Python 金额路径 float 运算审计

基准：Python 仓库 `golden-v1`，commit `0f498003c365c8a6d1bb1f066dc3afbd3bdb23fd`。审计确定性请求解析、覆盖分析、候选筛选、行程、预算、验证和公开响应；P1b 的入口绕过解析器，因此解析和覆盖分析标为 P2 相关。未审计可选 LLM、评测脚本或非金额耗时指标。以下行号均为 `golden-v1` Python 源码行号。float 包括 Python binary64 算术、比较和会改变数值表示的转换；同时列出关键 Decimal 边界，避免把 Decimal 运算误报为 float 算术。

## 结论：触发停下条件

**有 float 结果进入公开输出，不能按“仅内部筛选”分支继续。** 最明确的是 `service.py:TravelService.plan:201` 的 float 减法和 `round` 写入 `execution.repair.savings`。另一个是 `planner.py:hotel_price_ceiling:139` 的 float 除法写入公开工具请求的 `max_price`。用户可见 `PlanResponse` 和当前 28 个 e2e golden 的字段范围不同：golden 导出自 `TravelService.run` 的自定义投影，未包含 `execution.tools` 或公开 `execution.repair.savings`；不能因 fixtures 未覆盖就认为这些输出不存在。P1b 验收列出的字段不包括公开工具记录，但任务要求检查任何进入输出的 float，故也须上报。

## 确定性规划路径：float 运算与比较

| 源文件:函数:行号 | 表达式、输入来源 | 结果用途 |
|---|---|---|
| `backend/app/agent/planner.py:hotel_price_ceiling:136,139` | `nights = (trip_days or 0) - 1` 和 `travelers * nights` 都是整数运算；`constraint.value / (requirements.travelers * nights)` 是 float 除法。分子来自 Pydantic `BudgetConstraint.value`，人数和天数为整数。 | 用于酒店 `SearchInput.max_price` 和价格筛选；**也进入公开输出**：`planner.py:113` → `execution.py:151–169` 的 `requested_arguments.max_price` / `runtime_arguments.max_price`。例如酒店总额 760、1 人、3 晚输出 `253.33333333333334`。|
| `backend/app/tools/candidate_data.py:candidates_for:146` | `row.price > max_price`，两者均为 float；前者由快照或 mock 经 Pydantic 转换，后者来自上述除法。 | 仅产生筛选布尔值；改变输出候选，但不把比较结果作为数值输出。|
| `backend/app/tools/candidate_data.py:candidates_for:151` | 排序键 `(-len(row.preference_matches), row.price, row.id)` 中的 `row.price` 是 float；匹配数量的负号是整数运算。 | float 用于比较和排序；原价本身也进入输出候选，但排序没有生成新的金额数值。|
| `backend/app/agent/itinerary.py:select_option:121–122` | 修复时 `min(options, key=lambda option: (option.price, option.id))` 比较 float 价格；正常轮换 `(day - 1) % len(options)` 是整数运算。 | 内部选择；选择的候选及原价进入输出。|
| `backend/app/agent/itinerary.py:validate_itinerary:138,141,162` | `subtotal != day.estimated_cost`、`total != budget.estimated_total_cost`、`total != itinerary.estimated_total_cost`、`excess > 0`；各金额为 `money()` 返回的 float。 | 内部比较；控制错误、结构化 violation 和修复。`actual`、`expected`、`excess` 的 float 值本身也进入 violation 输出。|
| `backend/app/agent/service.py:TravelService.plan:193–203` | `initial_total - final_total` 是两个已量化金额 float 的减法，随后 `round(..., 2)` 仍是 Python float，最后 `max(..., 0)`。输入分别来自修复前行程总额和最终预算总额。 | **直接输出**到 `execution.repair.savings`。真实 Austin 预设：`394.0 - 317.0` → `77.0`。这属于公开 `plan()` 投影，不在当前 `golden/e2e` 自定义 repair 对象中。|

## Decimal 与 float 的关键边界

| 源文件:函数:行号 | 表达式、输入来源 | 结果用途 |
|---|---|---|
| `backend/app/tools/mock.py:money:23–24` | `float(value.quantize(Decimal("0.01")))`：先按当前 Decimal context 量化，再转换为 float；量化不是 float 运算。 | **转换结果直接进入金额输出**，包括活动、小计、预算和 violation 字段；也会被后续 float 比较或 `str(float)` 使用。|
| `backend/app/agent/itinerary.py:compose_draft:78–88` | `Decimal(str(option.price)) * multiplier` 是 Decimal 乘整数；`money(...)` 将结果转回 float。每日小计使用 `Decimal(str(a.estimated_cost))` 累加，再经 `money` 转 float。输入 price 是 Pydantic float。 | `Activity.estimated_cost` 和 `DayPlan.estimated_cost` **进入输出**；每次 `str(float)` 的十进制表示又是后续 Decimal 的输入。`CostItem.amount` 同样保留原价格 float。|
| `backend/app/tools/mock.py:calculate_budget:99–104,117–143` | `Decimal(str(item.amount))` 及 `Decimal(str(arguments.limit))` 将 float 的 Python 字符串转回 Decimal；分类汇总、乘人数、比较、减法均是 Decimal 运算。`money(...)` 把 per-person、total、breakdown、comparison 和 remaining 转为 float。 | 这些 `money` 结果**进入预算输出**；`provided_limit` 直接传入原 float。预算比较控制固定 warning 文本；文本本身不插入数值。|
| `backend/app/agent/itinerary.py:validate_itinerary:135–161` | 对活动、日小计、实际值和约束值执行 `Decimal(str(float))`；加总及 `Decimal(str(actual)) - Decimal(str(expected))` 为 Decimal 运算，经 `money` 转成 float。 | 核验总额并生成 violation；**量化后的 actual、expected、excess 进入输出**。|
| `backend/app/tools/contracts.py:TravelOption.price`, `CostItem.amount`, `BudgetInput.limit`, `BudgetSummary` 金额字段；`backend/app/agent/requirements.py:BudgetConstraint.value`, `TravelRequirements.budget_amount` | Pydantic 模型的字段声明为 `float`；从数据文件、解析结果或工具请求进入模型时可能发生数值到 binary64 的转换。 | 候选价格、预算上限、需求约束和各预算金额可直接出现在输出。最初 JSON 的小数精度可能在后续 Decimal 操作之前已经丢失。|

## 确定性解析及覆盖分析（P2 相关，P1b 绕过）

| 源文件:函数:行号 | 表达式、输入来源 | 结果用途 |
|---|---|---|
| `backend/app/agent/requirements.py:parse_requirements:301–303` | `float(amount["amount"].replace(",", "")) * multiplier`，金额来自 query 正则，multiplier 为 1 或 1000。 | **结果进入输出** `requirements.budget_amount`；可能随后作为预算 limit/provided_limit。|
| `backend/app/agent/requirements.py:extract_budget_constraints:541,553–560` | `float(amount["amount"].replace(",", "")) * (1000 if amount["multiplier"] else 1)`。 | 对支持的硬预算，**结果进入输出** `requirements.requirements_v2.constraints[*].value`，并可用于酒店上限或验证。|
| `backend/app/agent/semantic_coverage.py:budget_like_amounts:190–202` | `float(match["value"].replace(",", ""))`，再用 `value < 50`；输入来自原 query。 | 用于内部判断和返回内部 tuple；覆盖结果输出的 evidence 是原文子串，不是格式化的 float。|
| `backend/app/agent/semantic_coverage.py:unrepresented_hard_money:208–225` | `float(value.replace(",", ""))` 和 `float(match["value"].replace(",", ""))`；与已表示金额做 float 集合相等及交集判断。 | 内部比较影响覆盖原因和 evidence；这些转换结果不直接作为数值输出。|
| `backend/app/agent/semantic_coverage.py:unresolved_correction:249,258–260` | `float(amount["value"].replace(",", ""))`，与约束 `item.value` 做 float 相等比较。 | 内部比较影响覆盖原因及原文 evidence，不直接输出该 float。|

## 验证证据及边界

只读调用 `DeterministicTestPlanner.plan`：酒店总额 `760.0`、4 天、1 人时，酒店请求的 `max_price` 为 `253.33333333333334`。只读调用本地快照、确定性 `TravelService.plan`：Austin 修复预设成功，初始 `394.0`、最终 `317.0`、公开 `savings=77.0`。没有调用 LLM 或网络，也没有修改 Python 仓库。

`money()` 量化后转 float 再经 `str(float)` 输入 Decimal，和直接保持 BigDecimal 虽常在当前 fixtures 相同，不能假设对所有输入严格等价。高精度输入、binary64 边界、量化后 float 表示以及 `round` 的行为需要明确方案和针对性测试。`savings` 属于原公开响应但不属于现有 e2e repair 投影；`max_price` 属于公开工具记录但同样不在现有 e2e 投影。请确定移植范围与如何生成符合 Python 的数值表示后再继续；本次没有实现 `pyFloat` 或 P1b 引擎。

## D7 补充审计：负零与除数

在 `golden-v1` 的 `backend/app` 中检索 `copysign`、`atan2`、`signbit`、`is_signed`、`as_tuple`、`signum`、`nextafter`、`frexp`、浮点打包和 `.hex()`，没有发现匹配；并检查确定性金额计算和预算相关源码，未发现以其他方式读取零符号。可选 LLM 和评测指标不属于该确定性路径。

确定性金额路径唯一除法是 `planner.py:hotel_price_ceiling:139`：分母是整数人数乘以整数住宿晚数，不由预算值计算。Python 要求人数字段至少为 1，并在晚数小于 1 时返回 `None`，因此合法请求不会以零作除数。独立验证 `1.0 / 0.0` 和 `1.0 / -0.0` 均为 Python `ZeroDivisionError`；Kotlin `pyFloatHotelCeiling` 对零分母显式抛 `ArithmeticException`，而实际状态机同样在零晚时不调用它。测试分别覆盖这两层行为。

D1 按新版规则使用非 NaN 的 IEEE 754 数值相等；`-0.0` 与 `+0.0` 相等。`TravelEngineTest.signedZeroBudgetAndDivisionByZero` 固定预算输入负零后 Python/Kotlin 数值相同但符号位不同的例外。`MoneyFormatting` 对 Double 先取其精确十进制并按 HALF_EVEN 舍入，对 BigDecimal 直接舍入，再把显示为负零的情况统一为 `0.00`；当前占位 UI 不显示金额，P3 应使用该格式化入口。
