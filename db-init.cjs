// SQLite 建表 + 种子数据
// 被 deploy.cjs require

module.exports = function(db) {
  // ====== 20 张表 ======
  db.run(`CREATE TABLE tenants(id INTEGER PRIMARY KEY,name TEXT,type TEXT,city TEXT,total_rooms INTEGER,tags TEXT,target_audience TEXT,nearby TEXT,contact_phone TEXT,tier TEXT DEFAULT 'trial',status TEXT DEFAULT 'active',balance INTEGER DEFAULT 0,alert_threshold INTEGER DEFAULT 500,melt_threshold INTEGER DEFAULT 0,qps_limit INTEGER DEFAULT 5,created_at TEXT,updated_at TEXT)`)
  db.run(`CREATE TABLE tenant_branches(id INTEGER PRIMARY KEY,tenant_id INTEGER,name TEXT,city TEXT,allocated_quota INTEGER DEFAULT 0,consumed INTEGER DEFAULT 0,manager_phone TEXT,status TEXT DEFAULT 'active')`)
  db.run(`CREATE TABLE admins(id INTEGER PRIMARY KEY,email TEXT UNIQUE,name TEXT,password_hash TEXT,role TEXT,status TEXT DEFAULT 'active',created_at TEXT)`)
  db.run(`CREATE TABLE hotel_staff(id INTEGER PRIMARY KEY,tenant_id INTEGER,name TEXT,phone TEXT UNIQUE,role TEXT,avatar TEXT DEFAULT '',password_hash TEXT,created_at TEXT)`)
  db.run(`CREATE TABLE room_types(id INTEGER PRIMARY KEY,tenant_id INTEGER,name TEXT,base_price REAL,count INTEGER,sort_order INTEGER DEFAULT 0)`)
  db.run(`CREATE TABLE room_status(id INTEGER PRIMARY KEY,tenant_id INTEGER,room_type_id INTEGER,room_number TEXT,status TEXT DEFAULT 'free',updated_at TEXT)`)
  db.run(`CREATE TABLE future_room_status(id INTEGER PRIMARY KEY,tenant_id INTEGER,date TEXT,room_type_name TEXT,occupied INTEGER DEFAULT 0,available INTEGER DEFAULT 0,overbooked INTEGER DEFAULT 0)`)
  db.run(`CREATE TABLE guests(id INTEGER PRIMARY KEY,tenant_id INTEGER,room_type_id INTEGER,room_number TEXT,guest_type TEXT,source TEXT,nights INTEGER DEFAULT 1,checkin_date TEXT,checkout_date TEXT,status TEXT DEFAULT 'staying',created_at TEXT)`)
  db.run(`CREATE TABLE billing_rules(id INTEGER PRIMARY KEY,module_key TEXT UNIQUE,module_name TEXT,board TEXT,cost INTEGER,est_cost_rmb REAL DEFAULT 0,enabled INTEGER DEFAULT 1,sort_order INTEGER DEFAULT 0)`)
  db.run(`CREATE TABLE recharge_packages(id INTEGER PRIMARY KEY,name TEXT,credits INTEGER,price_rmb REAL,applicable_tiers TEXT,enabled INTEGER DEFAULT 1)`)
  db.run(`CREATE TABLE credit_ledger(id INTEGER PRIMARY KEY,tenant_id INTEGER,type TEXT,amount INTEGER,balance_after INTEGER,module_key TEXT,module_name TEXT,detail TEXT,status TEXT DEFAULT 'success',created_at TEXT)`)
  db.run(`CREATE TABLE prompt_templates(id INTEGER PRIMARY KEY,module_key TEXT,version TEXT,title TEXT,content TEXT,model_name TEXT DEFAULT 'claude-sonnet-4-6',max_tokens INTEGER DEFAULT 1000,status TEXT DEFAULT 'draft',gray_percent INTEGER DEFAULT 0,created_by TEXT,created_at TEXT)`)
  db.run(`CREATE TABLE style_library(id INTEGER PRIMARY KEY,name TEXT,scope TEXT DEFAULT 'public',tenant_id INTEGER,prompt_segment TEXT,feedback_score REAL,usage_count INTEGER DEFAULT 0,enabled INTEGER DEFAULT 1,created_at TEXT)`)
  db.run(`CREATE TABLE module_style_binding(id INTEGER PRIMARY KEY,module_key TEXT,style_id INTEGER)`)
  db.run(`CREATE TABLE content_tasks(id INTEGER PRIMARY KEY,tenant_id INTEGER,module_key TEXT,input_params TEXT,status TEXT DEFAULT 'pending',result_id INTEGER,error_msg TEXT,cost_credits INTEGER DEFAULT 0,created_at TEXT,completed_at TEXT)`)
  db.run(`CREATE TABLE content_results(id INTEGER PRIMARY KEY,task_id INTEGER,content TEXT,tokens_used INTEGER,moderated INTEGER DEFAULT 0,moderation_detail TEXT,created_at TEXT)`)
  db.run(`CREATE TABLE content_feedback(id INTEGER PRIMARY KEY,tenant_id INTEGER,module_key TEXT,prompt_version TEXT,style_id INTEGER,rating TEXT,content_snippet TEXT,created_at TEXT)`)
  db.run(`CREATE TABLE moderation_rules(id INTEGER PRIMARY KEY,name TEXT,rule_type TEXT,keywords TEXT,action TEXT DEFAULT 'block',hit_count_30d INTEGER DEFAULT 0,enabled INTEGER DEFAULT 1,created_at TEXT)`)
  db.run(`CREATE TABLE moderation_hits(id INTEGER PRIMARY KEY,tenant_id INTEGER,rule_id INTEGER,module_key TEXT,content_snippet TEXT,status TEXT DEFAULT 'pending',reviewed_by INTEGER,reviewed_at TEXT,created_at TEXT)`)
  db.run(`CREATE TABLE marketing_plans(id INTEGER PRIMARY KEY,tenant_id INTEGER,name TEXT,festival TEXT,status TEXT DEFAULT 'draft',hotel_name TEXT,period TEXT,target TEXT,tags TEXT,kpis TEXT,phases TEXT,channels TEXT,pricings TEXT,activities TEXT,alert_note TEXT,alerts TEXT,created_at TEXT,updated_at TEXT)`)
  db.run(`CREATE TABLE audit_logs(id INTEGER PRIMARY KEY,operator_type TEXT,operator_id INTEGER,operator_name TEXT,action TEXT,detail TEXT,target_tenant_id INTEGER,created_at TEXT)`)
  db.run(`CREATE TABLE api_call_logs(id INTEGER PRIMARY KEY,tenant_id INTEGER,module_key TEXT,duration_ms INTEGER,status TEXT,error_msg TEXT,created_at TEXT)`)

  // ====== 种子数据 ======
  db.run(`INSERT INTO tenants(id,name,type,city,total_rooms,tags,target_audience,nearby,contact_phone,tier,status,balance) VALUES
    (1,'松间·山野民宿','精品民宿','浙江·莫干山',12,'竹林景观、私汤温泉、无边泳池、有机早餐','面向长三角城市中产','距莫干山景区5分钟','13800000000','pro','active',1240),
    (2,'清风客栈','精品民宿','云南·大理',8,'洱海景观、白族庭院、手工扎染','面向自由行年轻客群','距古城5分钟','13800000001','pro','active',2150),
    (3,'海岸线度假酒店','度假酒店','福建·厦门',24,'海景房、无边泳池、海鲜餐厅','中高端度假','距鼓浪屿码头10分钟',NULL,'basic','active',8900),
    (4,'古城拾光民宿','精品民宿','山西·平遥',6,'明清老宅、古城文化','文化旅游爱好者','平遥古城南大街',NULL,'trial','active',480),
    (5,'观山雅集','精品民宿','四川·峨眉山',10,'峨眉山景、茶园禅修','康养度假','峨眉山景区入口',NULL,'basic','warning',0)`)

  db.run(`INSERT INTO hotel_staff(tenant_id,name,phone,role) VALUES
    (1,'张店长','13800000000','admin'),
    (2,'李小明','13800000001','manager'),
    (1,'王小红','13800000002','front_desk'),
    (1,'赵小丽','13800000003','marketing')`)

  db.run(`INSERT INTO room_types(tenant_id,name,base_price,count) VALUES
    (1,'竹语大床房',888,4),(1,'山景套房',1388,5),(1,'亲子家庭房',1688,3)`)

  db.run(`INSERT INTO room_status(tenant_id,room_type_id,room_number,status) VALUES
    (1,1,'101','sold'),(1,1,'102','sold'),(1,1,'103','free'),(1,1,'104','dirty'),
    (1,2,'201','sold'),(1,2,'202','sold'),(1,2,'203','sold'),(1,2,'204','sold'),(1,2,'205','free'),
    (1,3,'301','sold'),(1,3,'302','free'),(1,3,'303','repair')`)

  db.run(`INSERT INTO guests(tenant_id,room_type_id,room_number,guest_type,source,nights,checkin_date,checkout_date,status) VALUES
    (1,1,'101','couple','小红书',2,'2026-06-11','2026-06-13','staying'),
    (1,2,'203','family','美团',3,'2026-06-10','2026-06-13','staying'),
    (1,3,'301','family','携程',1,'2026-06-12','2026-06-13','checking_in')`)

  db.run(`INSERT INTO billing_rules(module_key,module_name,board,cost) VALUES
    ('room_status','房态AI识别','店长看板',0),('brain','运营智慧大脑','店长看板',5),('pricing','智能定价建议','店长看板',0),
    ('wechat','朋友圈文案','内容发布',8),('xhs','小红书图文','内容发布',10),('video','抖音口播','内容发布',12),
    ('poster','营销海报','内容发布',30),('repair','AI修图','内容发布',20),('article','公众号推文','内容发布',15),
    ('review','个性化好评模板','前台客服',6),('reply','AI回评话术','前台客服',8)`)

  db.run(`INSERT INTO recharge_packages(name,credits,price_rmb,applicable_tiers) VALUES
    ('体验包',500,150,'trial'),('标准包',2000,560,'basic,pro'),('畅享包',5000,1300,'pro,flagship'),('连锁包',20000,4800,'flagship')`)

  db.run(`INSERT INTO credit_ledger(tenant_id,type,amount,balance_after,module_name,detail,created_at) VALUES
    (1,'recharge',500,1246,'充值','月度套餐·500算力','2026-06-11 09:15'),
    (1,'consume',-10,1236,'小红书','生成选题','2026-06-11 10:08'),
    (1,'consume',-8,1228,'朋友圈','三档文案','2026-06-11 14:22'),
    (1,'consume',-30,746,'海报','端午海报','2026-06-10 20:45'),
    (1,'consume',-12,776,'抖音口播','口播文案','2026-06-10 16:30')`)

  db.run(`INSERT INTO style_library(name,scope,tenant_id,prompt_segment) VALUES
    ('治愈温暖','public',NULL,'温暖治愈风格，多感官描写，句子偏短，适当emoji'),
    ('活泼元气','public',NULL,'轻快俏皮有网感，短句排比，年轻客群'),
    ('轻奢精致','public',NULL,'优雅克制，少emoji，细节描写，高端调性'),
    ('故事叙事','public',NULL,'第一人称沉浸体验，时间线结构，留余韵'),
    ('竹林禅意','private',1,'禅意留白东方美学，自然意象，慢生活方式')`)

  db.run(`INSERT INTO marketing_plans(tenant_id,name,festival,status,hotel_name,period,target,tags,created_at) VALUES
    (1,'端午节完整营销方案','端午节','active','松间·山野民宿','D-7→假期末→收尾','端午3天出租率≥90%','["竹林景观","私汤温泉"]','2025-05-20')`)

  console.log('[DB] 22张表+种子数据 创建完成')
}
