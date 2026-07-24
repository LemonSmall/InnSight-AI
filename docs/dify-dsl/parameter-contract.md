# Dify 参数合同

所有应用统一接收 `commonContextJson`、`businessParamsJson`、`knowledgeContextJson` 和兼容字段 `message`。同时，每个模块会把用户端实际业务字段展开为 Dify Start 独立输入，便于在 Dify 节点里直接引用、分支和调试。

前三项由宿识家后端组装，Dify 不负责识别租户身份。独立业务字段与 `businessParamsJson` 冲突时，以独立业务字段为准。

| moduleKey | 智能体 | 应用类型 | DSL 文件 | 显式业务字段 |
| --- | --- | --- | --- | --- |
| brain | 宿识家 AI 店长 | chatflow | `brain-manager-chatflow.yml` | message（经营问题）、enableWebSearch、surroundingTaskMode、surroundingContextJson、outputStyle（回答方式） |
| strategy | 宿识家营销策略师 | workflow | `strategy-marketing-workflow.yml` | theme、objective、period、occasion、targetAudience、channels、budgetLevel、executionCapacity、outputDepth、marketSignals、competitorObservations、availableOffers、constraints、evidenceRequirement、surroundingContextJson |
| pricing | 宿识家收益定价顾问 | workflow | `pricing-revenue-workflow.yml` | dateRange、pricingPeriod、pricingGoal、demandSignal、bookingWindow、eventFactor、competitorPriceRange、currentPriceNotes、priceFloor、maxDiscountPercent、targetChannels、promotionAllowed、packagePreference、riskLevel、constraints、roomSnapshot、evidenceRequirement、surroundingContextJson |
| surrounding | 宿识家周边信息智能体 | workflow | `surrounding-intelligence-workflow.yml` | commonContextJson、businessParamsJson、knowledgeContextJson、taskMode、query、checkIn、checkOut、hotelName、city、address、longitude、latitude、message |
| xhs | 宿识家小红书创作官 | workflow | `xhs-content-workflow.yml` | topics、customTopic、tone、style、note、withImage、imageSize、imageCount |
| wechat | 宿识家朋友圈编辑 | workflow | `wechat-editor-workflow.yml` | slots、style、length、note、withImage、imageSize |
| article | 宿识家公众号主编 | workflow | `article-editor-workflow.yml` | title、style、length、withImage、imageCount、fileName |
| video | 宿识家短视频编导 | workflow | `video-director-workflow.yml` | sellingPoints、view、style、goal、duration、count |
| poster | 宿识家营销视觉设计师 | workflow | `poster-visual-workflow.yml` | mode、theme、content、style、scene、platform、targetAudience、textDensity、cta、sellingPoint、imageSize、width、height、imageData |
| polish | 宿识家文案润色师 | workflow | `polish-copy-workflow.yml` | sourceText、scene、field、style、purpose、immutableFacts |
| review | 宿识家好评引导师 | workflow | `review-guide-workflow.yml` | guestType、scene、additionalNotes |
| reply | 宿识家点评回复专员 | workflow | `reply-specialist-workflow.yml` | reviewText、reviewType、style、additionalNotes |
| knowledge | 宿识家知识整理员 | workflow | `knowledge-organizer-workflow.yml` | sourceType、sourceText、fileName、extractionMode、effectiveHint |

## 宿识家 AI 店长

| 字段 | 含义 | 类型 | 必填 | 来源 |
| --- | --- | --- | --- | --- |
| message | 用户问题 | string | 是 | AI 店长输入框 |
| enableWebSearch | 是否联网搜索 | boolean | 否 | AI 店长页面开关；true 时后端先调用周边 Agent |
| surroundingTaskMode | 周边任务模式：`full` 或 `weather_only` | string | 否 | 后端根据用户问题自动判断 |
| surroundingContextJson | 周边信息智能体结果 JSON | string | 否 | 仅开启联网搜索时由后端注入 |
| outputStyle | 回答方式 | string | 否 | AI 店长页面 |

## 宿识家营销策略师

| 字段 | 含义 | 类型 | 必填 | 来源 |
| --- | --- | --- | --- | --- |
| theme | 策略主题 | string | 是 | 营销策略页 |
| objective | 经营目标 | string | 是 | 营销策略页 |
| period | 执行周期 | string | 是 | 营销策略页 |
| occasion | 经营场景 | string | 是 | 营销策略页 |
| targetAudience | 目标客群 | string | 否 | 营销策略页 |
| channels | 执行渠道 | array | 是 | 营销策略页 |
| budgetLevel | 预算条件 | string | 否 | 营销策略页 |
| executionCapacity | 执行能力 | string | 否 | 营销策略页 |
| outputDepth | 方案深度 | string | 否 | 营销策略页 |
| marketSignals | 已确认市场信号 | string | 否 | 营销策略页 |
| competitorObservations | 竞品人工观察 | string | 否 | 营销策略页 |
| availableOffers | 可使用权益 | string | 否 | 营销策略页 |
| constraints | 执行限制 | string | 否 | 营销策略页 |
| evidenceRequirement | 标注待核实信息 | boolean | 否 | 营销策略页 |
| surroundingContextJson | 周边信息智能体结果 JSON | string | 是 | 后端先调用周边 Agent、校验后注入 |

## 宿识家收益定价顾问

| 字段 | 含义 | 类型 | 必填 | 来源 |
| --- | --- | --- | --- | --- |
| dateRange | 日期范围 | string | 是 | 智能定价页 |
| pricingPeriod | 定价周期 | string | 是 | 智能定价页 |
| pricingGoal | 定价目标 | string | 是 | 智能定价页 |
| demandSignal | 人工确认需求信号 | string | 是 | 智能定价页 |
| bookingWindow | 主要预订窗口 | string | 是 | 智能定价页 |
| eventFactor | 日期影响因素 | string | 是 | 智能定价页 |
| competitorPriceRange | 竞品价格观察 | string | 否 | 智能定价页 |
| currentPriceNotes | 当前销售现象 | string | 否 | 智能定价页 |
| surroundingContextJson | 周边信息智能体结果 JSON | string | 是 | 后端先调用周边 Agent、校验后注入 |
| priceFloor | 最低可接受价 | string | 否 | 智能定价页 |
| maxDiscountPercent | 最大折扣比例 | number | 否 | 智能定价页 |
| targetChannels | 重点销售渠道 | array | 否 | 智能定价页 |
| promotionAllowed | 允许限时促销 | boolean | 否 | 智能定价页 |
| packagePreference | 价格策略 | string | 否 | 智能定价页 |
| riskLevel | 风险偏好 | string | 否 | 智能定价页 |
| constraints | 价格限制 | string | 否 | 智能定价页 |
| roomSnapshot | 房型挂牌价快照 | array | 是 | 酒店房型资料 |
| evidenceRequirement | 区分事实与待核实信息 | boolean | 否 | 智能定价页 |

## 宿识家周边信息智能体

周边信息智能体只做外部情报搜索，不负责定价决策、营销策略或自动执行。它的变量设计以项目统一上下文为主，截图中的 hotelName/city/address/longitude/latitude/checkIn/checkOut 只作为 Dify 调试和工具 query 的冗余字段。

| 字段 | 含义 | 类型 | 必填 | 来源 |
| --- | --- | --- | --- | --- |
| commonContextJson | 酒店与调用上下文 JSON，含 `hotel.realWorldBinding`、房型、挂牌价、数据边界 | string | 是 | 后端统一注入 |
| businessParamsJson | 本次周边搜索参数 JSON，含搜索场景、日期、半径、业态限制 | string | 是 | 后端统一注入 |
| knowledgeContextJson | 已确认知识上下文 JSON | string | 否 | 后端兼容注入 |
| taskMode | 任务模式：`full` 或 `weather_only` | string | 是 | 后端根据调用场景生成 |
| query | 搜索任务/搜索意图 | string | 是 | 后端按调用场景生成 |
| checkIn | 入住日期 | string | 否 | 后端冗余注入，主要用于 Dify 调试和搜索 query |
| checkOut | 离店日期 | string | 否 | 后端冗余注入，主要用于 Dify 调试和搜索 query |
| hotelName | 绑定酒店名称 | string | 否 | 后端冗余注入，权威来源仍是 `commonContextJson` |
| city | 城市 | string | 否 | 后端冗余注入，权威来源仍是 `commonContextJson` |
| address | 酒店详细地址 | string | 否 | 后端冗余注入，权威来源仍是 `commonContextJson` |
| longitude | 经度 | string | 否 | 后端冗余注入，权威来源仍是 `commonContextJson` |
| latitude | 纬度 | string | 否 | 后端冗余注入，权威来源仍是 `commonContextJson` |
| message | 兼容搜索指令 | string | 否 | 后端兼容注入 |

## 宿识家小红书创作官

| 字段 | 含义 | 类型 | 必填 | 来源 |
| --- | --- | --- | --- | --- |
| topics | 内容方向 | string | 是 | 小红书页 |
| customTopic | 自定义主题 | string | 否 | 小红书页 |
| tone | 内容语气 | string | 否 | 小红书页 |
| style | 写作风格 | string | 否 | 小红书页 |
| note | 补充要求 | string | 否 | 小红书页 |
| withImage | 需要配图 | boolean | 否 | 小红书页 |
| imageSize | 图片比例 | string | 否 | 小红书页 |
| imageCount | 图片数量 | number | 否 | 小红书页 |

## 宿识家朋友圈编辑

| 字段 | 含义 | 类型 | 必填 | 来源 |
| --- | --- | --- | --- | --- |
| slots | 发布时段 | array | 是 | 朋友圈页 |
| style | 文案风格 | string | 否 | 朋友圈页 |
| length | 文案长度 | string | 否 | 朋友圈页 |
| note | 补充要求 | string | 否 | 朋友圈页 |
| withImage | 需要配图 | boolean | 否 | 朋友圈页 |
| imageSize | 图片比例 | string | 否 | 朋友圈页 |

## 宿识家公众号主编

| 字段 | 含义 | 类型 | 必填 | 来源 |
| --- | --- | --- | --- | --- |
| title | 推文主题 | string | 是 | 公众号页 |
| style | 文章风格 | string | 否 | 公众号页 |
| length | 文章长度 | string | 否 | 公众号页 |
| withImage | 需要配图 | boolean | 否 | 公众号页 |
| imageCount | 配图数量 | number | 否 | 公众号页 |
| fileName | 参考文件名 | string | 否 | 公众号页 |

## 宿识家短视频编导

| 字段 | 含义 | 类型 | 必填 | 来源 |
| --- | --- | --- | --- | --- |
| sellingPoints | 核心卖点 | string | 是 | 短视频页 |
| view | 叙事视角 | string | 否 | 短视频页 |
| style | 视频风格 | string | 否 | 短视频页 |
| goal | 发布目标 | string | 否 | 短视频页 |
| duration | 视频时长 | string | 否 | 短视频页 |
| count | 脚本数量 | number | 否 | 短视频页 |

## 宿识家营销视觉设计师

| 字段 | 含义 | 类型 | 必填 | 来源 |
| --- | --- | --- | --- | --- |
| mode | 创作模式 | string | 是 | 海报页 |
| theme | 海报主题 | string | 是 | 海报页 |
| content | 海报正文 | string | 否 | 海报页 |
| style | 视觉风格 | string | 否 | 海报页 |
| scene | 营销场景 | string | 否 | 海报页 |
| platform | 投放平台 | string | 否 | 海报页 |
| targetAudience | 目标客群 | string | 否 | 海报页 |
| textDensity | 文字密度 | string | 否 | 海报页 |
| cta | 行动号召 | string | 否 | 海报页 |
| sellingPoint | 核心卖点 | string | 否 | 海报页 |
| imageSize | 图片比例 | string | 是 | 海报页 |
| width | 图片宽度 | number | 是 | 海报页 |
| height | 图片高度 | number | 是 | 海报页 |
| imageData | 待美化图片 | string | 否 | 海报美化模式 |

## 宿识家文案润色师

| 字段 | 含义 | 类型 | 必填 | 来源 |
| --- | --- | --- | --- | --- |
| sourceText | 原始文本 | string | 是 | 各业务输入框 |
| scene | 使用场景 | string | 是 | 调用页面 |
| field | 目标字段 | string | 是 | 调用页面 |
| style | 目标风格 | string | 否 | 调用页面 |
| purpose | 润色目标 | string | 否 | 调用页面 |
| immutableFacts | 不可修改事实 | array | 否 | 后端酒店资料 |

## 宿识家好评引导师

| 字段 | 含义 | 类型 | 必填 | 来源 |
| --- | --- | --- | --- | --- |
| guestType | 客群类型 | string | 是 | 好评引导页 |
| scene | 评价场景 | string | 否 | 好评引导页 |
| additionalNotes | 补充要求 | string | 否 | 好评引导页 |

## 宿识家点评回复专员

| 字段 | 含义 | 类型 | 必填 | 来源 |
| --- | --- | --- | --- | --- |
| reviewText | 客人原始评价 | string | 否 | 点评回复页 |
| reviewType | 评价类型 | string | 是 | 点评回复页 |
| style | 回复语气 | string | 否 | 点评回复页 |
| additionalNotes | 回复要求 | string | 否 | 点评回复页 |

## 宿识家知识整理员

| 字段 | 含义 | 类型 | 必填 | 来源 |
| --- | --- | --- | --- | --- |
| sourceType | 资料来源类型 | string | 是 | 资料中心 |
| sourceText | 待整理内容 | string | 是 | 资料中心 |
| fileName | 文件名称 | string | 否 | 资料中心 |
| extractionMode | 提取模式 | string | 否 | 资料中心 |
| effectiveHint | 有效期提示 | string | 否 | 资料中心 |
