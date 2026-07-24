# 宿识家 Dify 核心经营 DSL v2

本目录本次重新生成 4 份核心 DSL：

1. `sushijia-manager-chatflow.yml`：AI 店长，可通过 `enableWebSearch` 控制是否先取联网/天气情报。
2. `sushijia-surrounding-chatflow.yml`：联网分析助手，`taskMode=weather_only` 时只查高德天气，`taskMode=full` 时再联网搜索。
3. `sushijia-strategy-chatflow.yml`：营销策略师，只使用后端注入的酒店资料、知识库和 `surroundingContextJson`。
4. `sushijia-pricing-chatflow.yml`：收益定价顾问，只使用后端注入的酒店资料、知识库和 `surroundingContextJson`。

后端调用顺序：AI 店长按开关决定是否预取 `surrounding`；营销策略和智能定价固定先预取 `surrounding`，再调用对应智能体。天气-only 问题只需要 `weather_only` 情报，由 AI 店长总结。
