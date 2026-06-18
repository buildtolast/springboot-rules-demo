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
CLUSTER_MODE=false
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -b|--build) FORCE_REBUILD=true ;;
        -l|--logs) FOLLOW_LOGS=true ;;
        -c|--cluster) CLUSTER_MODE=true ;;
        -h|--help)
            echo "Usage: ./run.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -b, --build    Force a clean rebuild of Docker images (uses --no-cache)"
            echo "  -l, --logs     Follow logs after starting the stack"
            echo "  -c, --cluster  Run Kafka and MongoDB in a 3-node cluster"
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
# Tear down across ALL profiles, not just the current run's mode. Profile-gated
# services (the cluster kafka-1/2/3, mongo-1/2/3) are only matched by 'down' when
# their profile is active; without this a previous cluster run would be left
# holding its ports, shifting this run's auto-incremented ports and breaking the
# Mongo replica set / container naming.
COMPOSE_PROFILES="single,cluster" $DOCKER_COMPOSE down --remove-orphans

# Resolve published host ports for every service we start. If the requested
# (default or overridden) port is already in use, auto-increment to the next
# free one so the user doesn't have to hunt for a free port manually.
# UI always starts. Seed the service list here so the per-service blocks below
# can append to it (the cluster Kafka/Mongo nodes in particular).
find_free_port "$UI_PORT" "UI"; export UI_PORT="$FREE_PORT"
SERVICES_TO_START="ui"

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
    # App port is dynamic in docker-compose for 10 replicas, 
    # but we still assign a base port for the first one / for UI proxy reference.
    find_free_port "$APP_PORT" "API"; export APP_PORT="$FREE_PORT"
    export BACKEND_URL="http://app:8081"
    START_APP=true
fi

# Kafka — reuse only when explicitly overridden, otherwise start a fresh container
if [ ! -z "$KAFKA_BOOTSTRAP_OVERRIDE" ]; then
    export KAFKA_BOOTSTRAP="$KAFKA_BOOTSTRAP_OVERRIDE"
    START_KAFKA=false
    echo "🌐 Using external Kafka: $KAFKA_BOOTSTRAP"
else
    if [ "$CLUSTER_MODE" = true ]; then
        # We use dynamic ports for the 3 nodes.
        find_free_port "$KAFKA_PORT" "Kafka-1"; export KAFKA_PORT="$FREE_PORT"
        # We also need free ports for the other 2 Kafka nodes
        find_free_port 9094 "Kafka-2"; export KAFKA_PORT_2="$FREE_PORT"
        find_free_port 9095 "Kafka-3"; export KAFKA_PORT_3="$FREE_PORT"
        export KAFKA_BOOTSTRAP="host.docker.internal:$KAFKA_PORT,host.docker.internal:$KAFKA_PORT_2,host.docker.internal:$KAFKA_PORT_3"
        # Since we use 3 containers, we'll tell the user we're starting a 3-node cluster
        echo "🐳 Starting 3-node Kafka and MongoDB cluster using official images..."
        SERVICES_TO_START="$SERVICES_TO_START kafka-1 kafka-2 kafka-3"
    else
        find_free_port "$KAFKA_PORT" "Kafka"; export KAFKA_PORT="$FREE_PORT"
        find_free_port "$KAFKA_CONTROLLER_PORT" "Kafka controller"; export KAFKA_CONTROLLER_PORT="$FREE_PORT"
        export KAFKA_BOOTSTRAP="host.docker.internal:$KAFKA_PORT"
        SERVICES_TO_START="$SERVICES_TO_START kafka"
    fi
    START_KAFKA=true
fi

# Mongo — reuse only when explicitly overridden, otherwise start a fresh container
if [ ! -z "$MONGODB_URI_OVERRIDE" ]; then
    export MONGODB_URI="$MONGODB_URI_OVERRIDE"
    START_MONGO=false
    echo "🌐 Using external MongoDB: $MONGODB_URI"
else
    if [ "$CLUSTER_MODE" = true ]; then
        find_free_port "$MONGO_PORT" "MongoDB-1"; export MONGO_PORT="$FREE_PORT"
        find_free_port 27018 "MongoDB-2"; export MONGO_PORT_2="$FREE_PORT"
        find_free_port 27019 "MongoDB-3"; export MONGO_PORT_3="$FREE_PORT"
        export MONGODB_URI="mongodb://host.docker.internal:$MONGO_PORT,host.docker.internal:$MONGO_PORT_2,host.docker.internal:$MONGO_PORT_3/ruleaudit?replicaSet=rs0"
        SERVICES_TO_START="$SERVICES_TO_START mongo-1 mongo-2 mongo-3"
    else
        find_free_port "$MONGO_PORT" "MongoDB"; export MONGO_PORT="$FREE_PORT"
        export MONGODB_URI="mongodb://host.docker.internal:$MONGO_PORT/ruleaudit"
        SERVICES_TO_START="$SERVICES_TO_START mongo"
    fi
    START_MONGO=true
fi

# Redis — reuse only when explicitly overridden, otherwise start a fresh container
if [ ! -z "$REDIS_HOST_OVERRIDE" ]; then
    export REDIS_HOST="$REDIS_HOST_OVERRIDE"
    START_REDIS=false
    echo "🌐 Using external Redis: $REDIS_HOST"
else
    find_free_port "$REDIS_PORT" "Redis"; export REDIS_PORT="$FREE_PORT"
    export REDIS_HOST="host.docker.internal"
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

# Rebuild and run. SERVICES_TO_START was seeded with "ui" above and the Kafka/
# Mongo blocks appended their (single- or cluster-mode) nodes; add app and redis.
if [ "$START_APP" = true ]; then SERVICES_TO_START="$SERVICES_TO_START app"; fi
if [ "$START_REDIS" = true ]; then SERVICES_TO_START="$SERVICES_TO_START redis"; fi

# Set Docker Compose profiles
if [ "$CLUSTER_MODE" = true ]; then
    COMPOSE_PROFILES="cluster"
    export APP_REPLICATION_FACTOR=3
else
    COMPOSE_PROFILES="single"
    export APP_REPLICATION_FACTOR=1
fi
export COMPOSE_PROFILES

if [ "$FORCE_REBUILD" = true ]; then
    echo "🔄 Forcing a clean rebuild..."
    $DOCKER_COMPOSE build --no-cache
    $DOCKER_COMPOSE up -d --remove-orphans $SERVICES_TO_START
else
    echo "🔍 Checking for changes and starting..."
    $DOCKER_COMPOSE up -d --build --remove-orphans $SERVICES_TO_START
fi

# Initialize MongoDB Replica Set if we started our own Mongo cluster
if [ "$START_MONGO" = true ] && [ "$CLUSTER_MODE" = true ]; then
    echo "🍃 Initializing MongoDB Replica Set..."
    # Wait for mongo-1 to be healthy before attempting rs.initiate
    echo "   Waiting for mongo-1 to be ready..."
    MAX_RS_WAIT=30
    RS_WAIT=0
    while ! docker exec mongo-1 mongosh --quiet --eval "db.adminCommand('ping')" > /dev/null 2>&1; do
        sleep 2
        RS_WAIT=$((RS_WAIT + 2))
        if [ $RS_WAIT -ge $MAX_RS_WAIT ]; then
            echo "   ⚠️  mongo-1 took too long to respond. Skipping RS initialization."
            break
        fi
    done

    if [ $RS_WAIT -lt $MAX_RS_WAIT ]; then
        docker exec mongo-1 mongosh --quiet --eval '
          try {
            if (rs.status().ok) {
              console.log("Replica set already initialized.");
            }
          } catch (e) {
            rs.initiate({
              _id: "rs0",
              members: [
                { _id: 0, host: "host.docker.internal:'$MONGO_PORT'" },
                { _id: 1, host: "host.docker.internal:'$MONGO_PORT_2'" },
                { _id: 2, host: "host.docker.internal:'$MONGO_PORT_3'" }
              ]
            });
            console.log("Replica set initialized.");
          }
        ' || echo "⚠️  MongoDB RS initialization failed."
    fi
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
