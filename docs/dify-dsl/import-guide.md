# 导入与绑定

1. 在 Dify 工作室逐个选择“导入 DSL”。
2. 打开 LLM 节点，将占位模型替换为工作区已配置模型。
3. 预览时填写三个 JSON 参数和 message，确认没有变量缺失。
4. 发布应用，在“访问 API”中创建该 App 独立 API Key。
5. 在宿识家管理端选择相同 moduleKey，填写名称、Endpoint、App API Key；绑定类型必须与参数合同中的 Chatflow/Workflow 一致。
6. 保存并校验。校验通过后再启用；同模块其他候选会自动停用。
7. 通过宿识家用户端调用，确认请求日志中存在三个 JSON 参数且未泄露 API Key。

海报应用的 DSL 会在 Dify Workflow 内部生成结构化视觉提示词，并通过 HTTP 节点调用 ModelScope 创建图片任务；宿识家后端默认只调用 Dify 并接收图片 URL。Token 不得提交到源码或 DSL，请在 Dify 凭据/环境变量中替换 MODELSCOPE_TOKEN 占位符。
