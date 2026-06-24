require('dotenv').config()
const http = require('http')
const fs = require('fs')
const path = require('path')
const jwt = require('jsonwebtoken')

const STATIC_DIR = path.join(__dirname, 'dist')
const ADMIN_FILE = 'saas_admin_final_v2.html'

// ====== 配置 ======
const JWT_SECRET = process.env.JWT_SECRET || 'sushijia-dev-secret-change-in-prod-2026'
const DEMO_SMS_CODE = '123456'
const ALLOWED_ORIGINS = (process.env.CORS_ORIGINS || '*').split(',').map(s => s.trim())

// ====== 数据库 ======
async function initDB() {
  const initSqlJs = require('sql.js')
  const SQL = await initSqlJs()
  const dbPath = path.join(__dirname, 'sushijia.db')
  let db
  if (fs.existsSync(dbPath)) {
    db = new SQL.Database(fs.readFileSync(dbPath))
    console.log('[DB] 已连接 ' + dbPath)
  } else {
    db = new SQL.Database()
    require('./db-init.cjs')(db)
    saveDB(db, dbPath)
  }
  return { db, dbPath }
}

function saveDB(db, dbPath) {
  fs.writeFileSync(dbPath, Buffer.from(db.export()))
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
  } catch { return null }
}

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type,Authorization',
}

function json(res, data, code = 200, msg = 'success') {
  res.writeHead(code, { 'Content-Type': 'application/json', ...CORS_HEADERS })
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
    if (stmt.step()) { const obj = stmt.getAsObject(); stmt.free(); return obj }
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
    while (stmt.step()) { results.push(stmt.getAsObject()) }
    stmt.free()
    return results
  } catch (e) { console.error('[DB] findAll error:', e.message); return [] }
}

// ====== 通义千问 AI ======
async function getAIConfig(db) {
  const rows = findAll(db, 'system_config')
  const map = {}; rows.forEach(r => { map[r.key] = r.value })
  return {
    apiKey: process.env.DASHSCOPE_API_KEY || map.ai_api_key || '',
    model: map.ai_model || 'qwen-plus',
    baseUrl: map.ai_base_url || 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    maxTokens: parseInt(map.ai_max_tokens || '4000', 10),
  }
}

async function callAI(config, systemPrompt, userMessage) {
  if (!config.apiKey) throw new Error('AI API Key 未配置')
  const resp = await fetch(`${config.baseUrl}/chat/completions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${config.apiKey}` },
    body: JSON.stringify({
      model: config.model,
      messages: [{ role: 'system', content: systemPrompt }, { role: 'user', content: userMessage }],
      max_tokens: config.maxTokens, temperature: 0.8,
    }),
  })
  if (!resp.ok) throw new Error(`AI 服务错误: ${resp.status}`)
  const data = await resp.json()
  return data.choices?.[0]?.message?.content || 'AI 未返回有效内容'
}

// ====== apimart.ai 图片生成（GPT-Image-2） ======
const APIMART_API_KEY = process.env.APIMART_API_KEY || ''
const APIMART_BASE = 'https://api.apimart.ai/v1'

async function submitImageGen(prompt, size = '3:4', resolution = '2k') {
  if (!APIMART_API_KEY) throw new Error('APIMART_API_KEY 未配置')
  const resp = await fetch(`${APIMART_BASE}/images/generations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${APIMART_API_KEY}` },
    body: JSON.stringify({ model: 'gpt-image-2', prompt, n: 1, size, resolution }),
  })
  if (!resp.ok) { const errText = await resp.text(); throw new Error(`图片生成 API 错误: ${resp.status} ${errText}`) }
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
      const resp = await fetch(`${APIMART_BASE}/tasks/${taskId}`, { headers: { 'Authorization': `Bearer ${APIMART_API_KEY}` } })
      if (!resp.ok) continue
      const data = await resp.json()
      const status = data.data?.status
      if (status === 'completed') {
        const url = data.data?.result?.images?.[0]?.url?.[0]
        if (url) return url
        throw new Error('完成但无图片 URL')
      }
      if (status === 'failed') throw new Error(data.data?.error?.message || '图片生成失败')
    } catch (err) {
      if (err.message.includes('完成但无') || err.message.includes('失败')) throw err
    }
  }
  throw new Error('图片生成超时')
}

// ====== 静态文件 ======
const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'application/javascript',
  '.css': 'text/css', '.json': 'application/json', '.png': 'image/png',
  '.jpg': 'image/jpeg', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.ico': 'image/x-icon',
}

function serveStatic(res, filePath) {
  const ext = path.extname(filePath)
  const mime = MIME[ext] || 'application/octet-stream'
  try {
    const content = fs.readFileSync(filePath)
    res.writeHead(200, { 'Content-Type': mime, 'Cache-Control': 'max-age=3600' })
    res.end(content)
  } catch {
    try {
      const html = fs.readFileSync(path.join(STATIC_DIR, 'index.html'), 'utf-8')
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
      res.end(html)
    } catch { res.writeHead(404); res.end('Not Found') }
  }
}

// ====== 启动 ======
(async () => {
  const { db, dbPath } = await initDB()

  const server = http.createServer(async (req, res) => {
    if (req.method === 'OPTIONS') { res.writeHead(204, CORS_HEADERS); res.end(); return }
    const url = new URL(req.url, `http://${req.headers.host}`)
    const pathname = url.pathname
    console.log(`[${new Date().toISOString()}] ${req.method} ${pathname}`)

    // ====== 公开路由 ======
    if (pathname === '/api/auth/sms/send' && req.method === 'POST') {
      return json(res, '验证码已发送（演示模式，验证码：123456）')
    }
    if (pathname === '/api/auth/login/phone' && req.method === 'POST') {
      const { phone, code } = await readBody(req)
      if (code && code !== DEMO_SMS_CODE) return json(res, null, 401, '验证码错误')
      const staff = findOne(db, 'hotel_staff', 'phone', phone)
      if (!staff) return json(res, null, 401, '手机号未注册')
      return json(res, { accessToken: jwtToken(staff.tenant_id, staff.id, staff.role), refreshToken: jwtToken(staff.tenant_id, staff.id, staff.role), role: staff.role, name: staff.name, tenantId: staff.tenant_id })
    }
    if (pathname === '/api/auth/token/refresh' && req.method === 'POST') {
      const user = authMiddleware(req)
      if (!user) return json(res, null, 401, '无效的 Token')
      return json(res, { accessToken: jwtToken(user.tenant_id, user.staff_id, user.role) })
    }

    // ====== 鉴权 ======
    const user = authMiddleware(req)
    if (!user) return json(res, null, 401, '请先登录')
    const tenantId = user.tenant_id
    const role = user.role

    // ====== 大盘 ======
    if (pathname === '/api/hotel/dashboard' && req.method === 'GET') {
      const tenant = findOne(db, 'tenants', 'id', tenantId)
      const roomTypes = findAll(db, 'room_types', 'tenant_id = ?', [tenantId])
      const statuses = findAll(db, 'room_status', 'tenant_id = ?', [tenantId])
      const guests = findAll(db, 'guests', 'tenant_id = ? AND status IN (?,?)', [tenantId, 'staying', 'checking_in'])
      const totalRooms = roomTypes.reduce((s, r) => s + r.count, 0)
      const totalSold = statuses.filter(r => r.status === 'sold').length
      const priceMap = Object.fromEntries(roomTypes.map(r => [r.id, r.base_price]))
      const totalRevenue = statuses.filter(r => r.status === 'sold').reduce((s, r) => s + (priceMap[r.room_type_id] || 0), 0)
      const occ = totalRooms > 0 ? Math.round(totalSold / totalRooms * 100) : 0
      const revpar = totalRooms > 0 ? Math.round(totalRevenue / totalRooms) : 0
      const stats = roomTypes.map(rt => {
        const rs = statuses.filter(s => s.room_type_id === rt.id)
        return { id: rt.id, name: rt.name, basePrice: rt.base_price, total: rs.length, sold: rs.filter(s => s.status === 'sold').length, free: rs.filter(s => s.status === 'free').length, dirty: rs.filter(s => s.status === 'dirty').length, repair: rs.filter(s => s.status === 'repair').length }
      })
      const futureDays = {}
      for (let i = 0; i < 7; i++) {
        const d = new Date(); d.setDate(d.getDate() + i)
        const key = d.toISOString().slice(5, 10)
        futureDays[key] = { date: key, totalOccupied: Math.floor(Math.random() * 25) + 3, totalAvailable: Math.floor(Math.random() * 50) + 20 }
      }
      return json(res, { config: tenant, kpi: { occupancyRate: occ, totalSold, totalRooms, freeCount: totalRooms - totalSold, totalRevenue, revpar }, roomTypeStats: stats, futureStatus: Object.values(futureDays), guests: guests.map(g => ({ roomNumber: g.room_number, guestType: g.guest_type, source: g.source, nights: g.nights, checkoutDate: g.checkout_date })) })
    }

    // ====== 酒店配置 ======
    if (pathname === '/api/hotel/config' && req.method === 'GET') return json(res, findOne(db, 'tenants', 'id', tenantId))
    if (pathname === '/api/hotel/config' && req.method === 'PUT') {
      const body = await readBody(req)
      const allowed = ['name', 'type', 'city', 'total_rooms', 'tags', 'target_audience', 'nearby', 'contact_phone']
      const fields = Object.keys(body).filter(k => allowed.includes(k))
      if (fields.length > 0) {
        db.run(`UPDATE tenants SET ${fields.map(k => `${k} = ?`).join(', ')}, updated_at = datetime('now','localtime') WHERE id = ?`, [...fields.map(k => body[k]), tenantId])
        saveDB(db, dbPath)
      }
      return json(res, '保存成功')
    }

    // ====== 房型 ======
    if (pathname === '/api/hotel/rooms' && req.method === 'GET') return json(res, findAll(db, 'room_types', 'tenant_id = ?', [tenantId]))
    if (pathname === '/api/hotel/rooms' && req.method === 'PUT') {
      const rooms = await readBody(req)
      db.run('DELETE FROM room_types WHERE tenant_id = ?', [tenantId])
      db.run('DELETE FROM room_status WHERE tenant_id = ?', [tenantId])
      if (Array.isArray(rooms)) rooms.forEach((r, i) => db.run('INSERT INTO room_types(tenant_id, name, base_price, count, sort_order) VALUES(?,?,?,?,?)', [tenantId, r.name, r.basePrice || r.base_price || 0, r.count || 0, i]))
      saveDB(db, dbPath)
      return json(res, 'ok')
    }

    // ====== 算力 ======
    if (pathname === '/api/hotel/credits/balance' && req.method === 'GET') {
      const t = findOne(db, 'tenants', 'id', tenantId)
      const today = new Date().toISOString().slice(0, 10)
      const r = db.exec(`SELECT COALESCE(SUM(ABS(amount)),0) FROM credit_ledger WHERE tenant_id = ${parseInt(tenantId)} AND type = 'consume' AND date(created_at) = '${today}'`)
      return json(res, { balance: t.balance, todayConsume: r.length > 0 ? r[0].values[0][0] : 0 })
    }
    if (pathname === '/api/hotel/credits/ledger' && req.method === 'GET') return json(res, findAll(db, 'credit_ledger', 'tenant_id = ?', [tenantId]).reverse())

    // ====== 定价 ======
    if (pathname === '/api/hotel/pricing/recommend' && req.method === 'POST') {
      const { holiday, occupancy, weather, competition } = await readBody(req)
      const hm = { big: 1.28, small: 1.15, weekend: 1.1, emotion: 1.2 }
      const om = { '90+': 0.10, '70-90': 0.05, '50-70': 0, '30-50': -0.08, '30-': -0.18 }
      const wm = { sunny: 0, rain: -0.08, heavy: -0.15, extreme: -0.22 }
      const cm = { none: 0.05, light: 0, medium: -0.10, high: -0.15 }
      const types = findAll(db, 'room_types', 'tenant_id = ?', [tenantId])
      const results = types.map(rt => {
        const adj = 1 + (om[occupancy] || 0) + (wm[weather] || 0) + (cm[competition] || 0)
        const rec = Math.round(rt.base_price * (hm[holiday] || 1) * adj)
        return { roomId: rt.id, roomName: rt.name, basePrice: rt.base_price, recommendedPrice: rec, changePercent: Math.round((rec / rt.base_price - 1) * 100) }
      })
      return json(res, { results, reasons: ['四因子模型计算结果'] })
    }

    // ====== AI 内容生成（通义千问） ======
    if (pathname === '/api/content/generate' && req.method === 'POST') {
      const body = await readBody(req)
      const { module } = body
      const inputParams = body.params || {}
      const validModules = ['wechat', 'xhs', 'video', 'poster', 'article', 'review', 'reply']
      if (!validModules.includes(module)) return json(res, null, 400, '无效模块')
      const tenant = findOne(db, 'tenants', 'id', tenantId)
      const name = tenant ? tenant.name : '民宿'
      const tags = tenant ? tenant.tags : ''
      const safeModule = module.replace(/[^a-z_]/g, '')
      const costRows = db.exec(`SELECT cost FROM billing_rules WHERE module_key = '${safeModule}' AND enabled = 1`)
      const cost = costRows.length > 0 ? costRows[0].values[0][0] : 0
      if (cost > 0) {
        const t = findOne(db, 'tenants', 'id', tenantId)
        if (t.balance < cost) return json(res, null, 402, `算力不足，需要 ${cost}，余额 ${t.balance}`)
        db.run('UPDATE tenants SET balance = balance - ? WHERE id = ?', [cost, tenantId])
        db.run('INSERT INTO credit_ledger(tenant_id,type,amount,balance_after,module_key,module_name,detail,created_at) VALUES(?,?,?,(SELECT balance FROM tenants WHERE id=?),?,?,?,datetime("now","localtime"))', [tenantId, 'consume', -cost, tenantId, safeModule, module, 'AI生成'])
      }
      const now = new Date().toISOString().slice(0, 19).replace('T', ' ')
      db.run('INSERT INTO content_tasks(tenant_id,module_key,input_params,status,cost_credits,created_at) VALUES(?,?,?,?,?,?)', [tenantId, safeModule, JSON.stringify(inputParams), 'processing', cost, now])
      const taskId = db.exec('SELECT last_insert_rowid()')[0].values[0][0]
      saveDB(db, dbPath)
      ;(async () => {
        try {
          if (module === 'poster') {
            const city = tenant ? tenant.city : ''
            const hotelTags = tenant ? tenant.tags : ''
            const theme = inputParams.theme || ''
            const content = inputParams.content || ''
            const style = inputParams.style || 'chinese'
            const styleMap = {
              chinese: '中式禅意风格，深绿色调，东方美学，竹林元素，留白意境',
              minimal: '轻奢简约风格，米白色调，简洁线条，高级质感',
              dark: '深夜极简风格，深色调，星空元素，神秘高级感',
              warm: '温暖治愈风格，暖色调，柔和光线，温馨氛围',
            }
            const prompt = `为「${name}」(${city})设计一张营销海报。酒店特色：${hotelTags}。主题：${theme}。${content ? '副标题/内容：' + content + '。' : ''}视觉风格：${styleMap[style] || styleMap.chinese}。要求：专业酒店营销海报设计，精美排版，高品质画面。`
            const imgTaskId = await submitImageGen(prompt)
            const imageUrl = await pollImageTask(imgTaskId)
            db.run('INSERT INTO content_results(task_id,content,created_at) VALUES(?,?,datetime("now","localtime"))', [taskId, imageUrl])
            db.run('UPDATE content_tasks SET status=?,completed_at=datetime("now","localtime") WHERE id=?', ['done', taskId])
            saveDB(db, dbPath)
          } else {
            const aiConfig = await getAIConfig(db)
            const systemPrompt = `你是专业的酒店民宿营销文案专家。你正在为「${name}」创作文案。\n酒店信息：类型${tenant?.type || ''}，城市${tenant?.city || ''}，特色${tags}，目标客群${tenant?.target_audience || ''}。\n请创作与酒店特色紧密结合的营销文案，不要通用模板。`
            const modulePrompts = {
              wechat: '请生成3条朋友圈文案：早间种草型、午间互动型、晚间促单型。每条100字以内。',
              xhs: '请生成一篇小红书种草笔记：标题（20字以内）、正文（300字以内）、5-8个话题标签。',
              video: '请生成30秒短视频口播脚本：开头hook（5秒）、卖点展示（20秒）、行动号召（5秒）。',
              article: '请生成公众号推文（500-800字）：标题、引言、2-3个小标题段落、结尾引导。',
              review: '请生成3条不同角度的好评模板：情侣、家庭、商务客人视角。每条100字以内。',
              reply: '请生成2条商家回复：五星好评感谢回复、差评挽回回复。各80字以内。',
            }
            const content = await callAI(aiConfig, systemPrompt, modulePrompts[module] || '请生成营销文案')
            db.run('INSERT INTO content_results(task_id,content,created_at) VALUES(?,?,datetime("now","localtime"))', [taskId, content])
            db.run('UPDATE content_tasks SET status=?,completed_at=datetime("now","localtime") WHERE id=?', ['done', taskId])
            saveDB(db, dbPath)
          }
        } catch (err) {
          db.run('UPDATE content_tasks SET status=?,error_msg=? WHERE id=?', ['error', err.message, taskId])
          saveDB(db, dbPath)
        }
      })()
      return json(res, { taskId, balance: findOne(db, 'tenants', 'id', tenantId)?.balance })
    }
    if (pathname.startsWith('/api/content/task/')) {
      const id = parseInt(pathname.split('/').pop())
      if (isNaN(id)) return json(res, null, 400, '无效任务ID')
      const r = db.exec(`SELECT t.id,t.status,t.error_msg,r.content FROM content_tasks t LEFT JOIN content_results r ON t.id=r.task_id WHERE t.id=${id}`)
      if (r.length === 0) return json(res, null, 404)
      return json(res, { taskId: r[0].values[0][0], status: r[0].values[0][1], error: r[0].values[0][2], content: r[0].values[0][3] })
    }

    // ====== 图片美化（apimart.ai） ======
    if (pathname === '/api/content/repair' && req.method === 'POST') {
      const { imageData, prompt, size } = await readBody(req)
      if (!imageData) return json(res, null, 400, '缺少图片数据')
      try {
        const imgPrompt = prompt
          ? `美化优化这张图片：${prompt}。保持原始构图和内容，提升色彩、光线和整体质感。`
          : '美化优化这张图片，提升色彩饱和度、光线质感和整体氛围感，保持原始构图不变。'
        const resp = await fetch(`${APIMART_BASE}/images/generations`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${APIMART_API_KEY}` },
          body: JSON.stringify({ model: 'gpt-image-2', prompt: imgPrompt, image_urls: [imageData], n: 1, size: size || '3:4', resolution: '2k' }),
        })
        if (!resp.ok) { const errText = await resp.text(); throw new Error(`API 错误: ${resp.status} ${errText}`) }
        const data = await resp.json()
        if (data.error) throw new Error(data.error.message || '图片美化失败')
        const taskId = data.data?.[0]?.task_id
        if (!taskId) throw new Error('未返回 task_id')
        const imageUrl = await pollImageTask(taskId)
        return json(res, { imageUrl })
      } catch (err) { return json(res, null, 500, `图片美化失败: ${err.message}`) }
    }

    // ====== 运营大脑 ======
    if (pathname === '/api/content/brain/chat' && req.method === 'POST') {
      const { message } = await readBody(req)
      const tenant = findOne(db, 'tenants', 'id', tenantId)
      try {
        const aiConfig = await getAIConfig(db)
        const systemPrompt = `你是「${tenant?.name}」的AI运营顾问。酒店特色：${tenant?.tags}。请给出专业、具体、可执行的运营建议。`
        const content = await callAI(aiConfig, systemPrompt, message || '请给我今天的运营建议')
        return json(res, { content, suggestions: ['帮我写今天的朋友圈文案', '如何提升入住率', '最近有什么营销节点'] })
      } catch (err) { return json(res, null, 500, `AI 不可用: ${err.message}`) }
    }

    // ====== 好评/回评 ======
    if (pathname === '/api/hotel/review/generate' && req.method === 'POST') {
      const { guestType } = await readBody(req)
      const tenant = findOne(db, 'tenants', 'id', tenantId)
      try {
        const aiConfig = await getAIConfig(db)
        const typeMap = { couple: '情侣', family: '家庭亲子', biz: '商务出差' }
        const content = await callAI(aiConfig, `你模拟一位${typeMap[guestType] || '情侣'}客人，为「${tenant?.name}」写一条好评。特色：${tenant?.tags}。100字以内。`, '请写好评')
        return json(res, { review: content })
      } catch (err) { return json(res, null, 500, `AI 不可用: ${err.message}`) }
    }
    if (pathname === '/api/hotel/reply/generate' && req.method === 'POST') {
      const { reviewType } = await readBody(req)
      const tenant = findOne(db, 'tenants', 'id', tenantId)
      try {
        const aiConfig = await getAIConfig(db)
        const isNeg = reviewType && (reviewType.includes('差评') || reviewType.includes('挽回'))
        const content = await callAI(aiConfig, `你是「${tenant?.name}」的客服负责人。请针对一条${isNeg ? '差评' : '好评'}写商家回复。80字以内。`, '请写回复')
        return json(res, { reply: content })
      } catch (err) { return json(res, null, 500, `AI 不可用: ${err.message}`) }
    }

    // ====== 客人 ======
    if (pathname === '/api/hotel/guests' && req.method === 'GET') return json(res, findAll(db, 'guests', 'tenant_id = ? AND status IN (?,?)', [tenantId, 'staying', 'checking_in']).map(g => ({ id: g.id, roomNumber: g.room_number, guestType: g.guest_type, source: g.source, nights: g.nights, checkinDate: g.checkin_date, checkoutDate: g.checkout_date, status: g.status })))

    // ====== 方案 CRUD ======
    if (pathname === '/api/hotel/plans' && req.method === 'GET') return json(res, findAll(db, 'marketing_plans', 'tenant_id = ?', [tenantId]).reverse())
    if (pathname === '/api/hotel/plans' && req.method === 'POST') {
      const b = await readBody(req)
      db.run('INSERT INTO marketing_plans(tenant_id,name,festival,status,hotel_name,created_at,updated_at) VALUES(?,?,?,?,?,datetime("now","localtime"),datetime("now","localtime"))', [tenantId, b.name, b.festival, 'draft', b.hotelName || ''])
      const id = db.exec('SELECT last_insert_rowid()')[0].values[0][0]
      saveDB(db, dbPath)
      return json(res, { id, name: b.name, festival: b.festival, status: 'draft' })
    }
    if (pathname.startsWith('/api/hotel/plans/') && req.method === 'PUT') {
      const id = parseInt(pathname.split('/').pop())
      const b = await readBody(req)
      if (b.name) db.run('UPDATE marketing_plans SET name=?,updated_at=datetime("now","localtime") WHERE id=? AND tenant_id=?', [b.name, id, tenantId])
      if (b.status) db.run('UPDATE marketing_plans SET status=?,updated_at=datetime("now","localtime") WHERE id=? AND tenant_id=?', [b.status, id, tenantId])
      saveDB(db, dbPath)
      return json(res, 'ok')
    }
    if (pathname.startsWith('/api/hotel/plans/') && req.method === 'DELETE') {
      db.run('DELETE FROM marketing_plans WHERE id=? AND tenant_id=?', [parseInt(pathname.split('/').pop()), tenantId])
      saveDB(db, dbPath)
      return json(res, 'ok')
    }

    // ====== 管理后台（需 admin） ======
    if (pathname.startsWith('/api/admin/')) {
      if (role !== 'admin') return json(res, null, 403, '需要管理员权限')

      if (pathname === '/api/admin/tenants' && req.method === 'GET') return json(res, findAll(db, 'tenants'))
      if (pathname.startsWith('/api/admin/tenants/') && pathname.endsWith('/recharge') && req.method === 'POST') {
        const id = parseInt(pathname.split('/')[4])
        const { amount, detail } = await readBody(req)
        const t = findOne(db, 'tenants', 'id', id)
        if (!t) return json(res, null, 404)
        const nb = t.balance + (amount || 0)
        db.run('UPDATE tenants SET balance=? WHERE id=?', [nb, id])
        db.run('INSERT INTO credit_ledger(tenant_id,type,amount,balance_after,module_name,detail,created_at) VALUES(?,?,?,?,?,?,datetime("now","localtime"))', [id, 'recharge', amount, nb, '充值', detail || '后台手动充值'])
        saveDB(db, dbPath)
        return json(res, { tenantId: id, balance: nb })
      }
      if (pathname.startsWith('/api/admin/tenants/') && req.method === 'GET') {
        return json(res, findOne(db, 'tenants', 'id', parseInt(pathname.split('/').pop())))
      }
      if (pathname === '/api/admin/tenants' && req.method === 'POST') {
        const body = await readBody(req)
        db.run('INSERT INTO tenants(name,type,city,total_rooms,tags,tier,status,balance) VALUES(?,?,?,?,?,?,?,?)', [body.name, body.type || '精品民宿', body.city, body.totalRooms || 0, body.tags || '', body.tier || 'trial', 'active', body.balance || 0])
        const id = db.exec('SELECT last_insert_rowid()')[0].values[0][0]
        saveDB(db, dbPath)
        return json(res, { id, name: body.name })
      }
      if (pathname.startsWith('/api/admin/tenants/') && req.method === 'PUT') {
        const id = parseInt(pathname.split('/').pop())
        const body = await readBody(req)
        const allowed = ['name', 'type', 'city', 'total_rooms', 'tags', 'target_audience', 'nearby', 'contact_phone', 'tier', 'status', 'balance', 'alert_threshold', 'qps_limit']
        const keys = Object.keys(body).filter(k => allowed.includes(k))
        if (keys.length > 0) {
          db.run(`UPDATE tenants SET ${keys.map(k => `${k}=?`).join(',')} WHERE id=?`, [...keys.map(k => body[k]), id])
          saveDB(db, dbPath)
        }
        return json(res, 'ok')
      }
      if (pathname.startsWith('/api/admin/tenants/') && req.method === 'DELETE') {
        db.run('DELETE FROM tenants WHERE id=?', [parseInt(pathname.split('/').pop())])
        saveDB(db, dbPath)
        return json(res, 'ok')
      }
      if (pathname === '/api/admin/billing-rules' && req.method === 'GET') return json(res, findAll(db, 'billing_rules'))
      if (pathname === '/api/admin/billing-rules' && req.method === 'POST') {
        const body = await readBody(req)
        db.run('INSERT INTO billing_rules(module_key,module_name,board,cost,enabled) VALUES(?,?,?,?,1)', [body.module_key || body.module_name, body.module_name, body.board || '内容发布', body.cost || 0])
        const id = db.exec('SELECT last_insert_rowid()')[0].values[0][0]
        saveDB(db, dbPath)
        return json(res, { id })
      }
      if (pathname === '/api/admin/packages' && req.method === 'GET') return json(res, findAll(db, 'recharge_packages'))
      if (pathname === '/api/admin/prompts' && req.method === 'GET') return json(res, findAll(db, 'prompt_templates'))
      if (pathname === '/api/admin/styles' && req.method === 'GET') return json(res, findAll(db, 'style_library'))
      if (pathname === '/api/admin/ledger' && req.method === 'GET') return json(res, findAll(db, 'credit_ledger'))
      if (pathname === '/api/admin/settings' && req.method === 'GET') {
        const rows = findAll(db, 'system_config')
        const map = {}; rows.forEach(r => { map[r.key] = r.value })
        return json(res, { items: rows, map })
      }
      if (pathname === '/api/admin/settings' && req.method === 'PUT') {
        const body = await readBody(req)
        Object.keys(body).forEach(k => {
          db.run('INSERT INTO system_config(key,value,label) VALUES(?,?,?) ON CONFLICT(key) DO UPDATE SET value=?,updated_at=datetime("now","localtime")', [k, String(body[k] || ''), k, String(body[k] || '')])
        })
        saveDB(db, dbPath)
        return json(res, '保存成功')
      }
    }

    // ====== 静态文件 ======
    if (pathname === '/admin') {
      try {
        const adminHtml = fs.readFileSync(path.join(__dirname, ADMIN_FILE), 'utf-8')
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
        res.end(adminHtml)
      } catch { res.writeHead(404); res.end('Admin file not found') }
      return
    }

    let filePath = path.join(STATIC_DIR, pathname === '/' ? 'index.html' : pathname.slice(1))
    if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
      filePath = path.join(STATIC_DIR, 'index.html')
    }
    serveStatic(res, filePath)
  })

  const PORT = process.env.PORT || 8080
  server.listen(PORT, '0.0.0.0', () => {
    console.log('')
    console.log('  ═══════════════════════════════════════════')
    console.log('   宿营家 AI  -  生产部署版')
    console.log('  ═══════════════════════════════════════════')
    console.log('')
    console.log('   酒店端:  http://localhost:' + PORT)
    console.log('   后台管理: http://localhost:' + PORT + '/admin')
    console.log('   数据库:  ' + dbPath)
    console.log('   AI:      通义千问（需配置 DASHSCOPE_API_KEY）')
    console.log('')
    console.log('   登录: 13800000000  验证码: 123456（演示模式）')
    console.log('')
    console.log('  ═══════════════════════════════════════════')
    console.log('')
  })
})()
