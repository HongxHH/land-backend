# AGENTS.md

## Cursor Cloud specific instructions

This repo is the Java 21 / Spring Boot backend of **LandCheck** (国土房产测绘数据智能归档管理系统).
It pairs with the Vue frontend in the `land-frontend` repo. Standard commands are in `README`/`pom.xml`;
the notes below are the non-obvious things that trip you up in the Cloud VM.

### Services & ports
- Backend API + WebSocket: Spring Boot module `lc-start`, port **8082** (profile `local`, single `application.yml`).
- Frontend: Vite dev server, port **5173**, proxies `/api` → `http://127.0.0.1:8082` (see `land-frontend`).
- Required infra (all run as local Docker containers): **MongoDB** (27017), **Redis** (6379), **RocketMQ** nameserver (9876) + broker (10911/10909).
- Optional/external (NOT runnable locally): **PaddleOCR-VL** OCR (needs NVIDIA GPU) and **Volcengine Ark / Doubao** LLM (remote SaaS). Login, project CRUD, and file upload/browse all work without them; only the OCR→AI parsing pipeline needs them.

### Key gotchas
- **Use system `mvn`, not `./mvnw`.** The wrapper fails with `cannot read distributionUrl property` because `.mvn/wrapper/maven-wrapper.properties` has no trailing newline. Maven 3.9.11 is installed at `/opt/maven` (on `PATH` as `mvn`).
- **Docker has no systemd here.** Start the daemon manually (once per VM boot) before touching containers:
  `sudo dockerd > /tmp/dockerd.log 2>&1 &`  (configured for `fuse-overlayfs` + `containerd-snapshotter=false` in `/etc/docker/daemon.json`; iptables set to legacy).
- **`docker/docker-compose.yml` is Windows-only** (volume paths like `E:\dev\...`, GPU OCR services). Do not `docker compose up` it as-is on Linux — start the containers manually (below).
- **MongoDB auth needs a replica set + a manually created user.** The app connects as `mongodb://landcheck_user:123@localhost:27017/landcheck?authSource=landcheck&replicaSet=rs0`. Compose does NOT create the user or run `rs.initiate()`.
- **No seed admin.** Create the first account via `POST /auth/register` (creates a `USER`; username 3–20 chars `[a-zA-Z0-9_]`, password 6–20, `realName` required). A plain `USER` can log in and create projects. Privileged accounts (`SUPER_ADMIN`/`ADMIN`/`DEVELOPER`) can only be made by an existing admin or by inserting directly into the `sys_user` collection.
- **RocketMQ is enabled by default** (`landcheck.rocketmq.enabled=true`); the producer starts eagerly at boot, so the broker must be up or startup is unreliable. To run without it, set `landcheck.rocketmq.enabled=false` AND remove `rocketmq.producer.group`/`rocketmq.name-server` from `application.yml`.

### Bring up infra (helper files persist at `~/landcheck-infra`)
The MongoDB keyfile + init script (`~/landcheck-infra/mongo/`), Redis conf, and RocketMQ `broker.conf` were created during setup and are captured in the VM snapshot, as are the `landcheck-mongo-data`/`landcheck-redis-data` volumes and the pulled images. Normal path after a fresh boot:

```bash
sudo dockerd > /tmp/dockerd.log 2>&1 &        # wait a few seconds
sudo docker start mongodb landcheck-redis rmqnamesrv rmqbroker
```

If a container is missing, recreate it (network `landcheck-net`):
```bash
sudo docker network create landcheck-net 2>/dev/null || true
# MongoDB (init.sh initiates rs0 + creates users, then restarts with --auth --keyFile; idempotent)
sudo docker run -d --name mongodb --network landcheck-net -p 27017:27017 \
  -v landcheck-mongo-data:/data/db \
  -v ~/landcheck-infra/mongo/keyfile:/etc/mongo/keyfile:ro \
  -v ~/landcheck-infra/mongo/init.sh:/init.sh:ro \
  --entrypoint bash mongo:8.0.13 /init.sh
# Redis (password 123)
sudo docker run -d --name landcheck-redis --network landcheck-net -p 6379:6379 \
  -v landcheck-redis-data:/data -v ~/landcheck-infra/redis/redis.conf:/etc/redis/redis.conf:ro \
  redis:8.2.1 redis-server /etc/redis/redis.conf
# RocketMQ nameserver + broker (broker.conf sets brokerIP1=127.0.0.1, autoCreateTopicEnable=true)
sudo docker run -d --name rmqnamesrv --network landcheck-net -p 9876:9876 \
  -e "JAVA_OPT_EXT=-Xms256m -Xmx256m -Xmn128m" apache/rocketmq:5.1.0 sh mqnamesrv
sudo docker run -d --name rmqbroker --network landcheck-net -p 10911:10911 -p 10909:10909 \
  -e "NAMESRV_ADDR=rmqnamesrv:9876" -e "JAVA_OPT_EXT=-Xms512m -Xmx512m -Xmn256m" \
  -v ~/landcheck-infra/rocketmq/broker.conf:/home/rocketmq/broker.conf:ro \
  apache/rocketmq:5.1.0 sh mqbroker -c /home/rocketmq/broker.conf
```
If `~/landcheck-infra/mongo/keyfile`/`init.sh` are gone: create a keyfile (`openssl rand -base64 756`, `chmod 600`, `chown 999:999`) and an init script that runs `mongod --replSet rs0` (no auth), `rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]})`, creates user `landcheck_user`/`123` (role `dbOwner` on db `landcheck`), then re-execs `mongod --replSet rs0 --auth --keyFile ...`.

### Build / run / test
- Build all modules: `mvn -B -DskipTests clean install` (run once; installs inter-module jars into `~/.m2`).
- Run backend (dev): `mvn spring-boot:run -pl lc-start` (needs infra up first). Health check: `curl http://localhost:8082/auth/user-types` → 200.
- API docs: `http://localhost:8082/doc.html` (Knife4j) and `/swagger-ui.html`.
- There are no backend unit tests in the reactor; validate via the running API.
