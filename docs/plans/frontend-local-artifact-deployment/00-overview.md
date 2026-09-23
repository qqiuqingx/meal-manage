# 前端本地构建与可回退静态产物发布实施计划

## 背景

当前前端随 Docker 镜像在生产服务器构建。服务器只有 4 核、3.7GB 内存，且与 MySQL 同机运行；Vue CLI 3、Webpack 4、Babel 和 Terser 的构建已发生过内存抖动。

前端镜像目前同时承担 Nginx 运行环境和 dist 构建产物。部署脚本要求 mealserver 与 mealweb 使用同一个 IMAGE_TAG，回退脚本也按成对镜像回退。这不适合本地构建、服务器只切换已验证静态产物、前端独立回退的目标。

## 目标

1. 在开发机按锁定依赖构建 dist。
2. 以“时间戳加 Git 短提交号”创建不可变前端版本。
3. 自动压缩、校验和上传到服务器 releases 目录。
4. 通过 current 软链接原子切换版本。
5. 仅重建 frontend Nginx 容器，不构建 Node 镜像，也不停止 backend。
6. 用 release.json 和 HTTP 检查确认新版本；失败时自动恢复旧版本。
7. 保留最近 5 个版本，支持默认回退和指定版本回退。

## 非目标

- 不迁移 Vue 2 到 Vue 3，也不迁移 Vite。
- 不修改业务接口、数据库、权限或后端业务逻辑。
- 不引入镜像仓库或 CI；后续 CI 可以调用同一产物协议。
- 不新增 config.js。当前前端 API 使用相对路径并由 Nginx 代理，没有新增运行时配置的需要。

## 影响范围

| 模块 | 影响 |
| --- | --- |
| eladmin-web | 固化 Node 24.21.0 和 package-lock，业务代码不变。生产构建显式使用相对 API 地址。 |
| docker | frontend 改为固定 Nginx 1.30.5 镜像加静态目录挂载；backend 独立保留镜像版本。 |
| scripts | 增加本地发布、服务器版本管理；既有 Git 部署和回退脚本转为后端专用。 |
| docs | 增加部署、自动回退、指定版本回退和首次切换手册。 |

## 规则来源

- /Users/qqx/.codex/AGENTS.md：架构、提交和验证约束。
- /Users/qqx/job/code/eladmin-mp/AGENTS.md：本仓库 Node、Docker 和前后端分离规则。
- enterprise-planner 技能：按阶段持久化计划、依赖和交付记录。

## 实施约束与确认门禁

- 生产服务器不得执行 npm install、npm ci、Webpack 或前端 Docker build。
- 生产服务器不要求安装 Node.js 或 npm；只有开发机本地构建发布器需要 Node.js 24.21.0。
- release 目录不可覆盖。清理只能删除受控 release ID 目录，且不能删除 current 或 previous。
- current 与 releases 必须在同一文件系统，确保软链接替换原子完成。
- 所有切换和清理持有 flock 锁，避免并发发布、回退相互覆盖。
- 不将服务器地址、账号、私钥、密码或业务密钥提交到仓库。本地脚本从 DEPLOY_TARGET 等环境变量读取连接信息。
- 用户已确认旧前端 Dockerfile 没有外部调用方，Phase 01 直接删除该构建路径，不保留双链路。deploy-from-github.sh 和 rollback.sh 的服务器定时入口仍需在首次切换前确认并安排窗口。
- 前端回退只恢复静态资源。联动发布若涉及接口或数据库变化，必须记录兼容版本组合，按操作手册分别回退前端 release 和后端 image。
- 首次切换前保留当前 mealweb 镜像、旧 Git commit 和镜像 tag；首次自动回退和人工回退演练成功前禁止清理。

## 跨阶段契约

### 发布目录

服务器根目录由 FRONTEND_RELEASE_ROOT 决定，默认 /data/meals/frontend：

~~~
<FRONTEND_RELEASE_ROOT>/
  releases/
    20260922190000-a1b2c3d/
  current -> releases/20260922190000-a1b2c3d
  previous -> releases/20260922181500-d4e5f6g
  incoming/
  lock/
~~~

release ID 格式为 YYYYMMDDHHMMSS-加 Git 短提交号。服务器只接受此格式。

### 产物协议

压缩包根目录包含所有 dist 文件和 release.json。release.json 含 releaseId、gitCommit、builtAt、packageLockSha256、nodeVersion。压缩包旁存在独立 SHA256 文件，服务器校验后才可解压。前端构建显式设置 `VUE_APP_BASE_API=/`，保持 Docker 构建时由 Nginx 代理 REST API 的行为；`VUE_APP_WS_API` 继续使用当前生产构建配置中的公开地址。

本地发布默认拒绝 Git 工作区包含未提交的前端、Docker 或发布脚本变更，保证每个发布版本可从 Git 追溯。node_modules 缺失，或 package/lock、Node/npm 版本、操作系统/架构指纹变化时，才执行 npm ci --legacy-peer-deps。

本地发布器在开发机的 `eladmin-web/.node-runtime/` 缓存 Node 24.21.0；校验 nodejs.org 发布的 SHA256 后，仅为本次发布进程设置 PATH，不改变系统默认 Node。生产服务器不安装 Node.js。

### 服务与回退契约

- frontend 将 FRONTEND_RELEASE_ROOT/current 以只读方式挂载到 /usr/share/nginx/html，只提供静态服务和既有 API 代理。发布目录和普通文件权限分别归一化为 0755、0644，使 Nginx worker 可读。
- backend 使用 BACKEND_IMAGE_TAG，frontend 不再使用 IMAGE_TAG。
- HTTP 健康检查读取 http://127.0.0.1:18080/release.json，响应 releaseId 必须与目标一致。
- manage-frontend-release.sh 提供 install、rollback 和 list。
- rollback.sh 改为后端镜像与 Git 回退；前端回退使用 manage-frontend-release.sh rollback。

## Phase 列表

| Phase | 名称 | 状态 |
| --- | --- | --- |
| 01 | 固化本地构建与 Nginx 运行载体 | in progress |
| 02 | 服务器端版本生命周期与自动回退 | in progress |
| 03 | 本地构建上传与前端独立发布 | in progress |
| 04 | 既有部署链切换、首次上线和回退演练 | in progress |

Phase 03 完成发布工具及本地验证；Phase 04 先把新版 Compose、Nginx 配置和脚本更新到预发，再使用 Phase 03 工具执行预发首次切换和端到端演练。首次切换前没有静态 current 可供自动恢复，失败时须按 Phase 04 手册恢复旧 Git commit 与旧 mealweb 镜像；后续发布才由管理脚本自动恢复到旧 release。

## 风险与回退策略

| 风险 | 处理 |
| --- | --- |
| 上传中断、校验失败 | 只影响 incoming 临时文件，不触碰 current。 |
| 新版本资源路径错误 | release.json、index.html 和首个 JS/CSS 都要通过 HTTP 校验。 |
| 软链接切换后容器仍读旧目录 | 每次切换均执行 compose up -d --force-recreate --no-deps frontend。 |
| 缓存造成回退不生效 | index.html、release.json 禁止缓存；带 hash 静态资源长期缓存。 |
| 健康检查失败 | 已有 current 时恢复 old current 和 old previous，重建 frontend 并确认 old releaseId。 |
| 首次架构切换失败 | 没有旧静态 current 时，按操作手册恢复旧 Git commit 与保留的 mealweb 旧镜像。 |
