#!/bin/bash
# GitHub MCP Server Installation Script

set -e

echo "🚀 GitHub MCP Server 설치 시작..."
echo ""

# 1. Java 확인
if ! command -v java &> /dev/null; then
    echo "❌ Java가 설치되어 있지 않습니다."
    echo "Java 21 이상이 필요합니다: https://adoptium.net/"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "❌ Java 21 이상이 필요합니다. 현재: $JAVA_VERSION"
    exit 1
fi

echo "✓ Java $JAVA_VERSION 확인됨"

# 2. Git 확인
if ! command -v git &> /dev/null; then
    echo "❌ Git이 설치되어 있지 않습니다."
    exit 1
fi

echo "✓ Git 확인됨"

# 3. 설치 디렉토리 설정
INSTALL_DIR="${HOME}/.local/share/github-mcp"
mkdir -p "$INSTALL_DIR"

echo "📦 설치 위치: $INSTALL_DIR"

# 4. 프로젝트 클론 또는 업데이트
if [ -d "$INSTALL_DIR/.git" ]; then
    echo "🔄 기존 설치본 업데이트 중..."
    cd "$INSTALL_DIR"
    git pull
else
    echo "📥 GitHub에서 다운로드 중..."
    # 임시: 로컬 디렉토리에서 복사 (GitHub에 올린 후에는 git clone으로 변경)
    cp -r "$(pwd)" "$INSTALL_DIR"
fi

# 5. 빌드
echo "🔨 프로젝트 빌드 중..."
cd "$INSTALL_DIR"
./gradlew clean build

# 6. 실행 스크립트를 PATH에 추가할 수 있는 위치로 심볼릭 링크
BIN_DIR="${HOME}/.local/bin"
mkdir -p "$BIN_DIR"
ln -sf "$INSTALL_DIR/scripts/github-mcp" "$BIN_DIR/github-mcp"

echo ""
echo "✅ 설치 완료!"
echo ""
echo "📝 다음 단계:"
echo ""
echo "1. PATH에 ~/.local/bin 추가 (아직 추가하지 않은 경우):"
echo "   echo 'export PATH=\"\$HOME/.local/bin:\$PATH\"' >> ~/.bashrc"
echo "   source ~/.bashrc"
echo ""
echo "2. Claude Desktop 설정 파일 수정:"
echo "   macOS: ~/Library/Application Support/Claude/claude_desktop_config.json"
echo "   Linux: ~/.config/Claude/claude_desktop_config.json"
echo ""
echo "   다음 내용 추가:"
echo '   {'
echo '     "mcpServers": {'
echo '       "github-pr": {'
echo '         "command": "'"$BIN_DIR/github-mcp"'",'
echo '         "env": {'
echo '           "GITHUB_TOKEN": "your_github_token_here",'
echo '           "WORKING_DIR": "'"$HOME"'",'
echo '           "PR_BASE_BRANCH": "develop",'
echo '           "PR_JIRA_PREFIX": "PROJ"'
echo '         }'
echo '       }'
echo '     }'
echo '   }'
echo ""
echo "3. Claude Desktop 재시작"
echo ""
echo "🔗 자세한 내용: https://github.com/YOUR_USERNAME/github-mcp"