# 宿识家 AI 模块参数合同

本文档是用户端、酒店后端、管理端与 Dify 应用之间的唯一参数依据。管理端只读展示这些字段，不能自行增加或修改字段名。

## 调用边界

- 浏览器只提交 `moduleKey` 和当前页面的业务参数。
- `tenantId`、`userId`、酒店资料和知识库内容由后端根据 JWT 与租户上下文注入。
- 平台没有 PMS、OTA、订单、实时房态和实时营收权限，智能体不得推断或伪造这些数据。
- 每个 `moduleKey` 绑定独立 Dify 应用；同一模块可以配置候选应用，但只能启用一个。
- 正式请求采用三段 JSON 字符串，`message` 仅用于 Dify Chatflow query 和旧应用兼容。

## 三段参数信封

### commonContextJson

包含 `schemaVersion`、`requestId`、`tenantId`、`userId`、`moduleKey`、`currentTime`、`locale`、`hotel` 和 `dataScope`。`hotel` 只包含酒店主动维护的基础资料、标签、目标客群、附近信息和房型挂牌价参考。

### businessParamsJson

完整保存用户本次页面选择。字段必须来自 `src/utils/aiModuleContract.ts` 中对应模块的 `params`，历史记录和“复用配置”也使用这一份业务参数快照。

### knowledgeContextJson

结构为 `required`、`query`、`items`、`missingFields`、`retrievedAt`、`maxItems`、`policy`。`items` 只能包含当前租户 `active`、已确认且在有效期内的知识，单项包含 `id`、`category`、`title`、`content`、`sourceType`、`sourceName`、`effectiveFrom`、`effectiveTo`、`updatedAt`、`confidence`。

## 模块清单

| moduleKey | 独立智能体 | 类型 | 业务用途 |
| --- | --- | --- | --- |
| brain | 宿识家 AI 店长 | Chatflow | 经营问答 |
| strategy | 宿识家营销策略师 | Workflow | 阶段营销执行方案 |
| pricing | 宿识家收益定价顾问 | Workflow | 房型定价建议 |
| xhs | 宿识家小红书创作官 | Workflow | 小红书图文 |
| wechat | 宿识家朋友圈编辑 | Workflow | 朋友圈文案 |
| article | 宿识家公众号主编 | Workflow | 公众号推文 |
| video | 宿识家短视频编导 | Workflow | 口播与分镜 |
| poster | 宿识家营销视觉设计师 | Workflow | 海报图片 |
| polish | 宿识家文案润色师 | Workflow | 自由文本润色 |
| review | 宿识家好评引导师 | Workflow | 合规评价邀请 |
| reply | 宿识家点评回复专员 | Workflow | 真实点评回复 |
| knowledge | 宿识家知识整理员 | Workflow | 酒店资料提取与整理 |

各模块完整字段、类型、来源和是否必填由 `src/utils/aiModuleContract.ts` 维护。后端的模块注册表和 Dify DSL 必须与其保持一致，并由合同测试检测漂移。
