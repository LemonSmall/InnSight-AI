# 内容创作官接入 ModelScope 文生图

主 DSL 会输出以下字段：

- `image_required`
- `image_size`
- `image_count`
- `image_prompt`

为了避免不同 Dify 版本的 HTTP Request 节点 DSL schema 导致导入后页面崩溃，请在 Dify 内容创作官画布中手动添加以下节点。

## 1. JSON 提取节点

在内容生成 LLM 节点后增加「参数提取器」或「代码执行」节点，提取：

- `image_required`
- `image_prompt`
- `image_size`
- `image_count`

代码节点示例：

```python
import json

def main(text: str) -> dict:
    data = json.loads(text)
    return {
        "image_required": bool(data.get("image_required")),
        "image_prompt": data.get("image_prompt", ""),
        "image_size": data.get("image_size", "3:4"),
        "image_count": int(data.get("image_count") or 1),
        "content_json": data
    }
```

## 2. 条件分支

- `image_required = true`：进入 ModelScope 图片生成分支。
- `image_required = false`：直接进入最终回复节点。

## 3. 创建图片任务

Method:

```text
POST
```

URL:

```text
https://api-inference.modelscope.cn/v1/images/generations
```

Headers:

```text
Authorization: Bearer {{MODELSCOPE_TOKEN}}
Content-Type: application/json
X-ModelScope-Async-Mode: true
```

Body:

```json
{
  "model": "Tongyi-MAI/Z-Image-Turbo",
  "prompt": "{{图片提示词变量}}"
}
```

将 `{{图片提示词变量}}` 绑定到 JSON 提取节点输出的 `image_prompt`。

从响应 body 提取：

```text
task_id = $.task_id
```

不要把 ModelScope Token 写进 DSL 文件或提交到 Git。请在 Dify 环境变量、凭据管理或部署环境中配置。

## 4. 等待

如果当前 Dify 版本支持等待节点，等待 5 秒后查询任务。

## 5. 查询图片任务

Method:

```text
GET
```

URL:

```text
https://api-inference.modelscope.cn/v1/tasks/{{task_id}}
```

Headers:

```text
Authorization: Bearer {{MODELSCOPE_TOKEN}}
X-ModelScope-Task-Type: image_generation
```

Body: none

## 6. 解析结果

```python
import json

def main(body: str) -> dict:
    data = json.loads(body)
    output_images = data.get("output_images") or []
    return {
        "task_status": data.get("task_status", ""),
        "output_image": output_images[0] if output_images else "",
        "raw": data
    }
```

## 7. 轮询策略

- `task_status = SUCCEED`：把 `output_image` 合并到最终结果。
- `task_status = FAILED`：直接返回图片生成失败，不伪造图片、不使用兜底图。
- 其他状态：每 5 秒查询一次，最多 10 次。
- 当前 Dify 版本不支持循环时：返回 `task_id` 和 `GENERATING`，由宿识家后端定时查询。

## 8. 最终结果

最终返回给宿识家后端的 JSON 建议为：

```json
{
  "content": {},
  "image_required": true,
  "image_status": "SUCCEED",
  "image_url": "https://...",
  "image_task_id": "..."
}
```

项目后端负责：

1. 保存文本、图片 URL、用户参数和生成时间。
2. 记录调用日志及算力流水。
3. 将流式文本通过 SSE 实时转发给用户端。
4. 图片异步生成完成后通过任务查询接口更新页面。

