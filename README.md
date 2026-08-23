# LandCheck 后端

土地评估数据比对系统后端。基于 Spring Boot 3 多模块单体，提供项目、文件、解析、用户与汇总比对相关 API。文件原文与 OCR 产物落在 MongoDB GridFS，异步解析走 RocketMQ，会话鉴权使用 Sa-Token。

配套前端仓库：[`land-frontend`](https://github.com/HongxHH/land-frontend)。

## 技术栈

| 项 | 版本 / 说明 |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.8 |
| 构建 | Maven 多模块 |
| 数据 | MongoDB（含 GridFS）、Redis |
| 消息 | RocketMQ |
| 鉴权 | Sa-Token（请求头 `satoken`） |
| API 文档 | Knife4j / OpenAPI 3（本地 profile） |
| LLM | 火山方舟 Ark（环境变量注入密钥） |
| OCR | 外部 PaddleOCR 服务（默认 `http://localhost:8080/layout-parsing`） |

## 模块划分

父工程 `landcheck` 只做版本与依赖管理；功能模块只依赖 `lc-core`，彼此不互相依赖。

| 模块 | 职责 |
| --- | --- |
| `lc-core` | 公共实体、配置、安全、WebSocket、MQ、异常与通用 Controller |
| `lc-start` | 启动类 `LandCheckApplication`、配置文件、可执行 jar 打包 |
| `lc-user` | 登录注册、用户与角色 |
| `lc-file` | 上传、GridFS、预处理、OCR/LLM 解析、任务线程池 |
| `lc-project` | 项目、合同地块、实测房间、规划复核、汇总与指标 |

主类：`com.gov.landcheck.start.LandCheckApplication`（扫描 `com.gov.landcheck`）。

## 本地快速启动

### 前置条件

- JDK 21、Maven 3.9+
- 本机可访问的 MongoDB（副本集 `rs0`）、Redis、RocketMQ NameServer
- 解析链路还需要：OCR 服务；PDF 预处理需要本机 Conda 环境 `SR`（见 `landcheck.file.preprocess.conda-env`）

仓库内 `docker/docker-compose.yml` 可拉起 MongoDB、Redis、RocketMQ 与 OCR 相关容器。其中数据卷路径是开发机本地路径，使用前请改成你的目录。

### 启动

默认 profile 为 `local`，HTTP 端口 **8082**。

```bash
mvn -pl lc-start -am spring-boot:run
```

打包：

```bash
mvn -pl lc-start -am package -DskipTests
java -jar lc-start/target/lc-start-0.0.1-SNAPSHOT.jar
```

生产请显式指定 profile，并注入环境变量：

```bash
java -Dspring.profiles.active=prod -jar lc-start/target/lc-start-0.0.1-SNAPSHOT.jar
```

启动成功后：

- 健康检查：访问任意已登录接口或先走 `/auth/login`
- 本地 API 文档：`http://localhost:8082/doc.html`（`prod` 下会关闭 Swagger）

### 匿名接口（无需 Token）

`/auth/login`、`/auth/register`、`/auth/user-types`，以及本地文档路径。其余接口需在 Header 携带 `satoken`。

## 配置与环境变量

敏感项不要写入仓库。常用变量：

| 变量 | 用途 | 本地默认 |
| --- | --- | --- |
| `LANDCHECK_MONGODB_URI` | Mongo 连接串 | `mongodb://landcheck_user:***@localhost:27017/landcheck?authSource=landcheck&retryWrites=false&replicaSet=rs0` |
| `LANDCHECK_REDIS_HOST` | Redis 主机 | `localhost` |
| `LANDCHECK_REDIS_PORT` | Redis 端口 | `6379` |
| `LANDCHECK_REDIS_PASSWORD` | Redis 密码 | 见 `application-local.yml` |
| `LANDCHECK_LLM_API_KEY` | 火山方舟 API Key | 空（解析/LLM 功能不可用） |
| `LANDCHECK_LOG_DIR` | 日志目录 | `./logs` |

其它可调项在 `lc-start/src/main/resources/application.yml`：

- 上传上限：单文件 100MB，整次请求 1000MB
- RocketMQ NameServer：`127.0.0.1:9876`
- OCR：`landcheck.ocr.api.url`
- LLM：`landcheck.llm.api.*`

`application-prod.yml` 要求必须提供 `LANDCHECK_MONGODB_URI` 和 `LANDCHECK_LLM_API_KEY`，并关闭 OpenAPI。

## 开发约定

- 业务模块必须被 `lc-start` 显式依赖，否则运行时 Controller 会 404。
- 功能模块只依赖 `lc-core`，禁止模块间循环依赖。
- 虚拟线程已开启（`spring.threads.virtual.enabled`）。
- 文件解析是异步的：上传落库后入队，客户端不能取消已发出的后处理。

## 测试

```bash
mvn test
```
