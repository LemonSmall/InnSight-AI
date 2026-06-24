import api from './index'

/**
 * 提交 AI 内容生成任务
 * @param moduleKey wechat/xhs/video/poster/article/review/reply
 * @param params 模块参数
 * @returns { taskId, balance }
 */
export function generateContent(moduleKey: string, params: Record<string, any>) {
  return api.post('/api/content/generate', { module: moduleKey, params })
}

/**
 * 查询任务状态
 * @param taskId 任务ID
 * @returns { status, content? }
 */
export function getTaskResult(taskId: number) {
  return api.get(`/api/content/task/${taskId}`)
}

/**
 * 智慧大脑对话
 */
export function brainChat(message: string) {
  return api.post('/api/content/brain/chat', { message })
}
