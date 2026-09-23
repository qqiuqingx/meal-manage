# Phase 03 本地构建上传与前端独立发布

## 执行前规则检查

- 重新读取 AGENTS.md，读取 status.yaml、00-overview.md 与前两个 Phase 交付记录。
- 确认本机可以访问 nodejs.org 下载并校验 .nvmrc 指定的 Node；管理脚本、Compose 配置和 Nginx 配置已通过 Phase 04 部署到目标仓库。
- 确认 DEPLOY_TARGET 由调用者环境提供；不得提交服务器连接信息或 SSH 密钥。

## 目标

提供开发机的一条命令，完成依赖检查、本地构建、版本元数据、压缩校验、rsync 上传和远程激活。

## 前置依赖

- Phase 01 已完成：.nvmrc、package-lock 和 frontend 运行载体可用。
- Phase 02 已完成：服务器端 install、rollback 和压缩包协议可用。

## 输入与输出

输入：

- Git 干净工作区。
- DEPLOY_TARGET、DEPLOY_BASE_DIR、FRONTEND_RELEASE_ROOT 等环境变量。

输出：

- scripts/deploy-frontend-local.sh。
- release.json、压缩包与 SHA256 文件。

## 本阶段实施约束

- 默认拒绝 eladmin-web、docker 和发布脚本存在未提交改动的工作区。
- 不提交 dist，也不上传 node_modules、源码、.env、私钥或日志。
- 本地构建、压缩校验、远程 install 全部成功前不得报告发布成功。
- node_modules 缺失，或 package/lock、Node/npm 版本、操作系统/架构指纹变化时执行 npm ci；其余情况复用本地依赖。

## 涉及文件

新增：

- scripts/deploy-frontend-local.sh

修改：

- docs/deployment/前端静态产物发布与回退.md

## 实施步骤

### Step 1：本地构建前检查

1. 校验 bash、git、curl、tar、shasum、rsync、ssh 等基础工具。
2. 若本机 Node 与 .nvmrc 不一致或未安装，从官方发行包准备项目本地 Node 24.21.0，校验 SHA256 后以子进程 PATH 使用，不修改系统默认版本。
3. 校验 Git 提交存在且相关工作树干净。
4. 计算 package.json、package-lock.json、Node/npm 版本和平台组合指纹；必要时 npm ci --legacy-peer-deps。
5. 设置 `VUE_APP_BASE_API=/` 后执行 npm run build:prod；失败时不连接服务器。

### Step 2：生成不可变产物

1. 以时间和 Git 短提交号生成 release ID。
2. 复制 dist 到临时工作目录，不修改项目 dist。
3. 生成 release.json，包含 releaseId、完整 Git commit、builtAt、packageLockSha256、Node 版本。
4. 打包为 frontend-release ID.tar.gz，使用 shasum -a 256 生成同名校验文件。
5. 本地复核压缩包内容和校验值。

### Step 3：上传和激活

1. 用 rsync 上传压缩包和校验文件到 incoming 下的临时名称。
2. 上传完成后用远程 mv 改为正式 incoming 名称，避免读到半包。
3. 先通过临时文件加 rename 原子更新远端 scripts/manage-frontend-release.sh，再 SSH 调用其 install。传入与 Compose env-file 一致的 FRONTEND_RELEASE_ROOT、DEPLOY_BASE_DIR 和 ENV_FILE。
4. 直接返回远程命令退出码；成功后输出 release ID、commit、health URL 和回退命令。
5. 清理仅由脚本创建的本地临时包。

### Step 4：文档

文档示例只使用占位连接信息：

~~~
export DEPLOY_TARGET=deploy@example-host
export DEPLOY_BASE_DIR=/data/meals/meal-manage
export FRONTEND_RELEASE_ROOT=/data/meals/frontend
bash scripts/deploy-frontend-local.sh
~~~

同时说明 list、默认 rollback、指定版本 rollback。

## 验证方式

- bash -n scripts/deploy-frontend-local.sh。
- 本地通过测试夹具验证产物协议、权限归一化、上传失败和错误校验值；完整预发发布与回退演练属于 Phase 04。
- 从系统 Node 25.6.1 启动发布器时，已验证其自动下载并校验项目本地 Node 24.21.0，子进程使用新版本而系统 `node -v` 保持不变；因 package-lock 尚未提交，发布器按设计在 SSH 前停止。
- 确认发布服务器没有 npm、node、webpack 或前端 Docker build。
- 未修改 lock 时再次发布确认不执行 npm ci；修改 lock 后确认会重新安装。

## 完成标准

- 一条本地命令可构建、上传并调用远端激活；预发完整演练由 Phase 04 记录。
- 服务器只接收校验后的静态产物。
- 每个线上版本可由 release ID 和 Git commit 精确定位。

## 状态

in progress

## 阶段交付记录

已完成本地发布器代码；精确 Node 24.21.0 的干净 npm ci 和生产构建通过。尚需在目标 staging 环境验证 SSH/rsync、远端脚本原子更新、完整上传激活和失败清理；仓库相关代码提交后才能通过发布器的 clean-worktree 检查。
