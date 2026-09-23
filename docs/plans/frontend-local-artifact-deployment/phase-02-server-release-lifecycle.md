# Phase 02 服务器端版本生命周期与自动回退

## 执行前规则检查

- 重新读取 AGENTS.md，读取 status.yaml、00-overview.md 和 Phase 01 交付记录。
- 确认目标服务器具备 bash、tar、sha256sum、flock、python3、docker compose v2（支持 `config --format json`）、curl；缺少任一命令直接失败。
- 不要求目标服务器安装 Node.js、npm 或 Webpack。
- 确认 releases 根目录由部署账号独占管理，不是工作区、根目录或未解析环境变量。

## 目标

提供服务器端脚本，安全安装、原子激活、健康检查、失败自动回退、列举和清理前端版本。

## 前置依赖

- Phase 01 已完成，frontend current 挂载、Nginx 缓存和 health URL 已确定。

## 输入与输出

输入：

- 本地上传的压缩包、SHA256 文件和 release ID。
- current、previous 软链接与 frontend 容器。

输出：

- scripts/manage-frontend-release.sh。
- 可审计、可回退的 releases 目录。

## 本阶段实施约束

- 只管理 FRONTEND_RELEASE_ROOT/release 下符合 release ID 格式的目录。
- 切换、回退、清理全程持有 flock 锁。
- 校验、解压或 health check 失败不得覆盖旧 current。
- 不对未验证路径执行递归删除。
- install、rollback、list 都是实际运维功能，不增加测试专用生产开关。
- 归档只接受普通文件和目录，拒绝符号链接、硬链接、设备文件、绝对路径、父目录穿越和重复成员。
- 解包后将目录和普通文件权限归一化为 0755、0644；不依赖本地 tar 包的所有者或权限。
- 健康检查失败时同时恢复 current 和 previous。若首次架构切换前没有旧 current，管理脚本不能恢复旧镜像；按 Phase 04 操作手册执行架构级恢复。

## 涉及文件

新增：

- scripts/manage-frontend-release.sh
- scripts/tests/test-frontend-release-manager.sh

修改：

- docs/deployment/前端静态产物发布与回退.md

## 实施步骤

### Step 1：命令边界

脚本支持：

| 命令 | 行为 |
| --- | --- |
| install 压缩包 校验文件 release ID | 校验、安装、激活并在失败时自动回退。 |
| rollback 可选 release ID | 无 ID 时切 previous，有 ID 时切指定版本。 |
| list | 列出 release ID、Git commit、构建时间和 current、previous 标记。 |

启动时校验命令、路径、release ID 格式、compose 文件和 frontend 配置。release ID 已存在则拒绝覆盖。

### Step 2：安全安装

1. 校验 SHA256。
2. 检查 tar 列表，拒绝绝对路径和父目录穿越。
3. 解压到同文件系统的 staging 目录。
4. 校验 index.html、release.json，校验 release.json 中 releaseId 与入参一致。
5. 用 rename 将 staging 变为 releases/release ID。
6. 失败仅清理本次 staging 和 incoming 临时文件；已完成目录保留以供审计，不覆盖同 ID 的 release。

### Step 3：原子激活和自动恢复

1. 读取 current 真实路径作为 old release。
2. 用 previous.next 和 mv -T 原子更新 previous。
3. 用 current.next 和 mv -T 原子更新 current。
4. 执行 compose up -d --force-recreate --no-deps frontend。
5. 轮询 release.json，必须返回目标 release ID；同时验证 index.html 引用的至少一个 JS/CSS 返回 200。
6. 若失败，原子恢复 old current 和原 old previous，重建 frontend，再确认 old releaseId；恢复失败保留失败版本并非零退出。首次切换没有 old current 时报告需按手册恢复旧镜像，不宣称自动回退成功。

### Step 4：回退和清理

1. 默认回退使用 previous，指定回退要求目标目录、index.html、release.json 都存在。
2. 成功回退后交换 current 与 previous。
3. 成功发布后保留最新 FRONTEND_RELEASE_KEEP 个 release，默认 5。
4. current、previous 与不匹配 release ID 的目录不参与清理。

## 验证方式

- bash -n scripts/manage-frontend-release.sh。
- 运行 scripts/tests/test-frontend-release-manager.sh，在临时 release root 安装两个最小静态版本，检查 current、previous、权限和 list。
- 用 SHA256 错误包、路径穿越/链接包、缺少 index.html 包、releaseId 不匹配包验证 current 和 previous 不变。
- 让 release.json 健康检查失败，验证自动恢复旧版本。
- 验证默认和指定 ID 回退。
- 并发执行两个命令，确认锁阻止破坏性并发。

## 完成标准

- 新版本在完整校验和 HTTP 校验成功前不成为 current。
- 任一失败路径可恢复旧版本并返回非零。
- 版本可列举、可指定回退、可安全保留和清理。

## 状态

in progress

## 阶段交付记录

已完成服务器管理脚本和临时 smoke 测试，覆盖 SHA256/归档路径/链接/缺失文件/releaseId 拒绝、权限归一化、健康检查失败恢复、默认及指定回退。尚需在 Linux 预发用真实 GNU `flock`、`mv -T`、Docker Compose 和 Nginx 验证，并完成真实并发演练。

Phase 03/04 交界：该脚本随新版部署仓库更新到服务器；Phase 03 的本地工具也会在远程调用前原子更新该脚本，避免首次发布时远端缺少管理入口。首次使用前，新版 Compose/Nginx 配置必须已由 Phase 04 更新到目标仓库。
