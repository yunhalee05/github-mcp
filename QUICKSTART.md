# 빠른 시작 가이드

## Claude 에 GitHub MCP 서버 추가하기

### 방법 1: claude code 에 `claude mcp add` 명령어 사용 (권장)

```bash
claude mcp add github-mcp -s user \
  -e GITHUB_TOKEN=ghp_your_token_here \
  -e PR_BASE_BRANCH=develop \
  -e PR_JIRA_PREFIX=PROJ \
  -- java -jar /path/to/github-mcp/build/libs/github-mcp-1.0-SNAPSHOT.jar
```

**환경변수 설명**:
- `GITHUB_TOKEN`: GitHub Personal Access Token (필수)
- `PR_BASE_BRANCH`: 기본 Base 브랜치 (선택, 기본값: develop)
- `PR_JIRA_PREFIX`: JIRA 티켓 프리픽스 (선택, 기본값: PROJ)

### 방법 2: 프로젝트 clone 및 build 후 실행 

#### 2-1) 직접 설정 파일 수정
claude code 설정 파일 위치: `~/.claude/settings.json`
claude desktop 설정 파일 위치: `~/Library/Application Support/Claude/claude_desktop_config.json`

다음 내용을 추가:

```json
{
  "mcpServers": {
    "github-mcp": {
      "command": "java",
      "args": [
        "-jar",
        "{clone 된 프로젝트 위치}/github-mcp/build/libs/github-mcp-1.0-SNAPSHOT.jar"
      ],
      "env": {
        "GITHUB_TOKEN": "ghp_your_token_here",
        "PR_BASE_BRANCH": "develop",
        "PR_JIRA_PREFIX": "PROJ",
        "PR_TEMPLATE_PATH": "/path/to/custom/template.md"
      }
    }
  }
}
```
#### 2-2) install 래퍼 스크립트 사용

`install.sh` 스크립트 사용:

```json
{
  "mcpServers": {
    "github-mcp": {
      "command": "{clone 된 프로젝트 위치}/github-mcp/install.sh",
      "env": {
        "GITHUB_TOKEN": "ghp_your_token_here",
        "PR_BASE_BRANCH": "develop",
        "PR_JIRA_PREFIX": "PROJ"
      }
    }
  }
}
```

**중요**:
- `clone 된 프로젝트 위치` 및 경로를 실제 경로로 변경
- `GITHUB_TOKEN`을 실제 GitHub Personal Access Token으로 변경
- GitHub Token 생성: https://github.com/settings/tokens
  - 필요한 권한: `repo` (전체), `workflow`

#### Claude Code 재시작

설정 파일을 수정한 경우, Claude Code를 재시작합니다.

```bash
# Claude Code 프로세스 종료 후 재시작
```

## 환경변수 설명

| 환경변수 | 필수 | 기본값 | 설명 |
|---------|------|--------|------|
| `GITHUB_TOKEN` | ✅ | - | GitHub Personal Access Token (repo 권한 필요) |
| `PR_BASE_BRANCH` | ❌ | develop | 기본 Base 브랜치 |
| `PR_JIRA_PREFIX` | ❌ | PROJ | JIRA 티켓 프리픽스 |
| `PR_TEMPLATE_PATH` | ❌ | 자동 감지 | 커스텀 PR 템플릿 파일 경로 |

**⚠️ 중요사항:**
- Claude Code가 실행되는 디렉토리가 자동으로 감지되어 사용됩니다
- AI가 실행 컨텍스트에서 작업 디렉토리를 자동으로 전달합니다




## 사용 방법

Claude Code에서 다음과 같이 대화하세요:

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


워크플로우를 한 번에 실행하는 방법:

```
나: PR을 생성해줘. base 브랜치는 develop이고, JIRA 티켓은 PROJ-1234야

Claude: (pr_smart Tool 호출)
현재 브랜치 확인 중...
변경사항 분석 중...
PR 내용 생성 중...
[생성된 PR 제목과 본문 표시]
생성하시겠습니까?

나: 네

Claude: ✅ PR이 성공적으로 생성되었습니다!
```

## 문제 해결

### MCP 서버가 보이지 않는 경우

1. **Claude Code 로그 확인**
   - macOS: `~/Library/Logs/Claude/mcp*.log`
   - Linux: `~/.local/share/Claude/logs/mcp*.log`

2. **JAR 파일 경로 확인**
   ```bash
   # JAR 파일 존재 확인
   ls -la ~/Developer/github-mcp/build/libs/github-mcp-1.0-SNAPSHOT.jar
   ```

3. **Java 버전 확인**
   ```bash
   java -version
   # Java 21 이상이어야 함
   ```

4. **수동으로 서버 테스트**
   ```bash
   java -jar ~/Developer/github-mcp/build/libs/github-mcp-1.0-SNAPSHOT.jar
   # 서버가 시작되고 로그가 출력되어야 함
   ```

### GitHub Token 에러

```
❌ GITHUB_TOKEN 환경변수가 설정되지 않았습니다.
```

- Claude Code 설정 파일의 `env.GITHUB_TOKEN` 확인
- GitHub Token이 유효한지 확인: https://github.com/settings/tokens
- Token 권한에 `repo` (전체)가 포함되어 있는지 확인

### Git 저장소 관련 에러

```
❌ working_dir이 필요합니다.
```

이 에러가 발생하면:
1. Claude Code가 Git 저장소 내에서 실행되고 있는지 확인
2. 현재 디렉토리가 Git 프로젝트인지 확인:
   ```bash
   git status
   ```

### JAR 파일이 없는 경우

```bash
cd ~/Developer/github-mcp
./gradlew clean build
```

## 고급 설정

### 커스텀 PR 템플릿 사용

저장소별로 자동 감지되는 PR 템플릿 우선순위:

1. `.github/PULL_REQUEST_TEMPLATE.md`
2. `.github/pull_request_template.md`
3. `docs/pull_request_template.md`
4. `PR_TEMPLATE_PATH` 환경변수로 지정한 경로
5. 기본 내장 템플릿

커스텀 템플릿 경로 지정:

```json
{
  "mcpServers": {
    "github-mcp": {
      "env": {
        "PR_TEMPLATE_PATH": "/Users/YOUR_USERNAME/custom-templates/pr-template.md"
      }
    }
  }
}
```

### 여러 프로젝트에서 다른 설정 사용

각 프로젝트마다 다른 base 브랜치나 JIRA 프리픽스를 사용하고 싶다면:

```json
{
  "mcpServers": {
    "github-mcp-main": {
      "command": "java",
      "args": ["-jar", "/path/to/github-mcp-1.0-SNAPSHOT.jar"],
      "env": {
        "GITHUB_TOKEN": "ghp_your_token",
        "PR_BASE_BRANCH": "main",
        "PR_JIRA_PREFIX": "PROJ1"
      }
    },
    "github-mcp-develop": {
      "command": "java",
      "args": ["-jar", "/path/to/github-mcp-1.0-SNAPSHOT.jar"],
      "env": {
        "GITHUB_TOKEN": "ghp_your_token",
        "PR_BASE_BRANCH": "develop",
        "PR_JIRA_PREFIX": "PROJ2"
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
    "github-mcp": {
      "command": "java",
      "args": [
        "-Dlog.level=DEBUG",
        "-jar",
        "/path/to/github-mcp-1.0-SNAPSHOT.jar"
      ],
      "env": {
        "GITHUB_TOKEN": "ghp_your_token"
      }
    }
  }
}
```

## 작동 원리

### 작업 디렉토리 자동 감지
1. Claude Code가 실행될 때 현재 작업 디렉토리를 AI 실행 컨텍스트로 전달
2. AI가 `<env>Working directory: /path/to/project</env>` 정보를 읽음
3. 각 Tool 호출 시 AI가 자동으로 `working_dir` 파라미터에 경로를 전달
4. MCP 서버가 해당 디렉토리에서 Git 명령어 실행

**장점**:
- 프로젝트마다 별도 설정 불필요
- Claude Code를 어느 프로젝트에서 실행하든 자동으로 해당 프로젝트의 Git 저장소 인식
- 설정 파일 간소화

## 더 알아보기

- [전체 문서](README.md)
- [개발 가이드](README.md#개발-가이드)
- [MCP 공식 문서](https://modelcontextprotocol.io/)
- [Claude Code 문서](https://docs.claude.com/claude-code)