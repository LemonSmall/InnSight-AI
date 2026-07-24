# 宿识家 AIFlowy 智能体搭建施工图

## 1. 总体定位

AIFlowy 在本项目中不负责酒店 SaaS 的核心业务数据，不负责登录、租户、员工、算力余额和订单类能力。AIFlowy 只负责 AI 智能体、工作流、知识库检索、模型编排、插件调用和内容审核。

项目边界如下：

| 系统 | 负责内容 |
| --- | --- |
| 宿识家 SaaS 后端 | 登录鉴权、租户、员工、酒店资料、房态、客人、营销计划、算力扣费、任务记录、结果保存 |
| AIFlowy | 智能体、工作流、Prompt 编排、RAG 知识库、模型路由、插件工具调用、AI 内容审核和改写 |

推荐命名：

| 项目项 | 名称 |
| --- | --- |
| AIFlowy 工作空间 | 宿识家 AI 运营中台 |
| 总智能体 | 宿识家 AI 店长 |
| 内容智能体 | 宿识家内容创作官 |
| 运营智能体 | 宿识家运营参谋 |
| 客服智能体 | 宿识家口碑管家 |
| 收益智能体 | 宿识家收益顾问 |
| 海报智能体 | 宿识家营销设计师 |

## 2. AIFlowy 左侧菜单怎么用

截图里的菜单建议这样规划。

| AIFlowy 菜单 | 在本项目中的用途 | 第一阶段要做什么 |
| --- | --- | --- |
| Wiki | 团队内部说明文档 | 写智能体使用规范、内容生成规范、审核规范 |
| 智能体 | 配置可对话的业务 Agent | 建 5 个智能体：店长、内容、客服、收益、设计 |
| 插件 | 封装 HTTP 工具 | 建宿识家数据插件，调用 SaaS 后端内部 API |
| 工作流 | 编排多步骤 AI 任务 | 建小红书、朋友圈、视频、公众号、回评、策略工作流 |
| 知识库 | RAG 检索资料 | 建酒店资料库、品牌语气库、平台规则库、营销案例库 |
| 素材库 | 存图片、海报、案例素材 | 存房间图、公共区图、周边图、历史海报 |
| 数据中枢 | 接业务数据源 | 接 tenants、room_types、room_status、guests、marketing_plans |
| 模型管理 | 管 OpenAI/DeepSeek/通义等模型 | 先配 DeepSeek 文案模型 + 通义/千问备选 |
| MCP | 后期接外部工具生态 | 第二阶段再接，先不用 |
| 向量数据库 | 知识库检索底座 | 建 hotel_knowledge、brand_voice、platform_rules 三个集合 |

## 3. 智能体清单

### 3.1 宿识家 AI 店长

用途：统一入口，回答经营问题，并把用户意图路由到内容、客服、收益、设计等智能体。

建议配置：

| 配置项 | 值 |
| --- | --- |
| 智能体名称 | 宿识家 AI 店长 |
| 英文标识 | sushijia_manager_agent |
| 开场白 | 我是你的 AI 店长，可以帮你看经营数据、生成营销内容、制定节假日策略、优化回评话术。 |
| 绑定知识库 | 酒店基础资料库、品牌语气库、营销案例库、平台规则库 |
| 绑定插件 | 宿识家数据插件、宿识家任务回调插件 |
| 主要工作流 | brain、strategy、xhs、wechat、video、article、review、reply、pricing |

系统提示词：

```text
你是“宿识家 AI 店长”，服务对象是酒店、民宿、度假酒店的经营者。

你的目标：
1. 把复杂经营问题转化成清晰、可执行的运营动作。
2. 根据酒店资料、房态、客群、天气、节假日、历史营销计划，给出内容创作、定价、客服和营销建议。
3. 当用户需要生成内容时，判断应调用哪个工作流：朋友圈、小红书、视频脚本、公众号、海报、好评、回评、营销策略。

回答要求：
- 使用中文。
- 直接给方案，不讲空话。
- 优先输出可执行清单。
- 涉及价格、满房率、活动节奏时，要说明依据。
- 不编造酒店不存在的设施、奖项、政策。
- 不承诺“ guaranteed 满房、必火、稳赚”等绝对结果。
```

### 3.2 宿识家内容创作官

用途：负责朋友圈、小红书、短视频、公众号。

| 配置项 | 值 |
| --- | --- |
| 智能体名称 | 宿识家内容创作官 |
| 英文标识 | sushijia_content_agent |
| 绑定工作流 | wechat_daily_post、xhs_note_generator、douyin_script_generator、wechat_article_generator |
| 绑定知识库 | 酒店基础资料库、品牌语气库、平台规则库、营销案例库 |

系统提示词：

```text
你是专业的酒店民宿内容营销专家，擅长小红书、朋友圈、抖音口播、公众号推文。

你必须根据输入的酒店真实信息创作内容，不得虚构设施、价格、距离和优惠。
内容要符合平台语气：
- 小红书：真实体验、种草、标题有记忆点、标签完整。
- 朋友圈：短、自然、像店主日常分享。
- 抖音：前 3 秒有钩子，镜头感强，口播自然。
- 公众号：结构完整，有故事和转化引导。

输出必须符合工作流要求的 JSON 格式。
```

### 3.3 宿识家口碑管家

用途：负责好评模板、差评/中评/好评回复。

| 配置项 | 值 |
| --- | --- |
| 智能体名称 | 宿识家口碑管家 |
| 英文标识 | sushijia_reputation_agent |
| 绑定工作流 | review_template_generator、reply_generator |
| 绑定知识库 | 酒店基础资料库、客服话术库、平台规则库 |

系统提示词：

```text
你是酒店口碑运营专家，负责生成自然真实的好评模板和平台回评话术。

要求：
- 好评模板要像真实客人写的，不要广告腔。
- 差评回复先表达歉意，再解释事实，再给改进动作。
- 不攻击客人，不甩锅平台，不承诺无法兑现的补偿。
- 每条回复适合直接发布到携程、美团、飞猪、小红书等平台。
```

### 3.4 宿识家收益顾问

用途：负责定价、房态早报、经营诊断。

| 配置项 | 值 |
| --- | --- |
| 智能体名称 | 宿识家收益顾问 |
| 英文标识 | sushijia_revenue_agent |
| 绑定工作流 | pricing_advisor、room_status_daily_brief、operation_brain |
| 绑定知识库 | 酒店基础资料库、收益管理规则库 |

系统提示词：

```text
你是酒店收益管理和运营诊断专家。

你要根据房型、基础价、当前出租率、未来房态、节假日、天气、客源结构，给出定价建议和经营动作。

要求：
- 输出每个房型的建议价、涨跌幅、理由。
- 高入住率时给出涨价和控房建议。
- 低入住率时给出促销和内容投放建议。
- 不输出没有依据的市场数据。
```

### 3.5 宿识家营销设计师

用途：负责海报文案、视觉方向、活动主题。

| 配置项 | 值 |
| --- | --- |
| 智能体名称 | 宿识家营销设计师 |
| 英文标识 | sushijia_design_agent |
| 绑定工作流 | poster_copy_generator |
| 绑定知识库 | 酒店基础资料库、品牌语气库、历史海报案例库 |

系统提示词：

```text
你是酒店民宿营销海报设计师，负责生成海报文案和视觉指导。

输出内容必须包括：
1. 主标题
2. 副标题
3. 活动利益点
4. 行动号召
5. 视觉建议
6. 画面元素
7. 风险提醒

不得虚构价格和优惠，除非输入中明确给出。
```

## 4. 知识库怎么建

### 4.1 酒店基础资料库

名称：宿识家酒店资料库

建议集合名：hotel_knowledge

资料来源：
- tenants：酒店名称、类型、城市、标签、目标客群、周边
- room_types：房型、基础价格、房量
- marketing_plans：历史营销计划
- 人工上传：酒店介绍、房间图片说明、周边玩法、交通说明、服务政策

文档模板：

```text
# 酒店基础资料

酒店名称：{{hotel_name}}
酒店类型：{{hotel_type}}
城市：{{city}}
总房量：{{total_rooms}}
核心特色：{{tags}}
目标客群：{{target_audience}}
周边资源：{{nearby}}

房型：
{{room_types}}

服务政策：
{{policies}}

不可虚构信息：
- 未提供的设施不能写。
- 未提供的优惠不能写。
- 未提供的距离不能写具体分钟数。
```

### 4.2 品牌语气库

名称：宿识家品牌语气库

建议集合名：brand_voice

来自现有 `style_library`：
- 治愈温暖
- 活泼元气
- 轻奢精致
- 故事叙事
- 竹林禅意系

每个风格都建成一篇知识库文档，格式：

```text
# 风格名称：治愈温暖

适合平台：小红书、朋友圈、公众号
适合客群：城市白领、亲子家庭、情侣游客
语气：温暖、治愈、有情绪共鸣
句式：短句为主，适当感官描写
禁忌：不要太广告，不要强行卖惨，不要过度 emoji
示例：
...
```

### 4.3 平台规则库

名称：宿识家平台规则库

建议集合名：platform_rules

内容：
- 小红书禁忌：绝对化承诺、夸大宣传、价格不透明、医疗功效词
- 抖音禁忌：虚假优惠、诱导违规私信、标题党过度
- OTA 回评禁忌：攻击用户、泄露隐私、承诺未授权赔付
- 公众号禁忌：虚假数据、未授权图片声明

### 4.4 营销案例库

名称：宿识家营销案例库

建议集合名：marketing_cases

内容：
- 端午活动案例
- 暑期亲子案例
- 七夕情侣案例
- 周末短途案例
- 雨天淡季转化案例

## 5. 插件怎么建

插件名称：宿识家数据插件

用途：让 AIFlowy 工作流读取 SaaS 后端里的实时数据。

建议先做 6 个 HTTP 工具。

| 工具名称 | 方法 | 路径 | 用途 |
| --- | --- | --- | --- |
| getHotelProfile | GET | /internal/ai/tenants/{tenantId}/profile | 获取酒店画像 |
| getRoomSnapshot | GET | /internal/ai/tenants/{tenantId}/rooms/snapshot | 获取今日房态和出租率 |
| getGuestSegments | GET | /internal/ai/tenants/{tenantId}/guests/segments | 获取在住客群结构 |
| getMarketingPlans | GET | /internal/ai/tenants/{tenantId}/plans | 获取营销计划 |
| saveAiResult | POST | /internal/ai/tasks/{taskId}/result | 保存 AI 结果 |
| reportAiUsage | POST | /internal/ai/tasks/{taskId}/usage | 上报 token、模型、耗时 |

内部接口需要加签名：

```text
X-Sushijia-Ai-Key: ${internal_ai_key}
X-Sushijia-Timestamp: 1720000000
X-Sushijia-Signature: HMAC_SHA256(body + timestamp, internal_ai_secret)
```

## 6. 工作流清单

第一阶段建议建 8 个工作流。

| module_key | 工作流名称 | AIFlowy 标识 | 业务页面 |
| --- | --- | --- | --- |
| brain | 运营智慧大脑 | operation_brain | BrainView |
| strategy | 周期营销策略生成 | holiday_strategy_generator | StrategyView |
| xhs | 小红书图文生成 | xhs_note_generator | XhsView |
| wechat | 朋友圈三档文案 | wechat_daily_post | WechatView |
| video | 短视频口播脚本 | douyin_script_generator | VideoView |
| article | 公众号推文生成 | wechat_article_generator | ArticleView |
| review | 好评模板生成 | review_template_generator | ReviewView |
| reply | 回评话术生成 | reply_generator | ReplyView |
| poster | 海报文案生成 | poster_copy_generator | PosterView |
| pricing | 智能定价建议 | pricing_advisor | PricingView |

## 7. 统一输入输出协议

### 7.1 SaaS 调 AIFlowy 输入

```json
{
  "tenantId": 1,
  "taskId": 10086,
  "moduleKey": "xhs",
  "requestId": "task-10086",
  "userInput": {
    "theme": "端午竹林民宿",
    "tone": "治愈温暖",
    "platform": "xiaohongshu",
    "extra": "突出私汤、竹林、雨天氛围"
  },
  "context": {
    "hotelName": "莫干山竹影民宿",
    "city": "湖州德清",
    "tags": "竹林、私汤、山景、亲子",
    "targetAudience": "城市白领、情侣、亲子家庭"
  },
  "callbackUrl": "https://api.sushijia.com/internal/ai/tasks/10086/result"
}
```

### 7.2 AIFlowy 回调 SaaS 输出

```json
{
  "taskId": 10086,
  "moduleKey": "xhs",
  "status": "success",
  "content": {
    "titles": [
      "住进竹林里的端午假期",
      "莫干山这家民宿，雨天更像一场疗愈",
      "逃离城市 48 小时，我在竹林里慢下来"
    ],
    "body": "正文内容...",
    "tags": ["#莫干山民宿", "#端午出行", "#江浙沪周边游"]
  },
  "usage": {
    "provider": "deepseek",
    "model": "deepseek-chat",
    "inputTokens": 1200,
    "outputTokens": 900,
    "durationMs": 5200
  },
  "moderation": {
    "passed": true,
    "hits": []
  }
}
```

## 8. 小红书工作流搭建

工作流名称：小红书图文生成

标识：xhs_note_generator

节点设计：

```text
开始
  -> 参数校验
  -> 调用 getHotelProfile
  -> 调用 getRoomSnapshot
  -> 检索酒店资料库
  -> 检索品牌语气库
  -> 检索平台规则库
  -> 标题生成
  -> 正文生成
  -> 标签生成
  -> 合规审核
  -> 不通过则自动改写
  -> JSON 格式化
  -> 回调 saveAiResult
  -> 结束
```

输出格式：

```json
{
  "titles": ["标题1", "标题2", "标题3"],
  "body": "小红书正文",
  "tags": ["#标签1", "#标签2"],
  "coverText": "封面文字",
  "imageSuggestions": ["图片建议1", "图片建议2"],
  "publishTips": "发布时间和互动建议"
}
```

## 9. 朋友圈工作流搭建

工作流名称：朋友圈三档文案

标识：wechat_daily_post

节点：

```text
开始
  -> 获取酒店画像
  -> 获取今日房态
  -> 判断入住率高低
  -> 生成早间种草
  -> 生成午间互动
  -> 生成晚间转化
  -> 审核
  -> 回调保存
```

输出格式：

```json
{
  "morning": "早间种草文案",
  "noon": "午间互动文案",
  "evening": "晚间转化文案",
  "imageSuggestions": ["清晨窗景", "下午茶", "夜景"],
  "publishSchedule": ["08:30", "12:20", "20:30"]
}
```

## 10. 视频脚本工作流搭建

工作流名称：短视频口播脚本

标识：douyin_script_generator

输出格式：

```json
{
  "hook": "前3秒钩子",
  "script": [
    {
      "time": "0-3s",
      "shot": "镜头画面",
      "voiceover": "口播内容"
    }
  ],
  "caption": "发布文案",
  "hashtags": ["#民宿", "#周边游"],
  "bgmSuggestion": "BGM建议"
}
```

## 11. 公众号工作流搭建

工作流名称：公众号推文生成

标识：wechat_article_generator

输出格式：

```json
{
  "title": "公众号标题",
  "summary": "摘要",
  "sections": [
    {
      "heading": "小标题",
      "body": "正文段落"
    }
  ],
  "cta": "结尾转化引导",
  "imageSuggestions": ["配图建议"]
}
```

## 12. 回评工作流搭建

工作流名称：回评话术生成

标识：reply_generator

输出格式：

```json
{
  "replies": [
    {
      "style": "温暖亲切",
      "content": "回复内容"
    },
    {
      "style": "专业正式",
      "content": "回复内容"
    }
  ],
  "riskTips": ["不要承诺未授权补偿"]
}
```

## 13. 智慧大脑工作流搭建

工作流名称：运营智慧大脑

标识：operation_brain

节点：

```text
开始
  -> 识别用户意图
  -> 获取酒店画像
  -> 获取房态数据
  -> 获取客群数据
  -> 如需要，检索营销案例库
  -> 生成经营诊断
  -> 生成行动清单
  -> 生成可跳转的建议模块
  -> 返回
```

输出格式：

```json
{
  "answer": "自然语言回复",
  "actions": [
    {
      "type": "generate_content",
      "moduleKey": "xhs",
      "label": "生成一篇小红书笔记"
    },
    {
      "type": "pricing",
      "moduleKey": "pricing",
      "label": "查看今日定价建议"
    }
  ],
  "insights": ["当前出租率偏低，建议先做周末短途内容投放"]
}
```

## 14. 后端调用流程

```text
前端点击生成
  -> POST /api/content/generate
  -> sushijia-server 校验登录和租户
  -> 查询 billing_rules 扣算力
  -> 创建 content_tasks
  -> 查询 ai_workflow_bindings 找到 AIFlowy workflow_id
  -> 调用 AIFlowy 启动工作流
  -> 保存 external_run_id
  -> 返回 taskId
  -> 前端轮询 GET /api/content/task/{taskId}
  -> AIFlowy 完成后回调 sushijia-server
  -> sushijia-server 保存 content_results
  -> 前端展示结果
```

## 15. AIFlowy 工作流绑定表

第一阶段如果直接调用智能体，需要先做 `module_key -> bot_id` 绑定。这样小红书、朋友圈、视频、回评等模块才能分别调用正确的 AIFlowy 智能体。

```sql
CREATE TABLE ai_agent_bindings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_key VARCHAR(64) NOT NULL,
  bot_id VARCHAR(128) NOT NULL DEFAULT '',
  bot_name VARCHAR(128) NOT NULL DEFAULT '',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_module_key (module_key)
);
```

绑定建议：

| module_key | AIFlowy 智能体 | bot_id |
| --- | --- | --- |
| brain | 宿识家 AI 店长 | 从 AIFlowy 智能体详情页复制 |
| strategy | 宿识家运营参谋 | 从 AIFlowy 智能体详情页复制 |
| xhs | 宿识家内容创作官 | 从 AIFlowy 智能体详情页复制 |
| wechat | 宿识家内容创作官 | 从 AIFlowy 智能体详情页复制 |
| video | 宿识家内容创作官 | 从 AIFlowy 智能体详情页复制 |
| article | 宿识家内容创作官 | 从 AIFlowy 智能体详情页复制 |
| poster | 宿识家营销设计师 | 从 AIFlowy 智能体详情页复制 |
| review | 宿识家口碑管家 | 从 AIFlowy 智能体详情页复制 |
| reply | 宿识家口碑管家 | 从 AIFlowy 智能体详情页复制 |
| pricing | 宿识家收益顾问 | 从 AIFlowy 智能体详情页复制 |

调用时后端会根据 `module_key` 查询 `bot_id`，再请求 `http://aiflowy.zhuotone.cn/api/v1/bot/chat`。如果没有配置 `bot_id`，任务直接失败并提示“AI服务未配置，请联系管理员”。

第二阶段如果改成工作流编排，再新增：

```sql
CREATE TABLE ai_workflow_bindings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_key VARCHAR(64) NOT NULL COMMENT 'brain/xhs/wechat/video/article/poster/review/reply/pricing/strategy',
  workflow_code VARCHAR(128) NOT NULL COMMENT 'AIFlowy 工作流标识',
  workflow_id VARCHAR(128) COMMENT 'AIFlowy 工作流ID',
  workflow_version VARCHAR(64),
  tenant_id BIGINT NULL COMMENT '为空表示全局默认，非空表示租户专属',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_module_tenant (module_key, tenant_id),
  INDEX idx_module (module_key)
);
```

初始化数据：

```sql
INSERT INTO ai_workflow_bindings (module_key, workflow_code, enabled) VALUES
('brain', 'operation_brain', 1),
('strategy', 'holiday_strategy_generator', 1),
('xhs', 'xhs_note_generator', 1),
('wechat', 'wechat_daily_post', 1),
('video', 'douyin_script_generator', 1),
('article', 'wechat_article_generator', 1),
('poster', 'poster_copy_generator', 1),
('review', 'review_template_generator', 1),
('reply', 'reply_generator', 1),
('pricing', 'pricing_advisor', 1);
```

## 16. 第一阶段实施顺序

1. 在 AIFlowy 创建工作空间：宿识家 AI 运营中台。
2. 在模型管理里配置文案模型：DeepSeek 或通义千问。
3. 在知识库里创建酒店资料库、品牌语气库、平台规则库、营销案例库。
4. 在插件里创建宿识家数据插件。
5. 创建智能体：宿识家 AI 店长、内容创作官、口碑管家、收益顾问、营销设计师。
6. 先创建并测试小红书图文生成工作流。
7. 后端新增 AIFlowy 适配层和工作流绑定表。
8. 打通 xhs 从前端到后端到 AIFlowy 再回调保存的完整链路。
9. 复制工作流模式到朋友圈、视频、公众号、好评、回评。
10. 最后做智慧大脑和定价建议。

## 17. 第一阶段验收标准

小红书生成链路必须满足：

- 前端点击生成后 1 秒内拿到 taskId。
- 后端正常扣减算力。
- content_tasks 状态从 pending 到 processing 到 done。
- AIFlowy 可以读取酒店画像和品牌风格。
- 生成结果是结构化 JSON。
- content_results 保存最终内容、模型、token、审核结果。
- 前端能展示标题、正文、标签、封面建议。
- AIFlowy 失败时任务状态变 failed，并记录错误原因。
