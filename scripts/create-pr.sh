#!/bin/bash

# GitHub PR 자동 생성 스크립트
# Claude Code를 사용하여 PR 설명을 자동으로 생성합니다.

set -e

echo ""
echo "🚀 GitHub Pull Request 생성 도구"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 1. GitHub CLI 확인
if ! command -v gh &> /dev/null; then
    echo -e "${RED}❌ GitHub CLI (gh)가 설치되어 있지 않습니다.${NC}"
    echo ""
    echo "설치 방법:"
    echo "  macOS: brew install gh"
    echo "  Linux: https://github.com/cli/cli/blob/trunk/docs/install_linux.md"
    echo ""
    exit 1
fi

# 2. Claude CLI 확인
if ! command -v claude &> /dev/null; then
    echo -e "${RED}❌ Claude CLI가 설치되어 있지 않습니다.${NC}"
    echo "💡 설치 방법: https://docs.claude.com/claude-code/installation"
    echo ""
    exit 1
fi

# 3. Git 저장소 확인
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo -e "${RED}❌ Git 저장소가 아닙니다.${NC}"
    exit 1
fi

# 4. GitHub 로그인 확인
if ! gh auth status &> /dev/null; then
    echo -e "${YELLOW}⚠️  GitHub에 로그인되어 있지 않습니다.${NC}"
    echo ""
    read -p "지금 로그인하시겠습니까? (y/N): " -r < /dev/tty
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        gh auth login
    else
        echo -e "${RED}❌ GitHub 로그인이 필요합니다.${NC}"
        exit 1
    fi
fi

# 5. 현재 브랜치 확인
CURRENT_BRANCH=$(git branch --show-current)
if [ -z "$CURRENT_BRANCH" ]; then
    echo -e "${RED}❌ 브랜치를 확인할 수 없습니다.${NC}"
    exit 1
fi

echo -e "${BLUE}📌 현재 브랜치: ${CURRENT_BRANCH}${NC}"

# 6. main/master 브랜치에서 PR 생성 방지
if [[ "$CURRENT_BRANCH" == "main" || "$CURRENT_BRANCH" == "master" ]]; then
    echo -e "${RED}❌ main/master 브랜치에서는 PR을 생성할 수 없습니다.${NC}"
    echo "💡 feature 브랜치를 생성하세요: git checkout -b feature/your-feature"
    exit 1
fi

# 7. 변경사항 확인
if ! git diff --quiet HEAD origin/"$CURRENT_BRANCH" 2>/dev/null; then
    echo -e "${YELLOW}⚠️  원격 브랜치와 로컬 브랜치가 다릅니다.${NC}"
    echo ""
    read -p "원격 브랜치에 push하시겠습니까? (y/N): " -r < /dev/tty
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        git push -u origin "$CURRENT_BRANCH"
    fi
elif ! git ls-remote --exit-code --heads origin "$CURRENT_BRANCH" &>/dev/null; then
    echo -e "${YELLOW}⚠️  원격 브랜치가 존재하지 않습니다.${NC}"
    echo ""
    read -p "원격 브랜치에 push하시겠습니까? (y/N): " -r < /dev/tty
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        git push -u origin "$CURRENT_BRANCH"
    else
        echo -e "${RED}❌ 원격 브랜치가 필요합니다.${NC}"
        exit 1
    fi
fi

echo ""
echo -e "${GREEN}✅ 원격 브랜치 확인 완료${NC}"
echo ""

# 8. base 브랜치 선택
echo "🎯 PR을 생성할 base 브랜치를 선택하세요:"
echo "  1) main"
echo "  2) master"
echo "  3) develop (기본값)"
echo "  4) 직접 입력"
echo ""
read -p "선택 (1-4, 기본값: 3): " BASE_CHOICE < /dev/tty

case $BASE_CHOICE in
    1) BASE_BRANCH="main" ;;
    2) BASE_BRANCH="master" ;;
    3) BASE_BRANCH="develop" ;;
    4)
        read -p "Base 브랜치 이름: " BASE_BRANCH < /dev/tty
        # 앞뒤 공백 제거
        BASE_BRANCH=$(echo "$BASE_BRANCH" | xargs)
        echo ""
        echo -e "${BLUE}입력한 브랜치: '$BASE_BRANCH'${NC}"
        ;;
    "")
        # 빈 입력 (엔터만 친 경우) - 기본값 develop
        BASE_BRANCH="develop"
        ;;
    *)
        # 그 외 모든 입력은 브랜치 이름으로 간주
        BASE_BRANCH=$(echo "$BASE_CHOICE" | xargs)
        echo ""
        echo -e "${BLUE}입력한 브랜치: '$BASE_CHOICE'${NC}"
        ;;
esac

# 원격 브랜치 fetch
echo ""
echo "🔄 원격 브랜치 정보를 가져오는 중..."
git fetch origin --quiet 2>&1 || true

# 원격 브랜치 존재 여부 확인
if ! git ls-remote --exit-code --heads origin "$BASE_BRANCH" &>/dev/null; then
    echo ""
    echo -e "${RED}❌ 원격 저장소(origin)에 '$BASE_BRANCH' 브랜치가 존재하지 않습니다.${NC}"
    echo ""
    echo "사용 가능한 원격 브랜치 목록:"
    git branch -r | grep "origin/" | grep -v "HEAD" | sed 's/origin\///' | sed 's/^/  - /'
    echo ""
    exit 1
fi

echo ""
echo -e "${BLUE}📌 Base 브랜치: origin/${BASE_BRANCH}${NC}"
echo ""

# 9. 변경사항 분석
echo "🔍 변경사항을 분석 중..."
echo ""

# 원격 브랜치 최신 정보 가져오기
git fetch origin "$BASE_BRANCH" --quiet

# base 브랜치와의 diff 가져오기 (origin/ prefix 사용)
DIFF=$(git diff "origin/$BASE_BRANCH"..."$CURRENT_BRANCH")
if [ -z "$DIFF" ]; then
    echo -e "${RED}❌ origin/${BASE_BRANCH} 브랜치와 비교할 변경사항이 없습니다.${NC}"
    exit 1
fi

# 변경된 파일 목록
CHANGED_FILES=$(git diff --name-only "origin/$BASE_BRANCH"..."$CURRENT_BRANCH")
FILE_COUNT=$(echo "$CHANGED_FILES" | wc -l | tr -d ' ')

echo -e "${BLUE}📝 변경된 파일 ($FILE_COUNT개):${NC}"
echo "$CHANGED_FILES" | head -20 | sed 's/^/  - /'
if [ "$FILE_COUNT" -gt 20 ]; then
    echo "  ... 외 $((FILE_COUNT - 20))개"
fi
echo ""

# 커밋 목록
COMMITS=$(git log "origin/$BASE_BRANCH".."$CURRENT_BRANCH" --pretty=format:"- %s" | head -20)
COMMIT_COUNT=$(git rev-list --count "origin/$BASE_BRANCH".."$CURRENT_BRANCH")

echo -e "${BLUE}📦 커밋 ($COMMIT_COUNT개):${NC}"
echo "$COMMITS" | head -10
if [ "$COMMIT_COUNT" -gt 10 ]; then
    echo "  ... 외 $((COMMIT_COUNT - 10))개"
fi
echo ""

# 10. JIRA 티켓 번호 입력
echo "🎫 JIRA 티켓 번호를 입력하세요 (예: NEWACC-1234):"
read -p "JIRA: " JIRA_TICKET < /dev/tty

if [ -z "$JIRA_TICKET" ]; then
    JIRA_TICKET="NEWACC-XXX"
    echo -e "${YELLOW}⚠️  JIRA 티켓을 입력하지 않았습니다. 기본값(NEWACC-XXX) 사용${NC}"
fi
echo ""

# 11. Claude Code에게 PR 설명 요청
echo "🤖 Claude Code가 PR 설명을 생성 중..."
echo ""

# 임시 파일에 프롬프트 저장
TEMP_FILE=$(mktemp)
cat > "$TEMP_FILE" << 'EOF'
다음 변경사항을 기반으로 GitHub Pull Request 설명을 작성해주세요.

## 변경사항 정보

### 브랜치
- 현재 브랜치: {CURRENT_BRANCH}
- Base 브랜치: {BASE_BRANCH}
- 커밋 개수: {COMMIT_COUNT}개

### 변경된 파일 ({FILE_COUNT}개)
{CHANGED_FILES}

### 커밋 목록
{COMMITS}

### 상세 변경 내용
```diff
{DIFF}
```

## 요청사항

다음 형식으로 PR 설명을 작성해주세요. **마크다운 코드 블록(```)을 사용하지 말고, 순수 마크다운 텍스트만 출력해주세요.**

### PR 제목 (한 줄)
간결하고 명확한 PR 제목을 작성해주세요. (예: "Feat: 사용자 인증 기능 추가")

### PR 설명 (다음 템플릿 형식을 정확히 따라주세요)

## 🛠 작업 내용

- JIRA  : {JIRA_TICKET}
- 이 PR의 핵심 작업 내용을 2-3문장으로 구체적으로 설명

## 📝 변경 사항

아래 체크리스트에서 해당하는 항목을 [x]로 체크해주세요:
- [ ] 새로운 기능
- [ ] 기존 기능 수정 or improve
- [ ] Bug fix
- [ ] 리팩토링
- [ ] 문서작성
- [ ] 설정값 변경

## ✔️ 체크리스트

아래 체크리스트를 코드 변경사항을 기반으로 판단하여 해당하는 항목을 [x]로 체크해주세요:
- [ ] 단위 테스트 작성완료
- [ ] Local 테스트 완료
- [ ] yaml prod 에 socar.me 가 있는지 확인

## 🙏🏻 작업 내용
- 주요 로직 변경 사항 상세 설명

## 🙏🏻 리뷰 포인트 (To Reviewers)
- 주요 리뷰 포인트
- 테스트 시나리오
---

**중요**:
1. 실제 코드 변경사항을 정확하게 반영해주세요
2. 위 템플릿 형식을 정확히 따라주세요 (섹션 제목, 이모지, 체크박스 형식 모두 동일하게)
3. 체크리스트는 변경사항을 분석하여 적절한 항목에 [x]를 표시해주세요
4. 마크다운 코드 블록 없이 순수 텍스트만 출력해주세요
5. PR 제목은 한 줄로 시작하고, 그 다음 줄은 비워주세요
6. {JIRA_TICKET}은 실제 티켓 번호로 교체됩니다
EOF

# 변수 치환
sed -i.bak "s|{CURRENT_BRANCH}|$CURRENT_BRANCH|g" "$TEMP_FILE"
sed -i.bak "s|{BASE_BRANCH}|$BASE_BRANCH|g" "$TEMP_FILE"
sed -i.bak "s|{COMMIT_COUNT}|$COMMIT_COUNT|g" "$TEMP_FILE"
sed -i.bak "s|{FILE_COUNT}|$FILE_COUNT|g" "$TEMP_FILE"
sed -i.bak "s|{JIRA_TICKET}|$JIRA_TICKET|g" "$TEMP_FILE"

# 파일 목록 삽입 (간단하게)
FILE_LIST=$(echo "$CHANGED_FILES" | head -50 | sed 's/^/- /' | tr '\n' '|' | sed 's/|/\\n/g')
sed -i.bak "s|{CHANGED_FILES}|$FILE_LIST|g" "$TEMP_FILE"

# 커밋 목록 삽입
COMMIT_LIST=$(echo "$COMMITS" | head -20 | tr '\n' '|' | sed 's/|/\\n/g')
sed -i.bak "s|{COMMITS}|$COMMIT_LIST|g" "$TEMP_FILE"

# Diff는 너무 크면 잘라내기 (첫 500줄만)
DIFF_TRUNCATED=$(echo "$DIFF" | head -500)
if [ $(echo "$DIFF" | wc -l) -gt 500 ]; then
    DIFF_TRUNCATED="$DIFF_TRUNCATED\n\n... (나머지 변경사항 생략)"
fi

# Diff 삽입을 위한 임시 파일 사용
DIFF_TEMP=$(mktemp)
echo "$DIFF_TRUNCATED" > "$DIFF_TEMP"

# awk를 사용하여 {DIFF} 부분을 파일 내용으로 교체
awk -v diff_file="$DIFF_TEMP" '
    /{DIFF}/ {
        while ((getline line < diff_file) > 0) {
            print line
        }
        close(diff_file)
        next
    }
    { print }
' "$TEMP_FILE" > "$TEMP_FILE.new"
mv "$TEMP_FILE.new" "$TEMP_FILE"

# Claude에게 요청 (--print 옵션으로 non-interactive 모드)
PROMPT=$(cat "$TEMP_FILE")
PR_CONTENT=$(echo "$PROMPT" | claude --print 2>&1)

# 임시 파일 정리
rm -f "$TEMP_FILE" "$TEMP_FILE.bak" "$DIFF_TEMP"

if [ -z "$PR_CONTENT" ]; then
    echo -e "${RED}❌ Claude Code로부터 응답을 받지 못했습니다.${NC}"
    echo ""
    echo "수동으로 PR을 생성하려면:"
    echo "  gh pr create --base $BASE_BRANCH"
    exit 1
fi

# PR 제목과 본문 분리
PR_TITLE=$(echo "$PR_CONTENT" | head -1 | sed 's/^#*\s*//')
PR_BODY=$(echo "$PR_CONTENT" | tail -n +2)

# 마크다운 코드 블록 제거 (혹시 모를 경우 대비)
PR_BODY=$(echo "$PR_BODY" | sed '/^```/d')

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "${GREEN}✨ PR 설명이 생성되었습니다!${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo -e "${BLUE}📋 PR 제목:${NC}"
echo "$PR_TITLE"
echo ""
echo -e "${BLUE}📝 PR 설명:${NC}"
echo "$PR_BODY"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 11. 사용자 확인
read -p "이 내용으로 PR을 생성하시겠습니까? (y/N): " -r < /dev/tty

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo ""
    echo -e "${YELLOW}💡 PR 생성이 취소되었습니다.${NC}"
    echo ""
    echo "수동으로 PR을 생성하려면:"
    echo "  gh pr create --base $BASE_BRANCH --title \"$PR_TITLE\" --body \"$PR_BODY\""
    echo ""
    exit 0
fi

# 12. PR 생성
echo ""
echo "🚀 GitHub PR 생성 중..."
echo ""

PR_URL=$(gh pr create \
    --base "$BASE_BRANCH" \
    --title "$PR_TITLE" \
    --body "$PR_BODY" 2>&1)

if [ $? -eq 0 ]; then
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo -e "${GREEN}✅ PR이 성공적으로 생성되었습니다!${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo -e "${BLUE}🔗 PR URL:${NC}"
    echo "$PR_URL"
    echo ""
else
    echo ""
    echo -e "${RED}❌ PR 생성 중 오류가 발생했습니다:${NC}"
    echo "$PR_URL"
    echo ""
fi