# Phase 04 既有部署链切换、首次上线和回退演练

## 执行前规则检查

- 重新读取 AGENTS.md，读取 status.yaml、00-overview.md 和前三个 Phase 交付记录。
- 记录当前生产 Git commit、mealserver image tag、mealweb image tag、容器 ID 和对外 URL。
- 确认 deploy-from-github.sh 与 rollback.sh 的外部调用方，前端镜像语义变化已进入切换窗口。
- 确认首次切换前不执行 mealweb 旧镜像清理。

## 目标

让既有后端 Git 部署和回退流程适配新的前端静态产物流程，在预发和生产完成首次切换、自动回退和人工回退演练。

## 前置依赖

- Phase 01 完成 current 挂载和 Nginx 配置。
- Phase 02 完成版本管理和自动回退。
- Phase 03 完成本地构建上传。

Phase 04 负责把新版运行配置部署到预发目标，再由 Phase 03 发布器执行首个真实静态版本。Phase 03 的发布器本身和归档协议可先完成本地验证，但完整预发发布结果在本阶段记录，避免依赖尚未部署的 Compose 配置。

## 输入与输出

输入：

- 当前 deploy-from-github.sh、rollback.sh、compose 配置。
- 预发发布与回退记录。

输出：

- 后端 Git 部署不再构建或重启 frontend。
- 前后端独立回退的运行手册和首次切换证据。

## 本阶段实施约束

- 移除单一 IMAGE_TAG 对前后端的强制绑定。
- 后端发布不停止 frontend，前端发布不停止 backend。
- 不根据时间戳猜测前后端兼容关系；联动回退使用发布单记录的具体版本。
- 生产演练通过前保留旧 mealweb 镜像和旧 Git commit。

## 涉及文件

新增：

- docs/deployment/前端静态产物发布与回退.md

修改：

- scripts/deploy-from-github.sh
- scripts/rollback.sh
- README.md
- docker/docker-compose.yml

## 实施步骤

### Step 1：后端 Git 部署脚本

1. 以 BACKEND_IMAGE_TAG 管理后端，不再匹配 mealweb 镜像。
2. 检测到 eladmin-web 改动时输出“需要执行本地前端产物发布”，服务器不再构建前端。
3. 移除 node:16 拉取、frontend build、mealweb 镜像清理。
4. Maven 构建前只停止 backend（frontend 和 MySQL 保持运行）；构建或新容器健康检查失败时自动按旧 backend tag 重新启动。成功后只执行 `compose up -d --no-build --no-deps backend`，不执行 `compose down`，不重启 frontend。
5. current 不存在时明确失败，提示先完成首次前端 release。

### Step 2：后端回退脚本

1. rollback.sh 改为只回退 mealserver 镜像和对应 Git commit。
2. 移除 mealweb 镜像检查和 frontend 重启。
3. 输出当前 frontend release ID；文档给出前端指定版本回退命令。
4. 不自动选择“最新前端旧版本”，避免独立发布节奏造成接口不兼容。

### Step 3：预发首次切换

1. 记录旧 commit、mealserver/mealweb tag 与 image ID、容器 ID 和业务冒烟结果；确认旧 mealweb 镜像仍可运行。
2. 确认部署仓库无 tracked 改动并记录当前 HEAD 后，运行服务器现有的 `/data/meals/deploy-bootstrap.sh` 更新代码。它会执行仓库中的新版 deploy-from-github.sh；本次仅有前端/运行配置变更时，日志应显示需要本地前端发布并跳过 backend 构建/重启和 frontend 重建。如检测到后端源码变更，先核实，不继续切换。
3. 更新预发 env-file 中的 BACKEND_IMAGE_TAG 为当前正在运行的 mealserver tag，保留其他私有配置。旧共享 IMAGE_TAG 只记录旧 mealweb tag 供恢复命令显式传入，不要求保存在新 env-file。
4. 使用 Phase 03 本地发布器构建、上传与预发代码提交对应的 release，首次激活并强制重建 frontend。
5. 验证首页、登录、hash JS、API 代理、上传资源和 release.json；确认静态文件由容器 Nginx worker 读取。
6. 完成默认回退、指定回退、恢复新版本和后续版本的健康检查失败自动回退演练。首次切换失败时按旧 Git commit 与 mealweb image ID 恢复步骤演练。

### Step 4：生产切换与交付

1. 确认生产部署仓库已包含新 compose、Nginx 配置和脚本，并在生产 env-file 中将 BACKEND_IMAGE_TAG 设为当前 mealserver tag；保留所有其他私有配置。旧 IMAGE_TAG 记录在切换单中，失败恢复时由命令行显式传入。
2. 按预发通过的顺序使用本地发布器上传、激活并仅重建 frontend。
3. 观察 Nginx error log、frontend health、backend 和 MySQL 存活。
4. 冒烟验证后记录实际 release ID 和兼容的 backend image tag。
5. 更新操作手册，包含发布、list、回退、磁盘检查、自动回退日志和首次切换失败恢复旧镜像的步骤。
6. 在一个完整发布周期稳定后，按保留策略清理遗留 mealweb 镜像。

## 验证方式

- bash -n scripts/deploy-from-github.sh、scripts/rollback.sh。
- docker compose config 通过，backend 和 frontend 不再共享镜像版本变量。
- 预发完成发布、默认回退、指定回退、自动回退。
- 后端单独发布不重启 frontend；前端发布不重启 backend。
- 后端构建失败或新 backend 健康检查失败时自动恢复旧 tag；frontend 在后端构建期间保持运行。
- 前端发布期间服务器没有 npm、node、webpack 或前端 Docker build。
- 生产首次切换后 MySQL、Nginx 和主要业务访问正常。

## 完成标准

- 生产服务器不再具备前端源码构建链路。
- 前后端版本独立、可追溯、可按发布单组合。
- 发布与回退不无故停止另一个服务。
- 预发、生产均有自动和人工回退证据。

## 状态

in progress

## 阶段交付记录

已完成代码部分：后端部署脚本独立管理 BACKEND_IMAGE_TAG、镜像记录 Git commit，只重建 backend；构建失败时恢复旧 backend；回退脚本只操作 backend；Compose、README 和操作手册已更新。未完成实际运维部分：外部调用方确认、预发仓库切换、首个静态 release 发布与回退演练、生产切换和旧 mealweb 镜像清理。完成时记录预发和生产 release ID、旧镜像 tag、自动回退和人工回退耗时、实际磁盘占用、保留版本数、兼容版本组合和遗留运维限制。
