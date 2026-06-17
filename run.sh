#!/bin/bash
# Spring-Kafka-Stream-Rules End-to-End Runner
# This script starts the entire stack and ensures code changes are reflected.

set -e

# Default values for environment variables
export UI_PORT=${UI_PORT:-8080}
export APP_PORT=${APP_PORT:-8081}
export KAFKA_PORT=${KAFKA_PORT:-9092}
export MONGO_PORT=${MONGO_PORT:-27017}
export REDIS_PORT=${REDIS_PORT:-6379}
export SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-demo}

# Connection overrides (defaults to internal docker names if not set)
export KAFKA_BOOTSTRAP=${KAFKA_BOOTSTRAP:-}
export MONGODB_URI=${MONGODB_URI:-}
export REDIS_HOST=${REDIS_HOST:-}

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
echo "🚀 Starting Spring-Kafka-Stream-Rules Stack"
echo "----------------------------------------------------------"
echo "🖥️  UI Port:      $UI_PORT"
echo "🔌 API Port:     $APP_PORT"
echo "📦 Kafka Port:   $KAFKA_PORT"
echo "🍃 Mongo Port:   $MONGO_PORT"
echo "🔴 Redis Port:   $REDIS_PORT"
echo "⚙️  Profiles:     $SPRING_PROFILES_ACTIVE"
if [ ! -z "$KAFKA_BOOTSTRAP" ]; then echo "🌐 Ext Kafka:    $KAFKA_BOOTSTRAP"; fi
if [ ! -z "$MONGODB_URI" ]; then     echo "🌐 Ext Mongo:    $MONGODB_URI"; fi
if [ ! -z "$REDIS_HOST" ]; then      echo "🌐 Ext Redis:    $REDIS_HOST"; fi
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
echo "🛑 Stopping existing services..."
$DOCKER_COMPOSE down --remove-orphans

# Rebuild and run
# We use --build by default to satisfy the "autodetect changes" requirement.
# Docker's layer caching ensures this is fast if no changes were made.
if [ "$FORCE_REBUILD" = true ]; then
    echo "🔄 Forcing a clean rebuild..."
    $DOCKER_COMPOSE build --no-cache
    $DOCKER_COMPOSE up -d --remove-orphans
else
    echo "🔍 Checking for changes and starting..."
    $DOCKER_COMPOSE up -d --build --remove-orphans
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
