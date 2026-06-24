const http = require('http')

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type,Authorization',
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

// 模拟数据
const tenant = { id: 1, name: '松间·山野民宿', type: '精品民宿', city: '浙江·莫干山', totalRooms: 12, tags: '竹林景观、私汤温泉、无边泳池、有机早餐', targetAudience: '面向长三角城市中产', nearby: '距莫干山景区5分钟', tier: 'pro', status: 'active', balance: 1240, alertThreshold: 500, meltThreshold: 0 }

const staffs = {
  '13800000000': { id: 1, tenantId: 1, name: '张店长', role: 'admin' },
  '13800000001': { id: 2, tenantId: 2, name: '李小明', role: 'manager' },
  '13800000002': { id: 3, tenantId: 1, name: '王小红', role: 'front_desk' },
  '13800000003': { id: 4, tenantId: 1, name: '赵小丽', role: 'marketing' },
}

function jwtToken(tenantId, staffId, role) {
  return 'eyJhbGciOiJIUzI1NiJ9.' + Buffer.from(JSON.stringify({ tenant_id: tenantId, staff_id: staffId, role })).toString('base64') + '.sig'
}

let taskCounter = 0
const tasks = {}

function generateContent(module, tenantName) {
  const n = tenantName || '民宿'
  const contents = {
    wechat: `【早间·种草引流型】清晨的${n}，推开窗就是满眼的绿色🌿\n\n趁着端午假期，来山里住两天。泡一池私汤，听一夜竹雨。\n#${n} #山居生活`,
    xhs: `【标题】${n}｜住进去就不想走了🌿\n\n【正文】雨天的莫干山，才是真正的江南。推开窗是漫山竹海，泡在私汤里看竹影摇曳，整个人都被治愈了。\n\n【标签】#${n} #莫干山民宿 #端午出行 #竹林民宿 #私汤温泉`,
    video: `【0-5秒】你想在山里藏一片只有当地人知道的竹林吗？🌿\n【5-20秒】我在${n}住了两天，推开后门就是竹林。\n【20-30秒】端午还剩最后几间，戳左下角👇`,
    poster: `主标题：${n}端午特惠\n副标题：连住2晚8.5折\n行动号召：立即预订 →\n视觉建议：深绿底色+竹林剪影`,
    article: `## ${n} · 运营手记\n\n暑期将至，端午在即。\n\n雨天是最佳卖点——雨中的竹林雾气缭绕，私汤温泉蒸汽升腾。\n\n📍 ${n} · 扫码预订 →`,
    review: `和男朋友来${n}过周末，选了这家竹林民宿真的选对了！\n私汤泡完听竹叶雨声，整个人都松弛了。早餐有机，下次还要来！`,
    reply: `感谢选择${n}，您的认可是我们最大动力。期待下个季节与您重逢！`,
  }
  return contents[module] || 'AI内容已生成'
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, CORS); res.end(); return
  }

  const url = new URL(req.url, `http://${req.headers.host}`)
  const path = url.pathname
  console.log(`[Mock] ${req.method} ${path}`)

  // ====== 登录 ======
  if (path === '/api/auth/sms/send' && req.method === 'POST') {
    return json(res, '验证码已发送（任意验证码均可登录）')
  }
  if (path === '/api/auth/login/phone' && req.method === 'POST') {
    const { phone } = await readBody(req)
    const staff = staffs[phone]
    if (!staff) return json(res, null, 401, '手机号未注册')
    return json(res, {
      accessToken: jwtToken(staff.tenantId, staff.id, staff.role),
      refreshToken: jwtToken(staff.tenantId, staff.id, staff.role),
      role: staff.role, name: staff.name, tenantId: staff.tenantId,
    })
  }
  if (path === '/api/auth/token/refresh' && req.method === 'POST') {
    return json(res, { accessToken: jwtToken(1, 1, 'admin') })
  }

  // ====== 大盘 ======
  if (path === '/api/hotel/dashboard' && req.method === 'GET') {
    return json(res, {
      config: tenant,
      kpi: { occupancyRate: 76, totalSold: 8, totalRooms: 12, freeCount: 4, totalRevenue: 7932, revpar: 661 },
      roomTypeStats: [
        { id: 1, name: '竹语大床房', basePrice: 888, total: 4, sold: 2, free: 1, dirty: 1, repair: 0 },
        { id: 2, name: '山景套房', basePrice: 1388, total: 5, sold: 4, free: 1, dirty: 0, repair: 0 },
        { id: 3, name: '亲子家庭房', basePrice: 1688, total: 3, sold: 1, free: 1, dirty: 0, repair: 1 },
      ],
      futureStatus: [
        { date: '06-12', totalOccupied: 25, totalAvailable: 54 },
        { date: '06-13', totalOccupied: 9, totalAvailable: 70 },
        { date: '06-14', totalOccupied: 16, totalAvailable: 63 },
        { date: '06-15', totalOccupied: 31, totalAvailable: 48 },
        { date: '06-16', totalOccupied: 21, totalAvailable: 58 },
        { date: '06-17', totalOccupied: 3, totalAvailable: 76 },
        { date: '06-18', totalOccupied: 3, totalAvailable: 76 },
      ],
      guests: [
        { roomNumber: '101', guestType: 'couple', source: '小红书引流', nights: 2, checkoutDate: '2026-06-13' },
        { roomNumber: '203', guestType: 'family', source: '美团引流', nights: 3, checkoutDate: '2026-06-13' },
        { roomNumber: '301', guestType: 'family', source: '携程引流', nights: 1, checkoutDate: '2026-06-13' },
      ],
    })
  }

  // ====== 酒店配置 ======
  if (path === '/api/hotel/config' && req.method === 'GET') {
    return json(res, tenant)
  }
  if (path === '/api/hotel/config' && req.method === 'PUT') {
    return json(res, '保存成功')
  }
  if (path === '/api/hotel/rooms' && req.method === 'GET') {
    return json(res, [
      { id: 1, tenantId: 1, name: '竹语大床房', basePrice: 888, count: 4, sortOrder: 1 },
      { id: 2, tenantId: 1, name: '山景套房', basePrice: 1388, count: 5, sortOrder: 2 },
      { id: 3, tenantId: 1, name: '亲子家庭房', basePrice: 1688, count: 3, sortOrder: 3 },
    ])
  }
  if (path === '/api/hotel/rooms' && req.method === 'PUT') {
    return json(res, '保存成功')
  }

  // ====== 算力 ======
  if (path === '/api/hotel/credits/balance' && req.method === 'GET') {
    return json(res, { balance: 1240, todayConsume: 30 })
  }
  if (path === '/api/hotel/credits/ledger' && req.method === 'GET') {
    return json(res, [
      { id: 1, type: 'consume', amount: -8, balanceAfter: 1228, moduleName: '朋友圈文案', detail: '生成三档文案', createdAt: '2026-06-12T14:22:00' },
      { id: 2, type: 'consume', amount: -10, balanceAfter: 1236, moduleName: '小红书营销', detail: '生成选题+图文', createdAt: '2026-06-12T10:08:00' },
      { id: 3, type: 'recharge', amount: 500, balanceAfter: 1246, moduleName: '充值', detail: '月度套餐 · 500算力', createdAt: '2026-06-11T09:15:00' },
      { id: 4, type: 'consume', amount: -30, balanceAfter: 746, moduleName: '营销海报', detail: '生成端午特惠海报', createdAt: '2026-06-10T20:45:00' },
      { id: 5, type: 'consume', amount: -12, balanceAfter: 776, moduleName: '抖音口播', detail: '生成今日口播文案', createdAt: '2026-06-10T16:30:00' },
    ])
  }
  if (path === '/api/hotel/credits/check' && req.method === 'GET') {
    return json(res, { canAfford: true })
  }

  // ====== 定价 ======
  if (path === '/api/hotel/pricing/recommend' && req.method === 'POST') {
    const { holiday, occupancy, weather, competition } = await readBody(req)
    return json(res, {
      results: [
        { roomId: 1, roomName: '竹语大床房', basePrice: 888, recommendedPrice: 1021, changePercent: 15 },
        { roomId: 2, roomName: '山景套房', basePrice: 1388, recommendedPrice: 1596, changePercent: 15 },
        { roomId: 3, roomName: '亲子家庭房', basePrice: 1688, recommendedPrice: 1941, changePercent: 15 },
      ],
      reasons: [
        '节假日类型倍率: ×1.15',
        '入住率较高供不应求，上浮5%',
        '天气对定价无负面影响',
        '周边竞争较小，适当上浮5%',
        '建议调价幅度控制在 ±25% 以内'
      ]
    })
  }

  // ====== 内容生成 ======
  if (path === '/api/content/generate' && req.method === 'POST') {
    const { module } = await readBody(req)
    const taskId = ++taskCounter
    tasks[taskId] = { status: 'processing', module, content: null }
    // 模拟异步：1.5秒后完成
    setTimeout(() => {
      tasks[taskId] = { status: 'done', module, content: generateContent(module, tenant.name) }
    }, 1500)
    return json(res, { taskId, balance: 1240 })
  }
  if (path.startsWith('/api/content/task/')) {
    const id = parseInt(path.split('/').pop())
    const task = tasks[id]
    if (!task) return json(res, { status: 'not_found' }, 404)
    return json(res, { taskId: id, status: task.status, content: task.content })
  }

  // ====== 智慧大脑 ======
  if (path === '/api/content/brain/chat' && req.method === 'POST') {
    return json(res, {
      content: `根据 ${tenant.name} 当前情况：\n\n【即时行动】出租率76%，还有4间空房，建议即时启动限时折扣。\n\n【内容营销】结合竹林景观、私汤温泉的特色拍摄短视频发小红书。\n\n【数据关注】持续监控未来7天预订趋势，周末房源紧张时适当提价。`,
      suggestions: ['帮我写今天的朋友圈文案', '端午海报文案怎么写', '如何设计端午套餐'],
    })
  }

  // ====== 好评/回评 ======
  if (path === '/api/hotel/review/generate' && req.method === 'POST') {
    const { guestType } = await readBody(req)
    const reviews = {
      couple: `和男朋友来${tenant.name}过周末，选了这家竹林里的民宿真的选对了！\n\n房间能看到整片竹海，私汤泡完出来听竹叶雨声，整个人都松弛了。早餐是有机的，鸡蛋是阿姨自己养的鸡下的。下次还要来！`,
      family: `带娃来${tenant.name}避暑，住了三晚，孩子不肯走了。\n\n竹林里的空气太好了，有机早餐也很棒，推荐亲子家庭房！`,
      biz: `出差顺路住了${tenant.name}一晚，没想到这么惊喜。环境清幽、隔音好、WiFi稳定，早餐品质远超连锁酒店...`,
    }
    return json(res, { review: reviews[guestType] || reviews.couple, guestType })
  }
  if (path === '/api/hotel/reply/generate' && req.method === 'POST') {
    const { reviewType } = await readBody(req)
    const replies = {
      '五星好评·夸环境': `感谢您选择${tenant.name}，您的认可是我们最大的动力。期待在下一个季节与您重逢，祝您旅途平安！`,
      '五星好评·夸服务': `谢谢您的认可！我们会继续保持优质服务，期待下次为您带来更棒的入住体验！`,
      '四星·有小建议': `感谢您的评价和建议！您提到的小细节我们已经记录，期待下次为您提供更完美的体验！`,
      '差评·需挽回': `非常抱歉给您带来了不好的体验，我们会立即改进您提到的问题。期待您下次光临时能看到我们的变化。`,
    }
    return json(res, { reply: replies[reviewType] || replies['五星好评·夸环境'], reviewType })
  }

  // ====== 在住客人 ======
  if (path === '/api/hotel/guests' && req.method === 'GET') {
    return json(res, [
      { id: 1, roomNumber: '101', guestType: 'couple', source: '小红书引流', nights: 2, checkinDate: '2026-06-11', checkoutDate: '2026-06-13', status: 'staying' },
      { id: 2, roomNumber: '203', guestType: 'family', source: '美团引流', nights: 3, checkinDate: '2026-06-10', checkoutDate: '2026-06-13', status: 'staying' },
      { id: 3, roomNumber: '301', guestType: 'family', source: '携程引流', nights: 1, checkinDate: '2026-06-12', checkoutDate: '2026-06-13', status: 'checking_in' },
    ])
  }

  // ====== 营销方案 ======
  if (path === '/api/hotel/plans' && req.method === 'GET') {
    return json(res, [{
      id: '1', tenantId: 1, name: '端午节完整营销方案', festival: '端午节', status: 'active',
      hotelName: '松间·山野民宿', period: 'D-7→假期末→收尾', target: '端午3天出租率 ≥ 90%',
      tags: '["竹林景观","私汤温泉"]', createdAt: '2025-05-20', updatedAt: '2025-05-25',
    }])
  }
  if (path === '/api/hotel/plans' && req.method === 'POST') {
    const body = await readBody(req)
    return json(res, { id: Date.now(), ...body, createdAt: new Date().toISOString().slice(0, 10) })
  }
  if (path.startsWith('/api/hotel/plans/')) {
    return json(res, { id: 1, name: '端午节完整营销方案', festival: '端午节', status: 'active' })
  }

  // ====== 房态分析（兼容） ======
  if (path === '/api/hotel/room-status' && req.method === 'GET') {
    return json(res, { futureStatus: [], peakDay: null, avgOccRate: 76 })
  }

  // ====== 后台 Admin 兼容 ======
  if (path.startsWith('/api/admin/')) {
    return json(res, [])
  }

  // ====== 404 ======
  res.writeHead(404, { 'Content-Type': 'application/json', ...CORS })
  res.end(JSON.stringify({ code: 404, message: 'Not Found: ' + path }))
})

const PORT = 8080
server.listen(PORT, () => {
  console.log('═══  宿营家AI  Mock 后端 已启动  ═══')
  console.log('  地址: http://localhost:' + PORT)
  console.log('  登录: 13800000000 + 任意验证码')
  console.log('  前端: http://localhost:5174')
  console.log('══════════════════════════════════')
})
