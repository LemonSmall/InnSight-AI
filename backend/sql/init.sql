-- =====================================================
-- 宿识家 AI 酒店 SaaS 平台初始化脚本
-- MySQL 8.0+
--
-- 用途：
-- 1. 开发环境重新生成一份干净数据库。
-- 2. 会先删除 sushijia 数据库，再重新创建全部表和默认数据。
-- 3. 生产环境禁止直接执行本脚本，避免清空真实数据。
-- =====================================================

SET FOREIGN_KEY_CHECKS = 0;
DROP DATABASE IF EXISTS sushijia;
SET FOREIGN_KEY_CHECKS = 1;

CREATE DATABASE sushijia
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE sushijia;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，使用 information_schema 保证重复执行安全。
DROP PROCEDURE IF EXISTS sushijia_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE sushijia_add_column_if_missing(
  IN p_table_name VARCHAR(128),
  IN p_column_name VARCHAR(128),
  IN p_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = p_column_name
  ) THEN
    SET @ddl = CONCAT(
      'ALTER TABLE `', REPLACE(p_table_name, '`', '``'),
      '` ADD COLUMN `', REPLACE(p_column_name, '`', '``'), '` ', p_definition
    );
    PREPARE ddl_statement FROM @ddl;
    EXECUTE ddl_statement;
    DEALLOCATE PREPARE ddl_statement;
  END IF;
END$$
DELIMITER ;

-- =====================================================
-- 1. 骞冲彴銆佺鎴枫€佸憳宸?-- =====================================================

CREATE TABLE IF NOT EXISTS tenants (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  name             VARCHAR(100) NOT NULL COMMENT '閰掑簵/姘戝鍚嶇О',
  type             VARCHAR(50)  NOT NULL COMMENT '姘戝/閰掑簵/搴﹀亣閰掑簵/杩為攣闂ㄥ簵',
  city             VARCHAR(100) NOT NULL,
  total_rooms      INT          NOT NULL DEFAULT 0,
  tags             VARCHAR(500),
  target_audience  VARCHAR(500),
  nearby           VARCHAR(500),
  contact_phone    VARCHAR(20),
  tier             ENUM('trial','basic','pro','flagship') NOT NULL DEFAULT 'trial',
  status           ENUM('active','warning','suspended','closed') NOT NULL DEFAULT 'active',
  balance          INT          NOT NULL DEFAULT 0 COMMENT '褰撳墠绠楀姏浣欓',
  alert_threshold  INT          NOT NULL DEFAULT 500,
  melt_threshold   INT          NOT NULL DEFAULT 0,
  qps_limit        INT          NOT NULL DEFAULT 5,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_tenants_status (status),
  INDEX idx_tenants_tier (tier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绉熸埛/閰掑簵璐︽埛';

CALL sushijia_add_column_if_missing('tenants', 'poi_provider', 'VARCHAR(30) NULL COMMENT ''鐜板疄閰掑簵缁戝畾鏉ユ簮锛歛map/baidu/tencent''');
CALL sushijia_add_column_if_missing('tenants', 'poi_id', 'VARCHAR(80) NULL COMMENT ''鍦板浘 POI ID''');
CALL sushijia_add_column_if_missing('tenants', 'poi_name', 'VARCHAR(150) NULL COMMENT ''鍦板浘杩斿洖閰掑簵鍚嶇О''');
CALL sushijia_add_column_if_missing('tenants', 'poi_address', 'VARCHAR(300) NULL COMMENT ''鍦板浘杩斿洖璇︾粏鍦板潃''');
CALL sushijia_add_column_if_missing('tenants', 'poi_province', 'VARCHAR(80) NULL');
CALL sushijia_add_column_if_missing('tenants', 'poi_city', 'VARCHAR(80) NULL');
CALL sushijia_add_column_if_missing('tenants', 'poi_district', 'VARCHAR(80) NULL');
CALL sushijia_add_column_if_missing('tenants', 'poi_adcode', 'VARCHAR(20) NULL');
CALL sushijia_add_column_if_missing('tenants', 'poi_longitude', 'DECIMAL(12,6) NULL');
CALL sushijia_add_column_if_missing('tenants', 'poi_latitude', 'DECIMAL(12,6) NULL');
CALL sushijia_add_column_if_missing('tenants', 'poi_type_code', 'VARCHAR(30) NULL');
CALL sushijia_add_column_if_missing('tenants', 'poi_type_name', 'VARCHAR(120) NULL');
CALL sushijia_add_column_if_missing('tenants', 'poi_verified', 'TINYINT(1) NOT NULL DEFAULT 0');
CALL sushijia_add_column_if_missing('tenants', 'poi_synced_at', 'DATETIME NULL');

CREATE TABLE IF NOT EXISTS tenant_branches (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT       NOT NULL,
  name            VARCHAR(100) NOT NULL,
  city            VARCHAR(100) NOT NULL,
  allocated_quota INT          NOT NULL DEFAULT 0,
  consumed        INT          NOT NULL DEFAULT 0,
  manager_phone   VARCHAR(20),
  status          ENUM('active','paused') NOT NULL DEFAULT 'active',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_branch_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='杩為攣/澶氶棬搴?;

CREATE TABLE IF NOT EXISTS admins (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  email         VARCHAR(100) NOT NULL UNIQUE,
  name          VARCHAR(50)  NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role          ENUM('super_admin','finance','support','content_ops') NOT NULL DEFAULT 'super_admin',
  status        ENUM('active','disabled') NOT NULL DEFAULT 'active',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='骞冲彴绠＄悊绔处鍙?;

CREATE TABLE IF NOT EXISTS hotel_staff (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  name          VARCHAR(50)  NOT NULL,
  phone         VARCHAR(20)  NOT NULL,
  role          ENUM('admin','manager','front_desk','marketing') NOT NULL DEFAULT 'manager',
  avatar        VARCHAR(500) NOT NULL DEFAULT '',
  password_hash VARCHAR(255),
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_hotel_staff_phone (phone),
  INDEX idx_hotel_staff_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='閰掑簵鐢ㄦ埛绔憳宸ヨ处鍙?;

-- =====================================================
-- 2. 閰掑簵缁忚惀鏁版嵁
-- =====================================================

CREATE TABLE IF NOT EXISTS room_types (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT        NOT NULL,
  name        VARCHAR(100)  NOT NULL,
  base_price  DECIMAL(10,2) NOT NULL DEFAULT 0,
  count       INT           NOT NULL DEFAULT 0,
  sort_order  INT           NOT NULL DEFAULT 0,
  enabled     TINYINT(1)    NOT NULL DEFAULT 1,
  INDEX idx_room_type_tenant (tenant_id),
  INDEX idx_room_type_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鎴垮瀷';
CALL sushijia_add_column_if_missing('room_types', 'enabled', 'TINYINT(1) NOT NULL DEFAULT 1 AFTER sort_order');


CREATE TABLE IF NOT EXISTS occupancy_import_records (
  id                        BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id                 BIGINT       NOT NULL,
  source_file_name          VARCHAR(255) DEFAULT '',
  business_date             VARCHAR(20)  NOT NULL,
  room_type_name            VARCHAR(100) NOT NULL,
  normalized_room_type_name VARCHAR(100) NOT NULL,
  total_rooms               INT          NOT NULL DEFAULT 0,
  occupied_rooms            INT          NOT NULL DEFAULT 0,
  remaining_rooms           INT          NOT NULL DEFAULT 0,
  occupancy_rate            DECIMAL(8,4) NOT NULL DEFAULT 0,
  created_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_occupancy_tenant_date_room (tenant_id, business_date, normalized_room_type_name),
  INDEX idx_occupancy_tenant_date (tenant_id, business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鍘嗗彶鎴挎€佸鍏ヨ褰?;

-- =====================================================
-- 3. 濂楅銆佺畻鍔涖€佽璐?-- =====================================================

CREATE TABLE IF NOT EXISTS billing_rules (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_key   VARCHAR(50)  NOT NULL UNIQUE,
  module_name  VARCHAR(100) NOT NULL,
  board        VARCHAR(20)  NOT NULL COMMENT 'ai/content/reputation',
  cost         INT          NOT NULL DEFAULT 0 COMMENT '姣忔鍩虹绠楀姏娑堣€?,
  est_cost_rmb DECIMAL(10,4) NOT NULL DEFAULT 0,
  enabled      TINYINT(1)   NOT NULL DEFAULT 1,
  sort_order   INT          NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='妯″潡璁¤垂瑙勫垯';

CREATE TABLE IF NOT EXISTS recharge_packages (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  name             VARCHAR(50)   NOT NULL,
  credits          INT           NOT NULL,
  price_rmb        DECIMAL(10,2) NOT NULL,
  applicable_tiers VARCHAR(200),
  enabled          TINYINT(1)    NOT NULL DEFAULT 1,
  UNIQUE KEY uk_recharge_package_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绠楀姏鍏呭€煎寘';

CREATE TABLE IF NOT EXISTS tenant_plans (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  code            VARCHAR(50)  NOT NULL UNIQUE,
  name            VARCHAR(100) NOT NULL,
  price_rmb       DECIMAL(10,2) NOT NULL DEFAULT 0,
  monthly_credits INT          NOT NULL DEFAULT 0,
  max_branches    INT          NOT NULL DEFAULT 1,
  enabled_modules VARCHAR(500) NOT NULL DEFAULT '',
  benefits_json   JSON,
  enabled         TINYINT(1)   NOT NULL DEFAULT 1,
  sort_order      INT          NOT NULL DEFAULT 0,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绉熸埛璁㈤槄濂楅';

CREATE TABLE IF NOT EXISTS tenant_subscriptions (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT       NOT NULL,
  plan_code       VARCHAR(50)  NOT NULL,
  status          ENUM('trialing','active','past_due','cancelled','expired') NOT NULL DEFAULT 'trialing',
  monthly_credits INT          NOT NULL DEFAULT 0,
  start_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  end_at          DATETIME,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_subscription_tenant (tenant_id),
  INDEX idx_subscription_tenant_status (tenant_id, status),
  INDEX idx_subscription_plan_code (plan_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绉熸埛璁㈤槄鐘舵€?;

CREATE TABLE IF NOT EXISTS credit_ledger (
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
  INDEX idx_credit_tenant_time (tenant_id, created_at),
  INDEX idx_credit_type_time (type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绠楀姏娴佹按';

-- =====================================================
-- 4. AI 閰嶇疆銆丏ify 缁戝畾銆佸唴瀹逛换鍔?-- =====================================================

CREATE TABLE IF NOT EXISTS system_settings (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  setting_key   VARCHAR(100) NOT NULL UNIQUE,
  setting_value TEXT,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_system_settings_key (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绯荤粺閰嶇疆';

CREATE TABLE IF NOT EXISTS ai_agent_bindings (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_key  VARCHAR(64)  NOT NULL,
  provider    VARCHAR(32)  NOT NULL DEFAULT 'dify',
  app_type    VARCHAR(32)  NOT NULL DEFAULT 'workflow' COMMENT 'chatflow/workflow',
  app_id      VARCHAR(128) NOT NULL DEFAULT '',
  api_key     TEXT,
  app_name    VARCHAR(128) NOT NULL DEFAULT '',
  endpoint    VARCHAR(255) NOT NULL DEFAULT '',
  input_schema JSON,
  bot_id      VARCHAR(128) NOT NULL DEFAULT '' COMMENT '鍏煎鏃?AiFlowy 瀛楁',
  bot_api_key TEXT COMMENT '鍏煎鏃?AiFlowy 瀛楁',
  bot_name    VARCHAR(128) NOT NULL DEFAULT '',
  enabled     TINYINT(1)   NOT NULL DEFAULT 0,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ai_agent_module_agent (module_key, app_name),
  INDEX idx_ai_agent_module_enabled (module_key, enabled),
  INDEX idx_ai_agent_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 妯″潡涓?Dify 鏅鸿兘浣撶粦瀹?;

CREATE TABLE IF NOT EXISTS ai_providers (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  provider_key      VARCHAR(50)  NOT NULL UNIQUE COMMENT 'dify/openai_compatible/image_provider/custom',
  provider_name     VARCHAR(100) NOT NULL,
  provider_type     VARCHAR(50)  NOT NULL DEFAULT 'workflow' COMMENT 'workflow/chat/completions/image/video/custom',
  endpoint          VARCHAR(255) NOT NULL DEFAULT '',
  auth_type         VARCHAR(50)  NOT NULL DEFAULT 'bearer',
  api_key_encrypted TEXT,
  enabled           TINYINT(1)   NOT NULL DEFAULT 1,
  sort_order        INT          NOT NULL DEFAULT 0,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_ai_provider_enabled (enabled),
  INDEX idx_ai_provider_type (provider_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 骞冲彴 Provider 閰嶇疆';

CREATE TABLE IF NOT EXISTS ai_capabilities (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  capability_key VARCHAR(64)  NOT NULL UNIQUE COMMENT 'text_generation/image_generation/video_script/knowledge_extract/operation_advice',
  capability_name VARCHAR(100) NOT NULL,
  description    VARCHAR(500),
  enabled        TINYINT(1)   NOT NULL DEFAULT 1,
  sort_order     INT          NOT NULL DEFAULT 0,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 鑳藉姏绫诲瀷';

CREATE TABLE IF NOT EXISTS ai_agent_configs (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id           BIGINT NOT NULL DEFAULT 0 COMMENT '0 琛ㄧず骞冲彴榛樿閰嶇疆',
  module_key          VARCHAR(64)  NOT NULL,
  module_name         VARCHAR(100) NOT NULL DEFAULT '',
  capability_key      VARCHAR(64)  NOT NULL,
  provider_key        VARCHAR(50)  NOT NULL DEFAULT 'dify',
  app_id              VARCHAR(128) NOT NULL DEFAULT '',
  workflow_id         VARCHAR(128) NOT NULL DEFAULT '',
  api_key_encrypted   TEXT,
  endpoint            VARCHAR(255) NOT NULL DEFAULT '',
  call_mode           VARCHAR(50)  NOT NULL DEFAULT 'workflow' COMMENT 'chatflow/workflow/chat/image/video',
  input_schema        JSON,
  output_schema       JSON,
  output_parser       VARCHAR(100) NOT NULL DEFAULT 'auto',
  knowledge_policy    VARCHAR(50)  NOT NULL DEFAULT 'tenant_confirmed' COMMENT 'none/tenant_confirmed/all',
  enabled             TINYINT(1)   NOT NULL DEFAULT 1,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ai_agent_config_tenant_module (tenant_id, module_key),
  INDEX idx_ai_agent_config_module (module_key),
  INDEX idx_ai_agent_config_capability (capability_key),
  INDEX idx_ai_agent_config_provider (provider_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 鏅鸿兘浣撹兘鍔涢厤缃?;

UPDATE ai_agent_configs SET tenant_id = 0 WHERE tenant_id IS NULL;
ALTER TABLE ai_agent_configs MODIFY tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '0 琛ㄧず骞冲彴榛樿閰嶇疆';

CREATE TABLE IF NOT EXISTS prompt_templates (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_key    VARCHAR(50)  NOT NULL,
  version       VARCHAR(20)  NOT NULL,
  title         VARCHAR(100) NOT NULL,
  content       TEXT         NOT NULL,
  model_name    VARCHAR(50)  DEFAULT '',
  max_tokens    INT          DEFAULT 1000,
  status        ENUM('draft','gray','production','rolled_back') NOT NULL DEFAULT 'draft',
  gray_percent  INT          DEFAULT 0,
  created_by    VARCHAR(100),
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_prompt_module_version (module_key, version),
  INDEX idx_prompt_module (module_key),
  INDEX idx_prompt_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='涓绘彁绀鸿瘝妯℃澘';

CREATE TABLE IF NOT EXISTS style_library (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  name           VARCHAR(100) NOT NULL,
  scope          ENUM('public','private') NOT NULL DEFAULT 'public',
  tenant_id      BIGINT NULL,
  prompt_segment TEXT NOT NULL,
  feedback_score DECIMAL(3,2),
  usage_count    INT DEFAULT 0,
  enabled        TINYINT(1) NOT NULL DEFAULT 1,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_style_scope (scope),
  INDEX idx_style_tenant (tenant_id),
  INDEX idx_style_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鍐呭椋庢牸搴?;

CREATE TABLE IF NOT EXISTS module_style_binding (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_key VARCHAR(50) NOT NULL,
  style_id   BIGINT      NOT NULL,
  UNIQUE KEY uk_module_style (module_key, style_id),
  INDEX idx_module_style_module (module_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='妯″潡鍙敤椋庢牸缁戝畾';

CREATE TABLE IF NOT EXISTS content_tasks (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id    BIGINT      NOT NULL,
  user_id      BIGINT NULL,
  module_key   VARCHAR(50) NOT NULL,
  input_params JSON,
  status       ENUM('pending','processing','done','failed','moderated') NOT NULL DEFAULT 'pending',
  result_id    BIGINT,
  generation_history_id BIGINT NULL,
  error_msg    VARCHAR(500),
  cost_credits INT         NOT NULL DEFAULT 0,
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME,
  INDEX idx_content_task_status (status),
  INDEX idx_content_task_tenant (tenant_id),
  INDEX idx_content_task_user (tenant_id, user_id, created_at),
  INDEX idx_content_task_history (generation_history_id),
  INDEX idx_content_task_module_time (module_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 鍐呭鐢熸垚浠诲姟';

CALL sushijia_add_column_if_missing('content_tasks', 'user_id', 'BIGINT NULL AFTER tenant_id');
CALL sushijia_add_column_if_missing('content_tasks', 'generation_history_id', 'BIGINT NULL AFTER result_id');

CREATE TABLE IF NOT EXISTS content_results (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id           BIGINT     NOT NULL,
  content           MEDIUMTEXT NOT NULL,
  tokens_used       INT,
  moderated         TINYINT(1) DEFAULT 0,
  moderation_detail JSON,
  created_at        DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_content_result_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 鍐呭鐢熸垚缁撴灉';

CREATE TABLE IF NOT EXISTS marketing_plans (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT       NOT NULL,
  name        VARCHAR(100) NOT NULL,
  festival    VARCHAR(100),
  status      VARCHAR(30)  NOT NULL DEFAULT 'draft',
  hotel_name  VARCHAR(100),
  period      VARCHAR(100),
  target      VARCHAR(500),
  tags        JSON,
  kpis        JSON,
  phases      JSON,
  channels    JSON,
  pricings    JSON,
  activities  JSON,
  alert_note  VARCHAR(500),
  alerts      JSON,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_marketing_plan_tenant_time (tenant_id, updated_at),
  INDEX idx_marketing_plan_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钀ラ攢璁″垝';

-- =====================================================
-- 4.1 鐢ㄦ埛绔煡璇嗗簱銆佺敓鎴愬巻鍙层€佸彂甯?-- =====================================================

CREATE TABLE IF NOT EXISTS hotel_knowledge_files (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id      BIGINT       NOT NULL,
  original_name  VARCHAR(255) NOT NULL,
  file_type      VARCHAR(50)  NOT NULL DEFAULT '',
  file_size      BIGINT       NOT NULL DEFAULT 0,
  storage_path   VARCHAR(500) NOT NULL DEFAULT '',
  parse_status   ENUM('pending','processing','done','failed') NOT NULL DEFAULT 'pending',
  parse_error    VARCHAR(500),
  created_by     BIGINT,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_knowledge_file_tenant_time (tenant_id, created_at),
  INDEX idx_knowledge_file_status (parse_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='閰掑簵鐭ヨ瘑搴撲笂浼犳枃浠?;

CREATE TABLE IF NOT EXISTS hotel_knowledge_extract_jobs (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id      BIGINT      NOT NULL,
  file_id        BIGINT NULL,
  input_text     MEDIUMTEXT,
  source_type    VARCHAR(50) NOT NULL DEFAULT 'text' COMMENT 'file/text/sentence',
  status         ENUM('pending','processing','awaiting_confirm','confirmed','failed','cancelled') NOT NULL DEFAULT 'pending',
  extracted_json JSON,
  summary        MEDIUMTEXT,
  error_msg      VARCHAR(500),
  created_by     BIGINT,
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at   DATETIME,
  INDEX idx_knowledge_extract_tenant_time (tenant_id, created_at),
  INDEX idx_knowledge_extract_status (status),
  INDEX idx_knowledge_extract_file (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='閰掑簵鐭ヨ瘑鎻愬彇浠诲姟';

CREATE TABLE IF NOT EXISTS hotel_knowledge_items (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT       NOT NULL,
  category        VARCHAR(64)  NOT NULL COMMENT 'basic/room/facility/policy/temporary_notice/promotion/faq',
  title           VARCHAR(200) NOT NULL,
  content         MEDIUMTEXT   NOT NULL,
  structured_json JSON,
  source_type     VARCHAR(50)  NOT NULL DEFAULT 'manual',
  source_name     VARCHAR(200) NOT NULL DEFAULT '',
  source_file_id  BIGINT NULL,
  extract_job_id  BIGINT NULL,
  effective_from  DATETIME NULL,
  effective_to    DATETIME NULL,
  status          ENUM('draft','active','expired','archived') NOT NULL DEFAULT 'active',
  confidence      DECIMAL(5,2) NOT NULL DEFAULT 0,
  created_by      BIGINT,
  updated_by      BIGINT,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_knowledge_item_tenant_category (tenant_id, category),
  INDEX idx_knowledge_item_status_time (status, effective_to),
  FULLTEXT KEY ft_knowledge_content (title, content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='閰掑簵鏈湴鐭ヨ瘑鏉＄洰';

CALL sushijia_add_column_if_missing('hotel_knowledge_items', 'source_name', 'VARCHAR(200) NOT NULL DEFAULT '''' AFTER source_type');
CALL sushijia_add_column_if_missing('hotel_knowledge_items', 'updated_by', 'BIGINT NULL AFTER created_by');

CREATE TABLE IF NOT EXISTS tenant_operation_logs (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT       NOT NULL,
  user_id     BIGINT NULL,
  action      VARCHAR(100) NOT NULL,
  target_type VARCHAR(50)  NOT NULL,
  target_id   VARCHAR(100) NOT NULL DEFAULT '',
  detail      TEXT,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_tenant_operation_tenant_time (tenant_id, created_at),
  INDEX idx_tenant_operation_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='閰掑簵绔搷浣滃璁℃棩蹇?;

CREATE TABLE IF NOT EXISTS hotel_knowledge_sync_jobs (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id         BIGINT      NOT NULL,
  provider_key      VARCHAR(50) NOT NULL DEFAULT 'dify',
  target_dataset_id VARCHAR(128) NOT NULL DEFAULT '',
  knowledge_item_id BIGINT      NOT NULL,
  status            ENUM('pending','processing','success','failed','skipped') NOT NULL DEFAULT 'pending',
  retry_count       INT         NOT NULL DEFAULT 0,
  error_msg         VARCHAR(500),
  synced_at         DATETIME NULL,
  created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_knowledge_sync_tenant_status (tenant_id, status),
  INDEX idx_knowledge_sync_item (knowledge_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='閰掑簵鐭ヨ瘑澶栭儴骞冲彴鍚屾浠诲姟';

CREATE TABLE IF NOT EXISTS ai_generation_history (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT       NOT NULL,
  user_id         BIGINT NULL,
  module_key      VARCHAR(64)  NOT NULL,
  title           VARCHAR(200) NOT NULL DEFAULT '',
  prompt          MEDIUMTEXT,
  input_params    JSON,
  output_content  MEDIUMTEXT,
  output_assets   JSON,
  provider_key    VARCHAR(50)  NOT NULL DEFAULT 'dify',
  agent_config_id BIGINT NULL,
  agent_binding_id BIGINT NULL,
  agent_name      VARCHAR(128) NOT NULL DEFAULT '',
  request_id      VARCHAR(100),
  knowledge_refs  JSON,
  duration_ms     INT NULL,
  cost_credits    INT          NOT NULL DEFAULT 0,
  status          ENUM('processing','success','failed','cancelled') NOT NULL DEFAULT 'processing',
  error_msg       VARCHAR(500),
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at    DATETIME NULL,
  INDEX idx_generation_tenant_time (tenant_id, created_at),
  INDEX idx_generation_user_module_time (user_id, module_key, created_at),
  INDEX idx_generation_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐢ㄦ埛绔?AI 鐢熸垚鍘嗗彶';

CALL sushijia_add_column_if_missing('ai_generation_history', 'user_id', 'BIGINT NULL AFTER tenant_id');
CALL sushijia_add_column_if_missing('ai_generation_history', 'output_assets', 'JSON NULL AFTER output_content');
CALL sushijia_add_column_if_missing('ai_generation_history', 'agent_config_id', 'BIGINT NULL AFTER provider_key');
CALL sushijia_add_column_if_missing('ai_generation_history', 'agent_binding_id', 'BIGINT NULL AFTER agent_config_id');
CALL sushijia_add_column_if_missing('ai_generation_history', 'agent_name', 'VARCHAR(128) NOT NULL DEFAULT '''' AFTER agent_binding_id');
CALL sushijia_add_column_if_missing('ai_generation_history', 'request_id', 'VARCHAR(100) NULL AFTER agent_name');
CALL sushijia_add_column_if_missing('ai_generation_history', 'knowledge_refs', 'JSON NULL AFTER request_id');
CALL sushijia_add_column_if_missing('ai_generation_history', 'duration_ms', 'INT NULL AFTER knowledge_refs');

CREATE TABLE IF NOT EXISTS ai_generation_assets (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id      BIGINT      NOT NULL,
  generation_id  BIGINT      NOT NULL,
  asset_type     VARCHAR(50) NOT NULL COMMENT 'image/video/file/text',
  url            VARCHAR(1000) NOT NULL DEFAULT '',
  storage_path   VARCHAR(500) NOT NULL DEFAULT '',
  metadata_json  JSON,
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_generation_asset_generation (generation_id),
  INDEX idx_generation_asset_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 鐢熸垚绱犳潗璧勬簮';

CREATE TABLE IF NOT EXISTS ai_usage_records (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id      BIGINT      NOT NULL,
  user_id        BIGINT NULL,
  module_key     VARCHAR(64) NOT NULL,
  provider_key   VARCHAR(50) NOT NULL DEFAULT 'dify',
  request_id     VARCHAR(100),
  input_tokens   INT         NOT NULL DEFAULT 0,
  output_tokens  INT         NOT NULL DEFAULT 0,
  image_count    INT         NOT NULL DEFAULT 0,
  video_seconds  INT         NOT NULL DEFAULT 0,
  raw_cost       DECIMAL(12,4) NOT NULL DEFAULT 0,
  credit_cost    INT         NOT NULL DEFAULT 0,
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_usage_tenant_time (tenant_id, created_at),
  INDEX idx_usage_module_time (module_key, created_at),
  INDEX idx_usage_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 浣跨敤閲忎笌绠楀姏鏍哥畻璁板綍';

CREATE TABLE IF NOT EXISTS user_recent_presets (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id    BIGINT       NOT NULL,
  user_id      BIGINT       NOT NULL,
  module_key   VARCHAR(64)  NOT NULL,
  preset_name  VARCHAR(100) NOT NULL DEFAULT '鏈€杩戜娇鐢?,
  params_json  JSON,
  last_used_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_recent_preset_user_module (tenant_id, user_id, module_key, preset_name),
  INDEX idx_recent_preset_user_time (user_id, last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐢ㄦ埛鏈€杩戠敓鎴愬弬鏁?;

CREATE TABLE IF NOT EXISTS publish_channels (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  channel_type  VARCHAR(50)  NOT NULL COMMENT 'wechat_mp/xhs/manual',
  channel_name  VARCHAR(100) NOT NULL,
  auth_config   JSON,
  enabled       TINYINT(1)   NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_publish_channel_tenant (tenant_id, channel_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鍐呭鍙戝竷娓犻亾';

CREATE TABLE IF NOT EXISTS publish_tasks (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id      BIGINT      NOT NULL,
  user_id        BIGINT NULL,
  generation_id  BIGINT      NOT NULL,
  channel_type   VARCHAR(50) NOT NULL,
  channel_id     BIGINT NULL,
  status         ENUM('draft','scheduled','publishing','published','failed','cancelled') NOT NULL DEFAULT 'draft',
  scheduled_at   DATETIME NULL,
  published_at   DATETIME NULL,
  error_msg      VARCHAR(500),
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_publish_task_tenant_status (tenant_id, status),
  INDEX idx_publish_task_generation (generation_id),
  INDEX idx_publish_task_schedule (scheduled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鍐呭鍙戝竷浠诲姟';

-- =====================================================
-- 5. 鍙嶉銆佸悎瑙勩€佽皟鐢ㄦ棩蹇椼€佸璁?-- =====================================================

CREATE TABLE IF NOT EXISTS content_feedback (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT      NOT NULL,
  module_key      VARCHAR(50) NOT NULL,
  prompt_version  VARCHAR(20),
  style_id        BIGINT,
  rating          ENUM('good','bad','none') NOT NULL DEFAULT 'none',
  content_snippet VARCHAR(1000),
  created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_feedback_tenant_module (tenant_id, module_key),
  INDEX idx_feedback_prompt_version (prompt_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鍐呭鍙嶉闂幆';

CREATE TABLE IF NOT EXISTS moderation_rules (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  name          VARCHAR(100) NOT NULL,
  rule_type     ENUM('keyword','wordbank','regex') NOT NULL DEFAULT 'keyword',
  keywords      TEXT         NOT NULL,
  action        ENUM('block','review','replace') NOT NULL DEFAULT 'block',
  hit_count_30d INT          NOT NULL DEFAULT 0,
  enabled       TINYINT(1)   NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_moderation_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鍐呭鍚堣瑙勫垯';

CREATE TABLE IF NOT EXISTS ai_call_logs (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id        BIGINT NULL,
  user_id          BIGINT NULL,
  module_key       VARCHAR(50) NOT NULL,
  provider         VARCHAR(32) NOT NULL DEFAULT 'dify',
  app_name         VARCHAR(128),
  app_type         VARCHAR(32),
  endpoint         VARCHAR(255),
  request_id       VARCHAR(64),
  task_id          BIGINT NULL,
  status           ENUM('success','failed') NOT NULL,
  http_status      INT,
  duration_ms      INT,
  input_tokens     INT,
  output_tokens    INT,
  credits_cost     INT NOT NULL DEFAULT 0,
  error_code       VARCHAR(100),
  error_message    VARCHAR(500),
  request_summary  TEXT,
  response_summary TEXT,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ai_call_time (created_at),
  INDEX idx_ai_call_tenant_time (tenant_id, created_at),
  INDEX idx_ai_call_module_time (module_key, created_at),
  INDEX idx_ai_call_status_time (status, created_at),
  INDEX idx_ai_call_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 璋冪敤鏃ュ織';

CREATE TABLE IF NOT EXISTS api_call_logs (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id   BIGINT      NOT NULL,
  module_key  VARCHAR(50) NOT NULL,
  duration_ms INT,
  status      ENUM('success','failed','timeout') NOT NULL DEFAULT 'success',
  error_msg   VARCHAR(500),
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_api_call_tenant_time (tenant_id, created_at),
  INDEX idx_api_call_module_time (module_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='閫氱敤 API 璋冪敤鏃ュ織';

CREATE TABLE IF NOT EXISTS admin_operation_logs (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  admin_id    BIGINT,
  admin_name  VARCHAR(50),
  action      VARCHAR(100) NOT NULL,
  target_type VARCHAR(50),
  target_id   VARCHAR(100),
  detail      TEXT,
  ip          VARCHAR(64),
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_admin_operation_time (created_at),
  INDEX idx_admin_operation_admin (admin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绠＄悊绔搷浣滃璁℃棩蹇?;

CREATE TABLE IF NOT EXISTS sms_send_logs (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  phone       VARCHAR(20) NOT NULL,
  scene       VARCHAR(50) NOT NULL DEFAULT 'login',
  status      ENUM('success','failed') NOT NULL DEFAULT 'success',
  error_msg   VARCHAR(500),
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_sms_phone_time (phone, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐭俊鍙戦€佹棩蹇?;

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- 6. 榛樿鏁版嵁
-- =====================================================

INSERT INTO admins (email, name, password_hash, role, status)
VALUES
('admin@sushijia.ai', '瓒呯骇绠＄悊鍛?, '$2a$10$auvlIg6/8RDChpiPADQneOkcli0OWe.M6OAtaIYor5Dw2Db93d0pm', 'super_admin', 'active'),
('prompt@sushijia.ai', '鍐呭杩愯惀', '$2a$10$auvlIg6/8RDChpiPADQneOkcli0OWe.M6OAtaIYor5Dw2Db93d0pm', 'content_ops', 'active')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  role = VALUES(role),
  status = VALUES(status);

INSERT INTO tenants (id, name, type, city, total_rooms, tags, target_audience, nearby, contact_phone, tier, status, balance, alert_threshold, melt_threshold, qps_limit)
VALUES
(1, '浜戞爾灞卞眳姘戝', '绮惧搧姘戝', '鏉窞', 12, '浜插瓙,灞辨櫙,鍛ㄦ湯搴﹀亣,灏忕孩涔﹀弸濂?, '浜插瓙瀹跺涵,鎯呬荆鍛ㄦ湯娓?鍩庡競鐧介', '瑗挎箹,鐏甸殣瀵?寰掓璺嚎', '13800000000', 'pro', 'active', 12000, 500, 0, 5),
(2, '娴峰笨瑙傛櫙閰掑簵', '搴﹀亣閰掑簵', '鍘﹂棬', 24, '娴锋櫙,鎯呬荆,鏃呮媿,鏃╅', '鎯呬荆娓稿,瀹跺涵娓稿,骞磋交瀹㈢兢', '鐜矝璺?娌欐哗,鍜栧暋琛?, '13800000001', 'basic', 'active', 3000, 500, 0, 5),
(3, '鍩庡競杞诲ア閰掑簵', '鍩庡競閰掑簵', '涓婃捣', 36, '鍟嗗姟,鍦伴搧鏃?浼氳,闀夸綇', '鍟嗗姟瀹?浼氬睍瀹?鐭€斿樊鏃?, '鍦伴搧绔?浼氬睍涓績,鍟嗗湀', '13800000002', 'trial', 'active', 300, 300, 0, 5)
ON DUPLICATE KEY UPDATE
  id = id;

INSERT INTO tenant_branches (tenant_id, name, city, allocated_quota, consumed, manager_phone, status)
SELECT 1, '浜戞爾灞卞眳涓诲簵', '鏉窞', 12000, 0, '13800000000', 'active'
WHERE EXISTS (SELECT 1 FROM tenants WHERE id = 1 AND name = '浜戞爾灞卞眳姘戝')
  AND NOT EXISTS (SELECT 1 FROM tenant_branches WHERE tenant_id = 1 AND name = '浜戞爾灞卞眳涓诲簵');

INSERT INTO hotel_staff (tenant_id, name, phone, role, avatar, password_hash)
VALUES
(1, '搴楅暱', '13800000000', 'admin', '', '$2a$10$auvlIg6/8RDChpiPADQneOkcli0OWe.M6OAtaIYor5Dw2Db93d0pm'),
(1, '杩愯惀', '13800000003', 'marketing', '', '$2a$10$auvlIg6/8RDChpiPADQneOkcli0OWe.M6OAtaIYor5Dw2Db93d0pm'),
(2, '娴峰笨搴楅暱', '13800000001', 'admin', '', '$2a$10$auvlIg6/8RDChpiPADQneOkcli0OWe.M6OAtaIYor5Dw2Db93d0pm')
ON DUPLICATE KEY UPDATE
  tenant_id = VALUES(tenant_id),
  name = VALUES(name),
  role = VALUES(role),
  avatar = VALUES(avatar);

-- Demo room type seed rows are intentionally not inserted. Real room types must come
-- from each hotel's own setup data so rebinding a hotel cannot revive stale rooms.

INSERT INTO billing_rules (module_key, module_name, board, cost, est_cost_rmb, enabled, sort_order)
VALUES
('brain', '杩愯惀鏅烘収澶ц剳', 'ai', 2, 0.0600, 1, 1),
('strategy', '鍛ㄦ湡钀ラ攢绛栫暐', 'ai', 10, 0.1800, 1, 2),
('xhs', '灏忕孩涔﹀浘鏂?, 'content', 5, 0.1200, 1, 3),
('wechat', '鏈嬪弸鍦堟枃妗?, 'content', 4, 0.1000, 1, 4),
('video', '鐭棰戝彛鎾?, 'content', 6, 0.1800, 1, 5),
('article', '鍏紬鍙锋帹鏂?, 'content', 8, 0.2500, 1, 6),
('poster', '娴锋姤鏂囨/鍥剧墖', 'content', 15, 0.4500, 1, 7),
('pricing', '鏀剁泭瀹氫环寤鸿', 'ai', 6, 0.1200, 1, 8),
('surrounding', '鍛ㄨ竟淇℃伅鏅鸿兘浣?, 'ai', 4, 0.1000, 1, 9),
('review', '濂借瘎鐢熸垚', 'reputation', 3, 0.0800, 1, 10),
('reply', '鍥炶瘎璇濇湳', 'reputation', 2, 0.0600, 1, 11),
('polish', 'AI 鏂囨娑﹁壊', 'content', 1, 0.0300, 1, 12)
ON DUPLICATE KEY UPDATE
  module_name = VALUES(module_name),
  board = VALUES(board),
  cost = VALUES(cost),
  est_cost_rmb = VALUES(est_cost_rmb),
  enabled = VALUES(enabled),
  sort_order = VALUES(sort_order);

INSERT INTO recharge_packages (name, credits, price_rmb, applicable_tiers, enabled)
VALUES
('浣撻獙鍖?, 1000, 99.00, 'trial,basic,pro,flagship', 1),
('鏍囧噯鍖?, 4000, 299.00, 'basic,pro,flagship', 1),
('澧為暱鍖?, 12000, 699.00, 'pro,flagship', 1),
('杩為攣鍖?, 50000, 1999.00, 'flagship', 1)
ON DUPLICATE KEY UPDATE
  credits = VALUES(credits),
  price_rmb = VALUES(price_rmb),
  applicable_tiers = VALUES(applicable_tiers),
  enabled = VALUES(enabled);

INSERT INTO tenant_plans (code, name, price_rmb, monthly_credits, max_branches, enabled_modules, benefits_json, enabled, sort_order)
VALUES
('trial', '浣撻獙鐗?, 0, 300, 1, 'brain,xhs,wechat,reply,polish', JSON_OBJECT('teamMembers', 2, 'imageGeneration', false, 'batchGeneration', false), 1, 1),
('basic', '鏍囧噯鐗?, 299, 3000, 1, 'brain,xhs,wechat,video,article,review,reply,polish', JSON_OBJECT('teamMembers', 5, 'imageGeneration', false, 'batchGeneration', false), 1, 2),
('pro', '澧為暱鐗?, 699, 12000, 3, 'brain,strategy,xhs,wechat,video,article,poster,pricing,review,reply,polish', JSON_OBJECT('teamMembers', 20, 'imageGeneration', true, 'batchGeneration', true), 1, 3),
('flagship', '杩為攣鐗?, 1999, 50000, 50, 'brain,strategy,xhs,wechat,video,article,poster,pricing,review,reply,polish', JSON_OBJECT('teamMembers', 100, 'imageGeneration', true, 'batchGeneration', true), 1, 4)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  price_rmb = VALUES(price_rmb),
  monthly_credits = VALUES(monthly_credits),
  max_branches = VALUES(max_branches),
  enabled_modules = VALUES(enabled_modules),
  benefits_json = VALUES(benefits_json),
  enabled = VALUES(enabled),
  sort_order = VALUES(sort_order);

INSERT INTO tenant_subscriptions (tenant_id, plan_code, status, monthly_credits, start_at, end_at)
VALUES
(1, 'pro', 'active', 12000, CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 YEAR)),
(2, 'basic', 'active', 3000, CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 YEAR)),
(3, 'trial', 'trialing', 300, CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 14 DAY))
ON DUPLICATE KEY UPDATE
  plan_code = VALUES(plan_code),
  status = VALUES(status),
  monthly_credits = VALUES(monthly_credits),
  end_at = VALUES(end_at);

INSERT INTO system_settings (setting_key, setting_value)
VALUES
('ai_provider', 'dify'),
('ai_api_key', ''),
('ai_model', ''),
('ai_base_url', ''),
('ai_max_tokens', '4000'),
('dify_enabled', 'true'),
('dify_endpoint', 'https://api.dify.ai/v1'),
('dify_timeout_seconds', '180'),
('dify_retry_times', '2'),
('modelscope_api_key', ''),
('modelscope_image_model', 'Tongyi-MAI/Z-Image-Turbo'),
('modelscope_image_poll_attempts', '30'),
('modelscope_image_poll_interval_seconds', '4'),
('sms_provider', ''),
('sms_access_key', ''),
('sms_secret_key', ''),
('sms_sign_name', ''),
('sms_template_code', '')
ON DUPLICATE KEY UPDATE setting_value = IF(VALUES(setting_value) = '', setting_value, VALUES(setting_value));

INSERT IGNORE INTO ai_agent_bindings (module_key, provider, app_type, app_id, api_key, app_name, endpoint, bot_id, bot_api_key, bot_name, enabled)
VALUES
('brain', 'dify', 'workflow', '', '', '瀹胯瘑瀹?AI 搴楅暱', '', 'workflow', '', '瀹胯瘑瀹?AI 搴楅暱', 0),
('strategy', 'dify', 'workflow', '', '', '瀹胯瘑瀹惰惀閿€绛栫暐甯?, '', 'workflow', '', '瀹胯瘑瀹惰惀閿€绛栫暐甯?, 0),
('pricing', 'dify', 'workflow', '', '', '瀹胯瘑瀹舵敹鐩婂畾浠烽【闂?, '', 'workflow', '', '瀹胯瘑瀹舵敹鐩婂畾浠烽【闂?, 0),
('surrounding', 'dify', 'workflow', '', '', '瀹胯瘑瀹跺懆杈逛俊鎭櫤鑳戒綋', '', 'workflow', '', '瀹胯瘑瀹跺懆杈逛俊鎭櫤鑳戒綋', 0),
('xhs', 'dify', 'workflow', '', '', '瀹胯瘑瀹跺皬绾功鍒涗綔瀹?, '', 'workflow', '', '瀹胯瘑瀹跺皬绾功鍒涗綔瀹?, 0),
('wechat', 'dify', 'workflow', '', '', '瀹胯瘑瀹舵湅鍙嬪湀缂栬緫', '', 'workflow', '', '瀹胯瘑瀹舵湅鍙嬪湀缂栬緫', 0),
('article', 'dify', 'workflow', '', '', '瀹胯瘑瀹跺叕浼楀彿涓荤紪', '', 'workflow', '', '瀹胯瘑瀹跺叕浼楀彿涓荤紪', 0),
('video', 'dify', 'workflow', '', '', '瀹胯瘑瀹剁煭瑙嗛缂栧', '', 'workflow', '', '瀹胯瘑瀹剁煭瑙嗛缂栧', 0),
('poster', 'dify', 'workflow', '', '', '瀹胯瘑瀹惰惀閿€瑙嗚璁捐甯?, '', 'workflow', '', '瀹胯瘑瀹惰惀閿€瑙嗚璁捐甯?, 0),
('polish', 'dify', 'workflow', '', '', '瀹胯瘑瀹舵枃妗堟鼎鑹插笀', '', 'workflow', '', '瀹胯瘑瀹舵枃妗堟鼎鑹插笀', 0),
('review', 'dify', 'workflow', '', '', '瀹胯瘑瀹跺ソ璇勫紩瀵煎笀', '', 'workflow', '', '瀹胯瘑瀹跺ソ璇勫紩瀵煎笀', 0),
('reply', 'dify', 'workflow', '', '', '瀹胯瘑瀹剁偣璇勫洖澶嶄笓鍛?, '', 'workflow', '', '瀹胯瘑瀹剁偣璇勫洖澶嶄笓鍛?, 0),
('knowledge', 'dify', 'workflow', '', '', '瀹胯瘑瀹剁煡璇嗘暣鐞嗗憳', '', 'workflow', '', '瀹胯瘑瀹剁煡璇嗘暣鐞嗗憳', 0);

-- 灏嗗巻鍙查粯璁ょ粦瀹氬悓姝ュ埌褰撳墠鈥滀竴鍔熻兘涓€搴旂敤鈥濆悎鍚岋紱API Key銆丒ndpoint 鍜屽惎鐢ㄧ姸鎬佷繚鎸佷笉鍙樸€?UPDATE ai_agent_bindings
SET app_type = 'workflow',
    bot_id = 'workflow',
    app_name = CASE module_key
      WHEN 'brain' THEN '瀹胯瘑瀹?AI 搴楅暱'
      WHEN 'strategy' THEN '瀹胯瘑瀹惰惀閿€绛栫暐甯?
      WHEN 'pricing' THEN '瀹胯瘑瀹舵敹鐩婂畾浠烽【闂?
      WHEN 'surrounding' THEN '瀹胯瘑瀹跺懆杈逛俊鎭櫤鑳戒綋'
      WHEN 'xhs' THEN '瀹胯瘑瀹跺皬绾功鍒涗綔瀹?
      WHEN 'wechat' THEN '瀹胯瘑瀹舵湅鍙嬪湀缂栬緫'
      WHEN 'article' THEN '瀹胯瘑瀹跺叕浼楀彿涓荤紪'
      WHEN 'video' THEN '瀹胯瘑瀹剁煭瑙嗛缂栧'
      WHEN 'poster' THEN '瀹胯瘑瀹惰惀閿€瑙嗚璁捐甯?
      WHEN 'polish' THEN '瀹胯瘑瀹舵枃妗堟鼎鑹插笀'
      WHEN 'review' THEN '瀹胯瘑瀹跺ソ璇勫紩瀵煎笀'
      WHEN 'reply' THEN '瀹胯瘑瀹剁偣璇勫洖澶嶄笓鍛?
      WHEN 'knowledge' THEN '瀹胯瘑瀹剁煡璇嗘暣鐞嗗憳'
      ELSE app_name END,
    bot_name = CASE module_key
      WHEN 'brain' THEN '瀹胯瘑瀹?AI 搴楅暱'
      WHEN 'strategy' THEN '瀹胯瘑瀹惰惀閿€绛栫暐甯?
      WHEN 'pricing' THEN '瀹胯瘑瀹舵敹鐩婂畾浠烽【闂?
      WHEN 'surrounding' THEN '瀹胯瘑瀹跺懆杈逛俊鎭櫤鑳戒綋'
      WHEN 'xhs' THEN '瀹胯瘑瀹跺皬绾功鍒涗綔瀹?
      WHEN 'wechat' THEN '瀹胯瘑瀹舵湅鍙嬪湀缂栬緫'
      WHEN 'article' THEN '瀹胯瘑瀹跺叕浼楀彿涓荤紪'
      WHEN 'video' THEN '瀹胯瘑瀹剁煭瑙嗛缂栧'
      WHEN 'poster' THEN '瀹胯瘑瀹惰惀閿€瑙嗚璁捐甯?
      WHEN 'polish' THEN '瀹胯瘑瀹舵枃妗堟鼎鑹插笀'
      WHEN 'review' THEN '瀹胯瘑瀹跺ソ璇勫紩瀵煎笀'
      WHEN 'reply' THEN '瀹胯瘑瀹剁偣璇勫洖澶嶄笓鍛?
      WHEN 'knowledge' THEN '瀹胯瘑瀹剁煡璇嗘暣鐞嗗憳'
      ELSE bot_name END
WHERE module_key IN ('brain','strategy','pricing','xhs','wechat','article','video','poster','polish','review','reply','knowledge','surrounding');

-- 鍘嗗彶鐗堟湰鍙兘鎶婂悓涓€涓?Dify App 鍚屾椂缁戝畾缁欏涓姛鑳姐€傜嫭绔嬫櫤鑳戒綋涓婄嚎鍓嶇粺涓€鍋滅敤杩欎簺鍐茬獊缁戝畾銆?UPDATE ai_agent_bindings b
JOIN (
  SELECT api_key
  FROM ai_agent_bindings
  WHERE api_key IS NOT NULL AND api_key <> ''
  GROUP BY api_key
  HAVING COUNT(DISTINCT module_key) > 1
) duplicate_apps ON duplicate_apps.api_key = b.api_key
SET b.enabled = 0;

UPDATE ai_agent_bindings
SET input_schema = JSON_OBJECT(
  'commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','message','string',
  'theme','string','target','string','objective','string','objectiveLabel','string',
  'period','string','occasion','string','targetAudience','string','channels','array',
  'budgetLevel','string','executionCapacity','string','outputDepth','string',
  'marketSignals','string','competitorObservations','string','marketContext','string',
  'availableOffers','string','constraints','string','evidenceRequirement','boolean','surroundingContextJson','string',
  'selectedParamsJson','string'
)
WHERE module_key = 'strategy';

UPDATE ai_agent_bindings
SET input_schema = JSON_OBJECT(
  'commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','message','string',
  'dateRange','string','pricingPeriod','string','pricingGoal','string',
  'demandSignal','string','bookingWindow','string','eventFactor','string',
  'competitorPriceRange','string','currentPriceNotes','string','priceFloor','string',
  'maxDiscountPercent','number','targetChannels','array','promotionAllowed','boolean',
  'packagePreference','string','riskLevel','string','constraints','string',
  'roomSnapshot','array','evidenceRequirement','boolean','surroundingContextJson','string','selectedParamsJson','string'
)
WHERE module_key = 'pricing';

UPDATE ai_agent_bindings
SET input_schema = JSON_OBJECT(
  'commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','message','string',
  'hotelName','string','city','string','district','string','address','string',
  'checkIn','string','checkOut','string','selectedParamsJson','string'
)
WHERE module_key = 'surrounding';

INSERT INTO ai_providers (provider_key, provider_name, provider_type, endpoint, auth_type, api_key_encrypted, enabled, sort_order)
VALUES
('dify', 'Dify 浜戠缂栨帓', 'chatflow', 'https://api.dify.ai/v1', 'bearer', '', 1, 1),
('openai_compatible', 'OpenAI 鍏煎鏂囨湰妯″瀷', 'completions', '', 'bearer', '', 0, 2),
('image_provider', '鍥剧墖鐢熸垚鏈嶅姟', 'image', '', 'bearer', '', 0, 3),
('custom', '鑷畾涔?AI 鏈嶅姟', 'custom', '', 'bearer', '', 0, 9)
ON DUPLICATE KEY UPDATE
  provider_name = VALUES(provider_name),
  provider_type = VALUES(provider_type),
  endpoint = VALUES(endpoint),
  auth_type = VALUES(auth_type),
  enabled = VALUES(enabled),
  sort_order = VALUES(sort_order);

INSERT INTO ai_capabilities (capability_key, capability_name, description, enabled, sort_order)
VALUES
('text_generation', '鏂囨湰鍐呭鐢熸垚', '灏忕孩涔︺€佹湅鍙嬪湀銆佸叕浼楀彿銆佸洖澶嶃€佹椿鍔ㄦ枃妗堢瓑鏂囨湰鐢熶骇鑳藉姏', 1, 1),
('image_generation', '鍥剧墖鐢熸垚', '娴锋姤銆佸皝闈€佸浼犲浘銆佸叕浼楀彿澶村浘绛夊浘鐗囩敓鎴愯兘鍔?, 1, 2),
('video_script', '瑙嗛鑴氭湰', '鐭棰戣剼鏈€佸垎闀溿€佸彛鎾€佹媿鎽勬竻鍗曡兘鍔?, 1, 3),
('knowledge_extract', '鐭ヨ瘑搴撴彁鍙?, '浠庢枃浠躲€佹枃鏈€佷竴鍙ヨ瘽鏇存柊涓彁鍙栭厭搴楃煡璇嗗苟鐢熸垚寰呯‘璁ゆ憳瑕?, 1, 4),
('operation_advice', '杩愯惀鍐崇瓥', '鍩轰簬閰掑簵璧勬枡鐨勫畾浠峰缓璁€佽惀閿€绛栫暐銆佽祫鏂欐鏌ュ拰琛屽姩娓呭崟', 1, 5),
('text_polish', '鏂囨娑﹁壊', '瀵圭敤鎴疯緭鍏ョ殑涓婚銆佸崠鐐广€佹枃妗堝拰鍥剧墖鎻忚堪鍋氳〃杈句紭鍖栵紝涓嶆柊澧炰簨瀹?, 1, 6)
ON DUPLICATE KEY UPDATE
  capability_name = VALUES(capability_name),
  description = VALUES(description),
  enabled = VALUES(enabled),
  sort_order = VALUES(sort_order);

INSERT INTO ai_agent_configs
  (tenant_id, module_key, module_name, capability_key, provider_key, app_id, workflow_id, api_key_encrypted, endpoint, call_mode, input_schema, output_schema, output_parser, knowledge_policy, enabled)
VALUES
(0, 'brain', '瀹胯瘑瀹?AI 搴楅暱', 'operation_advice', 'dify', '', '', '', '', 'workflow', JSON_OBJECT('commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','surroundingContextJson','string','enableWebSearch','boolean','surroundingTaskMode','string','message','string','outputStyle','string'), JSON_OBJECT('content','string'), 'auto', 'tenant_confirmed', 0),
(0, 'strategy', '瀹胯瘑瀹惰惀閿€绛栫暐甯?, 'operation_advice', 'dify', '', '', '', '', 'chatflow', JSON_OBJECT('commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','message','string','theme','string','objective','string','period','string','occasion','string','targetAudience','string','channels','array','budgetLevel','string','executionCapacity','string','outputDepth','string','marketSignals','string','competitorObservations','string','availableOffers','string','constraints','string','evidenceRequirement','boolean','surroundingContextJson','string'), JSON_OBJECT('content','string'), 'auto', 'tenant_confirmed', 0),
(0, 'pricing', '瀹胯瘑瀹舵敹鐩婂畾浠烽【闂?, 'operation_advice', 'dify', '', '', '', '', 'chatflow', JSON_OBJECT('commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','message','string','dateRange','string','pricingPeriod','string','pricingGoal','string','demandSignal','string','bookingWindow','string','eventFactor','string','competitorPriceRange','string','currentPriceNotes','string','priceFloor','string','maxDiscountPercent','number','targetChannels','array','promotionAllowed','boolean','packagePreference','string','riskLevel','string','constraints','string','roomSnapshot','array','evidenceRequirement','boolean','surroundingContextJson','string'), JSON_OBJECT('content','string'), 'auto', 'tenant_confirmed', 0),
(0, 'surrounding', '瀹胯瘑瀹跺懆杈逛俊鎭櫤鑳戒綋', 'operation_advice', 'dify', '', '', '', '', 'workflow', JSON_OBJECT('commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','taskMode','string','query','string','message','string','hotelName','string','city','string','address','string','longitude','string','latitude','string','checkIn','string','checkOut','string'), JSON_OBJECT('hotelProfileSuggestion','object','currentHotelPrices','array','nearbyHotelPrices','array','nearbyHotPlaces','array','localEvents','array','weather','object','searchEvidence','array','unavailableFields','array'), 'json', 'tenant_confirmed', 0),
(0, 'xhs', '瀹胯瘑瀹跺皬绾功鍒涗綔瀹?, 'text_generation', 'dify', '', '', '', '', 'chatflow', JSON_OBJECT('commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','message','string','topics','string','customTopic','string','tone','string','style','string','note','string','withImage','boolean','imageSize','string','imageCount','number'), JSON_OBJECT('title','string','body','string','tags','array','imageSuggestions','array'), 'json', 'tenant_confirmed', 0),
(0, 'wechat', '瀹胯瘑瀹舵湅鍙嬪湀缂栬緫', 'text_generation', 'dify', '', '', '', '', 'chatflow', JSON_OBJECT('commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','message','string','slots','array','style','string','length','string','note','string','withImage','boolean','imageSize','string'), JSON_OBJECT('morning','string','noon','string','evening','string'), 'json', 'tenant_confirmed', 0),
(0, 'article', '瀹胯瘑瀹跺叕浼楀彿涓荤紪', 'text_generation', 'dify', '', '', '', '', 'chatflow', JSON_OBJECT('commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','message','string','title','string','style','string','length','string','withImage','boolean','imageCount','number','fileName','string'), JSON_OBJECT('title','string','summary','string','content','string','imageSuggestions','array'), 'json', 'tenant_confirmed', 0),
(0, 'video', '瀹胯瘑瀹剁煭瑙嗛缂栧', 'video_script', 'dify', '', '', '', '', 'chatflow', JSON_OBJECT('commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','message','string','sellingPoints','string','view','string','style','string','goal','string','duration','string','count','number'), JSON_OBJECT('scripts','array','shots','array','publishTips','string','bgm','string'), 'json', 'tenant_confirmed', 0),
(0, 'poster', '瀹胯瘑瀹惰惀閿€瑙嗚璁捐甯?, 'image_generation', 'dify', '', '', '', '', 'workflow', JSON_OBJECT('commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','message','string','mode','string','theme','string','content','string','style','string','scene','string','platform','string','targetAudience','string','textDensity','string','cta','string','sellingPoint','string','imageSize','string','width','number','height','number','imageData','string'), JSON_OBJECT('imageUrl','string','prompt','string','imageSize','string','width','number','height','number'), 'auto', 'tenant_confirmed', 0),
(0, 'polish', '瀹胯瘑瀹舵枃妗堟鼎鑹插笀', 'text_polish', 'dify', '', '', '', '', 'chatflow', JSON_OBJECT('commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','message','string','sourceText','string','scene','string','field','string','style','string','purpose','string','immutableFacts','array'), JSON_OBJECT('content','string'), 'auto', 'tenant_confirmed', 0),
(0, 'review', '瀹胯瘑瀹跺ソ璇勫紩瀵煎笀', 'text_generation', 'dify', '', '', '', '', 'chatflow', JSON_OBJECT('commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','message','string','guestType','string','scene','string','additionalNotes','string'), JSON_OBJECT('content','string'), 'auto', 'tenant_confirmed', 0),
(0, 'reply', '瀹胯瘑瀹剁偣璇勫洖澶嶄笓鍛?, 'text_generation', 'dify', '', '', '', '', 'chatflow', JSON_OBJECT('commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','message','string','reviewText','string','reviewType','string','style','string','additionalNotes','string'), JSON_OBJECT('content','string'), 'auto', 'tenant_confirmed', 0),
(0, 'knowledge', '瀹胯瘑瀹剁煡璇嗘暣鐞嗗憳', 'knowledge_extract', 'dify', '', '', '', '', 'chatflow', JSON_OBJECT('commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','message','string','sourceType','string','sourceText','string','fileName','string','extractionMode','string','effectiveHint','string'), JSON_OBJECT('summary','string','items','array'), 'json', 'tenant_confirmed', 0)
ON DUPLICATE KEY UPDATE
  module_name = VALUES(module_name),
  capability_key = VALUES(capability_key),
  provider_key = VALUES(provider_key),
  call_mode = VALUES(call_mode),
  input_schema = VALUES(input_schema),
  output_schema = VALUES(output_schema),
  output_parser = VALUES(output_parser),
  knowledge_policy = VALUES(knowledge_policy);

UPDATE ai_agent_configs
SET call_mode = 'workflow',
    module_name = CASE module_key
      WHEN 'brain' THEN '瀹胯瘑瀹?AI 搴楅暱'
      WHEN 'strategy' THEN '瀹胯瘑瀹惰惀閿€绛栫暐甯?
      WHEN 'pricing' THEN '瀹胯瘑瀹舵敹鐩婂畾浠烽【闂?
      WHEN 'xhs' THEN '瀹胯瘑瀹跺皬绾功鍒涗綔瀹?
      WHEN 'wechat' THEN '瀹胯瘑瀹舵湅鍙嬪湀缂栬緫'
      WHEN 'article' THEN '瀹胯瘑瀹跺叕浼楀彿涓荤紪'
      WHEN 'video' THEN '瀹胯瘑瀹剁煭瑙嗛缂栧'
      WHEN 'poster' THEN '瀹胯瘑瀹惰惀閿€瑙嗚璁捐甯?
      WHEN 'polish' THEN '瀹胯瘑瀹舵枃妗堟鼎鑹插笀'
      WHEN 'review' THEN '瀹胯瘑瀹跺ソ璇勫紩瀵煎笀'
      WHEN 'reply' THEN '瀹胯瘑瀹剁偣璇勫洖澶嶄笓鍛?
      WHEN 'knowledge' THEN '瀹胯瘑瀹剁煡璇嗘暣鐞嗗憳'
      ELSE module_name END
WHERE module_key IN ('brain','strategy','pricing','xhs','wechat','article','video','poster','polish','review','reply','knowledge','surrounding');

INSERT INTO style_library (id, name, scope, tenant_id, prompt_segment, feedback_score, usage_count, enabled)
VALUES
(1, '娓╂殩娌绘剤', 'public', NULL, '璇皵娓╂煍銆佺敾闈㈡劅寮猴紝閫傚悎姘戝銆佸皬绾功銆佹湅鍙嬪湀锛岄伩鍏嶅じ澶ф壙璇恒€?, 4.80, 0, 1),
(2, '涓撲笟鍏嬪埗', 'public', NULL, '琛ㄨ揪绠€娲併€佷俊鎭瘑搴﹂珮锛岄€傚悎缁忚惀鍒嗘瀽銆佸畾浠峰缓璁€佺鐞嗘眹鎶ャ€?, 4.70, 0, 1),
(3, '骞磋交绉嶈崏', 'public', NULL, '璇█杞诲揩銆佹湁浠ｅ叆鎰燂紝閫傚悎骞磋交瀹㈢兢锛屼絾涓嶄娇鐢ㄨ櫄鍋囩儹闂ㄨ瘝銆?, 4.60, 0, 1),
(4, '楂樼搴﹀亣', 'public', NULL, '寮鸿皟鍝佽川銆佺┖闂淬€佹湇鍔′笌鐩殑鍦颁綋楠岋紝璇皵楂樼骇浣嗕笉娴じ銆?, 4.60, 0, 1)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  scope = VALUES(scope),
  tenant_id = VALUES(tenant_id),
  prompt_segment = VALUES(prompt_segment),
  feedback_score = VALUES(feedback_score),
  enabled = VALUES(enabled);

INSERT INTO module_style_binding (module_key, style_id)
VALUES
('xhs', 1), ('xhs', 3), ('xhs', 4),
('wechat', 1), ('wechat', 3),
('video', 1), ('video', 3),
('article', 2), ('article', 4),
('poster', 1), ('poster', 4),
('review', 1), ('reply', 2), ('polish', 2)
ON DUPLICATE KEY UPDATE style_id = VALUES(style_id);

INSERT INTO prompt_templates (module_key, version, title, content, model_name, max_tokens, status, gray_percent, created_by)
VALUES
('brain', 'v2', '杩愯惀鏅烘収澶ц剳', '浣犳槸瀹胯瘑瀹?AI 搴楅暱銆傝浠呭熀浜庨厭搴椾富鍔ㄥ～鍐欑殑鍩虹璧勬枡銆佹埧鍨嬪弬鑰冧俊鎭€佸凡纭鐭ヨ瘑鍜岀敤鎴锋湰娆￠棶棰樼粰鍑烘竻鏅般€佸彲鎵ц鐨勫缓璁€傚钩鍙版湭鎺ュ叆 PMS銆丱TA 鎴栬鍗曠郴缁燂紝涓嶅緱澹扮О鎺屾彙瀹炴椂鍏ヤ綇鐜囥€佸彲鍞埧銆佽鍗曘€佽惀鏀躲€丷evPAR 鎴栨湭鏉ユ埧鎬併€傜己灏戜俊鎭椂鍒楀嚭闇€瑕侀厭搴椾汉宸ョ‘璁ょ殑浜嬮」锛屼笉瑕佽櫄鏋勬暟鎹€?, '', 1200, 'production', 0, 'system'),
('xhs', 'v1', '灏忕孩涔﹀浘鏂囩敓鎴?, '浣犳槸姘戝鍐呭钀ラ攢涓撳銆傝鏍规嵁閰掑簵璧勬枡銆佺敤鎴蜂富棰樸€佺洰鏍囧缇ゅ拰椋庢牸锛岀敓鎴愬皬绾功鍥炬枃鍐呭锛屽寘鍚爣棰樸€佹鏂囥€佹爣绛俱€佸皝闈㈠缓璁€備笉寰楁壙璇虹粷瀵圭粨鏋滐紝涓嶅緱铏氭瀯璁炬柦銆?, '', 1200, 'production', 0, 'system'),
('wechat', 'v1', '鏈嬪弸鍦堟枃妗堢敓鎴?, '璇蜂负閰掑簵鐢熸垚閫傚悎鏈嬪弸鍦堝彂甯冪殑鐭枃妗堬紝璇皵鑷劧銆佹湁鐢婚潰鎰燂紝绐佸嚭鐪熷疄鍗栫偣鍜屽綋涓嬪満鏅€?, '', 800, 'production', 0, 'system'),
('video', 'v1', '鐭棰戝彛鎾剼鏈?, '璇风敓鎴愮煭瑙嗛鍙ｆ挱鑴氭湰锛屽寘鍚紑鍦洪挬瀛愩€侀暅澶村缓璁€佸彛鎾鏂囥€佺粨灏惧紩瀵硷紝鍐呭蹇呴』閫傚悎閰掑簵/姘戝缁忚惀鑰呯洿鎺ヤ娇鐢ㄣ€?, '', 1000, 'production', 0, 'system'),
('article', 'v1', '鍏紬鍙锋帹鏂?, '璇风敓鎴愬叕浼楀彿鎺ㄦ枃缁撴瀯鍜屾鏂囷紝閫傚悎閰掑簵鍝佺墝鍐呭杩愯惀锛岄€昏緫娓呮櫚锛屾爣棰樻湁鍚稿紩鍔涳紝姝ｆ枃涓嶅じ澶у浼犮€?, '', 1600, 'production', 0, 'system'),
('poster', 'v1', '娴锋姤鏂囨', '璇风敓鎴愰厭搴楄惀閿€娴锋姤鏂囨锛屽寘鍚富鏍囬銆佸壇鏍囬銆佸崠鐐广€佽鍔ㄦ寜閽拰鍥剧墖鐢熸垚鎻愮ず璇嶃€?, '', 900, 'production', 0, 'system'),
('polish', 'v1', 'AI 鏂囨娑﹁壊', '浣犳槸閰掑簵/姘戝杩愯惀鏂囨娑﹁壊鍔╂墜銆傝鍙熀浜庣敤鎴峰師鏂囧仛琛ㄨ揪浼樺寲锛岃鍐呭鏇存竻鏅般€佹洿閫傚悎褰撳墠鍦烘櫙鍜岀洰鏍囧缇ゃ€備笉寰楁柊澧炰簨瀹炪€佷环鏍笺€佽鏂姐€佽窛绂汇€佹椿鍔ㄦ垨鏀跨瓥銆傚彧杈撳嚭娑﹁壊鍚庣殑涓枃鏂囨湰锛屼笉杈撳嚭瑙ｉ噴銆?, '', 500, 'production', 0, 'system'),
('strategy', 'v2', '鍛ㄦ湡钀ラ攢绛栫暐', '璇锋牴鎹厭搴楃湡瀹炶祫鏂欍€佺洰鏍囧缇ゃ€佹墽琛屽懆鏈熴€佹笭閬撱€侀绠椼€佸洟闃熻兘鍔涖€佺敤鎴风‘璁ょ殑甯傚満瑙傚療浠ュ強 surroundingContextJson 鐢熸垚绛栫暐璁″垝涔︺€傝緭鍑虹瓥鐣ユ憳瑕併€佸懆杈逛笌绔炲搧娲炲療琛ㄣ€佹笭閬撴帓鏈熺敇鐗硅〃銆佸唴瀹圭煩闃点€佸姩浣滄竻鍗曘€侀绠?璧勬簮鍥捐〃銆佸緟鏍稿疄椤瑰拰澶嶇洏鎸囨爣銆備笉寰楄櫄鏋勫疄鏃剁儹搴︺€佺珵鍝佺粡钀ユ暟鎹€佹湰鍦版椿鍔ㄣ€佽窛绂汇€佷环鏍兼垨骞冲彴鏀跨瓥銆?, '', 1800, 'production', 0, 'system'),
('pricing', 'v2', '鏀剁泭瀹氫环寤鸿', '璇峰熀浜庨厭搴楃湡瀹炴埧鍨嬩笌鎸傜墝浠枫€佸畾浠峰懆鏈熴€侀渶姹備俊鍙枫€侀璁㈢獥鍙ｃ€佹棩鏈熷奖鍝嶃€佺珵鍝佷环鏍艰瀵熴€佷环鏍煎簳绾裤€佹笭閬撱€侀闄╁亸濂戒互鍙?surroundingContextJson 鐢熸垚鏀剁泭瀹氫环璁″垝涔︺€傞€愭埧鍨嬭緭鍑哄缓璁环鏍煎尯闂淬€侀€傜敤鏃ユ湡銆佹笭閬撳姩浣溿€佹墽琛屾椂鐐广€佷緷鎹€佸姩浣滄竻鍗曘€佸緟鏍稿疄椤瑰拰椋庨櫓鎻愰啋銆備笉寰楀亣璁炬嫢鏈夊疄鏃跺叆浣忕巼銆佸疄鏃剁珵鍝佷环鏍兼垨 OTA 鍐呴儴鏁版嵁銆?, '', 1400, 'production', 0, 'system'),
('surrounding', 'v2', '鍛ㄨ竟淇℃伅鏅鸿兘浣?, '璇疯鍙?commonContextJson銆乥usinessParamsJson銆乲nowledgeContextJson銆乼askMode 鍜?query銆倀askMode=weather_only 鏃跺彧璋冪敤楂樺痉澶╂皵宸ュ叿鎴栧ぉ姘斿伐鍏锋煡璇㈤厭搴楁墍鍦ㄥ湴鏈潵澶╂皵锛屼笉璋冪敤鑱旂綉鎼滅储宸ュ叿锛屼笉鎼滅储鎴夸环銆佺珵鍝併€佺儹鐐瑰拰娲诲姩锛泃askMode=full 鏃朵娇鐢ㄥ彲鑱旂綉鎼滅储鑳藉姏姹囨€诲綋鍓嶉厭搴楀叕寮€鍙鎴夸环銆佸懆杈归厭搴楀叕寮€鍙鎴夸环銆佸懆杈圭儹闂ㄥ湴鐐广€佹湰鍦颁簨浠躲€佺儹鐐瑰拰澶╂皵銆傚彧鑳借緭鍑轰弗鏍?JSON锛涙瘡鏉′环鏍笺€佺儹鐐广€佷簨浠躲€佸ぉ姘旈兘蹇呴』鏈夋潵婧愩€侀摼鎺ユ垨寰呮牳瀹炶鏄庛€傛棤娉曡仈缃戞垨骞冲彴浠锋牸涓嶅彲瑙佹椂锛屼笉寰楃寽娴嬶紝蹇呴』鍐欏叆 unavailableFields銆?, '', 1600, 'production', 0, 'system'),
('review', 'v1', '濂借瘎鐢熸垚', '璇峰熀浜庨厭搴楃湡瀹炲崠鐐圭敓鎴愯嚜鐒跺彲淇＄殑濂借瘎妯℃澘锛屼笉寰楄櫄鏋勪綋楠屻€?, '', 600, 'production', 0, 'system'),
('reply', 'v1', '鍥炶瘎璇濇湳', '璇锋牴鎹敤鎴疯瘎浠峰唴瀹圭敓鎴愰厭搴楀畼鏂瑰洖澶嶏紝璇皵鐪熻瘹銆佸厠鍒躲€佹湁鏈嶅姟鎰忚瘑銆?, '', 600, 'production', 0, 'system')
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  content = VALUES(content),
  model_name = VALUES(model_name),
  max_tokens = VALUES(max_tokens),
  status = VALUES(status),
  gray_percent = VALUES(gray_percent);

INSERT INTO moderation_rules (name, rule_type, keywords, action, enabled)
VALUES
('缁濆鍖栨壙璇?, 'keyword', '淇濊瘉婊℃埧,涓€瀹氱垎鍗?100%鏈夋晥,绋宠禋涓嶈禂', 'review', 1),
('铏氬亣璁炬柦椋庨櫓', 'keyword', '绉佷汉娴锋哗,鏃犺竟娉虫睜,绫冲叾鏋?浜旀槦璁よ瘉', 'review', 1),
('浠锋牸杩濊鎻愮ず', 'keyword', '鍏ㄧ綉鏈€浣?浣庝环鍊鹃攢,鍒峰崟', 'block', 1)
ON DUPLICATE KEY UPDATE
  rule_type = VALUES(rule_type),
  keywords = VALUES(keywords),
  action = VALUES(action),
  enabled = VALUES(enabled);

INSERT INTO credit_ledger (tenant_id, type, amount, balance_after, module_key, module_name, detail, status)
SELECT 1, 'recharge', 12000, 12000, NULL, '濂楅璧犻€?, '澧為暱鐗堝垵濮嬪寲璧犻€佺畻鍔?, 'success'
WHERE NOT EXISTS (SELECT 1 FROM credit_ledger WHERE tenant_id = 1 AND type = 'recharge' AND detail = '澧為暱鐗堝垵濮嬪寲璧犻€佺畻鍔?);

INSERT INTO credit_ledger (tenant_id, type, amount, balance_after, module_key, module_name, detail, status)
SELECT 2, 'recharge', 3000, 3000, NULL, '濂楅璧犻€?, '鏍囧噯鐗堝垵濮嬪寲璧犻€佺畻鍔?, 'success'
WHERE NOT EXISTS (SELECT 1 FROM credit_ledger WHERE tenant_id = 2 AND type = 'recharge' AND detail = '鏍囧噯鐗堝垵濮嬪寲璧犻€佺畻鍔?);

INSERT INTO credit_ledger (tenant_id, type, amount, balance_after, module_key, module_name, detail, status)
SELECT 3, 'recharge', 300, 300, NULL, '濂楅璧犻€?, '浣撻獙鐗堝垵濮嬪寲璧犻€佺畻鍔?, 'success'
WHERE NOT EXISTS (SELECT 1 FROM credit_ledger WHERE tenant_id = 3 AND type = 'recharge' AND detail = '浣撻獙鐗堝垵濮嬪寲璧犻€佺畻鍔?);

-- =====================================================
-- 7. 鍒濆鍖栨牎楠?-- =====================================================

DROP PROCEDURE IF EXISTS sushijia_add_column_if_missing;
SET FOREIGN_KEY_CHECKS = 1;

-- Occupancy image recognition module. Kept as an additive migration so older
-- garbled seed text above does not need to be rewritten.
INSERT INTO billing_rules (module_key, module_name, board, cost, est_cost_rmb, enabled, sort_order)
VALUES ('occupancy_image', '鎴挎€佸鍏?, 'ai', 2, 0.0500, 1, 13)
ON DUPLICATE KEY UPDATE
  module_name = VALUES(module_name),
  board = VALUES(board),
  cost = VALUES(cost),
  est_cost_rmb = VALUES(est_cost_rmb),
  enabled = VALUES(enabled),
  sort_order = VALUES(sort_order);

INSERT IGNORE INTO ai_agent_bindings
  (module_key, provider, app_type, app_id, api_key, app_name, endpoint, bot_id, bot_api_key, bot_name, enabled)
VALUES
  ('occupancy_image', 'dify', 'workflow', '', '', '瀹胯瘑瀹舵埧鎬佸浘鐗囪瘑鍒憳', '', 'workflow', '', '瀹胯瘑瀹舵埧鎬佸浘鐗囪瘑鍒憳', 0);

UPDATE ai_agent_bindings
SET app_type = 'workflow',
    bot_id = 'workflow',
    app_name = '瀹胯瘑瀹舵埧鎬佸浘鐗囪瘑鍒憳',
    bot_name = '瀹胯瘑瀹舵埧鎬佸浘鐗囪瘑鍒憳',
    input_schema = JSON_OBJECT(
      'commonContextJson','string',
      'businessParamsJson','string',
      'knowledgeContextJson','string',
      'message','string',
      'image','file',
      'sourceFileName','string',
      'sourceFileType','string',
      'sourceFileSize','number',
      'uploadedFileName','string'
    )
WHERE module_key = 'occupancy_image';

INSERT INTO ai_capabilities (capability_key, capability_name, description, enabled, sort_order)
VALUES ('image_recognition', '鍥剧墖璇嗗埆', '璇嗗埆缁忚惀鍥剧墖骞惰緭鍑虹粨鏋勫寲鏁版嵁', 1, 7)
ON DUPLICATE KEY UPDATE
  capability_name = VALUES(capability_name),
  description = VALUES(description),
  enabled = VALUES(enabled),
  sort_order = VALUES(sort_order);

INSERT INTO ai_agent_configs
  (tenant_id, module_key, module_name, capability_key, provider_key, app_id, workflow_id, api_key_encrypted, endpoint, call_mode, input_schema, output_schema, output_parser, knowledge_policy, enabled)
VALUES
  (0, 'occupancy_image', '瀹胯瘑瀹舵埧鎬佸浘鐗囪瘑鍒憳', 'image_recognition', 'dify', '', '', '', '', 'workflow',
   JSON_OBJECT('commonContextJson','string','businessParamsJson','string','knowledgeContextJson','string','message','string','image','file','sourceFileName','string','sourceFileType','string','sourceFileSize','number','uploadedFileName','string'),
   JSON_OBJECT('records','array','warnings','array'), 'json', 'none', 0)
ON DUPLICATE KEY UPDATE
  module_name = VALUES(module_name),
  capability_key = VALUES(capability_key),
  provider_key = VALUES(provider_key),
  call_mode = VALUES(call_mode),
  input_schema = VALUES(input_schema),
  output_schema = VALUES(output_schema),
  output_parser = VALUES(output_parser),
  knowledge_policy = VALUES(knowledge_policy);

UPDATE tenant_plans
SET enabled_modules = CONCAT(enabled_modules, ',occupancy_image')
WHERE FIND_IN_SET('occupancy_image', enabled_modules) = 0
  AND code IN ('basic', 'pro', 'flagship');

SELECT 'init.sql completed' AS message;
