# LiveSeat 项目启动说明

## 配置说明

仓库不包含支付私钥、JWT 密钥或数据库口令。启动前请通过环境变量配置 `MYSQL_ROOT_PASSWORD`、`MYSQL_PASSWORD`、`JWT_SECRET`；如需启用支付宝沙箱，再配置 `ALIPAY_APP_ID`、`ALIPAY_PRIVATE_KEY` 和 `ALIPAY_PUBLIC_KEY`。这些值只保存在本地环境或私有配置中。

## 环境要求

- JDK 17
- Maven 3.9 或更高版本
- Node.js 18 或更高版本
- Docker Desktop

以下命令均在项目根目录执行。

## 首次构建

启动 MySQL、Redis 和 Nacos：

```powershell
docker compose up -d
```

构建全部后端模块：

```powershell
mvn clean package -DskipTests
```

安装前端依赖：

```powershell
cd ticket-web
npm install
cd ..
```

## 启动各个后端模块

为下面每条命令分别打开一个 PowerShell 窗口，并在项目根目录执行。建议按列出的顺序启动，最后启动网关。

```powershell
java -jar ticket-auth-service/target/ticket-auth-service-1.0.0.jar
```

```powershell
java -jar ticket-show-service/target/ticket-show-service-1.0.0.jar
```

```powershell
java -jar ticket-seat-service/target/ticket-seat-service-1.0.0.jar
```

```powershell
java -jar ticket-order-service/target/ticket-order-service-1.0.0.jar
```

```powershell
java -jar ticket-payment-service/target/ticket-payment-service-1.0.0.jar
```

```powershell
java -jar ticket-gateway/target/ticket-gateway-1.0.0.jar
```

## 在 IDEA 中启动后端

也可以在 IDEA 中依次运行以下主类：

```text
com.liveseat.auth.AuthApplication
com.liveseat.show.ShowApplication
com.liveseat.seat.SeatApplication
com.liveseat.order.OrderApplication
com.liveseat.payment.PaymentApplication
com.liveseat.gateway.GatewayApplication
```

## 启动前端

另开一个 PowerShell 窗口，在项目根目录执行：

```powershell
cd ticket-web
npm run dev
```

浏览器访问：

```text
http://localhost:5173
```

## 完整启动顺序

每次重新启动整个项目时，按以下顺序操作：

1. 执行 `docker compose up -d`。
2. 启动 Auth、Show、Seat、Order、Payment 五个业务服务。
3. 启动 Gateway。
4. 在 `ticket-web` 目录执行 `npm run dev`。
5. 访问 `http://localhost:5173`。

## MySQL 端口被占用时

如果本机 `3306` 端口已被占用，可以将 MySQL 改到 `3307`：

```powershell
$env:MYSQL_PORT="3307"
docker compose up -d
```

此时每个后端服务启动前也要在对应 PowerShell 窗口执行：

```powershell
$env:MYSQL_PORT="3307"
```
