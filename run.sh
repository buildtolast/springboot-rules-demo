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
export SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-demo}

# Connection overrides
KAFKA_BOOTSTRAP_OVERRIDE=${KAFKA_BOOTSTRAP:-}
MONGODB_URI_OVERRIDE=${MONGODB_URI:-}
REDIS_HOST_OVERRIDE=${REDIS_HOST:-}

# Function to check if a port is open
is_port_open() {
    nc -z localhost "$1" > /dev/null 2>&1
}

# Check for existing services and set connection strings
# Kafka
if [ ! -z "$KAFKA_BOOTSTRAP_OVERRIDE" ]; then
    export KAFKA_BOOTSTRAP="$KAFKA_BOOTSTRAP_OVERRIDE"
    START_KAFKA=false
    echo "🌐 Using external Kafka: $KAFKA_BOOTSTRAP"
elif is_port_open "$KAFKA_PORT"; then
    echo "🔍 Detected existing Kafka on port $KAFKA_PORT. Using it."
    export KAFKA_BOOTSTRAP="host.docker.internal:$KAFKA_PORT"
    START_KAFKA=false
else
    export KAFKA_BOOTSTRAP="kafka:9092"
    START_KAFKA=true
fi

# Mongo
if [ ! -z "$MONGODB_URI_OVERRIDE" ]; then
    export MONGODB_URI="$MONGODB_URI_OVERRIDE"
    START_MONGO=false
    echo "🌐 Using external MongoDB: $MONGODB_URI"
elif is_port_open "$MONGO_PORT"; then
    echo "🔍 Detected existing MongoDB on port $MONGO_PORT. Using it."
    export MONGODB_URI="mongodb://host.docker.internal:$MONGO_PORT/ruleaudit"
    START_MONGO=false
else
    export MONGODB_URI="mongodb://mongo:27017/ruleaudit"
    START_MONGO=true
fi

# Redis
if [ ! -z "$REDIS_HOST_OVERRIDE" ]; then
    export REDIS_HOST="$REDIS_HOST_OVERRIDE"
    START_REDIS=false
    echo "🌐 Using external Redis: $REDIS_HOST"
elif is_port_open "$REDIS_PORT"; then
    echo "🔍 Detected existing Redis on port $REDIS_PORT. Using it."
    export REDIS_HOST="host.docker.internal"
    START_REDIS=false
else
    export REDIS_HOST="redis"
    START_REDIS=true
fi

# Parse command line arguments
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

echo "----------------------------------------------------------"
echo "🚀 Starting springboot-rules-demo Stack"
echo "----------------------------------------------------------"
echo "🖥️  UI Port:      $UI_PORT"
echo "🔌 API Port:     $APP_PORT"
echo "⚙️  Profiles:     $SPRING_PROFILES_ACTIVE"
echo "📦 Kafka:        $KAFKA_BOOTSTRAP"
echo "🍃 Mongo:        $MONGODB_URI"
echo "🔴 Redis:        $REDIS_HOST"
echo "----------------------------------------------------------"

# If external services are provided, we don't necessarily want to fail if internal ones aren't healthy,
# but 'docker compose up' will still try to start them.
# The 'app' container will now use the provided KAFKA_BOOTSTRAP/MONGODB_URI if set.

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

# Stop existing services to ensure a clean start
# We only stop what we might have started or what is in our compose file
# However, to be safe and satisfy "restarting should be clean", we down the whole thing
# but only if we are NOT using external services for everything.
echo "🛑 Stopping existing services..."
$DOCKER_COMPOSE down --remove-orphans

# Rebuild and run
# We only start services that are not already running locally or overridden
SERVICES_TO_START="app ui"
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
echo "✅ Stack is running in the background."
echo "📝 To view logs, run: $DOCKER_COMPOSE logs -f"
echo "🌐 UI is available at:  http://localhost:$UI_PORT"
echo "🔌 API is available at: http://localhost:$APP_PORT"
echo "----------------------------------------------------------"

if [ "$FOLLOW_LOGS" = true ]; then
    echo "📜 Following logs..."
    $DOCKER_COMPOSE logs -f
fi
