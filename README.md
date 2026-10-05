# Vaadin CKEditor Builder

*[中文文档](README.zh-CN.md)*

A visual **CKEditor 5 configuration builder**: configure an editor through a
7-step wizard with no code, then export ready-to-paste Java, TypeScript or
JSON configuration.

Built for Java / Vaadin developers — it turns "which plugins do I need, how do
I lay out the toolbar, how do I theme it?" from documentation trial-and-error
into a few clicks and a copy-paste.

Built on [`com.wontlost:ckeditor-vaadin`](https://github.com/wontlost-ltd/vaadin-ckeditor)
(Apache 2.0, on Maven Central and the Vaadin Directory).

## Features

- **7-step wizard** — getting started → editor type → plugins → toolbar → style & language → advanced config → preview & export
- **70+ CKEditor plugins** to choose from, with mutual-exclusion and dependency resolution handled for you
- **Three export formats** — Java (`VaadinCKEditor` builder code), TypeScript, and JSON
- **Live preview** — configuration changes are reflected in a real editor instance immediately
- **Localised UI** — English, Chinese, Spanish, French, Russian and Arabic (parity enforced by a CI gate)

## Tech stack

| Component | Version |
|---|---|
| Java | 21 |
| Vaadin | 25.3.0 |
| Spring Boot | 4.1.1 |
| ckeditor-vaadin | 5.5.0 |
| CKEditor 5 | 48.5.2 |

## Quick start

```bash
./gradlew bootRun
```

Listens on <http://localhost:8082> by default.

### License key

CKEditor 5's premium plugins require a key. The default falls back to GPL:

```bash
CKEDITOR_LICENSE_KEY=GPL ./gradlew bootRun
```

For production, or when using premium plugins, supply a commercial key via the
environment:

```bash
CKEDITOR_LICENSE_KEY=<your-key> ./gradlew bootRun
```

> ⚠️ When the key has expired the editor degrades to **read-only** and the
> browser console reports `license-key-expired`. If the editor renders but
> won't accept input, check this first.

### Data storage

Uses a local H2 file database by default (`./data/ckeditor-builder.mv.db`) —
no setup required.

Oracle ATP sync is **optional** and off by default. If it is enabled without a
local wallet configured, startup will hang retrying the Oracle connection.
Turn it off explicitly:

```bash
./gradlew bootRun --args='--app.sync.oracle-enabled=false'
```

## Building

```bash
./gradlew test              # unit tests
./gradlew productionBuild -Pvaadin.productionMode=true   # production build (includes frontend bundle)
```

The Docker image is a multi-stage build (jlink-trimmed JRE, roughly 170 MB):

```bash
docker build -t vaadin-ckeditor-builder .
```

## Releasing

Releases are tag-driven. The `version` in `build.gradle` must match the tag, or
CI's version-sync gate rejects the build:

```bash
git tag -a v5.3.0 -m "v5.3.0"
git push origin v5.3.0
```

Pushes to `main` only run the tests; the image build and GitOps update are
triggered by tags alone.

## License

[Apache License 2.0](LICENSE) © 2026 WontLost Ltd

This project is licensed under Apache 2.0. **CKEditor 5 itself is licensed
separately** — GPL for open-source use, or a commercial licence from
[CKEditor](https://ckeditor.com/pricing). This project does not distribute any
CKEditor commercial key.

## Related projects

- [vaadin-ckeditor](https://github.com/wontlost-ltd/vaadin-ckeditor) — the underlying Vaadin component (Apache 2.0)
- [wontlost.com](https://wontlost.com) — products and services
