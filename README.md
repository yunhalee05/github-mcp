# GitHub MCP Server

대화형 GitHub Pull Request 생성을 위한 Model Context Protocol (MCP) 서버입니다.

Git 변경사항 분석, PR 내용 자동 생성, GitHub API 연동 등의 기능을 단계별 워크플로우로 제공합니다.

## 🚀 주요 기능

- **단계별 PR 생성 워크플로우**: 브랜치 선택 → 변경사항 분석 → PR 내용 생성 → GitHub PR 생성
- **빠른 PR 생성**: 한 번의 명령으로 전체 워크플로우 실행 (pr_smart)
- **Git 변경사항 분석**: 커밋 히스토리, 변경된 파일, 파일 타입별 분류
- **지능적인 PR 내용 생성**: 커밋 메시지와 변경사항을 기반으로 자동 생성
- **동적 PR 템플릿 지원**: 저장소별 커스텀 템플릿 자동 감지
- **JIRA 티켓 연동**: PR 제목과 본문에 JIRA 티켓 번호 자동 포함
- **GitHub API 통합**: PR 생성, 브랜치 push 자동화
- **작업 디렉토리 자동 감지**: 어느 프로젝트에서든 바로 사용 가능

## 📋 요구사항

- Java 21+
- Gradle 9.2+
- Git
- GitHub Personal Access Token (repo 권한 필요)
- Claude Code 또는 Claude Desktop

## 🛠️ 설치

### 방법 1: Claude Code에서 `claude mcp add` 사용 (권장)

```bash
claude mcp add github-mcp -s user \
  -e GITHUB_TOKEN=ghp_your_token_here \
  -e PR_BASE_BRANCH=develop \
  -e PR_JIRA_PREFIX=PROJ \
  -- java -jar https://github.com/yunhalee05/github-mcp/releases/download/v1.0.0/github_mcp-0.0.1-SNAPSHOT.jar
```

### 방법 2: 설정 파일 직접 수정

#### Claude Code 설정

설정 파일 위치: `~/.claude/settings.json`

```json
{
  "mcpServers": {
    "github-mcp": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/github-mcp/build/libs/github-mcp-1.0-SNAPSHOT.jar"
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

#### Claude Desktop 설정

**macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`

```json
{
  "mcpServers": {
    "github-mcp": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/github-mcp/build/libs/github-mcp-1.0-SNAPSHOT.jar"
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

### 환경변수 설명

| 환경변수 | 필수 | 기본값 | 설명 |
|---------|------|--------|------|
| `GITHUB_TOKEN` | ✅ | - | GitHub Personal Access Token (repo 권한 필요) |
| `PR_BASE_BRANCH` | ❌ | develop | 기본 Base 브랜치 |
| `PR_JIRA_PREFIX` | ❌ | PROJ | JIRA 티켓 프리픽스 |
| `PR_TEMPLATE_PATH` | ❌ | 자동 감지 | 커스텀 PR 템플릿 파일 경로 |

**⚠️ 중요사항:**
- **작업 디렉토리는 자동으로 감지됩니다** - 환경변수 설정 불필요
- Claude Code/Desktop이 실행되는 디렉토리가 자동으로 사용됩니다
- AI가 실행 컨텍스트에서 작업 디렉토리를 자동으로 전달합니다

### GitHub Token 생성

1. https://github.com/settings/tokens 접속
2. "Generate new token (classic)" 클릭
3. 필요한 권한 선택:
   - `repo` (전체)
   - `workflow` (선택사항)
4. 생성된 토큰을 복사하여 설정 파일에 추가

### 빌드

```bash
# 프로젝트 클론
git clone https://github.com/YOUR_USERNAME/github-mcp.git
cd github-mcp

# 빌드
./gradlew clean build

# JAR 파일 확인
ls -la build/libs/github-mcp-1.0-SNAPSHOT.jar
```

## 🏗️ 아키텍처

### 프로젝트 구조

```
src/main/kotlin/com/yunhalee/github_mcp/
├── McpServer.kt                     # MCP 서버 메인 엔트리포인트
├── component/
│   └── TemplateLoader.kt           # PR 템플릿 로더
├── service/
│   ├── GitService.kt               # Git 명령어 실행 서비스 (싱글톤)
│   └── GitHubService.kt            # GitHub API 호출 서비스
└── tool/
    ├── ToolContext.kt              # Tool 공유 컨텍스트
    ├── ToolRegistry.kt             # Tool 등록 관리자
    ├── StartPrWorkflowTool.kt      # PR 워크플로우 시작
    ├── SelectBaseBranchTool.kt     # Base 브랜치 선택 및 분석
    ├── GeneratePrContentTool.kt    # PR 내용 생성
    ├── CreatePrConfirmedTool.kt    # PR 생성 실행
    ├── GetCurrentBranchTool.kt     # 현재 브랜치 확인
    └── PrSmartTool.kt              # 빠른 PR 생성 (워크플로우 통합)
```

### 핵심 컴포넌트

#### 1. **RegisteredTool 패턴**

Kotlin MCP SDK의 공식 패턴을 사용하여 Tool을 정의합니다:

```kotlin
fun createStartPrWorkflowTool(context: ToolContext) = RegisteredTool(
    Tool(
        name = "start_pr_workflow",
        description = "PR 생성 워크플로우를 시작합니다...",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("working_dir", buildJsonObject {
                    put("type", "string")
                    put("description", "현재 작업 디렉토리 경로 - REQUIRED")
                })
            },
            required = listOf("working_dir")
        )
    )
) { request ->
    val workingDir = request.arguments?.get("working_dir")?.jsonPrimitive?.content
        ?: return@RegisteredTool CallToolResult(
            content = listOf(TextContent(text = "❌ working_dir이 필요합니다.")),
            isError = true
        )

    // Tool 로직 구현
    CallToolResult(content = listOf(TextContent(text = result)))
}
```

#### 2. **ToolContext**

모든 Tool이 공유하는 컨텍스트:

```kotlin
data class ToolContext(
    val defaultBaseBranch: String,
    val jiraPrefix: String,
    val githubService: GitHubService?,
    val prTemplatePath: String? = null
) {
    val gitService = GitService()  // 싱글톤 인스턴스

    private val templateLoader: TemplateLoader by lazy {
        TemplateLoader(customTemplatePath = prTemplatePath)
    }

    fun loadPrTemplate(workingDir: String): String {
        return templateLoader.loadPrTemplate(workingDir)
    }
}
```

**주요 변경사항:**
- `defaultWorkingDir` 제거 - 더 이상 필요하지 않음
- `gitService` 싱글톤 인스턴스 추가
- `templateLoader` 지연 초기화로 PR 템플릿 로딩

#### 3. **GitService (싱글톤 패턴)**

모든 Git 작업을 처리하며, `workingDir`을 메서드 파라미터로 받습니다:

```kotlin
class GitService {
    suspend fun getCurrentBranch(workingDir: String): Result<String>
    suspend fun getBranches(workingDir: String): Result<List<String>>
    suspend fun getDiff(workingDir: String, baseBranch: String, currentBranch: String): Result<String>
    suspend fun getChangedFiles(workingDir: String, baseBranch: String, currentBranch: String): Result<List<String>>
    suspend fun getCommits(workingDir: String, baseBranch: String, currentBranch: String): Result<List<String>>
    suspend fun getCommitCount(workingDir: String, baseBranch: String, currentBranch: String): Result<Int>
    suspend fun pushBranch(workingDir: String, branch: String): Result<String>
    suspend fun fetchBranch(workingDir: String, branch: String): Result<String>
    suspend fun checkRemoteBranchExists(workingDir: String, branch: String): Result<Boolean>
    suspend fun getRepositoryInfo(workingDir: String): Result<Map<String, String>>
}
```

**설계 특징:**
- 싱글톤 패턴으로 인스턴스 재사용
- `workingDir`을 인스턴스 변수가 아닌 메서드 파라미터로 받음
- MCP 서버가 여러 프로젝트를 동시에 처리 가능

#### 4. **TemplateLoader**

PR 템플릿을 동적으로 로드합니다:

```kotlin
class TemplateLoader(private val customTemplatePath: String? = null) {
    fun loadPrTemplate(workingDir: String): String {
        val templatePaths = listOf(
            "$workingDir/.github/PULL_REQUEST_TEMPLATE.md",
            "$workingDir/.github/pull_request_template.md",
            "$workingDir/docs/pull_request_template.md",
            customTemplatePath
        )

        for (path in templatePaths) {
            path?.let {
                val file = File(it)
                if (file.exists() && file.isFile) {
                    return file.readText()
                }
            }
        }

        return DEFAULT_PR_TEMPLATE
    }
}
```

#### 5. **ToolRegistry**

Tool 등록을 중앙 관리:

```kotlin
class ToolRegistry(private val context: ToolContext) {
    fun getAllTools(): List<RegisteredTool> = listOf(
        createStartPrWorkflowTool(context),
        createSelectBaseBranchTool(context),
        createGeneratePrContentTool(context),
        createCreatePrConfirmedTool(context),
        createGetCurrentBranchTool(context),
        createPrSmartTool(context)
    )

    fun registerAll(server: Server) {
        getAllTools().forEach { tool ->
            server.addTool(tool.tool, tool.handler)
        }
    }
}
```

## 📚 사용 가능한 Tools

### 1. `start_pr_workflow` - PR 워크플로우 시작

PR 생성 워크플로우를 시작합니다.

**Parameters:**
- `working_dir` (필수): 작업 디렉토리 경로 (AI가 자동 전달)

**동작:**
- 현재 Git 브랜치 확인
- main/master 브랜치 체크
- 사용 가능한 base 브랜치 목록 반환

**다음 단계:** 사용자가 base 브랜치를 선택하면 `select_base_branch` 호출

### 2. `select_base_branch` - Base 브랜치 선택

Base 브랜치를 선택하고 변경사항을 분석합니다.

**Parameters:**
- `base_branch` (필수): Base 브랜치 이름
- `working_dir` (필수): 작업 디렉토리 경로 (AI가 자동 전달)

**동작:**
- 브랜치 존재 확인 및 fetch
- 변경된 파일 목록 조회
- 커밋 히스토리 분석
- 파일 타입별 분류

**다음 단계:** 사용자가 JIRA 티켓을 입력하면 `generate_pr_content` 호출

### 3. `generate_pr_content` - PR 내용 생성

JIRA 티켓과 변경사항을 기반으로 PR 제목과 본문을 생성합니다.

**Parameters:**
- `base_branch` (필수): Base 브랜치
- `jira_ticket` (필수): JIRA 티켓 번호 (없으면 "없음")
- `additional_context` (선택): 추가 컨텍스트
- `working_dir` (필수): 작업 디렉토리 경로 (AI가 자동 전달)

**동작:**
- Git diff 분석
- PR 제목 생성 (JIRA 티켓 포함)
- 저장소의 PR 템플릿 자동 감지 및 로드
- AI에게 변경사항 요약 및 템플릿 작성 요청

**템플릿 우선순위:**
1. `.github/PULL_REQUEST_TEMPLATE.md`
2. `.github/pull_request_template.md`
3. `docs/pull_request_template.md`
4. `PR_TEMPLATE_PATH` 환경변수 경로
5. 기본 내장 템플릿

**다음 단계:** 사용자가 확인하면 `create_pr_confirmed` 호출

### 4. `create_pr_confirmed` - PR 생성 실행

실제로 GitHub PR을 생성합니다.

**Parameters:**
- `title` (필수): PR 제목
- `body` (필수): PR 본문
- `base_branch` (필수): Base 브랜치
- `working_dir` (필수): 작업 디렉토리 경로 (AI가 자동 전달)

**동작:**
- 브랜치 push (원격에 없는 경우)
- Repository 정보 조회 (owner/repo 추출)
- GitHub API로 PR 생성
- 생성된 PR URL 반환

### 5. `get_current_branch` - 현재 브랜치 확인

현재 Git 브랜치를 확인합니다.

**Parameters:**
- `working_dir` (필수): 작업 디렉토리 경로 (AI가 자동 전달)

**동작:**
- 현재 브랜치명 반환

### 6. `pr_smart` - 빠른 PR 생성

전체 워크플로우를 한 번에 실행합니다.

**Parameters:**
- `base_branch` (필수): Base 브랜치
- `jira_ticket` (선택): JIRA 티켓 번호
- `additional_context` (선택): 추가 컨텍스트
- `working_dir` (필수): 작업 디렉토리 경로 (AI가 자동 전달)

**동작:**
- 브랜치 확인 → 변경사항 분석 → PR 내용 생성을 한 번에 처리
- 생성된 PR 내용을 사용자에게 보여주고 확인 요청
- 확인 시 `create_pr_confirmed` 호출

## 🔧 개발 가이드

### 새로운 Tool 추가하기

#### 1. Tool 파일 생성

`src/main/kotlin/com/yunhalee/github_mcp/tool/YourNewTool.kt`:


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
java -jar build/libs/github-mcp-1.0-SNAPSHOT.jar
```

### GitService / GithubService 사용하기

**중요:** GitService는 싱글톤으로 사용되며, `workingDir`을 메서드 파라미터로 전달합니다.


### PR 템플릿 사용하기

```kotlin
// ToolContext를 통해 템플릿 로드
val template = context.loadPrTemplate(workingDir)

// 템플릿은 실행 저장소의 .github/PULL_REQUEST_TEMPLATE.md 또는
// PR_TEMPLATE_PATH 환경변수 경로에서 자동으로 로드됩니다
```

## 🌟 작동 원리

### 작업 디렉토리 자동 감지

이 MCP 서버는 **작업 디렉토리를 자동으로 감지**합니다:

1. Claude Code/Desktop이 실행될 때 현재 작업 디렉토리를 AI 실행 컨텍스트로 전달
2. AI가 `<env>Working directory: /path/to/project</env>` 정보를 읽음
3. 각 Tool 호출 시 AI가 자동으로 `working_dir` 파라미터에 경로를 전달
4. MCP 서버가 해당 디렉토리에서 Git 명령어 실행

**장점:**
- 프로젝트마다 별도 설정 불필요
- Claude Code/Desktop을 어느 프로젝트에서 실행하든 자동으로 해당 프로젝트의 Git 저장소 인식
- 설정 파일 간소화 - 환경변수로 WORKING_DIR 지정 불필요
- 여러 프로젝트에서 동시에 작업 가능

### MCP 통신 방식

MCP 서버는 **STDIO**(Standard Input/Output)로 통신합니다:

- HTTP 서버 불필요
- Spring Boot Context 불필요
- Dependency Injection 불필요
- 순수 Kotlin + MCP SDK로 충분

**결과:**
- 13MB의 가벼운 JAR (Spring Boot 사용 시 30-40MB)
- 빠른 시작 시간
- 명확한 의존성

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

## 🔗 참고 자료

- [Model Context Protocol](https://modelcontextprotocol.io/)
- [MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk)
- [MCP Kotlin SDK Documentation](https://modelcontextprotocol.github.io/kotlin-sdk/)
- [Claude Code 문서](https://docs.claude.com/claude-code)
- [Building an MCP Server in Kotlin](https://medium.com/@nishantpardamwar/building-an-mcp-server-in-kotlin-a-step-by-step-guide-7ec96c7d9e00)

## 📝 빠른 시작

자세한 설치 및 사용 방법은 [QUICKSTART.md](QUICKSTART.md)를 참고하세요.

## 📝 라이센스

이 프로젝트는 개인 프로젝트입니다.

## 🤝 기여

버그 리포트나 기능 제안은 이슈로 등록해주세요.

---