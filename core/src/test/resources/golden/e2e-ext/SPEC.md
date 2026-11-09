# golden-v1.1 e2e-ext 格式

本目录每个 JSON 来自确定性本地快照 `TravelService.plan`，包含 query、requirements、status、公开 execution.tools 和公开 execution.repair；所有 Python float 值均保存为 `repr(float)` 字符串，整数、布尔、null 保持类型。工具请求中的 `requested_arguments.max_price` 与 `runtime_arguments.max_price`、repair 的 `savings` 应以二进制 double 的原始位比较。扩展用例 `hotel-ceiling-760.json` 的酒店总额 760、1 人、3 晚，两个 max_price 均为字符串 `253.33333333333334`。其余 28 个输入来自 `golden/e2e`。

运行：`.venv\Scripts\python.exe scripts\export_golden_e2e_ext.py`。不含 run_id、latency 或其他非业务变化字段。原有 `golden/SPEC.md` 与 fixtures 保持不变。
