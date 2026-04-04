#!/bin/bash
# DDD 防腐层演示 - 一键启动脚本
# 启动 User Service (8082) + Order Service (8081) + Frontend (3000)

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "========================================="
echo "  DDD Anti-Corruption Layer Demo"
echo "========================================="
echo ""

# Check Java
if ! command -v java &> /dev/null; then
    echo "[ERROR] Java not found. Please install JDK 17+."
    exit 1
fi

# Check Maven
MVN=""
if command -v mvn &> /dev/null; then
    MVN="mvn"
elif [ -f "$SCRIPT_DIR/user-service/mvnw" ]; then
    MVN="./mvnw"
else
    echo "[ERROR] Maven not found. Please install Maven or use mvnw."
    exit 1
fi

cleanup() {
    echo ""
    echo "Shutting down services..."
    kill $USER_PID $ORDER_PID $FRONTEND_PID 2>/dev/null || true
    wait $USER_PID $ORDER_PID $FRONTEND_PID 2>/dev/null || true
    echo "Done."
}
trap cleanup EXIT INT TERM

# Build both services
echo "[1/4] Building User Service..."
cd "$SCRIPT_DIR/user-service"
$MVN package -q -DskipTests 2>&1 | tail -3
echo "      User Service built."

echo "[2/4] Building Order Service..."
cd "$SCRIPT_DIR/order-service"
$MVN package -q -DskipTests 2>&1 | tail -3
echo "      Order Service built."

# Start User Service
echo "[3/4] Starting User Service on :8082..."
cd "$SCRIPT_DIR/user-service"
java -jar target/*.jar --server.port=8082 > /tmp/user-service.log 2>&1 &
USER_PID=$!

# Start Order Service
echo "      Starting Order Service on :8081..."
cd "$SCRIPT_DIR/order-service"
java -jar target/*.jar --server.port=8081 > /tmp/order-service.log 2>&1 &
ORDER_PID=$!

# Wait for services to be ready
echo "      Waiting for services to start..."
for i in $(seq 1 30); do
    USER_UP=false
    ORDER_UP=false
    curl -sf http://localhost:8082/api/users/health-check 2>/dev/null && USER_UP=true || curl -sf -o /dev/null -w "%{http_code}" http://localhost:8082/api/users/nonexistent 2>/dev/null | grep -q "404" && USER_UP=true
    curl -sf http://localhost:8081/api/orders/health-check 2>/dev/null && ORDER_UP=true || curl -sf -o /dev/null -w "%{http_code}" http://localhost:8081/api/orders/nonexistent 2>/dev/null | grep -q "404" && ORDER_UP=true
    if $USER_UP && $ORDER_UP; then
        break
    fi
    sleep 2
done
echo "      Services started."

# Start Frontend
echo "[4/4] Starting Frontend on :3000..."
cd "$SCRIPT_DIR/ddd-frontend"
if command -v python3 &> /dev/null; then
    python3 -m http.server 5173 > /dev/null 2>&1 &
    FRONTEND_PID=$!
elif command -v python &> /dev/null; then
    python -m http.server 5173 > /dev/null 2>&1 &
    FRONTEND_PID=$!
else
    echo "      [WARN] Python not found. Please open ddd-frontend/index.html manually."
    FRONTEND_PID=0
fi

echo ""
echo "========================================="
echo "  All services are running!"
echo ""
echo "  Frontend:      http://localhost:5173"
echo "  User Service:  http://localhost:8082"
echo "  Order Service: http://localhost:8081"
echo ""
echo "  Logs:"
echo "    User Service:  /tmp/user-service.log"
echo "    Order Service: /tmp/order-service.log"
echo ""
echo "  Press Ctrl+C to stop all services"
echo "========================================="

# Keep script alive
wait
