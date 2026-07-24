# 宿识家 Dify 独立智能体 DSL

本目录包含 12 个独立应用。需要连续经营对话的 brain 使用 Chatflow；其余确定性生成、润色、回复、策略、定价、海报和知识整理任务使用 Workflow。每个功能必须导入为独立 Dify App、单独发布、生成独立 App API Key，再在宿识家管理端绑定。

所有 DSL 使用官方仓库当前样例结构 `version: 0.3.1`。Chatflow 通过 Answer 节点输出，Workflow 通过 End 节点的 `output` 字段输出；宿识家后端均使用 `response_mode=streaming` 接收事件。

1. `brain-manager-chatflow.yml`：宿识家 AI 店长（chatflow）
2. `strategy-marketing-workflow.yml`：宿识家营销策略师（workflow）
3. `pricing-revenue-workflow.yml`：宿识家收益定价顾问（workflow）
4. `xhs-content-workflow.yml`：宿识家小红书创作官（workflow）
5. `wechat-editor-workflow.yml`：宿识家朋友圈编辑（workflow）
6. `article-editor-workflow.yml`：宿识家公众号主编（workflow）
7. `video-director-workflow.yml`：宿识家短视频编导（workflow）
8. `poster-visual-workflow.yml`：宿识家营销视觉设计师（workflow）
9. `polish-copy-workflow.yml`：宿识家文案润色师（workflow）
10. `review-guide-workflow.yml`：宿识家好评引导师（workflow）
11. `reply-specialist-workflow.yml`：宿识家点评回复专员（workflow）
12. `knowledge-organizer-workflow.yml`：宿识家知识整理员（workflow）

禁止把一个 App API Key 同时绑定给多个 moduleKey。DSL 故意不绑定具体模型，导入后必须在 Dify 中选择当前工作区真实可用的模型并重新发布。
