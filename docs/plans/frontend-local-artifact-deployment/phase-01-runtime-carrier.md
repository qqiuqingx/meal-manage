# Phase 01 固化本地构建与 Nginx 运行载体

## 执行前规则检查

- 重新读取用户级和仓库根目录 AGENTS.md。
- 确认本阶段只涉及前端构建、Docker 运行配置和文档，不改业务接口、数据库或后端业务逻辑。
- 检索旧前端 Dockerfile 的仓库内调用方；用户已确认没有外部调用方，因此本阶段直接删除旧 Dockerfile。
- 确认 FRONTEND_RELEASE_ROOT 与 releases 在同一文件系统，部署账号对其有读写权限；Nginx worker 对已发布目录有遍历权限、对静态文件有读取权限。

## 目标

本地能按锁定依赖构建前端；frontend 容器只负责 Nginx 服务与 API 代理。

## 前置依赖

无。

## 输入与输出

输入：

- 当前 eladmin-web/package.json、被忽略的 package-lock.json。
- 当前 compose、Nginx 配置和前端 Dockerfile。

输出：

- 被 Git 管理的 package-lock.json 与 .nvmrc。
- 从 current 静态目录读取文件的 frontend 服务。
- 可保证发布与回退行为的 Nginx 缓存规则。

## 本阶段实施约束

- 不改 Vue、Element UI、业务源码和 API 路径。
- 不提交 docker/.env；新变量只写 docker/.env.example 与操作手册。
- package-lock 和 package.json 必须用 npm ci --legacy-peer-deps 一并验证。
- 前端 build 调用方迁移并完成首次切换预案后，删除不再使用的 Dockerfile。

## 涉及文件

新增：

- eladmin-web/.nvmrc

修改：

- eladmin-web/.gitignore
- eladmin-web/package-lock.json
- docker/docker-compose.yml
- docker/.env.example
- docker/mealweb/nginx.conf

删除：

- docker/mealweb/Dockerfile

## 实施步骤

### Step 1：固化本地依赖

1. 在 .nvmrc 写入 Node 24.21.0。Node 20 已结束官方支持，当前锁定的 cross-env 10.1.0 要求 Node 20 或更高。
2. 删除 .gitignore 中 package-lock.json 的忽略规则，将现有 lock 文件提交。
3. 在干净临时目录使用 Node 24.21.0 执行 npm ci --legacy-peer-deps 与 `VUE_APP_BASE_API=/ npm run build:prod`。保留 `.env.production` 当前的 VUE_APP_WS_API 值，避免改变现有 WebSocket 地址行为。
4. 失败时仅修复 package.json 和 lock 文件一致性，不升级无关依赖。

### Step 2：拆分运行版本

1. backend 镜像使用 BACKEND_IMAGE_TAG。
2. frontend 固定使用 nginx:1.30.5-alpine3.24，不声明 build，也不使用 IMAGE_TAG。
3. frontend 只读挂载 FRONTEND_RELEASE_ROOT/current 到 /usr/share/nginx/html，并只读挂载仓库的 docker/mealweb/nginx.conf 到 /etc/nginx/conf.d/default.conf。
4. 保留端口、网络、健康检查和已有 API、auth、file、avatar、doc.html、druid 代理；移除 frontend 对 backend 的 Compose 启动依赖，使两者可独立启动和重建。
5. 在 docker/.env.example 说明 BACKEND_IMAGE_TAG、FRONTEND_RELEASE_ROOT、FRONTEND_RELEASE_KEEP。

### Step 3：缓存和探针

1. 为 /index.html 与 /release.json 增加 no-store、no-cache、must-revalidate。
2. 仅对 /static/ 下构建生成的 js、css、字体和图片设置一年 immutable 缓存，避免缓存 API 响应或未带指纹的根目录文件。
3. release.json 仅包含版本元数据，不能包含密钥、地址或用户数据。
4. 验证 API、auth、file、avatar、doc.html、druid 代理不受缓存 location 影响。

### Step 4：移除旧 Dockerfile

1. 保留当前运行 mealweb 镜像 tag、镜像 ID 和部署 Git commit 的记录，供首次切换失败时恢复。
2. 删除 docker/mealweb/Dockerfile，仓库中的 frontend Compose 服务不再具有 Node 构建路径。

## 验证方式

- Node 24.21.0 下 npm ci --legacy-peer-deps、`VUE_APP_BASE_API=/ npm run build:prod` 成功。
- docker compose -f docker/docker-compose.yml --env-file docker/.env.example config 成功。
- 使用临时 current 目录启动 frontend；验证 Nginx worker 可读取只读挂载文件，index.html、release.json 不缓存，hash JS 仍长期缓存。
- nginx -t 通过，代理路径冒烟正常。
- git diff --check 通过。

## 完成标准

- 依赖可从 Git lock 文件完整恢复。
- compose frontend 不含 Node 构建步骤，可从 current 目录提供静态文件。
- 缓存策略满足新版本切换和旧资源缓存。
- Dockerfile 删除决定有调用方证据和首次切换保障。

## 状态

in progress

## 阶段交付记录

已完成代码：固定 Node 24.21.0、纳入 package-lock、拆分 Compose 镜像 tag、只读挂载静态 current 与 Nginx 配置、配置缓存策略并删除旧前端 Dockerfile。用户确认 Dockerfile 没有外部调用方。已用官方 SHA256 校验的 Node 24.21.0 在临时源码副本执行干净 `npm ci --legacy-peer-deps` 和生产构建；Docker Compose 配置校验通过。尚需在 Linux 预发容器执行 `nginx -t` 和代理冒烟，并记录首次切换状态。
