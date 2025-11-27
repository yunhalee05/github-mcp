#!/bin/bash

# Git alias 설정 스크립트
# PR 생성 스크립트를 편리하게 사용할 수 있도록 git 명령어로 등록합니다.

echo ""
echo "🔧 Git alias 설정"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 현재 스크립트의 절대 경로
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PR_SCRIPT="$SCRIPT_DIR/create-pr.sh"

if [ ! -f "$PR_SCRIPT" ]; then
    echo "❌ create-pr.sh 파일을 찾을 수 없습니다."
    exit 1
fi

# Git alias 설정
git config --local alias.pr "!bash $PR_SCRIPT"

echo "✅ Git alias가 설정되었습니다!"
echo ""
echo "이제 다음 명령어로 PR을 생성할 수 있습니다:"
echo ""
echo "  git pr"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""