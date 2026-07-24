import fs from 'node:fs'
import YAML from 'yaml'

const [input, output] = process.argv.slice(2)

if (!input || !output) {
  console.error('Usage: node scripts/fix-poster-yml.mjs <input.yml> <output.yml>')
  process.exit(1)
}

const source = fs.readFileSync(input, 'utf8')
const doc = YAML.parse(source)

const graph = doc?.workflow?.graph
if (!graph?.nodes || !graph?.edges) {
  throw new Error('Invalid Dify workflow yml: workflow.graph.nodes/edges not found')
}

const codeNodeId = '1784273192506'
const taskNodeId = '1784272970300'
const endNodeId = '1784273408658'
const removeNodeIds = new Set([
  '1784273005519',
  '1784273097724',
  '1784273097724start',
  '1784276657358',
  '1784276657358start',
  '1784273244824',
])

const tokenMatch = source.match(/Authorization\s*:\s*Bearer\s+(ms-[^\s'"]+)/i)
const token = tokenMatch?.[1] || 'REPLACE_WITH_MODELSCOPE_TOKEN'

graph.nodes = graph.nodes.filter(node => !removeNodeIds.has(String(node.id)))
graph.edges = graph.edges.filter(edge => {
  const sourceId = String(edge.source)
  const targetId = String(edge.target)
  return !removeNodeIds.has(sourceId) && !removeNodeIds.has(targetId)
})

function edgeExists(sourceId, targetId) {
  return graph.edges.some(edge => String(edge.source) === sourceId && String(edge.target) === targetId)
}

if (!edgeExists(taskNodeId, codeNodeId)) {
  graph.edges.push({
    id: `${taskNodeId}-source-${codeNodeId}-target`,
    source: taskNodeId,
    sourceHandle: 'source',
    target: codeNodeId,
    targetHandle: 'target',
    type: 'custom',
    data: {
      isInIteration: false,
      isInLoop: false,
      sourceType: 'code',
      targetType: 'code',
    },
    zIndex: 0,
  })
}

if (!edgeExists(codeNodeId, endNodeId)) {
  graph.edges.push({
    id: `${codeNodeId}-source-${endNodeId}-target`,
    source: codeNodeId,
    sourceHandle: 'source',
    target: endNodeId,
    targetHandle: 'target',
    type: 'custom',
    data: {
      isInIteration: false,
      isInLoop: false,
      sourceType: 'code',
      targetType: 'end',
    },
    zIndex: 0,
  })
}

const codeNode = graph.nodes.find(node => String(node.id) === codeNodeId)
if (!codeNode) {
  throw new Error(`Code node ${codeNodeId} not found`)
}

codeNode.data.title = '轮询图片结果'
codeNode.data.variables = [
  {
    value_selector: [taskNodeId, 'task_id'],
    value_type: 'string',
    variable: 'task_id',
  },
]
codeNode.data.outputs = {
  task_id: { type: 'string', children: null },
  task_status: { type: 'string', children: null },
  output_image: { type: 'string', children: null },
  message: { type: 'string', children: null },
  output: { type: 'string', children: null },
}
codeNode.data.code = `import json
import time
import requests

TOKEN = "${token}"
MAX_ATTEMPTS = 36
INTERVAL_SECONDS = 5

def _pack(status: str, task_id: str, image: str = "", message: str = "") -> dict:
    payload = {
        "stream": True,
        "taskStatus": status,
        "taskId": task_id,
        "imageUrl": image,
        "content": f"![poster]({image})" if image else "",
        "message": message,
    }
    return {
        "task_id": task_id,
        "task_status": status,
        "output_image": image,
        "message": message,
        "output": json.dumps(payload, ensure_ascii=False),
    }

def main(task_id: str) -> dict:
    task_id = str(task_id or "").strip()
    if not task_id:
        return _pack("FAILED", "", "", "缺少 task_id")

    token = TOKEN.strip()
    if not token or token == "REPLACE_WITH_MODELSCOPE_TOKEN":
        return _pack("FAILED", task_id, "", "ModelScope Token 未配置")

    url = f"https://api-inference.modelscope.cn/v1/tasks/{task_id}"
    headers = {
        "Authorization": f"Bearer {token}",
        "X-ModelScope-Task-Type": "image_generation",
    }

    last_status = "RUNNING"
    last_message = ""

    for attempt in range(MAX_ATTEMPTS):
        try:
            resp = requests.get(url, headers=headers, timeout=15)
            if resp.status_code != 200:
                last_status = "RUNNING"
                last_message = f"HTTP {resp.status_code}: {resp.text[:200]}"
            else:
                data = resp.json()
                last_status = str(
                    data.get("task_status")
                    or data.get("data", {}).get("task_status")
                    or "RUNNING"
                )
                output_images = (
                    data.get("output_images")
                    or data.get("data", {}).get("output_images")
                    or data.get("output", {}).get("images")
                    or data.get("data", {}).get("output", {}).get("images")
                    or []
                )

                if output_images:
                    return _pack("SUCCEED", task_id, str(output_images[0]), "")

                if last_status.upper() in ("FAILED", "FAIL", "ERROR"):
                    return _pack("FAILED", task_id, "", "图片生成失败")
        except Exception as e:
            last_status = "RUNNING"
            last_message = f"查询异常: {str(e)}"

        if attempt < MAX_ATTEMPTS - 1:
            time.sleep(INTERVAL_SECONDS)

    return _pack(
        "RUNNING",
        task_id,
        "",
        f"图片仍在生成中，已等待 {MAX_ATTEMPTS * INTERVAL_SECONDS} 秒。请稍后用 taskId 继续查询。",
    )
`

const endNode = graph.nodes.find(node => String(node.id) === endNodeId)
if (!endNode) {
  throw new Error(`End node ${endNodeId} not found`)
}

endNode.data.outputs = [
  {
    value_selector: [codeNodeId, 'output'],
    value_type: 'string',
    variable: 'output',
  },
]

fs.writeFileSync(output, YAML.stringify(doc), 'utf8')
console.log(output)
