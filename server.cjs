require('dotenv').config()
const http = require('http')
const { readFileSync, existsSync, writeFileSync } = require('fs')
const { join } = require('path')
const jwt = require('jsonwebtoken')
const { ProxyAgent, fetch: proxyFetch } = require('undici')

// 代理配置（用于访问 apimart.ai 等海外 API）
const HTTPS_PROXY = process.env.HTTPS_PROXY || 'http://127.0.0.1:7897'
const proxyAgent = new ProxyAgent(HTTPS_PROXY)

// ====== 配置 ======
const JWT_SECRET = process.env.JWT_SECRET || 'sushijia-dev-secret-change-in-prod-2026'
const DEMO_SMS_CODE = '123456'
const ALLOWED_ORIGINS = (process.env.CORS_ORIGINS || 'http://localhost:5173,http://localhost:3000,http://localhost:8080').split(',').map(s => s.trim())

const DB_PATH = join(__dirname, 'sushijia.db')

// ====== 数据库 ======
async function initDB() {
  const initSqlJs = require('sql.js')
  const SQL = await initSqlJs()
  let db
  if (existsSync(DB_PATH)) {
    db = new SQL.Database(readFileSync(DB_PATH))
  } else {
    db = new SQL.Database()
    createTables(db)
    seedData(db)
    saveDB(db)
  }
  return db
}

function saveDB(db) {
  const data = db.export()
  writeFileSync(DB_PATH, Buffer.from(data))
}

function createTables(db) {
  db.run(`CREATE TABLE tenants(id INTEGER PRIMARY KEY,name TEXT,type TEXT,city TEXT,total_rooms INTEGER,tags TEXT,target_audience TEXT,nearby TEXT,contact_phone TEXT,tier TEXT DEFAULT 'trial',status TEXT DEFAULT 'active',balance INTEGER DEFAULT 0,alert_threshold INTEGER DEFAULT 500,melt_threshold INTEGER DEFAULT 0,qps_limit INTEGER DEFAULT 5,created_at TEXT,updated_at TEXT)`)
  db.run(`CREATE TABLE hotel_staff(id INTEGER PRIMARY KEY,tenant_id INTEGER,name TEXT,phone TEXT UNIQUE,role TEXT,avatar TEXT DEFAULT '',password_hash TEXT,created_at TEXT)`)
  db.run(`CREATE TABLE room_types(id INTEGER PRIMARY KEY,tenant_id INTEGER,name TEXT,base_price REAL,count INTEGER,sort_order INTEGER DEFAULT 0)`)
  db.run(`CREATE TABLE room_status(id INTEGER PRIMARY KEY,tenant_id INTEGER,room_type_id INTEGER,room_number TEXT,status TEXT DEFAULT 'free',updated_at TEXT)`)
  db.run(`CREATE TABLE future_room_status(id INTEGER PRIMARY KEY,tenant_id INTEGER,date TEXT,room_type_name TEXT,occupied INTEGER DEFAULT 0,available INTEGER DEFAULT 0,overbooked INTEGER DEFAULT 0)`)
  db.run(`CREATE TABLE guests(id INTEGER PRIMARY KEY,tenant_id INTEGER,room_type_id INTEGER,room_number TEXT,guest_type TEXT,source TEXT,nights INTEGER DEFAULT 1,checkin_date TEXT,checkout_date TEXT,status TEXT DEFAULT 'staying',created_at TEXT)`)
  db.run(`CREATE TABLE billing_rules(id INTEGER PRIMARY KEY,module_key TEXT UNIQUE,module_name TEXT,board TEXT,cost INTEGER,est_cost_rmb REAL DEFAULT 0,enabled INTEGER DEFAULT 1,sort_order INTEGER DEFAULT 0)`)
  db.run(`CREATE TABLE recharge_packages(id INTEGER PRIMARY KEY,name TEXT,credits INTEGER,price_rmb REAL,applicable_tiers TEXT,enabled INTEGER DEFAULT 1)`)
  db.run(`CREATE TABLE credit_ledger(id INTEGER PRIMARY KEY,tenant_id INTEGER,type TEXT,amount INTEGER,balance_after INTEGER,module_key TEXT,module_name TEXT,detail TEXT,status TEXT DEFAULT 'success',created_at TEXT)`)
  db.run(`CREATE TABLE prompt_templates(id INTEGER PRIMARY KEY,module_key TEXT,version TEXT,title TEXT,content TEXT,model_name TEXT DEFAULT 'qwen-plus',max_tokens INTEGER DEFAULT 1000,status TEXT DEFAULT 'draft',gray_percent INTEGER DEFAULT 0,created_by TEXT,created_at TEXT)`)
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
  db.run(`CREATE TABLE system_config(key TEXT PRIMARY KEY,value TEXT,label TEXT,updated_at TEXT)`)
  console.log('[DB] 21张表创建完成')
}

function seedData(db) {
  db.run(`INSERT INTO tenants(id,name,type,city,total_rooms,tags,target_audience,nearby,contact_phone,tier,status,balance) VALUES
    (1,'松间·山野民宿','精品民宿','浙江·莫干山',12,'竹林景观、私汤温泉、无边泳池、有机早餐','面向长三角城市中产','距莫干山景区5分钟','13800000000','pro','active',12400),
    (2,'清风客栈','精品民宿','云南·大理',8,'洱海景观、白族庭院、手工扎染','面向自由行年轻客群','距古城5分钟','13800000001','pro','active',21500),
    (3,'海岸线度假酒店','度假酒店','福建·厦门',24,'海景房、无边泳池、海鲜餐厅','中高端度假','距鼓浪屿码头10分钟',NULL,'basic','active',89000),
    (4,'古城拾光民宿','精品民宿','山西·平遥',6,'明清老宅、古城文化','文化旅游爱好者','平遥古城南大街',NULL,'trial','active',4800),
    (5,'观山雅集','精品民宿','四川·峨眉山',10,'峨眉山景、茶园禅修','康养度假','峨眉山景区入口',NULL,'basic','warning',0)`)
  db.run(`INSERT INTO hotel_staff(tenant_id,name,phone,role) VALUES
    (1,'张店长','13800000000','admin'),
    (2,'李小明','13800000001','manager'),
    (1,'王小红','13800000002','front_desk'),
    (1,'赵小丽','13800000003','marketing')`)
  db.run(`INSERT INTO room_types(tenant_id,name,base_price,count) VALUES(1,'竹语大床房',888,4),(1,'山景套房',1388,5),(1,'亲子家庭房',1688,3)`)
  db.run(`INSERT INTO room_status(tenant_id,room_type_id,room_number,status) VALUES
    (1,1,'101','sold'),(1,1,'102','sold'),(1,1,'103','free'),(1,1,'104','dirty'),
    (1,2,'201','sold'),(1,2,'202','sold'),(1,2,'203','sold'),(1,2,'204','sold'),(1,2,'205','free'),
    (1,3,'301','sold'),(1,3,'302','free'),(1,3,'303','repair')`)
  db.run(`INSERT INTO guests(tenant_id,room_type_id,room_number,guest_type,source,nights,checkin_date,checkout_date,status) VALUES
    (1,1,'101','couple','小红书',2,'2026-06-11','2026-06-13','staying'),
    (1,2,'203','family','美团',3,'2026-06-10','2026-06-13','staying'),
    (1,3,'301','family','携程',1,'2026-06-12','2026-06-13','checking_in')`)
  db.run(`INSERT INTO billing_rules(module_key,module_name,board,cost) VALUES
    ('room_status','房态AI识别','店长看板',0),
    ('brain','运营智慧大脑','店长看板',5),
    ('pricing','智能定价建议','店长看板',0),
    ('wechat','朋友圈文案','内容发布',8),
    ('xhs','小红书图文','内容发布',10),
    ('video','抖音口播','内容发布',12),
    ('poster','营销海报','内容发布',30),
    ('repair','AI修图','内容发布',20),
    ('article','公众号推文','内容发布',15),
    ('review','个性化好评模板','前台客服',6),
    ('reply','AI回评话术','前台客服',8)`)
  db.run(`INSERT INTO recharge_packages(name,credits,price_rmb,applicable_tiers) VALUES
    ('体验包',500,150,'trial'),('标准包',2000,560,'basic,pro'),('畅享包',5000,1300,'pro,flagship'),('连锁包',20000,4800,'flagship')`)
  db.run(`INSERT INTO credit_ledger(tenant_id,type,amount,balance_after,module_key,module_name,detail,created_at) VALUES
    (1,'recharge',5000,17400,'recharge','充值','月度套餐·5000算力','2026-06-11 09:15'),
    (1,'consume',-100,17300,'wechat','朋友圈','三档文案生成','2026-06-11 10:08'),
    (1,'consume',-80,17220,'xhs','小红书','选题生成','2026-06-11 14:22'),
    (1,'consume',-300,16920,'poster','海报','端午海报','2026-06-10 20:45'),
    (1,'consume',-120,16800,'video','抖音口播','口播文案','2026-06-10 16:30')`)
  db.run(`INSERT INTO style_library(name,scope,tenant_id,prompt_segment) VALUES
    ('治愈温暖','public',NULL,'温暖治愈风格，用柔和的语言和画面感描写...'),
    ('活泼元气','public',NULL,'轻快俏皮风格，多用感叹号和emoji...'),
    ('轻奢精致','public',NULL,'优雅质感风格，注重细节描写和品味感...'),
    ('故事叙事','public',NULL,'第一人称沉浸风格，以故事线展开...'),
    ('竹林禅意','private',1,'禅意留白东方美学，以竹、雾、静为核心意象...')`)
  db.run(`INSERT INTO marketing_plans(tenant_id,name,festival,status,hotel_name,period,target,tags,created_at) VALUES
    (1,'端午节完整营销方案','端午节','active','松间·山野民宿','D-7→假期末→收尾','端午3天出租率≥90%','["竹林景观","私汤温泉"]','2025-05-20')`)
  db.run(`INSERT INTO system_config(key,value,label) VALUES
    ('sms_provider','','短信服务商(aliyun/tencent)'),
    ('sms_access_key','','短信AccessKey'),
    ('sms_secret_key','','短信SecretKey'),
    ('sms_sign_name','','短信签名'),
    ('sms_template_code','','短信模板ID'),
    ('ai_provider','dashscope','AI服务商(dashscope/openai/deepseek)'),
    ('ai_api_key','','AI API Key'),
    ('ai_model','qwen-plus','AI模型'),
    ('ai_base_url','https://dashscope.aliyuncs.com/compatible-mode/v1','AI Base URL'),
    ('ai_max_tokens','4000','最大Token数')`)
  console.log('[DB] 种子数据写入完成')
}

// ====== 工具函数 ======
function jwtToken(tenantId, staffId, role) {
  return jwt.sign({ tenant_id: tenantId, staff_id: staffId, role }, JWT_SECRET, { expiresIn: '7d' })
}

function authMiddleware(req) {
  const authHeader = req.headers.authorization
  if (!authHeader || !authHeader.startsWith('Bearer ')) return null
  try {
    return jwt.verify(authHeader.slice(7), JWT_SECRET)
  } catch {
    return null
  }
}

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type,Authorization'
}

function json(res, data, code = 200, msg = 'success') {
  res.writeHead(code, { 'Content-Type': 'application/json', ...CORS })
  res.end(JSON.stringify({ code, message: msg, data, timestamp: Date.now() }))
}

function readBody(req) {
  return new Promise(resolve => {
    let body = ''
    req.on('data', d => body += d)
    req.on('end', () => { try { resolve(JSON.parse(body)) } catch { resolve({}) } })
  })
}

function findOne(db, table, field, value) {
  try {
    const stmt = db.prepare(`SELECT * FROM ${table} WHERE ${field} = ?`)
    stmt.bind([value])
    if (stmt.step()) {
      const obj = stmt.getAsObject()
      stmt.free()
      return obj
    }
    stmt.free()
  } catch (e) { console.error('[DB] findOne error:', e.message) }
  return null
}

function findAll(db, table, where = '', params = []) {
  try {
    const sql = where ? `SELECT * FROM ${table} WHERE ${where}` : `SELECT * FROM ${table}`
    const results = []
    const stmt = db.prepare(sql)
    if (params.length > 0) stmt.bind(params)
    while (stmt.step()) {
      results.push(stmt.getAsObject())
    }
    stmt.free()
    return results
  } catch (e) {
    console.error('[DB] findAll error:', e.message)
    return []
  }
}

// ====== 通义千问 AI 调用 ======
async function getAIConfig(db) {
  const rows = findAll(db, 'system_config')
  const map = {}
  rows.forEach(r => { map[r.key] = r.value })
  return {
    provider: map.ai_provider || 'dashscope',
    apiKey: process.env.DASHSCOPE_API_KEY || map.ai_api_key || '',
    model: map.ai_model || 'qwen-plus',
    baseUrl: map.ai_base_url || 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    maxTokens: parseInt(map.ai_max_tokens || '4000', 10),
  }
}

async function callAI(config, systemPrompt, userMessage) {
  if (!config.apiKey) {
    throw new Error('AI API Key 未配置，请在管理后台或 .env 中设置 DASHSCOPE_API_KEY')
  }
  const resp = await fetch(`${config.baseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${config.apiKey}`,
    },
    body: JSON.stringify({
      model: config.model,
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: userMessage },
      ],
      max_tokens: config.maxTokens,
      temperature: 0.8,
    }),
  })
  if (!resp.ok) {
    const errText = await resp.text()
    console.error('[AI] API error:', resp.status, errText)
    throw new Error(`AI 服务返回错误: ${resp.status}`)
  }
  const data = await resp.json()
  return data.choices?.[0]?.message?.content || 'AI 未返回有效内容'
}

// ====== apimart.ai 图片生成（GPT-Image-2） ======
const APIMART_API_KEY = process.env.APIMART_API_KEY || ''
const APIMART_BASE = 'https://api.apimart.ai/v1'

async function submitImageGen(prompt, size = '3:4', resolution = '2k') {
  if (!APIMART_API_KEY) throw new Error('APIMART_API_KEY 未配置')
  const resp = await proxyFetch(`${APIMART_BASE}/images/generations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${APIMART_API_KEY}` },
    body: JSON.stringify({ model: 'gpt-image-2', prompt, n: 1, size, resolution }),
    dispatcher: proxyAgent,
  })
  if (!resp.ok) {
    const errText = await resp.text()
    throw new Error(`图片生成 API 错误: ${resp.status} ${errText}`)
  }
  const data = await resp.json()
  if (data.error) throw new Error(data.error.message || '图片生成失败')
  const taskId = data.data?.[0]?.task_id
  if (!taskId) throw new Error('未返回 task_id')
  return taskId
}

async function pollImageTask(taskId, maxAttempts = 24) {
  for (let i = 0; i < maxAttempts; i++) {
    await new Promise(r => setTimeout(r, 5000))
    try {
      const resp = await proxyFetch(`${APIMART_BASE}/tasks/${taskId}`, {
        headers: { 'Authorization': `Bearer ${APIMART_API_KEY}` },
        dispatcher: proxyAgent,
      })
      if (!resp.ok) continue
      const data = await resp.json()
      const status = data.data?.status
      if (status === 'completed') {
        const url = data.data?.result?.images?.[0]?.url?.[0]
        if (url) return url
        throw new Error('完成但无图片 URL')
      }
      if (status === 'failed') {
        throw new Error(data.data?.error?.message || '图片生成失败')
      }
    } catch (err) {
      if (err.message.includes('完成但无') || err.message.includes('失败')) throw err
    }
  }
  throw new Error('图片生成超时')
}

// ====== 主函数 ======
(async () => {
  const db = await initDB()

  const server = http.createServer(async (req, res) => {
    if (req.method === 'OPTIONS') { res.writeHead(204, CORS); res.end(); return }
    const url = new URL(req.url, `http://${req.headers.host}`)
    const path = url.pathname

    console.log(`[${new Date().toISOString()}] ${req.method} ${path}`)

    // ====== 公开路由（不需要鉴权） ======

    // 短信验证码（演示模式）
    if (path === '/api/auth/sms/send' && req.method === 'POST') {
      return json(res, '验证码已发送（演示模式，验证码：123456）')
    }

    // 手机号登录
    if (path === '/api/auth/login/phone' && req.method === 'POST') {
      const { phone, code } = await readBody(req)
      if (code && code !== DEMO_SMS_CODE) {
        return json(res, null, 401, '验证码错误')
      }
      const staff = findOne(db, 'hotel_staff', 'phone', phone)
      if (!staff) return json(res, null, 401, '手机号未注册')
      return json(res, {
        accessToken: jwtToken(staff.tenant_id, staff.id, staff.role),
        refreshToken: jwtToken(staff.tenant_id, staff.id, staff.role),
        role: staff.role,
        name: staff.name,
        tenantId: staff.tenant_id,
      })
    }

    // Token 刷新
    if (path === '/api/auth/token/refresh' && req.method === 'POST') {
      const user = authMiddleware(req)
      if (!user) return json(res, null, 401, '无效的 Token')
      return json(res, { accessToken: jwtToken(user.tenant_id, user.staff_id, user.role) })
    }

    // ====== 需要鉴权的路由 ======
    const user = authMiddleware(req)
    if (!user) return json(res, null, 401, '请先登录')
    const tenantId = user.tenant_id
    const staffId = user.staff_id
    const role = user.role

    // ====== 大盘 ======
    if (path === '/api/hotel/dashboard' && req.method === 'GET') {
      const tenant = findOne(db, 'tenants', 'id', tenantId)
      const roomTypes = findAll(db, 'room_types', 'tenant_id = ?', [tenantId])
      const statuses = findAll(db, 'room_status', 'tenant_id = ?', [tenantId])
      const guests = findAll(db, 'guests', 'tenant_id = ? AND status IN (?,?)', [tenantId, 'staying', 'checking_in'])
      const futureSt = findAll(db, 'future_room_status', 'tenant_id = ?', [tenantId])

      const totalRooms = roomTypes.reduce((s, r) => s + r.count, 0)
      const totalSold = statuses.filter(r => r.status === 'sold').length
      const priceMap = Object.fromEntries(roomTypes.map(r => [r.id, r.base_price]))
      const totalRevenue = statuses.filter(r => r.status === 'sold').reduce((s, r) => s + (priceMap[r.room_type_id] || 0), 0)
      const occupancyRate = totalRooms > 0 ? Math.round(totalSold / totalRooms * 100) : 0
      const revpar = totalRooms > 0 ? Math.round(totalRevenue / totalRooms) : 0

      const roomTypeStats = roomTypes.map(rt => {
        const rooms = statuses.filter(s => s.room_type_id === rt.id)
        return { id: rt.id, name: rt.name, basePrice: rt.base_price, total: rooms.length, sold: rooms.filter(s => s.status === 'sold').length, free: rooms.filter(s => s.status === 'free').length, dirty: rooms.filter(s => s.status === 'dirty').length, repair: rooms.filter(s => s.status === 'repair').length }
      })

      const futureDays = {}
      futureSt.forEach(f => {
        const key = f.date
        if (!futureDays[key]) futureDays[key] = { date: f.date, totalOccupied: 0, totalAvailable: 0 }
        futureDays[key].totalOccupied += f.occupied
        futureDays[key].totalAvailable += f.available
      })
      const nowDate = new Date()
      for (let i = 0; i < 7; i++) {
        const d = new Date(nowDate)
        d.setDate(d.getDate() + i)
        const key = d.toISOString().slice(5, 10)
        if (!futureDays[key]) futureDays[key] = { date: key, totalOccupied: Math.floor(Math.random() * 20) + 5, totalAvailable: 50 }
      }

      return json(res, { config: tenant, kpi: { occupancyRate, totalSold, totalRooms, freeCount: totalRooms - totalSold, totalRevenue, revpar }, roomTypeStats, futureStatus: Object.values(futureDays).slice(0, 7), guests: guests.map(g => ({ roomNumber: g.room_number, guestType: g.guest_type, source: g.source, nights: g.nights, checkoutDate: g.checkout_date })) })
    }

    // ====== 酒店配置 ======
    if (path === '/api/hotel/config' && req.method === 'GET') {
      return json(res, findOne(db, 'tenants', 'id', tenantId))
    }
    if (path === '/api/hotel/config' && req.method === 'PUT') {
      const body = await readBody(req)
      const allowed = ['name', 'type', 'city', 'total_rooms', 'tags', 'target_audience', 'nearby', 'contact_phone']
      const fields = Object.keys(body).filter(k => allowed.includes(k))
      if (fields.length === 0) return json(res, '无需更新')
      const sets = fields.map(k => `${k} = ?`).join(', ')
      const values = fields.map(k => body[k])
      db.run(`UPDATE tenants SET ${sets}, updated_at = datetime('now','localtime') WHERE id = ?`, [...values, tenantId])
      saveDB(db)
      return json(res, '保存成功')
    }

    // ====== 房型 ======
    if (path === '/api/hotel/rooms' && req.method === 'GET') {
      return json(res, findAll(db, 'room_types', 'tenant_id = ?', [tenantId]))
    }
    if (path === '/api/hotel/rooms' && req.method === 'PUT') {
      const rooms = await readBody(req)
      db.run('DELETE FROM room_types WHERE tenant_id = ?', [tenantId])
      db.run('DELETE FROM room_status WHERE tenant_id = ?', [tenantId])
      if (Array.isArray(rooms)) {
        rooms.forEach((r, i) => {
          db.run('INSERT INTO room_types(tenant_id, name, base_price, count, sort_order) VALUES(?,?,?,?,?)',
            [tenantId, r.name, r.basePrice || r.base_price || 0, r.count || 0, i])
        })
      }
      saveDB(db)
      return json(res, '保存成功')
    }

    // ====== 算力 ======
    if (path === '/api/hotel/credits/balance' && req.method === 'GET') {
      const t = findOne(db, 'tenants', 'id', tenantId)
      const today = new Date().toISOString().slice(0, 10)
      const result = db.exec(`SELECT IFNULL(SUM(ABS(amount)),0) FROM credit_ledger WHERE tenant_id = ? AND type = 'consume' AND created_at LIKE ?`)
      // sql.js doesn't support LIKE with params easily, use direct query with sanitized value
      const safeToday = today.replace(/[^0-9-]/g, '')
      const res2 = db.exec(`SELECT IFNULL(SUM(ABS(amount)),0) FROM credit_ledger WHERE tenant_id = ${parseInt(tenantId)} AND type = 'consume' AND date(created_at) = '${safeToday}'`)
      const todayConsume = res2.length > 0 ? res2[0].values[0][0] : 0
      return json(res, { balance: t.balance, todayConsume })
    }
    if (path === '/api/hotel/credits/ledger' && req.method === 'GET') {
      return json(res, findAll(db, 'credit_ledger', 'tenant_id = ?', [tenantId]).reverse())
    }

    // ====== 定价 ======
    if (path === '/api/hotel/pricing/recommend' && req.method === 'POST') {
      const { holiday, occupancy, weather, competition } = await readBody(req)
      const holidayMap = { big: 1.28, small: 1.15, weekend: 1.1, emotion: 1.2 }; const hMul = holidayMap[holiday] || 1
      const occMap = { '90+': 0.10, '70-90': 0.05, '50-70': 0, '30-50': -0.08, '30-': -0.18 }; const oAdj = occMap[occupancy] || 0
      const wMap = { sunny: 0, rain: -0.08, heavy: -0.15, extreme: -0.22 }; const wAdj = wMap[weather] || 0
      const cMap = { none: 0.05, light: 0, medium: -0.10, high: -0.15 }; const cAdj = cMap[competition] || 0
      const types = findAll(db, 'room_types', 'tenant_id = ?', [tenantId])
      const results = types.map(rt => {
        const adj = 1 + oAdj + wAdj + cAdj
        const rec = Math.round(rt.base_price * hMul * adj)
        return { roomId: rt.id, roomName: rt.name, basePrice: rt.base_price, recommendedPrice: rec, changePercent: Math.round((rec / rt.base_price - 1) * 100) }
      })
      const reasons = [`节假日类型倍率: ×${hMul}`]
      if (oAdj > 0) reasons.push(`入住率较高，上浮${Math.round(oAdj * 100)}%`)
      else if (oAdj < 0) reasons.push(`入住率偏低，下调${Math.round(Math.abs(oAdj) * 100)}%`)
      else reasons.push('入住率正常区间')
      return json(res, { results, reasons })
    }

    // ====== AI 内容生成（接入通义千问） ======
    if (path === '/api/content/generate' && req.method === 'POST') {
      const body = await readBody(req)
      const { module } = body
      const inputParams = body.params || {}
      const validModules = ['wechat', 'xhs', 'video', 'poster', 'article', 'review', 'reply', 'brain']
      if (!validModules.includes(module)) {
        return json(res, null, 400, `无效的模块: ${module}`)
      }

      const tenant = findOne(db, 'tenants', 'id', tenantId)
      const name = tenant ? tenant.name : '民宿'
      const tags = tenant ? tenant.tags : ''

      // 扣算力
      const ruleResult = db.exec(`SELECT cost FROM billing_rules WHERE module_key = ? AND enabled = 1`)
      // sql.js parameterized exec is tricky, use safe parseInt approach
      const safeModule = module.replace(/[^a-z_]/g, '')
      const costRows = db.exec(`SELECT cost FROM billing_rules WHERE module_key = '${safeModule}' AND enabled = 1`)
      const cost = costRows.length > 0 ? costRows[0].values[0][0] : 0

      if (cost > 0) {
        const t = findOne(db, 'tenants', 'id', tenantId)
        if (t.balance < cost) {
          return json(res, null, 402, `算力不足，需要 ${cost}，当前余额 ${t.balance}`)
        }
        db.run('UPDATE tenants SET balance = balance - ? WHERE id = ?', [cost, tenantId])
        db.run('INSERT INTO credit_ledger(tenant_id, type, amount, balance_after, module_key, module_name, detail, created_at) VALUES(?,?,?,(SELECT balance FROM tenants WHERE id=?),?,?,?,datetime("now","localtime"))',
          [tenantId, 'consume', -cost, tenantId, safeModule, module, 'AI生成'])
      }

      const now = new Date().toISOString().slice(0, 19).replace('T', ' ')
      db.run('INSERT INTO content_tasks(tenant_id, module_key, input_params, status, cost_credits, created_at) VALUES(?,?,?,?,?,?)',
        [tenantId, safeModule, JSON.stringify(inputParams), 'processing', cost, now])
      const taskId = db.exec('SELECT last_insert_rowid()')[0].values[0][0]
      saveDB(db)

      // 异步调用 AI
      ;(async () => {
        try {
          if (module === 'poster') {
            // 海报模块：使用 apimart.ai GPT-Image-2 生成图片
            const tenant = findOne(db, 'tenants', 'id', tenantId)
            const name = tenant ? tenant.name : '民宿'
            const city = tenant ? tenant.city : ''
            const hotelTags = tenant ? tenant.tags : ''
            const params = inputParams
            const theme = params.theme || ''
            const content = params.content || ''
            const style = params.style || 'chinese'

            const styleMap = {
              chinese: '中式禅意风格，深绿色调，东方美学，竹林元素，留白意境',
              minimal: '轻奢简约风格，米白色调，简洁线条，高级质感',
              dark: '深夜极简风格，深色调，星空元素，神秘高级感',
              warm: '温暖治愈风格，暖色调，柔和光线，温馨氛围',
            }

            const prompt = `为「${name}」(${city})设计一张营销海报。酒店特色：${hotelTags}。主题：${theme}。${content ? '副标题/内容：' + content + '。' : ''}视觉风格：${styleMap[style] || styleMap.chinese}。要求：专业酒店营销海报设计，精美排版，高品质画面。`

            console.log('[Poster] 提交图片生成:', prompt.slice(0, 80) + '...')
            const imgTaskId = await submitImageGen(prompt)
            console.log('[Poster] 图片任务ID:', imgTaskId, '开始轮询...')
            const imageUrl = await pollImageTask(imgTaskId)
            console.log('[Poster] 图片生成完成:', imageUrl.slice(0, 80) + '...')

            db.run('INSERT INTO content_results(task_id, content, created_at) VALUES(?,?,datetime("now","localtime"))', [taskId, imageUrl])
            db.run('UPDATE content_tasks SET status = ?, completed_at = datetime("now","localtime") WHERE id = ?', ['done', taskId])
            saveDB(db)
          } else {
            // 其他模块：使用通义千问生成文案
            const aiConfig = await getAIConfig(db)
            const tenant = findOne(db, 'tenants', 'id', tenantId)
            const name = tenant ? tenant.name : '民宿'
            const tags = tenant ? tenant.tags : ''
            const systemPrompt = `你是一位专业的酒店民宿营销文案专家。你正在为「${name}」创作文案。
酒店信息：
- 名称：${name}
- 类型：${tenant?.type || '精品民宿'}
- 城市：${tenant?.city || ''}
- 特色标签：${tags}
- 目标客群：${tenant?.target_audience || ''}
- 周边：${tenant?.nearby || ''}

请根据以上酒店信息，创作风格独特、与酒店特色紧密结合的营销文案。不要使用通用模板，要体现这家酒店的独特魅力。`

            const modulePrompts = {
              wechat: '请生成3条朋友圈文案，分别对应：早间种草型、午间互动型、晚间促单型。每条100字以内，包含合适的emoji和话题标签。',
              xhs: '请生成一篇小红书种草笔记，包含：吸引人的标题（20字以内）、正文（300字以内）、5-8个话题标签。要有真实体验感，避免广告感。',
              video: '请生成一个30秒短视频口播脚本，包含：开头hook（5秒吸引停留）、中间卖点展示（20秒）、结尾行动号召（5秒）。语言口语化，有节奏感。',
              article: '请生成一篇公众号推文（500-800字），有标题、引言、2-3个小标题段落、结尾引导。风格有质感，适合酒店品牌传播。',
              review: '请生成3条不同角度的个性化好评模板，分别模拟情侣、家庭、商务客人的口吻。每条100字以内，真实自然，不夸张。',
              reply: '请生成2条商家回复话术，分别针对：五星好评的感谢回复、需要挽回的差评回复。语气真诚温暖，体现酒店品牌调性。',
            }

            const userPrompt = modulePrompts[module] || '请生成营销文案'
            const content = await callAI(aiConfig, systemPrompt, userPrompt)

            // 如果请求配图，额外生成一张图片
            let finalContent = content
            const wantImage = inputParams.withImage && ['wechat', 'xhs', 'article'].includes(module)
            if (wantImage) {
              try {
                const imgPromptMap = {
                  wechat: `为「${name}」(${tenant?.city || ''})设计一张精美的朋友圈配图。特色：${tags}。风格：温馨自然，高品质民宿营销图片，光影柔和，构图精美。`,
                  xhs: `为「${name}」(${tenant?.city || ''})设计一张小红书种草封面图。特色：${tags}。风格：精致美观，ins风民宿摄影，吸引眼球，高颜值。`,
                  article: `为「${name}」(${tenant?.city || ''})设计一张公众号推文配图。特色：${tags}。风格：高级质感，专业酒店摄影，氛围感强。`,
                }
                console.log(`[${module}] 开始生成配图...`)
                const imgTaskId = await submitImageGen(imgPromptMap[module] || imgPromptMap.wechat, inputParams.imageSize || '1:1')
                const imageUrl = await pollImageTask(imgTaskId)
                console.log(`[${module}] 配图生成完成:`, imageUrl.slice(0, 80) + '...')
                finalContent = JSON.stringify({ text: content, imageUrl })
              } catch (imgErr) {
                console.error(`[${module}] 配图生成失败:`, imgErr.message)
                // 配图失败不影响文案，仍然保存文字内容
              }
            }

            db.run('INSERT INTO content_results(task_id, content, created_at) VALUES(?,?,datetime("now","localtime"))', [taskId, finalContent])
            db.run('UPDATE content_tasks SET status = ?, completed_at = datetime("now","localtime") WHERE id = ?', ['done', taskId])
            saveDB(db)
          }
        } catch (err) {
          console.error('[AI] 生成失败:', err.message)
          db.run('UPDATE content_tasks SET status = ?, error_msg = ? WHERE id = ?', ['error', err.message, taskId])
          saveDB(db)
        }
      })()

      return json(res, { taskId, balance: findOne(db, 'tenants', 'id', tenantId).balance })
    }

    // 查询内容任务状态
    if (path.startsWith('/api/content/task/')) {
      const id = parseInt(path.split('/').pop())
      if (isNaN(id)) return json(res, null, 400, '无效的任务ID')
      const taskResult = db.exec(`SELECT t.id, t.status, t.error_msg, r.content FROM content_tasks t LEFT JOIN content_results r ON t.id = r.task_id WHERE t.id = ${id}`)
      if (taskResult.length === 0) return json(res, null, 404)
      const row = taskResult[0].values[0]
      return json(res, { taskId: row[0], status: row[1], error: row[2], content: row[3] })
    }

    // ====== 图片美化（apimart.ai） ======
    if (path === '/api/content/repair' && req.method === 'POST') {
      const { imageData, prompt, size } = await readBody(req)
      if (!imageData) return json(res, null, 400, '缺少图片数据')

      try {
        const imgPrompt = prompt
          ? `美化优化这张图片：${prompt}。保持原始构图和内容，提升色彩、光线和整体质感。`
          : '美化优化这张图片，提升色彩饱和度、光线质感和整体氛围感，保持原始构图不变。'

        const resp = await proxyFetch(`${APIMART_BASE}/images/generations`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${APIMART_API_KEY}` },
          body: JSON.stringify({
            model: 'gpt-image-2',
            prompt: imgPrompt,
            image_urls: [imageData],
            n: 1,
            size: size || '3:4',
            resolution: '2k',
          }),
          dispatcher: proxyAgent,
        })
        if (!resp.ok) {
          const errText = await resp.text()
          throw new Error(`API 错误: ${resp.status} ${errText}`)
        }
        const data = await resp.json()
        if (data.error) throw new Error(data.error.message || '图片美化失败')
        const taskId = data.data?.[0]?.task_id
        if (!taskId) throw new Error('未返回 task_id')

        const imageUrl = await pollImageTask(taskId)
        return json(res, { imageUrl })
      } catch (err) {
        console.error('[Repair] 失败:', err.message)
        return json(res, null, 500, `图片美化失败: ${err.message}`)
      }
    }

    // ====== 运营大脑（接入通义千问） ======
    if (path === '/api/content/brain/chat' && req.method === 'POST') {
      const { message } = await readBody(req)
      const tenant = findOne(db, 'tenants', 'id', tenantId)
      const name = tenant ? tenant.name : '民宿'
      const tags = tenant ? tenant.tags : ''

      try {
        const aiConfig = await getAIConfig(db)
        const systemPrompt = `你是「${name}」的AI运营顾问。你深入了解这家酒店的情况：
- 名称：${name}
- 类型：${tenant?.type || ''}
- 城市：${tenant?.city || ''}
- 特色：${tags}
- 目标客群：${tenant?.target_audience || ''}
- 周边：${tenant?.nearby || ''}

请基于以上信息，给出专业、具体、可执行的运营建议。不要泛泛而谈，要结合酒店特色给出针对性方案。回答简洁有条理。`

        const content = await callAI(aiConfig, systemPrompt, message || '请给我今天的运营建议')
        return json(res, {
          content,
          suggestions: ['帮我写今天的朋友圈文案', '最近有什么营销节点', '如何提升入住率'],
        })
      } catch (err) {
        return json(res, null, 500, `AI 服务暂时不可用: ${err.message}`)
      }
    }

    // ====== 好评/回评（接入通义千问） ======
    if (path === '/api/hotel/review/generate' && req.method === 'POST') {
      const { guestType } = await readBody(req)
      const tenant = findOne(db, 'tenants', 'id', tenantId)
      const name = tenant ? tenant.name : '民宿'
      const tags = tenant ? tenant.tags : ''

      try {
        const aiConfig = await getAIConfig(db)
        const typeMap = { couple: '情侣', family: '家庭亲子', biz: '商务出差' }
        const typeLabel = typeMap[guestType] || '情侣'
        const systemPrompt = `你模拟一位${typeLabel}客人，为「${name}」写一条真实自然的好评。酒店特色：${tags}。要求100字以内，有细节，不夸张。`
        const content = await callAI(aiConfig, systemPrompt, '请写一条好评')
        return json(res, { review: content })
      } catch (err) {
        return json(res, null, 500, `AI 服务暂时不可用: ${err.message}`)
      }
    }

    if (path === '/api/hotel/reply/generate' && req.method === 'POST') {
      const { reviewType } = await readBody(req)
      const tenant = findOne(db, 'tenants', 'id', tenantId)
      const name = tenant ? tenant.name : '民宿'

      try {
        const aiConfig = await getAIConfig(db)
        const isNegative = reviewType && (reviewType.includes('差评') || reviewType.includes('挽回'))
        const tone = isNegative ? '需要挽回的差评' : '五星好评'
        const systemPrompt = `你是「${name}」的客服负责人。请针对一条${tone}写一条商家回复。语气真诚温暖，体现品牌调性。80字以内。`
        const content = await callAI(aiConfig, systemPrompt, '请写回复')
        return json(res, { reply: content })
      } catch (err) {
        return json(res, null, 500, `AI 服务暂时不可用: ${err.message}`)
      }
    }

    // ====== 客人 ======
    if (path === '/api/hotel/guests' && req.method === 'GET') {
      return json(res, findAll(db, 'guests', 'tenant_id = ? AND status IN (?,?)', [tenantId, 'staying', 'checking_in']).map(g => ({ id: g.id, roomNumber: g.room_number, guestType: g.guest_type, source: g.source, nights: g.nights, checkinDate: g.checkin_date, checkoutDate: g.checkout_date, status: g.status })))
    }

    // ====== 营销方案 CRUD ======
    if (path === '/api/hotel/plans' && req.method === 'GET') {
      return json(res, findAll(db, 'marketing_plans', 'tenant_id = ?', [tenantId]).reverse())
    }
    if (path === '/api/hotel/plans' && req.method === 'POST') {
      const body = await readBody(req)
      db.run('INSERT INTO marketing_plans(tenant_id, name, festival, status, hotel_name, created_at, updated_at) VALUES(?,?,?,?,?,datetime("now","localtime"),datetime("now","localtime"))',
        [tenantId, body.name, body.festival, 'draft', body.hotelName || ''])
      const planId = db.exec('SELECT last_insert_rowid()')[0].values[0][0]
      saveDB(db)
      return json(res, { id: planId, name: body.name, festival: body.festival, status: 'draft' })
    }
    if (path.startsWith('/api/hotel/plans/') && req.method === 'PUT') {
      const id = parseInt(path.split('/').pop())
      const body = await readBody(req)
      if (body.name) db.run('UPDATE marketing_plans SET name = ?, updated_at = datetime("now","localtime") WHERE id = ? AND tenant_id = ?', [body.name, id, tenantId])
      if (body.status) db.run('UPDATE marketing_plans SET status = ?, updated_at = datetime("now","localtime") WHERE id = ? AND tenant_id = ?', [body.status, id, tenantId])
      saveDB(db)
      return json(res, 'ok')
    }
    if (path.startsWith('/api/hotel/plans/') && req.method === 'DELETE') {
      const id = parseInt(path.split('/').pop())
      db.run('DELETE FROM marketing_plans WHERE id = ? AND tenant_id = ?', [id, tenantId])
      saveDB(db)
      return json(res, 'ok')
    }

    // ====== 管理后台（需要 admin 角色） ======
    if (path.startsWith('/api/admin/')) {
      if (role !== 'admin') return json(res, null, 403, '需要管理员权限')

      // 租户列表
      if (path === '/api/admin/tenants' && req.method === 'GET') {
        return json(res, findAll(db, 'tenants'))
      }
      if (path.startsWith('/api/admin/tenants/') && path.endsWith('/recharge') && req.method === 'POST') {
        const id = parseInt(path.split('/')[4])
        const body = await readBody(req)
        const amount = body.amount || 0
        const t = findOne(db, 'tenants', 'id', id)
        if (!t) return json(res, null, 404)
        const newBal = t.balance + amount
        db.run('UPDATE tenants SET balance = ? WHERE id = ?', [newBal, id])
        db.run('INSERT INTO credit_ledger(tenant_id, type, amount, balance_after, module_name, detail, created_at) VALUES(?,?,?,?,?,?,datetime("now","localtime"))',
          [id, 'recharge', amount, newBal, '充值', body.detail || '后台手动充值'])
        saveDB(db)
        return json(res, { tenantId: id, balance: newBal })
      }
      if (path.startsWith('/api/admin/tenants/') && req.method === 'GET') {
        const id = parseInt(path.split('/').pop())
        return json(res, findOne(db, 'tenants', 'id', id))
      }
      if (path === '/api/admin/tenants' && req.method === 'POST') {
        const body = await readBody(req)
        db.run('INSERT INTO tenants(name, type, city, total_rooms, tags, tier, status, balance) VALUES(?,?,?,?,?,?,?,?)',
          [body.name, body.type || '精品民宿', body.city, body.totalRooms || 0, body.tags || '', body.tier || 'trial', 'active', body.balance || 0])
        const id = db.exec('SELECT last_insert_rowid()')[0].values[0][0]
        saveDB(db)
        return json(res, { id, name: body.name })
      }
      if (path.startsWith('/api/admin/tenants/') && req.method === 'PUT') {
        const id = parseInt(path.split('/').pop())
        const body = await readBody(req)
        const allowed = ['name', 'type', 'city', 'total_rooms', 'tags', 'target_audience', 'nearby', 'contact_phone', 'tier', 'status', 'balance', 'alert_threshold', 'melt_threshold', 'qps_limit']
        const keys = Object.keys(body).filter(k => allowed.includes(k))
        if (keys.length > 0) {
          const sets = keys.map(k => `${k} = ?`).join(', ')
          const vals = keys.map(k => body[k])
          db.run(`UPDATE tenants SET ${sets} WHERE id = ?`, [...vals, id])
          saveDB(db)
        }
        return json(res, 'ok')
      }
      if (path.startsWith('/api/admin/tenants/') && req.method === 'DELETE') {
        const id = parseInt(path.split('/').pop())
        db.run('DELETE FROM tenants WHERE id = ?', [id])
        saveDB(db)
        return json(res, 'ok')
      }

      // 计费规则
      if (path === '/api/admin/billing-rules' && req.method === 'GET') {
        return json(res, findAll(db, 'billing_rules'))
      }
      if (path === '/api/admin/billing-rules' && req.method === 'POST') {
        const body = await readBody(req)
        db.run('INSERT INTO billing_rules(module_key, module_name, board, cost, enabled) VALUES(?,?,?,?,1)',
          [body.module_key || body.module_name, body.module_name, body.board || '内容发布', body.cost || 0])
        const id = db.exec('SELECT last_insert_rowid()')[0].values[0][0]
        saveDB(db)
        return json(res, { id })
      }
      if (path.match(/^\/api\/admin\/billing-rules\/\d+$/) && req.method === 'PUT') {
        const id = parseInt(path.split('/').pop())
        const body = await readBody(req)
        const allowed = ['module_key', 'module_name', 'board', 'cost', 'est_cost_rmb', 'enabled', 'sort_order']
        const keys = Object.keys(body).filter(k => allowed.includes(k))
        if (keys.length > 0) {
          const sets = keys.map(k => `${k} = ?`).join(', ')
          const vals = keys.map(k => body[k])
          db.run(`UPDATE billing_rules SET ${sets} WHERE id = ?`, [...vals, id])
          saveDB(db)
        }
        return json(res, 'ok')
      }
      if (path.match(/^\/api\/admin\/billing-rules\/\d+$/) && req.method === 'DELETE') {
        const id = parseInt(path.split('/').pop())
        db.run('DELETE FROM billing_rules WHERE id = ?', [id])
        saveDB(db)
        return json(res, 'ok')
      }

      // 充值套餐
      if (path === '/api/admin/packages' && req.method === 'GET') {
        return json(res, findAll(db, 'recharge_packages'))
      }
      if (path === '/api/admin/packages' && req.method === 'POST') {
        const body = await readBody(req)
        db.run('INSERT INTO recharge_packages(name, credits, price_rmb, applicable_tiers) VALUES(?,?,?,?)',
          [body.name, body.credits || 0, body.price_rmb || 0, body.applicable_tiers || 'basic,pro'])
        const id = db.exec('SELECT last_insert_rowid()')[0].values[0][0]
        saveDB(db)
        return json(res, { id })
      }
      if (path.match(/^\/api\/admin\/packages\/\d+$/) && req.method === 'PUT') {
        const id = parseInt(path.split('/').pop())
        const body = await readBody(req)
        const allowed = ['name', 'credits', 'price_rmb', 'applicable_tiers', 'enabled']
        const keys = Object.keys(body).filter(k => allowed.includes(k))
        if (keys.length > 0) {
          const sets = keys.map(k => `${k} = ?`).join(', ')
          const vals = keys.map(k => body[k])
          db.run(`UPDATE recharge_packages SET ${sets} WHERE id = ?`, [...vals, id])
          saveDB(db)
        }
        return json(res, 'ok')
      }
      if (path.match(/^\/api\/admin\/packages\/\d+$/) && req.method === 'DELETE') {
        const id = parseInt(path.split('/').pop())
        db.run('DELETE FROM recharge_packages WHERE id = ?', [id])
        saveDB(db)
        return json(res, 'ok')
      }

      // Prompt / 风格 / 流水
      if (path === '/api/admin/prompts' && req.method === 'GET') {
        return json(res, findAll(db, 'prompt_templates'))
      }
      if (path === '/api/admin/styles' && req.method === 'GET') {
        return json(res, findAll(db, 'style_library'))
      }
      if (path === '/api/admin/ledger' && req.method === 'GET') {
        return json(res, findAll(db, 'credit_ledger'))
      }

      // 系统配置
      if (path === '/api/admin/settings' && req.method === 'GET') {
        const rows = findAll(db, 'system_config')
        const map = {}
        rows.forEach(r => { map[r.key] = r.value })
        return json(res, { items: rows, map })
      }
      if (path === '/api/admin/settings' && req.method === 'PUT') {
        const body = await readBody(req)
        Object.keys(body).forEach(k => {
          db.run('INSERT INTO system_config(key,value,label) VALUES(?,?,?) ON CONFLICT(key) DO UPDATE SET value = ?, updated_at = datetime("now","localtime")',
            [k, String(body[k] || ''), k, String(body[k] || '')])
        })
        saveDB(db)
        return json(res, '保存成功')
      }
    }

    // 404
    res.writeHead(404, { 'Content-Type': 'application/json', ...CORS })
    res.end(JSON.stringify({ code: 404, message: 'Not Found' }))
  })

  server.listen(8080, () => {
    console.log('═══  宿营家AI  后端服务  ═══')
    console.log('  地址: http://localhost:8080')
    console.log('  数据库: sushijia.db (SQLite)')
    console.log('  登录: 13800000000 + 验证码 123456（演示模式）')
    console.log('  AI: 通义千问（需配置 DASHSCOPE_API_KEY）')
    console.log('═══════════════════════════════')
  })
})()
