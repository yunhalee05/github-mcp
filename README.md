# GitHub MCP Server

대화형 GitHub Pull Request 생성을 위한 Model Context Protocol (MCP) 서버입니다.

Git 변경사항 분석, PR 내용 자동 생성, GitHub API 연동 등의 기능을 단계별 워크플로우로 제공합니다.

## 🚀 주요 기능

- **단계별 PR 생성 워크플로우**: 브랜치 선택 → 변경사항 분석 → PR 내용 생성 → GitHub PR 생성
- **Git 변경사항 분석**: 커밋 히스토리, 변경된 파일, 파일 타입별 분류
- **지능적인 PR 내용 생성**: 커밋 메시지와 변경사항을 기반으로 자동 생성
- **JIRA 티켓 연동**: PR 제목과 본문에 JIRA 티켓 번호 자동 포함
- **GitHub API 통합**: PR 생성, 브랜치 push 자동화

## 📋 요구사항

- Java 21+
- Gradle 9.2+
- Git
- GitHub CLI (gh) - PR 생성용
- GitHub Personal Access Token (선택사항)

## 🛠️ 설치

### 방법 1: 자동 설치 (추천)

```bash
# GitHub에서 클론
git clone https://github.com/YOUR_USERNAME/github-mcp.git
cd github-mcp

# 설치 스크립트 실행
./install.sh
```

설치 후 Claude Desktop 설정 파일을 수정하세요:

**macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
**Linux**: `~/.config/Claude/claude_desktop_config.json`

```json
{
  "mcpServers": {
    "github-pr": {
      "command": "/Users/YOUR_USERNAME/.local/bin/github-mcp",
      "env": {
        "GITHUB_TOKEN": "your_github_token_here",
        "WORKING_DIR": "/Users/YOUR_USERNAME",
        "PR_BASE_BRANCH": "develop",
        "PR_JIRA_PREFIX": "PROJ"
      }
    }
  }
}
```

그런 다음 Claude Desktop을 재시작하면 완료!

### 방법 2: 수동 설치

#### 1. 프로젝트 빌드

```bash
./gradlew clean build
```

#### 2. Claude Desktop 설정

**macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
**Linux**: `~/.config/Claude/claude_desktop_config.json`

```json
{
  "mcpServers": {
    "github-pr": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/github-mcp/build/libs/github_mcp-0.0.1-SNAPSHOT.jar"
      ],
      "env": {
        "GITHUB_TOKEN": "your_github_token_here",
        "WORKING_DIR": "/Users/YOUR_USERNAME",
        "PR_BASE_BRANCH": "develop",
        "PR_JIRA_PREFIX": "PROJ"
      }
    }
  }
}
```

#### 3. Claude Desktop 재시작

### 방법 3: 개발 모드

```bash
# 환경 변수 설정
export GITHUB_TOKEN="your_github_personal_access_token"
export WORKING_DIR="/path/to/your/project"
export PR_BASE_BRANCH="develop"
export PR_JIRA_PREFIX="PROJ"

# 직접 실행
java -jar build/libs/github_mcp-0.0.1-SNAPSHOT.jar

# 또는 Gradle로
./gradlew run
```

## 🏗️ 아키텍처

### 프로젝트 구조

```
src/main/kotlin/com/yunhalee/github_mcp/
├── McpServer.kt                    # MCP 서버 메인 엔트리포인트
├── service/
│   ├── GitService.kt              # Git 명령어 실행 서비스
│   └── GitHubService.kt           # GitHub API 호출 서비스
└── tool/
    ├── ToolContext.kt             # Tool 공유 컨텍스트
    ├── ToolRegistry.kt            # Tool 등록 관리자
    ├── StartPrWorkflowTool.kt     # PR 워크플로우 시작
    ├── SelectBaseBranchTool.kt    # Base 브랜치 선택 및 분석
    ├── GeneratePrContentTool.kt   # PR 내용 생성
    ├── CreatePrConfirmedTool.kt   # PR 생성 실행
    └── GetCurrentBranchTool.kt    # 현재 브랜치 확인
```

### 핵심 컴포넌트

#### 1. **RegisteredTool 패턴**

Kotlin MCP SDK의 공식 패턴을 사용하여 Tool을 정의합니다:

```kotlin
fun createStartPrWorkflowTool(context: ToolContext) = RegisteredTool(
    Tool(
        name = "start_pr_workflow",
        description = "PR 생성 워크플로우를 시작합니다...",
        inputSchema = Tool.Input(
            properties = buildJsonObject { /* ... */ }
        )
    )
) { request ->
    // Tool 실행 로직
    CallToolResult(content = listOf(TextContent(text = result)))
}
```

#### 2. **ToolContext**

모든 Tool이 공유하는 컨텍스트:

```kotlin
data class ToolContext(
    val defaultWorkingDir: String,
    val defaultBaseBranch: String,
    val jiraPrefix: String,
    val githubService: GitHubService?
)
```

#### 3. **ToolRegistry**

Tool 등록을 중앙 관리:

```kotlin
class ToolRegistry(private val context: ToolContext) {
    fun getAllTools(): List<RegisteredTool> = listOf(
        createStartPrWorkflowTool(context),
        createSelectBaseBranchTool(context),
        // ...
    )

    fun registerAll(server: Server) {
        getAllTools().forEach { tool ->
            server.addTool(tool.tool, tool.handler)
        }
    }
}
```

## 📚 사용 가능한 Tools

### 1. `start_pr_workflow`

PR 생성 워크플로우를 시작합니다.

**Parameters:**
- `working_dir` (선택): 작업 디렉토리 경로

**동작:**
- 현재 Git 브랜치 확인
- main/master 브랜치 체크
- 사용 가능한 base 브랜치 목록 반환

### 2. `select_base_branch`

Base 브랜치를 선택하고 변경사항을 분석합니다.

**Parameters:**
- `base_branch` (필수): Base 브랜치 이름
- `working_dir` (선택): 작업 디렉토리 경로

**동작:**
- 브랜치 존재 확인
- 변경된 파일 목록 조회
- 커밋 히스토리 분석
- 파일 타입별 분류

### 3. `generate_pr_content`

JIRA 티켓과 변경사항을 기반으로 PR 제목과 본문을 생성합니다.

**Parameters:**
- `base_branch` (필수): Base 브랜치
- `jira_ticket` (필수): JIRA 티켓 번호 (없으면 "없음")
- `additional_context` (선택): 추가 컨텍스트
- `working_dir` (선택): 작업 디렉토리 경로

**동작:**
- PR 제목 생성 (JIRA 티켓 포함)
- 변경 유형 자동 추론 (새로운 기능, Bug fix, 리팩토링 등)
- PR 본문 템플릿 생성

### 4. `create_pr_confirmed`

실제로 GitHub PR을 생성합니다.

**Parameters:**
- `title` (필수): PR 제목
- `body` (필수): PR 본문
- `base_branch` (필수): Base 브랜치
- `working_dir` (선택): 작업 디렉토리 경로

**동작:**
- 브랜치 push (원격에 없는 경우)
- Repository 정보 조회
- GitHub API로 PR 생성

### 5. `get_current_branch`

현재 Git 브랜치를 확인합니다.

**Parameters:**
- `working_dir` (선택): 작업 디렉토리 경로

## 🔧 개발 가이드

### 새로운 Tool 추가하기

#### 1. Tool 파일 생성

`src/main/kotlin/com/yunhalee/github_mcp/tool/YourNewTool.kt`:

```kotlin
package com.yunhalee.github_mcp.tool

import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*

fun createYourNewTool(context: ToolContext) = RegisteredTool(
    Tool(
        name = "your_new_tool",
        description = "Tool 설명을 작성하세요",
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                put("param1", buildJsonObject {
                    put("type", "string")
                    put("description", "파라미터 설명")
                })
            },
            required = listOf("param1")
        )
    )
) { request ->
    val param1 = request.arguments?.get("param1")?.jsonPrimitive?.content

    // Tool 로직 구현

    CallToolResult(
        content = listOf(TextContent(text = "결과 메시지"))
    )
}
```

#### 2. ToolRegistry에 등록

`src/main/kotlin/com/yunhalee/github_mcp/tool/ToolRegistry.kt`:

```kotlin
fun getAllTools(): List<RegisteredTool> = listOf(
    createStartPrWorkflowTool(context),
    createSelectBaseBranchTool(context),
    // ...
    createYourNewTool(context)  // 추가!
)
```

#### 3. 빌드 및 테스트

```bash
./gradlew clean build
java -jar build/libs/github_mcp-0.0.1-SNAPSHOT.jar
```

### Git Service 사용하기

`GitService`는 다양한 Git 명령어를 제공합니다:

```kotlin
val gitService = context.createGitService(workingDir)

// 현재 브랜치
val branch = gitService.getCurrentBranch().getOrNull()

// 브랜치 목록
val branches = gitService.getBranches().getOrElse { emptyList() }

// 변경된 파일
val files = gitService.getChangedFiles(baseBranch, currentBranch).getOrNull()

// 커밋 목록
val commits = gitService.getCommits(baseBranch, currentBranch).getOrNull()

// 브랜치 push
val pushResult = gitService.pushBranch(branchName)

// Repository 정보
val repoInfo = gitService.getRepositoryInfo().getOrNull()
```

### GitHub API 사용하기

```kotlin
if (context.githubService != null) {
    val result = context.githubService.createPullRequest(
        owner = "username",
        repo = "repository",
        title = "PR Title",
        body = "PR Body",
        head = "feature-branch",
        base = "develop"
    )

    result.fold(
        onSuccess = { pr -> println("PR URL: ${pr.html_url}") },
        onFailure = { error -> println("Error: ${error.message}") }
    )
}
```

## 📦 의존성

```kotlin
dependencies {
    // MCP SDK
    implementation("io.modelcontextprotocol:kotlin-sdk:0.8.0")

    // Ktor Client (HTTP 요청)
    implementation("io.ktor:ktor-client-cio:3.3.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.3.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.2")

    // Kotlin IO (STDIO 통신)
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.5.4")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.16")
}
```

## 🌟 왜 Spring Boot를 제거했나요?

MCP 서버는 **STDIO**(Standard Input/Output)로 통신하므로:

❌ HTTP 서버 불필요
❌ Spring Boot Context 불필요
❌ Dependency Injection 불필요
❌ 30-40MB의 무거운 의존성

✅ 순수 Kotlin + MCP SDK로 충분
✅ 13MB의 가벼운 JAR
✅ 빠른 시작 시간
✅ 명확한 의존성

## 🔗 참고 자료

- [Model Context Protocol](https://modelcontextprotocol.io/)
- [MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk)
- [MCP Kotlin SDK Documentation](https://modelcontextprotocol.github.io/kotlin-sdk/)
- [Building an MCP Server in Kotlin](https://medium.com/@nishantpardamwar/building-an-mcp-server-in-kotlin-a-step-by-step-guide-7ec96c7d9e00)

## 📝 라이센스

이 프로젝트는 개인 프로젝트입니다.

## 🤝 기여

버그 리포트나 기능 제안은 이슈로 등록해주세요.

---

Made with ❤️ using Kotlin and MCP SDK