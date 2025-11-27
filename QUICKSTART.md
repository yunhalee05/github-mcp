# 빠른 시작 가이드

## Claude Desktop에 GitHub MCP 서버 추가하기

### 1. 프로젝트 설치

```bash
# 1. 프로젝트 클론
git clone https://github.com/YOUR_USERNAME/github-mcp.git
cd github-mcp

# 2. 설치 스크립트 실행
./install.sh
```

### 2. Claude Desktop 설정

#### macOS
파일 위치: `~/Library/Application Support/Claude/claude_desktop_config.json`

```bash
# 설정 파일 열기
open ~/Library/Application\ Support/Claude/claude_desktop_config.json
```

#### Linux
파일 위치: `~/.config/Claude/claude_desktop_config.json`

```bash
# 설정 파일 열기
nano ~/.config/Claude/claude_desktop_config.json
```

### 3. 설정 파일에 다음 내용 추가

```json
{
  "mcpServers": {
    "github-pr": {
      "command": "/Users/YOUR_USERNAME/.local/bin/github-mcp",
      "env": {
        "GITHUB_TOKEN": "ghp_your_token_here",
        "WORKING_DIR": "/Users/YOUR_USERNAME",
        "PR_BASE_BRANCH": "develop",
        "PR_JIRA_PREFIX": "PROJ"
      }
    }
  }
}
```

**중요**:
- `YOUR_USERNAME`을 실제 사용자명으로 변경
- `GITHUB_TOKEN`을 실제 GitHub Personal Access Token으로 변경
- GitHub Token 생성: https://github.com/settings/tokens
  - 필요한 권한: `repo` (전체), `workflow`

### 4. Claude Desktop 재시작

설정을 저장한 후 Claude Desktop을 완전히 종료하고 다시 시작하세요.

### 5. 사용 방법

Claude Desktop에서 다음과 같이 대화하세요:

```
나: PR 생성 워크플로우를 시작해줘

Claude: (start_pr_workflow Tool 호출)
현재 브랜치: feature/new-feature
Base 브랜치를 선택해주세요:
1. develop (기본값)
2. main

나: 1번 (또는 "develop")

Claude: (select_base_branch Tool 호출)
변경사항 분석 완료
- 변경 파일: 5개
- 커밋: 3개
JIRA 티켓 번호를 입력해주세요

나: PROJ-1234

Claude: (generate_pr_content Tool 호출)
PR 내용이 생성되었습니다
[PR 제목과 본문 표시]

나: 좋아, 생성해줘

Claude: (create_pr_confirmed Tool 호출)
✅ PR이 성공적으로 생성되었습니다!
PR URL: https://github.com/...
```

## 문제 해결

### MCP 서버가 보이지 않는 경우

1. **Claude Desktop 로그 확인**
   - macOS: `~/Library/Logs/Claude/mcp.log`
   - Linux: `~/.local/share/Claude/logs/mcp.log`

2. **경로 확인**
   ```bash
   # 실행 스크립트 존재 확인
   ls -la ~/.local/bin/github-mcp

   # 실행 권한 확인
   chmod +x ~/.local/bin/github-mcp
   ```

3. **Java 버전 확인**
   ```bash
   java -version
   # Java 21 이상이어야 함
   ```

4. **수동으로 서버 테스트**
   ```bash
   ~/.local/bin/github-mcp
   # 서버가 시작되고 로그가 출력되어야 함
   ```

### GitHub Token 에러

```
❌ GITHUB_TOKEN 환경변수가 설정되지 않았습니다.
```

- Claude Desktop 설정 파일의 `env.GITHUB_TOKEN` 확인
- GitHub Token이 유효한지 확인: https://github.com/settings/tokens

### JAR 파일이 없는 경우

```bash
cd ~/path/to/github-mcp
./gradlew clean build
```

## 고급 설정

### 여러 프로젝트에서 다른 설정 사용

```json
{
  "mcpServers": {
    "github-pr-project1": {
      "command": "/Users/YOUR_USERNAME/.local/bin/github-mcp",
      "env": {
        "WORKING_DIR": "/path/to/project1",
        "PR_BASE_BRANCH": "main"
      }
    },
    "github-pr-project2": {
      "command": "/Users/YOUR_USERNAME/.local/bin/github-mcp",
      "env": {
        "WORKING_DIR": "/path/to/project2",
        "PR_BASE_BRANCH": "develop"
      }
    }
  }
}
```

### 디버그 모드

로그를 더 자세히 보려면:

```json
{
  "mcpServers": {
    "github-pr": {
      "command": "/Users/YOUR_USERNAME/.local/bin/github-mcp",
      "env": {
        "LOG_LEVEL": "DEBUG"
      }
    }
  }
}
```

## 더 알아보기

- [전체 문서](README.md)
- [개발 가이드](README.md#개발-가이드)
- [MCP 공식 문서](https://modelcontextprotocol.io/)