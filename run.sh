#!/bin/bash
# springboot-rules-demo End-to-End Runner
# This script starts the entire stack and ensures code changes are reflected.

set -e

# Default values for environment variables
export UI_PORT=${UI_PORT:-8080}
export APP_PORT=${APP_PORT:-8081}
export KAFKA_PORT=${KAFKA_PORT:-9092}
export MONGO_PORT=${MONGO_PORT:-27017}
export REDIS_PORT=${REDIS_PORT:-6379}
export KAFKA_CONTROLLER_PORT=${KAFKA_CONTROLLER_PORT:-9093}
export SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-demo}

# Connection overrides
KAFKA_BOOTSTRAP_OVERRIDE=${KAFKA_BOOTSTRAP:-}
MONGODB_URI_OVERRIDE=${MONGODB_URI:-}
REDIS_HOST_OVERRIDE=${REDIS_HOST:-}
BACKEND_URL_OVERRIDE=${BACKEND_URL:-}

# Function to check if a port is open
is_port_open() {
    nc -z localhost "$1" > /dev/null 2>&1
}

# Ports already claimed during this run. Tracked so two services can't both pick
# the same incremented port before any container has actually bound it.
ASSIGNED_PORTS=""

# Whether a port is unavailable: occupied on the host, or already claimed above.
port_taken() {
    if is_port_open "$1"; then return 0; fi
    case " $ASSIGNED_PORTS " in
        *" $1 "*) return 0 ;;
    esac
    return 1
}

# Resolve the next free port at or above $1, auto-incrementing past anything that
# is in use, and store it in the global FREE_PORT. $2 is a human-readable label.
# Runs in the current shell (not a subshell) so ASSIGNED_PORTS persists across
# calls and two services can't be handed the same port.
FREE_PORT=""
find_free_port() {
    local port=$1
    local label=$2
    while port_taken "$port"; do
        echo "⚠️  ${label} port $port is in use, trying $((port + 1))..."
        port=$((port + 1))
    done
    ASSIGNED_PORTS="$ASSIGNED_PORTS $port"
    FREE_PORT="$port"
}

# Parse command line arguments (before any teardown so --help exits cleanly)
FORCE_REBUILD=false
FOLLOW_LOGS=false
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -b|--build) FORCE_REBUILD=true ;;
        -l|--logs) FOLLOW_LOGS=true ;;
        -h|--help)
            echo "Usage: ./run.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -b, --build    Force a clean rebuild of Docker images (uses --no-cache)"
            echo "  -l, --logs     Follow logs after starting the stack"
            echo "  -h, --help     Show this help message"
            echo ""
            echo "Environment Variables (Overrides):"
            echo "  (Any port below is auto-incremented to the next free port if already in use.)"
            echo "  UI_PORT        The port where the UI will be accessible (default: 8080)"
            echo "  APP_PORT       The port where the Backend API will be accessible (default: 8081)"
            echo "  KAFKA_PORT     The port for Kafka (default: 9092)"
            echo "  MONGO_PORT     The port for MongoDB (default: 27017)"
            echo "  REDIS_PORT     The port for Redis (default: 6379)"
            echo "  SPRING_PROFILES_ACTIVE Spring profiles to run with (default: demo)"
            echo ""
            echo "External Connections (Overrides):"
            echo "  KAFKA_BOOTSTRAP  External Kafka broker (e.g., localhost:9092)"
            echo "  MONGODB_URI      External MongoDB URI (e.g., mongodb://localhost:27017/db)"
            echo "  REDIS_HOST       External Redis host (e.g., localhost)"
            echo "  BACKEND_URL      External Backend API URL (e.g., http://localhost:8081)"
            echo ""
            echo "Example:"
            echo "  UI_PORT=9000 ./run.sh"
            echo "  ./run.sh --build --logs"
            exit 0
            ;;
        *) echo "Unknown parameter: $1"; exit 1 ;;
    esac
    shift
done

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running. Please start Docker and try again."
    exit 1
fi

# Detect if we should use 'docker compose' (V2) or 'docker-compose' (V1)
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

# Stop our own existing services FIRST, before probing for reusable services.
# This is critical: the port probes below decide whether a service is already
# running externally and should be reused. If we probed before tearing down,
# our own containers from a previous run would still be holding their ports and
# would be misdetected as "external" — then this 'down' would kill them, leaving
# nothing running. Tearing down first means only genuinely external services
# survive to be detected and reused.
echo "🛑 Stopping existing services..."
$DOCKER_COMPOSE down --remove-orphans

# Resolve published host ports for every service we start. If the requested
# (default or overridden) port is already in use, auto-increment to the next
# free one so the user doesn't have to hunt for a free port manually.
# UI always starts.
find_free_port "$UI_PORT" "UI"; export UI_PORT="$FREE_PORT"

# Backend API (for UI proxy) — reuse an external backend only when BACKEND_URL is
# explicitly set; otherwise start our own app on an auto-resolved free port.
if [ ! -z "$BACKEND_URL_OVERRIDE" ]; then
    export BACKEND_URL="$BACKEND_URL_OVERRIDE"
    # If it points at host.docker.internal we assume the app already runs on the
    # host and we should not start our own; any other URL is an in-network target.
    if [[ "$BACKEND_URL" == *"host.docker.internal"* ]]; then
        START_APP=false
    else
        START_APP=true
    fi
    echo "🌐 Using Backend API: $BACKEND_URL"
else
    find_free_port "$APP_PORT" "API"; export APP_PORT="$FREE_PORT"
    export BACKEND_URL="http://app:$APP_PORT"
    START_APP=true
fi

# Kafka — reuse only when explicitly overridden, otherwise start a fresh container
if [ ! -z "$KAFKA_BOOTSTRAP_OVERRIDE" ]; then
    export KAFKA_BOOTSTRAP="$KAFKA_BOOTSTRAP_OVERRIDE"
    START_KAFKA=false
    echo "🌐 Using external Kafka: $KAFKA_BOOTSTRAP"
else
    find_free_port "$KAFKA_PORT" "Kafka"; export KAFKA_PORT="$FREE_PORT"
    find_free_port "$KAFKA_CONTROLLER_PORT" "Kafka controller"; export KAFKA_CONTROLLER_PORT="$FREE_PORT"
    export KAFKA_BOOTSTRAP="kafka:$KAFKA_PORT"
    START_KAFKA=true
fi

# Mongo — reuse only when explicitly overridden, otherwise start a fresh container
if [ ! -z "$MONGODB_URI_OVERRIDE" ]; then
    export MONGODB_URI="$MONGODB_URI_OVERRIDE"
    START_MONGO=false
    echo "🌐 Using external MongoDB: $MONGODB_URI"
else
    find_free_port "$MONGO_PORT" "MongoDB"; export MONGO_PORT="$FREE_PORT"
    export MONGODB_URI="mongodb://mongo:$MONGO_PORT/ruleaudit"
    START_MONGO=true
fi

# Redis — reuse only when explicitly overridden, otherwise start a fresh container
if [ ! -z "$REDIS_HOST_OVERRIDE" ]; then
    export REDIS_HOST="$REDIS_HOST_OVERRIDE"
    START_REDIS=false
    echo "🌐 Using external Redis: $REDIS_HOST"
else
    find_free_port "$REDIS_PORT" "Redis"; export REDIS_PORT="$FREE_PORT"
    export REDIS_HOST="redis"
    START_REDIS=true
fi

echo "----------------------------------------------------------"
echo "🚀 Starting springboot-rules-demo Stack"
echo "----------------------------------------------------------"
echo "🖥️  UI Port:      $UI_PORT"
echo "🔌 API Port:     $APP_PORT"
echo "🔌 API:        $BACKEND_URL"
echo "⚙️  Profiles:     $SPRING_PROFILES_ACTIVE"
echo "📦 Kafka:        $KAFKA_BOOTSTRAP"
echo "🍃 Mongo:        $MONGODB_URI"
echo "🔴 Redis:        $REDIS_HOST"
echo "----------------------------------------------------------"

# Rebuild and run
# We only start services that are not already running locally or overridden
SERVICES_TO_START="ui"
if [ "$START_APP" = true ]; then SERVICES_TO_START="$SERVICES_TO_START app"; fi
if [ "$START_KAFKA" = true ]; then SERVICES_TO_START="$SERVICES_TO_START kafka"; fi
if [ "$START_MONGO" = true ]; then SERVICES_TO_START="$SERVICES_TO_START mongo"; fi
if [ "$START_REDIS" = true ]; then SERVICES_TO_START="$SERVICES_TO_START redis"; fi

if [ "$FORCE_REBUILD" = true ]; then
    echo "🔄 Forcing a clean rebuild..."
    $DOCKER_COMPOSE build --no-cache
    $DOCKER_COMPOSE up -d --remove-orphans $SERVICES_TO_START
else
    echo "🔍 Checking for changes and starting..."
    $DOCKER_COMPOSE up -d --build --remove-orphans $SERVICES_TO_START
fi

echo "----------------------------------------------------------"
echo "⏳ Waiting for services to initialize..."
MAX_ATTEMPTS=60
ATTEMPT=1
while [ $ATTEMPT -le $MAX_ATTEMPTS ]; do
    RESPONSE=$(curl -s "http://localhost:$UI_PORT/api/health/status" || echo "FAILED")
    if echo "$RESPONSE" | grep -q "HEALTHY" > /dev/null 2>&1; then
        echo "✅ System is healthy and ready!"
        break
    fi

    if [ "$RESPONSE" = "FAILED" ]; then
        STATE="OFFLINE"
    else
        # Extract Kafka state using grep/sed for portability
        STATE=$(echo "$RESPONSE" | grep -o '"kafkaStreams":"[^"]*"' | cut -d'"' -f4)
    fi

    echo "   (Attempt $ATTEMPT/$MAX_ATTEMPTS) System status: $STATE"
    sleep 2
    ATTEMPT=$((ATTEMPT + 1))
done

if [ $ATTEMPT -gt $MAX_ATTEMPTS ]; then
    echo "⚠️  System is taking longer than expected to warm up."
    echo "   Check logs with: $DOCKER_COMPOSE logs -f"
fi

echo "----------------------------------------------------------"
echo "✅ Stack is running in the background."
if [ "$START_APP" = true ]; then
    echo "📝 To view logs, run: $DOCKER_COMPOSE logs -f"
else
    echo "📝 To view UI logs, run: $DOCKER_COMPOSE logs -f ui"
fi
echo "🌐 UI is available at:  http://localhost:$UI_PORT"
if [ "$START_APP" = true ]; then
    echo "🔌 API is available at: http://localhost:$APP_PORT"
else
    echo "🔌 External API is at: $BACKEND_URL"
fi
echo "----------------------------------------------------------"

if [ "$FOLLOW_LOGS" = true ]; then
    echo "📜 Following logs..."
    $DOCKER_COMPOSE logs -f
fi
