#!/bin/sh
# Nexus Maven Wrapper Script

if [ -z "$JAVA_HOME" ] && [ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

if command -v mvn >/dev/null 2>&1; then
    exec mvn "$@"
elif [ -x "$HOME/.m2/wrapper/dists/apache-maven-3.10.0/dae9a06d/bin/mvn" ]; then
    exec "$HOME/.m2/wrapper/dists/apache-maven-3.10.0/dae9a06d/bin/mvn" "$@"
else
    FOUND_MVN=$(find "$HOME/.m2" -name "mvn" -type f -perm -111 2>/dev/null | head -n 1)
    if [ -n "$FOUND_MVN" ] && [ -x "$FOUND_MVN" ]; then
        exec "$FOUND_MVN" "$@"
    fi
    echo "Error: Maven binary not found. Please install Maven or add it to PATH." >&2
    exit 1
fi
