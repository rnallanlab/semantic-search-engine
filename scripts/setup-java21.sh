#!/bin/bash

echo "🔧 Setting up Java 21 for the project..."

# Check if Java 21 is available
if command -v java &> /dev/null; then
    CURRENT_JAVA=$(java -version 2>&1 | head -n 1)
    echo "Current Java: $CURRENT_JAVA"
fi

# Common Java 21 installation paths
JAVA21_PATHS=(
    "/usr/lib/jvm/java-21-openjdk"
    "/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home"
    "/usr/local/opt/openjdk@21"
    "$HOME/.sdkman/candidates/java/21.0.8-oracle"
    "$HOME/.jenv/versions/21"
)

FOUND_JAVA21=""

echo "🔍 Searching for Java 21 installation..."

for path in "${JAVA21_PATHS[@]}"; do
    if [ -d "$path" ]; then
        if [ -x "$path/bin/java" ]; then
            VERSION_OUTPUT=$($path/bin/java -version 2>&1 | head -n 1)
            if echo "$VERSION_OUTPUT" | grep -q "21\."; then
                FOUND_JAVA21="$path"
                echo "✅ Found Java 21 at: $path"
                break
            fi
        fi
    fi
done

if [ -z "$FOUND_JAVA21" ]; then
    echo "❌ Java 21 not found in common locations."
    echo ""
    echo "Please install Java 21:"
    echo "  - macOS: brew install openjdk@21"
    echo "  - Ubuntu: sudo apt install openjdk-21-jdk"
    echo "  - Or download from: https://openjdk.org/projects/jdk/21/"
    echo ""
    echo "You mentioned you have Java 21.0.8, please check:"
    echo "  - which java"
    echo "  - java -version"
    echo "  - echo \$JAVA_HOME"
    exit 1
fi

# Set JAVA_HOME for this session
export JAVA_HOME="$FOUND_JAVA21"
export PATH="$JAVA_HOME/bin:$PATH"

echo "🎯 Java 21 configured for this session:"
echo "  JAVA_HOME: $JAVA_HOME"

# Verify Java version
echo ""
echo "🔍 Verifying Java version:"
java -version

echo ""
echo "🏗️ Testing Maven build with Java 21..."
cd search-api

# Test Maven compilation
if ./mvnw clean compile; then
    echo "✅ Maven build successful with Java 21!"
else
    echo "❌ Maven build failed. Check for Java 21 compatibility issues."
    exit 1
fi

echo ""
echo "🎉 Java 21 setup complete!"
echo ""
echo "To use Java 21 permanently, add these to your shell profile:"
echo "  export JAVA_HOME=$JAVA_HOME"
echo "  export PATH=\$JAVA_HOME/bin:\$PATH"
echo ""
echo "Or use a Java version manager like:"
echo "  - jenv: jenv local 21"
echo "  - SDKMAN: sdk use java 21.0.8-oracle"