# Vaadin CKEditor Builder

*[English](README.md)*

一个可视化的 **CKEditor 5 配置生成器**：通过 7 步向导零代码配置编辑器，
再导出可直接粘贴的 Java / TypeScript / JSON 配置。

面向 Java / Vaadin 开发者 —— 把「要装哪些插件、工具栏怎么排、主题怎么配」
从翻文档试错，变成点几下再复制走。

基于 [`com.wontlost:ckeditor-vaadin`](https://github.com/wontlost-ltd/vaadin-ckeditor)
（Apache 2.0，Maven Central + Vaadin Directory）构建。

## 功能

- **7 步向导**：入门引导 → 编辑器类型 → 插件 → 工具栏 → 样式与语言 → 高级配置 → 预览导出
- **70+ CKEditor 插件**可选，自动处理互斥与依赖关系
- **三种导出格式**：Java（`VaadinCKEditor` 构建器代码）、TypeScript、JSON
- **实时预览**：配置即时反映到真实编辑器实例
- **多语言界面**：英 / 中 / 西 / 法 / 俄 / 阿拉伯 共 6 种（i18n parity 由 CI 闸门保证）

## 技术栈

| 组件 | 版本 |
|---|---|
| Java | 21 |
| Vaadin | 25.3.0 |
| Spring Boot | 4.1.1 |
| ckeditor-vaadin | 5.5.0 |
| CKEditor 5 | 48.5.2 |

## 快速开始

```bash
./gradlew bootRun
```

默认监听 <http://localhost:8082>。

### 许可证密钥

CKEditor 5 的商业插件需要密钥。缺省走 GPL：

```bash
CKEDITOR_LICENSE_KEY=GPL ./gradlew bootRun
```

生产或使用高级插件时，通过环境变量提供商业密钥：

```bash
CKEDITOR_LICENSE_KEY=<your-key> ./gradlew bootRun
```

> ⚠️ 密钥过期时编辑器会降级为**只读**，浏览器控制台报 `license-key-expired`。
> 遇到「渲染正常但无法输入」，先查这一项。

### 数据存储

默认使用本地 H2 文件库（`./data/ckeditor-builder.mv.db`），开箱即用。

Oracle ATP 同步为**可选**能力，缺省关闭。若本地未配置 wallet 而误开，
应用会在启动时卡在 Oracle 连接重试上 —— 显式关掉即可：

```bash
./gradlew bootRun --args='--app.sync.oracle-enabled=false'
```

## 构建

```bash
./gradlew test              # 单元测试
./gradlew productionBuild -Pvaadin.productionMode=true   # 生产构建（含前端打包）
```

Docker 镜像为多阶段构建（jlink 裁剪 JRE，约 170MB）：

```bash
docker build -t vaadin-ckeditor-builder .
```

## 发布

版本号由 tag 驱动，`build.gradle` 的 `version` 必须与 tag 一致，
否则 CI 的版本同步闸门会拒绝：

```bash
git tag -a v5.3.0 -m "v5.3.0"
git push origin v5.3.0
```

`main` 推送只跑测试，不产出镜像；镜像与 GitOps 更新仅由 tag 触发。

## 许可证

[Apache License 2.0](LICENSE) © 2026 WontLost Ltd

本项目以 Apache 2.0 授权。**CKEditor 5 本身另行授权** —— 开源项目可用 GPL，
商业用途需向 [CKEditor](https://ckeditor.com/pricing) 取得商业许可。
本项目不分发任何 CKEditor 商业密钥。

## 相关项目

- [vaadin-ckeditor](https://github.com/wontlost-ltd/vaadin-ckeditor) — 底层 Vaadin 组件（Apache 2.0）
- [wontlost.com](https://wontlost.com) — 产品与服务
