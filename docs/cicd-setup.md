# CI/CD 配置指南

本项目通过 GitHub Actions 构建并发布 Docker 镜像，并自动更新 GitOps 仓库 `wontlost-ltd/k3s` 触发 ArgoCD 部署到 k3s 集群。

## 架构概览

```
开发者 push main
    ↓
GitHub Actions (.github/workflows/release.yml)
    ├─ test            (Gradle test + i18n parity)
    ├─ docker-build-push (arm64 镜像 → Docker Hub)
    └─ update-gitops    (GitHub App → kustomize edit → push k3s/main)
                                    ↓
                          ArgoCD ApplicationSet 检测变更
                                    ↓
                          k3s 集群拉取新 image digest
                                    ↓
                          ckeditor-builder.wontlost.com
```

## 一次性环境配置

### 1. 创建 GitHub App

> 必须由 `wontlost-ltd` 组织管理员操作。

1. 访问 https://github.com/organizations/wontlost-ltd/settings/apps
2. 点击 **New GitHub App**
3. 填写：
   - **Name**: `ckeditor-builder-gitops`（必须全局唯一，可加后缀）
   - **Homepage URL**: `https://github.com/wontlost-ltd/vaadin-ckeditor-builder`
   - **Webhook**: 取消勾选 **Active**（不需要 webhook）
   - **Repository permissions**:
     - `Contents`: **Read and write**
     - `Metadata`: **Read-only**（强制必选）
   - **Organization permissions**: 全部 No access
   - **User permissions**: 全部 No access
   - **Where can this GitHub App be installed?**: **Only on this account**
4. 点击 **Create GitHub App**

### 2. 生成 Private Key

1. 在 App 设置页底部找到 **Private keys** → **Generate a private key**
2. 浏览器会自动下载 `.pem` 文件
3. **保管好这个文件**，GitHub 不会再次显示

### 3. 安装 App 到 k3s 仓库

1. App 设置页左侧 **Install App** → 选 `wontlost-ltd`
2. **Only select repositories** → 仅勾选 `wontlost-ltd/k3s`
3. **Install**

> ⚠️ 不要勾选 `vaadin-ckeditor-builder` 自己，App 只需要写 k3s 仓库

### 4. 记录 App ID

App 设置页顶部有 **App ID**（6-7 位数字），后续步骤需要。

### 5. 配置 GitHub Repository Secrets

在 `wontlost-ltd/vaadin-ckeditor-builder` 仓库：

`Settings → Secrets and variables → Actions → New repository secret`

添加 4 个 secret：

| Name | Value |
|---|---|
| `DOCKERHUB_USERNAME` | Docker Hub 账号（推 `wontlost/ckeditor-builder` 用） |
| `DOCKERHUB_TOKEN` | Docker Hub Access Token（**不是密码**） |
| `K3S_GITHUB_APP_ID` | 步骤 4 记录的 App ID（仅数字） |
| `K3S_GITHUB_APP_PRIVATE_KEY` | 步骤 2 下载的 `.pem` 文件**全部内容**（含 `-----BEGIN/END RSA PRIVATE KEY-----` 行） |

#### Docker Hub Access Token 创建

1. https://hub.docker.com/settings/security → **New Access Token**
2. Name: `github-actions-ckeditor-builder`
3. Access permissions: **Read, Write, Delete**（Delete 用于清理 buildcache）
4. 复制 token，**只显示一次**

## 触发与发布流程

### 普通发布（main push）

```bash
git push origin main
```

CI 自动：
1. 跑 `./gradlew test`
2. 构建 arm64 镜像，推 Docker Hub tag：`main`、`sha-<shortsha>`、`latest`
3. 更新 `wontlost-ltd/k3s` 的 `apps/wontlost/ckeditor-builder/kustomization.yaml`
4. ArgoCD 自动 sync（默认 3 分钟轮询，可在 ArgoCD UI 手动 Refresh）

### 版本发布（tag）

```bash
# 1. 修改 build.gradle 的 version='X.Y.Z' 与 git tag 一致
# 2. 提交并打 tag
git tag v5.2.0
git push origin v5.2.0
```

CI 额外：
- 推 tag：`5.2.0`、`5.2`、`5`、`latest`、`sha-<shortsha>`
- 校验 `build.gradle:version` 与 git tag 严格匹配（不匹配则 fail）

### PR 验证

PR 触发只跑 `test` job，不构建镜像、不推送，作为质量门禁。

### 手动重发

GitHub repo → Actions → release → **Run workflow**

## 验证清单

部署完成后检查：

```bash
# 1. ArgoCD 同步状态
argocd app get wontlost-ckeditor-builder

# 2. Pod 状态
kubectl -n wontlost-ckeditor-builder get pods -o wide

# 3. 实际运行的 image digest
kubectl -n wontlost-ckeditor-builder get deployment ckeditor-builder \
  -o jsonpath='{.spec.template.spec.containers[0].image}'

# 4. 健康端点
kubectl -n wontlost-ckeditor-builder port-forward svc/ckeditor-builder 8082:80
curl http://localhost:8082/actuator/health

# 5. 公网入口
curl -I https://ckeditor-builder.wontlost.com/
```

## 回滚

由于使用 immutable digest，回滚 = 在 k3s 仓库 revert commit：

```bash
cd k3s
git log --oneline apps/wontlost/ckeditor-builder/
git revert <bad-commit-sha>
git push origin main
# ArgoCD 自动 sync 到旧 digest
```

或在 ArgoCD UI 选择历史版本直接回滚（注意会被下一次 CI push 覆盖）。

## 常见问题

### Q: CI push 到 k3s/main 时 401/403
**A**: GitHub App 未正确安装到 k3s 仓库，或 `K3S_GITHUB_APP_PRIVATE_KEY` 缺失/格式错误（必须包含完整 PEM 头尾行）。

### Q: ArgoCD 看不到镜像变化
**A**: 检查 `kustomization.yaml` 是否真的 commit 到 k3s 仓库。`kubectl get application wontlost-ckeditor-builder -n argocd -o yaml | grep -A2 sync` 看 ApplicationSet 是否触发了 sync。

### Q: 容器启动健康检查失败
**A**: `kubectl logs` 查看具体错误。常见原因：
- jlink 漏 JDK 模块（NoClassDefFoundError）→ 在 `Dockerfile:jre-builder` 的 `--add-modules` 列表追加
- External Secret 未就绪（环境变量为空导致 Spring 启动失败）→ `kubectl get externalsecret -n wontlost-ckeditor-builder`

### Q: Docker Hub rate limit
**A**: CI 已通过 `docker/login-action` 认证，使用认证后的 200 pull/6h 配额（公开仓库无限）。如果仍触发，把基础镜像（`eclipse-temurin`、`alpine`）镜像到 GHCR 后修改 Dockerfile。

### Q: arm64 runner 排队太久
**A**: `ubuntu-24.04-arm` 是 GitHub 较新的 runner，免费版有 1 个并发限制。如果阻塞严重，临时改回 `ubuntu-latest` + `docker/setup-qemu-action`，但 Vaadin 前端构建会慢 3-5 倍。
