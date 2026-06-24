-- =====================================================
-- 宿营家 AI SaaS 平台 完整建表SQL
-- MySQL 8.0+
-- 执行顺序：从上到下
-- =====================================================

-- ===== 一、租户与账户 =====

CREATE TABLE tenants (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  name          VARCHAR(100) NOT NULL COMMENT '酒店名称',
  type          VARCHAR(50)  NOT NULL COMMENT '精品民宿/度假酒店/商务酒店/亲子民宿',
  city          VARCHAR(100) NOT NULL,
  total_rooms   INT          NOT NULL DEFAULT 0,
  tags          VARCHAR(500),
  target_audience VARCHAR(500),
  nearby        VARCHAR(500),
  contact_phone VARCHAR(20),
  tier          ENUM('trial','basic','pro','flagship') NOT NULL DEFAULT 'trial' COMMENT '套餐版本',
  status        ENUM('active','warning','suspended','closed') NOT NULL DEFAULT 'active',
  balance       INT          NOT NULL DEFAULT 0 COMMENT '算力余额',
  alert_threshold INT        NOT NULL DEFAULT 500 COMMENT '余额预警阈值',
  melt_threshold  INT        NOT NULL DEFAULT 0 COMMENT '熔断阈值',
  qps_limit     INT          NOT NULL DEFAULT 5,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_status (status),
  INDEX idx_tier (tier)
) COMMENT='租户/酒店主表';

CREATE TABLE tenant_branches (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  name          VARCHAR(100) NOT NULL,
  city          VARCHAR(100) NOT NULL,
  allocated_quota INT        NOT NULL DEFAULT 0,
  consumed      INT          NOT NULL DEFAULT 0,
  manager_phone VARCHAR(20),
  status        ENUM('active','paused') NOT NULL DEFAULT 'active',
  FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) COMMENT='连锁门店子账号';

CREATE TABLE admins (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  email         VARCHAR(100) NOT NULL UNIQUE,
  name          VARCHAR(50)  NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role          ENUM('super_admin','finance','support','content_ops') NOT NULL,
  status        ENUM('active','disabled') NOT NULL DEFAULT 'active',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT='后台管理员';

CREATE TABLE hotel_staff (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  name          VARCHAR(50)  NOT NULL,
  phone         VARCHAR(20)  NOT NULL,
  role          ENUM('admin','manager','front_desk','marketing') NOT NULL,
  avatar        VARCHAR(500) DEFAULT '',
  password_hash VARCHAR(255),
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  INDEX idx_tenant (tenant_id),
  UNIQUE KEY uk_phone (phone)
) COMMENT='酒店端员工';

-- ===== 二、房态 =====

CREATE TABLE room_types (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  name          VARCHAR(100) NOT NULL COMMENT '房型名称',
  base_price    DECIMAL(10,2) NOT NULL,
  count         INT          NOT NULL COMMENT '该房型总间数',
  sort_order    INT          DEFAULT 0,
  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  INDEX idx_tenant (tenant_id)
) COMMENT='房型配置';

CREATE TABLE room_status (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  room_type_id  BIGINT       NOT NULL,
  room_number   VARCHAR(20)  NOT NULL COMMENT '房号',
  status        ENUM('sold','free','dirty','repair') NOT NULL DEFAULT 'free',
  updated_at    DATETIME     ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (tenant_id)    REFERENCES tenants(id),
  FOREIGN KEY (room_type_id) REFERENCES room_types(id),
  INDEX idx_tenant_type (tenant_id, room_type_id)
) COMMENT='实时房态';

CREATE TABLE future_room_status (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  date          DATE         NOT NULL,
  room_type_name VARCHAR(100) NOT NULL,
  occupied      INT          NOT NULL DEFAULT 0,
  available     INT          NOT NULL DEFAULT 0,
  overbooked    INT          NOT NULL DEFAULT 0,
  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  UNIQUE KEY uk_tenant_date_room (tenant_id, date, room_type_name),
  INDEX idx_tenant_date (tenant_id, date)
) COMMENT='未来7天房态';

CREATE TABLE guests (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  room_type_id  BIGINT,
  room_number   VARCHAR(20),
  guest_type    VARCHAR(50)  COMMENT '情侣/家庭/商务',
  source        VARCHAR(100) COMMENT '小红书/美团/携程引流',
  nights        INT          NOT NULL DEFAULT 1,
  checkin_date  DATE,
  checkout_date DATE,
  status        ENUM('checking_in','staying','checking_out','departed') NOT NULL DEFAULT 'staying',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (tenant_id)    REFERENCES tenants(id),
  FOREIGN KEY (room_type_id) REFERENCES room_types(id),
  INDEX idx_tenant_status (tenant_id, status)
) COMMENT='在住客人';

-- ===== 三、算力计费 =====

CREATE TABLE billing_rules (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_key    VARCHAR(50)  NOT NULL UNIQUE,
  module_name   VARCHAR(100) NOT NULL,
  board         VARCHAR(20)  NOT NULL COMMENT '店长看板/内容发布/前台客服',
  cost          INT          NOT NULL COMMENT '消耗算力',
  est_cost_rmb  DECIMAL(10,4) NOT NULL DEFAULT 0,
  enabled       TINYINT(1)   NOT NULL DEFAULT 1,
  sort_order    INT          DEFAULT 0
) COMMENT='模块计费规则';

INSERT INTO billing_rules (module_key, module_name, board, cost, est_cost_rmb, sort_order) VALUES
('room_status', '房态图AI识别',        '店长看板', 0,  0.02, 1),
('brain',       '运营智慧大脑（每轮）',  '店长看板', 5,  0.06, 2),
('pricing',     '智能定价建议',          '店长看板', 0,  0.00, 3),
('wechat',      '朋友圈文案（单条）',    '内容发布', 8,  0.10, 4),
('xhs',         '小红书图文（含标签）',  '内容发布', 10, 0.12, 5),
('video',       '视频口播文案',          '内容发布', 12, 0.18, 6),
('poster',      '营销海报生成',          '内容发布', 30, 0.45, 7),
('repair',      'AI修图（单张）',        '内容发布', 20, 0.30, 8),
('article',     '公众号推文生成',        '内容发布', 15, 0.25, 9),
('review',      '个性化好评模板',        '前台客服', 6,  0.08, 10),
('reply',       'AI回评话术（5条/批）', '前台客服', 8,  0.10, 11);

CREATE TABLE recharge_packages (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  name          VARCHAR(50)  NOT NULL,
  credits       INT          NOT NULL,
  price_rmb     DECIMAL(10,2) NOT NULL,
  applicable_tiers VARCHAR(200),
  enabled       TINYINT(1)   NOT NULL DEFAULT 1
) COMMENT='充值套餐';

INSERT INTO recharge_packages (name, credits, price_rmb, applicable_tiers) VALUES
('体验包', 500,  150,  'trial'),
('标准包', 2000, 560,  'basic,pro'),
('畅享包', 5000, 1300, 'pro,flagship'),
('连锁包', 20000,4800, 'flagship');

CREATE TABLE credit_ledger (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  type          ENUM('consume','recharge') NOT NULL,
  amount        INT          NOT NULL,
  balance_after INT          NOT NULL,
  module_key    VARCHAR(50),
  module_name   VARCHAR(100),
  detail        VARCHAR(500),
  status        ENUM('success','failed','melted') NOT NULL DEFAULT 'success',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  INDEX idx_tenant_time (tenant_id, created_at),
  INDEX idx_type_time (type, created_at)
) COMMENT='算力流水';

-- ===== 四、AI内容引擎 =====

CREATE TABLE prompt_templates (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_key    VARCHAR(50)  NOT NULL,
  version       VARCHAR(20)  NOT NULL,
  title         VARCHAR(100) NOT NULL,
  content       TEXT         NOT NULL COMMENT '模板正文，含{{变量}}',
  model_name    VARCHAR(50)  DEFAULT 'claude-sonnet-4-6',
  max_tokens    INT          DEFAULT 1000,
  status        ENUM('draft','gray','production','rolled_back') NOT NULL DEFAULT 'draft',
  gray_percent  INT          DEFAULT 0,
  created_by    VARCHAR(100),
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_module_version (module_key, version),
  INDEX idx_module (module_key),
  INDEX idx_status (status)
) COMMENT='提示词模板版本';

CREATE TABLE style_library (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  name          VARCHAR(100) NOT NULL,
  scope         ENUM('public','private') NOT NULL DEFAULT 'public',
  tenant_id     BIGINT       NULL,
  prompt_segment TEXT        NOT NULL,
  feedback_score DECIMAL(3,2),
  usage_count   INT          DEFAULT 0,
  enabled       TINYINT(1)   NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  INDEX idx_scope (scope),
  INDEX idx_tenant (tenant_id)
) COMMENT='风格库';

CREATE TABLE module_style_binding (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_key    VARCHAR(50)  NOT NULL,
  style_id      BIGINT       NOT NULL,
  FOREIGN KEY (style_id) REFERENCES style_library(id),
  UNIQUE KEY uk_module_style (module_key, style_id)
) COMMENT='模块风格绑定';

CREATE TABLE content_tasks (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  module_key    VARCHAR(50)  NOT NULL,
  input_params  JSON,
  status        ENUM('pending','processing','done','failed','moderated') NOT NULL DEFAULT 'pending',
  result_id     BIGINT,
  error_msg     VARCHAR(500),
  cost_credits  INT          NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at  DATETIME,
  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  INDEX idx_status (status),
  INDEX idx_tenant (tenant_id)
) COMMENT='AI生成任务表';

CREATE TABLE content_results (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id       BIGINT       NOT NULL,
  content       MEDIUMTEXT   NOT NULL,
  tokens_used   INT,
  moderated     TINYINT(1)   DEFAULT 0,
  moderation_detail JSON,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (task_id) REFERENCES content_tasks(id) ON DELETE CASCADE
) COMMENT='AI生成结果';

-- ===== 系统配置（键值对） =====

CREATE TABLE system_settings (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  setting_key   VARCHAR(100) NOT NULL UNIQUE,
  setting_value TEXT,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_key (setting_key)
) COMMENT='系统配置键值对（AI、短信等）';

INSERT INTO system_settings (setting_key, setting_value) VALUES
('ai_provider', 'openai'),
('ai_api_key', ''),
('ai_model', 'gpt-4o'),
('ai_base_url', ''),
('ai_max_tokens', '4000'),
('sms_provider', ''),
('sms_access_key', ''),
('sms_secret_key', ''),
('sms_sign_name', ''),
('sms_template_code', '');

-- ===== 五、内容质量与合规 =====

CREATE TABLE content_feedback (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  module_key    VARCHAR(50)  NOT NULL,
  prompt_version VARCHAR(20),
  style_id      BIGINT,
  rating        ENUM('good','bad','none') NOT NULL,
  content_snippet VARCHAR(1000),
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  INDEX idx_tenant_module (tenant_id, module_key),
  INDEX idx_prompt_version (prompt_version)
) COMMENT='内容反馈';

CREATE TABLE moderation_rules (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  name          VARCHAR(100) NOT NULL,
  rule_type     ENUM('keyword','wordbank','regex') NOT NULL,
  keywords      TEXT         NOT NULL,
  action        ENUM('block','review','replace') NOT NULL DEFAULT 'block',
  hit_count_30d INT          DEFAULT 0,
  enabled       TINYINT(1)   NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT='合规审查规则';

CREATE TABLE moderation_hits (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  rule_id       BIGINT       NOT NULL,
  module_key    VARCHAR(50)  NOT NULL,
  content_snippet VARCHAR(1000),
  status        ENUM('pending','approved','blocked') NOT NULL DEFAULT 'pending',
  reviewed_by   BIGINT,
  reviewed_at   DATETIME,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  FOREIGN KEY (rule_id)    REFERENCES moderation_rules(id)
) COMMENT='合规命中待审';

-- ===== 六、营销方案 =====

CREATE TABLE marketing_plans (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  name          VARCHAR(200) NOT NULL,
  festival      VARCHAR(50)  NOT NULL,
  status        ENUM('draft','active','completed') NOT NULL DEFAULT 'draft',
  hotel_name    VARCHAR(100) NOT NULL,
  period        VARCHAR(200),
  target        VARCHAR(200),
  tags          JSON,
  kpis          JSON,
  phases        JSON,
  channels      JSON,
  pricings      JSON,
  activities    JSON,
  alert_note    TEXT,
  alerts        JSON,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (tenant_id) REFERENCES tenants(id),
  INDEX idx_tenant_status (tenant_id, status)
) COMMENT='营销方案';

-- ===== 七、系统 =====

CREATE TABLE audit_logs (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator_type   ENUM('admin','tenant','system') NOT NULL,
  operator_id     BIGINT,
  operator_name   VARCHAR(100),
  action          VARCHAR(50)  NOT NULL,
  detail          VARCHAR(1000),
  target_tenant_id BIGINT,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_time (created_at),
  INDEX idx_operator (operator_type, operator_id)
) COMMENT='操作审计日志';

CREATE TABLE api_call_logs (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  module_key    VARCHAR(50)  NOT NULL,
  duration_ms   INT,
  status        ENUM('success','failed','timeout') NOT NULL,
  error_msg     VARCHAR(500),
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_tenant_time (tenant_id, created_at),
  INDEX idx_module_time (module_key, created_at)
) COMMENT='API调用日志';

-- ===== 种子数据：默认管理员 =====
INSERT INTO admins (email, name, password_hash, role) VALUES
('admin@sushijia.ai', '超级管理员', '$2b$10$LHIpbgS5DXxEyNPf9qgRI.//R9ZL6aAnU9or.JtgYKcQky8z05sla', 'super_admin');
INSERT INTO admins (email, name, password_hash, role) VALUES
('prompt@sushijia.ai', '内容运营', '$2b$10$LHIpbgS5DXxEyNPf9qgRI.//R9ZL6aAnU9or.JtgYKcQky8z05sla', 'content_ops');

-- ===== 种子数据：演示租户 =====

INSERT INTO tenants (id, name, type, city, total_rooms, tags, target_audience, nearby, contact_phone, tier, status, balance, alert_threshold, melt_threshold, qps_limit) VALUES
(1, '松间·山野民宿', '精品民宿', '浙江·莫干山', 12, '竹林景观、私汤温泉、无边泳池、有机早餐', '面向长三角城市中产，主打情侣度假与亲子出行，提供轻奢逃离体验。', '距莫干山景区5分钟·竹林徒步·茶园采摘', '13800000000', 'pro', 'active', 1240, 500, 0, 5),
(2, '清风客栈', '精品民宿', '云南·大理', 8, '洱海景观、白族庭院、手工扎染体验', '面向全国自由行年轻客群，主打大理慢生活体验。', '距古城5分钟步行', '13800000001', 'pro', 'active', 2150, 500, 0, 5),
(3, '海岸线度假酒店', '度假酒店', '福建·厦门', 24, '海景房、无边泳池、海鲜餐厅', '面向中高端度假客群，主打海岛度假。', '距鼓浪屿码头10分钟', NULL, 'basic', 'active', 8900, 500, 0, 5),
(4, '古城拾光民宿', '精品民宿', '山西·平遥', 6, '明清老宅、古城文化、晋商体验', '面向文化旅游爱好者。', '平遥古城南大街', NULL, 'trial', 'active', 480, 300, 0, 5),
(5, '观山雅集', '精品民宿', '四川·峨眉山', 10, '峨眉山景、茶园禅修、素食料理', '面向康养度假客群。', '峨眉山景区入口', NULL, 'basic', 'warning', 0, 500, 0, 5);

INSERT INTO room_types (tenant_id, name, base_price, count, sort_order) VALUES
(1, '竹语大床房', 888, 4, 1),
(1, '山景套房', 1388, 5, 2),
(1, '亲子家庭房', 1688, 3, 3),
(2, '洱海大床房', 688, 4, 1),
(2, '白族庭院套房', 1088, 4, 2),
(3, '海景标准间', 588, 12, 1),
(3, '海景套房', 988, 8, 2),
(3, '家庭连通房', 1288, 4, 3);

INSERT INTO room_status (tenant_id, room_type_id, room_number, status) VALUES
(1, 1, '101', 'sold'), (1, 1, '102', 'sold'), (1, 1, '103', 'free'), (1, 1, '104', 'dirty'),
(1, 2, '201', 'sold'), (1, 2, '202', 'sold'), (1, 2, '203', 'sold'), (1, 2, '204', 'sold'), (1, 2, '205', 'free'),
(1, 3, '301', 'sold'), (1, 3, '302', 'free'), (1, 3, '303', 'repair'),
(2, 4, '101', 'sold'), (2, 4, '102', 'sold'), (2, 4, '103', 'free'), (2, 4, '104', 'free'),
(2, 5, '201', 'free'), (2, 5, '202', 'free'), (2, 5, '203', 'free'), (2, 5, '204', 'dirty');

INSERT INTO future_room_status (tenant_id, date, room_type_name, occupied, available, overbooked) VALUES
(1, '2026-06-12', '双床房', 2, 3, 0), (1, '2026-06-12', '大床房', 3, 3, 0), (1, '2026-06-12', '亲子房', 0, 12, 0), (1, '2026-06-12', '套房', 0, 2, 0),
(1, '2026-06-13', '双床房', 3, 2, 0), (1, '2026-06-13', '大床房', 0, 6, 0), (1, '2026-06-13', '亲子房', 0, 12, 0), (1, '2026-06-13', '套房', 0, 2, 0),
(1, '2026-06-14', '双床房', 3, 2, 0), (1, '2026-06-14', '大床房', 3, 3, 0), (1, '2026-06-14', '亲子房', 4, 8, 0), (1, '2026-06-14', '套房', 2, 0, 0),
(1, '2026-06-15', '双床房', 2, 3, 0), (1, '2026-06-15', '大床房', 0, 6, 0), (1, '2026-06-15', '亲子房', 4, 8, 0), (1, '2026-06-15', '套房', 2, 0, 0),
(1, '2026-06-16', '双床房', 0, 5, 0), (1, '2026-06-16', '大床房', 0, 6, 0), (1, '2026-06-16', '亲子房', 0, 12, 0), (1, '2026-06-16', '套房', 2, 0, 0),
(1, '2026-06-17', '双床房', 0, 5, 0), (1, '2026-06-17', '大床房', 0, 6, 0), (1, '2026-06-17', '亲子房', 0, 12, 0), (1, '2026-06-17', '套房', 2, 0, 0);

INSERT INTO hotel_staff (tenant_id, name, phone, role, password_hash) VALUES
(1, '张店长', '13800000000', 'admin', '$2b$10$gB1yWG9eE/YobVNph2Hh.u4YmSCNCk9VE196XcqJrJvQb/PVrxsfi'),
(2, '李小明', '13800000001', 'manager', '$2b$10$gB1yWG9eE/YobVNph2Hh.u4YmSCNCk9VE196XcqJrJvQb/PVrxsfi'),
(1, '王小红', '13800000002', 'front_desk', '$2b$10$gB1yWG9eE/YobVNph2Hh.u4YmSCNCk9VE196XcqJrJvQb/PVrxsfi'),
(1, '赵小丽', '13800000003', 'marketing', '$2b$10$gB1yWG9eE/YobVNph2Hh.u4YmSCNCk9VE196XcqJrJvQb/PVrxsfi');

INSERT INTO guests (tenant_id, room_type_id, room_number, guest_type, source, nights, checkin_date, checkout_date, status) VALUES
(1, 1, '101', 'couple', '小红书引流', 2, '2026-06-11', '2026-06-13', 'staying'),
(1, 2, '203', 'family', '美团引流', 3, '2026-06-10', '2026-06-13', 'staying'),
(1, 3, '301', 'family', '携程引流', 1, '2026-06-12', '2026-06-13', 'checking_in');

INSERT INTO style_library (id, name, scope, tenant_id, prompt_segment, enabled) VALUES
(1, '治愈温暖（主流种草风）', 'public', NULL, '写作语气：温暖、治愈、有情绪共鸣感。多用感官描写，句子偏短，营造放松氛围。适当使用emoji但不过度。结尾引导收藏/关注，语气像朋友分享私藏好去处。', 1),
(2, '活泼元气（年轻客群）', 'public', NULL, '写作语气：轻快、俏皮、有网感。多用网络流行语和梗，节奏快，短句+排比。emoji使用频率较高，标题要有反差感或悬念。适合年轻女性客群。', 1),
(3, '轻奢精致（高端调性）', 'public', NULL, '写作语气：克制、优雅、有质感。少用emoji，强调细节描写。句子结构完整，避免口语化网络用语。整体传递"低调的好品质"调性。', 1),
(4, '故事叙事（沉浸体验）', 'public', NULL, '写作语气：第一人称叙事，像在写旅行日记。以时间线或场景切换为结构，细节描写带入感强，让读者身临其境。结尾留有余韵。', 1),
(5, '竹林禅意系', 'private', 1, '写作语气：禅意、留白、东方美学。多用自然意象（竹、雾、茶、风），句子有节奏感。避免过度营销语言，传递"慢下来"的生活方式。', 1);

INSERT INTO module_style_binding (module_key, style_id) VALUES
('xhs', 1), ('xhs', 2), ('xhs', 3), ('xhs', 4),
('wechat', 1), ('wechat', 2), ('wechat', 3), ('wechat', 4),
('video', 1), ('video', 2), ('video', 3), ('video', 4),
('review', 1), ('review', 2), ('review', 3), ('review', 4);

INSERT INTO prompt_templates (module_key, version, title, content, status) VALUES
('xhs', 'v3', '小红书图文生成', '你是专业的民宿内容营销专家。请为以下民宿生成小红书图文笔记：\n\n民宿信息：\n- 名称：{{hotel_name}}（{{hotel_type}}）\n- 位置：{{hotel_city}}\n- 核心特色：{{hotel_tags}}\n\n实时上下文：\n- 今日天气：{{weather}}\n- 出租率：{{occupancy_rate}}%\n- 节假日：{{holiday_countdown}}\n\n写作风格：{{writing_style}}\n\n请按格式输出：【标题备选】（3个）【正文内容】【话题标签】（8-12个）', 'production'),
('wechat', 'v3', '朋友圈三档文案', '你是民宿新媒体运营专家。请为{{hotel_name}}生成今日朋友圈三档文案：\n\n民宿信息：{{hotel_tags}}\n今日房态：出租率{{occupancy_rate}}%\n写作风格：{{writing_style}}\n\n请生成早间种草型、午间互动型、晚间凡尔赛型三条文案。', 'production'),
('video', 'v1', '抖音口播文案', '你是抖音内容编导。请为{{hotel_name}}生成15-30秒口播文案：\n\n民宿特色：{{hotel_tags}}\n今日场景：{{weather}}\n写作风格：{{writing_style}}\n\n开头3秒需有强钩子，结尾引导私信/关注。', 'production'),
('poster', 'v1', '营销海报生成', '你是专业的民宿海报设计师。请为{{hotel_name}}生成营销海报文案与视觉指导：\n\n海报主题：{{poster_theme}} 视觉风格：{{visual_style}}\n\n请输出：【主标题】【副标题】【行动号召】【视觉建议】【底部信息】', 'production'),
('article', 'v1', '公众号推文生成', '你是民宿品牌内容创作者。请为{{hotel_name}}撰写一篇公众号推文：\n\n文章主题：{{article_topic}} 写作风格：{{writing_style}}\n\n请按结构输出：【开篇导语】【正文分节】【结尾引导】【配图建议】', 'production'),
('brain', 'v6', '运营智慧大脑', '你是"{{hotel_name}}"的运营智慧大脑。\n\n【民宿基础信息】\n- 名称：{{hotel_name}}（{{hotel_type}}）\n- 核心特色：{{hotel_tags}}\n\n【实时数据】\n- 今日出租率：{{occupancy_rate}}%　天气：{{weather}}\n\n基于实时数据给出运营策略、内容创作指导、定价建议。回答简洁实操，不超过300字。', 'production'),
('room_status', 'v5', '房态数据分析与早报', '你是酒店收益管理与运营诊断专家。基于以下房态数据生成今日早报：\n\n民宿：{{hotel_name}}（{{hotel_city}}）\n总房量：{{total_rooms}}间 | 占用：{{occupied_rooms}}间 | 出租率：{{occupancy_rate}}%\n\n外部：天气{{weather}} | 节假日{{holiday_countdown}}\n\n请生成：风险预警、今日核心动作、定价指令。', 'production'),
('pricing', 'v4', '智能定价建议', '你是酒店收益管理专家。请根据以下信息生成动态定价建议：\n\n房型与基础价：{{room_types}}\n市场因子：\n- 入住率：{{occupancy_rate}}%\n- 节假日：{{holiday_type}}\n- 天气：{{weather}}\n\n请输出每个房型建议价、涨跌幅度、定价理由。', 'production'),
('review', 'v2', '好评模板/回评话术', '你是{{hotel_name}}的店主。请根据客群类型生成个性化好评模板：\n\n民宿特色：{{hotel_tags}} 客群类型：{{guest_segment}} 写作风格：{{writing_style}}\n\n要求：真实自然、突出特色，150字以内。', 'production'),
('strategy', 'v2', '周期营销策略生成', '你是民宿营销策略顾问。请为{{hotel_name}}生成{{holiday_name}}的完整营销策略：\n\n民宿特色：{{hotel_tags}} 房型：{{room_types}}\n距节假日{{days_until_holiday}}天\n\n请按四阶段输出：蓄水期、冲刺期、假期服务期、收尾期。', 'production');

INSERT INTO credit_ledger (tenant_id, type, amount, balance_after, module_key, module_name, detail, created_at) VALUES
(1, 'recharge', 500, 1246, NULL, '充值', '月度套餐 · 500算力', '2026-06-11 09:15'),
(1, 'consume', -10, 1236, 'xhs', '小红书营销', '生成3个选题+完整图文', '2026-06-11 10:08'),
(1, 'consume', -8, 1228, 'wechat', '朋友圈文案', '生成三档文案（早/中/晚）', '2026-06-11 14:22'),
(1, 'recharge', 200, 796, NULL, '充值', '周套餐 · 200算力', '2026-06-10 08:55'),
(1, 'consume', -8, 788, 'wechat', '朋友圈文案', '生成三档文案', '2026-06-10 11:20'),
(1, 'consume', -12, 776, 'video', '抖音口播', '生成今日口播文案', '2026-06-10 16:30'),
(1, 'consume', -30, 746, 'poster', '营销海报', '生成端午特惠海报', '2026-06-10 20:45'),
(1, 'recharge', 500, 641, NULL, '充值', '月度套餐 · 500算力', '2026-06-06 09:00'),
(1, 'consume', -12, 629, 'video', '抖音口播', '生成周末口播文案', '2026-06-06 20:15'),
(1, 'recharge', 1000, -791, NULL, '充值', '首充赠送 · 1000算力', '2026-06-04 17:55');

INSERT INTO moderation_rules (name, rule_type, keywords, action, enabled) VALUES
('违反广告法用词', 'keyword', '最低价\n全网最低\n绝无仅有\n史上最低\n第一\n最好', 'block', 1),
('虚假承诺/绝对化用语', 'keyword', '包治百病\n保证赚钱\n100%满意\n绝对有效', 'block', 1),
('医疗/疗效相关表述', 'keyword', '治愈\n疗效\n治疗\n奇效\n药方\n偏方', 'review', 1),
('竞品品牌名提及', 'wordbank', '携程\n美团\n小猪民宿\n爱彼迎\n途家', 'replace', 1);
