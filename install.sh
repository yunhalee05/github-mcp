#!/bin/bash
# GitHub MCP Server Launcher

set -e

# 스크립트 위치 확인
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

# JAR 파일 경로
JAR_FILE="$PROJECT_ROOT/build/libs/github_mcp-0.0.1-SNAPSHOT.jar"

# JAR 파일이 없으면 에러
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found at $JAR_FILE" >&2
    echo "Please run: ./gradlew build" >&2
    exit 1
fi

# 환경 변수 기본값
export WORKING_DIR="${WORKING_DIR:-$HOME}"
export PR_BASE_BRANCH="${PR_BASE_BRANCH:-develop}"
export PR_JIRA_PREFIX="${PR_JIRA_PREFIX:-PROJ}"

# Java 버전 확인
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed" >&2
    exit 1
fi

# MCP 서버 실행
exec java -jar "$JAR_FILE" "$@"
