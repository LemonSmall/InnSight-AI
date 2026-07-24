# InnSight AI

InnSight AI 是一套面向酒店、民宿和度假门店的 AI 运营工作台。项目围绕酒店日常经营中的内容发布、房态分析、智能定价、营销策略、知识沉淀和平台管理等场景，将 AI 生成能力和酒店基础数据结合，帮助中小酒店团队提升运营效率。

本仓库保留项目核心代码：Vue 3 前端、Spring Boot 多模块后端，以及 MySQL 初始化 SQL。

## 功能模块

- **酒店基础资料管理**：维护酒店名称、地区、房型房量、特色标签、目标客群、周边信息等基础数据。
- **AI 内容发布**：提供小红书、朋友圈、公众号、营销海报、视频口播等内容生成能力。
- **AI 店长助手**：根据酒店资料、房型配置和经营数据生成运营建议、执行清单和日常提醒。
- **智能定价**：结合房型、房量、历史入住率和经营周期，生成价格参考和调整建议。
- **营销策略**：根据目标客群、周边资源和当前经营情况生成阶段性营销方案。
- **房态导入**：支持表格和图片导入房态数据，并将解析结果保存到系统历史中。
- **内容历史**：统一保存 AI 生成记录，支持查看、复用、复制、下载和任务状态追踪。
- **知识库管理**：支持上传酒店资料并整理为可复用的结构化知识。
- **管理端后台**：支持租户、员工、AI 配置、算力流水、调用日志和平台参数管理。

## 技术栈

### 前端

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Tailwind CSS
- Axios
- Lucide Icons
- Chart.js
- XLSX

### 后端

- Java
- Spring Boot
- Spring Security
- MyBatis-Plus
- Maven 多模块架构
- JWT 登录认证
- 多租户上下文隔离
- RESTful API
- AI Provider 抽象封装
- Dify / 大模型服务调用集成

### 数据库

- MySQL
- 初始化 SQL 脚本
- 覆盖租户、员工、房型、房态、算力流水、AI 生成历史、AI 素材、知识库、模型配置等核心表结构

## 目录结构

```text
InnSight-AI/
├── public/                         # 前端静态资源
├── src/                            # Vue 前端源码
│   ├── api/                        # API 请求封装
│   ├── components/                 # 公共组件
│   ├── router/                     # 前端路由
│   ├── stores/                     # Pinia 状态管理
│   ├── utils/                      # 内容解析、导出、渲染等工具函数
│   └── views/                      # 业务页面和管理端页面
├── sushijia-server/                # Spring Boot 后端源码
│   ├── sql/init.sql                # MySQL 初始化脚本
│   ├── sushijia-admin/             # 管理端服务模块
│   ├── sushijia-ai/                # AI 调用与模型服务模块
│   ├── sushijia-common/            # 通用响应、异常、工具类
│   ├── sushijia-framework/         # 安全、租户、审计等框架能力
│   ├── sushijia-hotel/             # 酒店端业务接口
│   └── sushijia-repository/        # 实体类与 MyBatis Mapper
├── package.json                    # 前端依赖与脚本
├── vite.config.ts                  # Vite 配置
└── README.md
```

## 本地运行

安装前端依赖：

```bash
npm install
```

启动前端开发环境：

```bash
npm run dev
```

构建前端：

```bash
npm run build
```

数据库初始化脚本：

```text
sushijia-server/sql/init.sql
```

后端构建：

```bash
cd sushijia-server
mvn clean package
```

