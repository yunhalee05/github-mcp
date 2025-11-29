package com.yunhalee.github_mcp.tool

import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * PR 내용을 생성하는 Tool
 *
 * 동적 템플릿 지원:
 * - 저장소 내 .github/PULL_REQUEST_TEMPLATE.md 자동 감지
 * - 환경변수 PR_TEMPLATE_PATH로 커스텀 경로 지정 가능
 * - 템플릿이 없으면 기본 템플릿 사용
 */
fun createGeneratePrContentTool(context: ToolContext) = RegisteredTool(
    Tool(
        name = "generate_pr_content",
        description = """
            [STEP 3/4] JIRA 티켓과 변경사항을 기반으로 PR 제목과 본문을 생성합니다.

            **이 툴의 역할:**
            - Git diff를 분석하여 주요 변경사항 요약 생성
            - 저장소의 PR 템플릿을 자동으로 감지하여 사용 (없으면 기본 템플릿)
            - 변경사항 정보와 템플릿을 AI에게 전달하여 PR 본문 작성 요청

            **템플릿 탐색 우선순위:**
            1. .github/PULL_REQUEST_TEMPLATE.md
            2. .github/pull_request_template.md  
            3. docs/pull_request_template.md
            4. 환경변수 PR_TEMPLATE_PATH
            5. 기본 내장 템플릿

            **필수 입력:**
            - base_branch: STEP 2에서 선택된 base 브랜치 (반드시 이전 단계 값 전달)
            - jira_ticket: 사용자가 입력한 JIRA 티켓 번호 (예: PROJ-1234, 또는 "없음")

            **선택 입력:**
            - additional_context: 사용자가 추가로 언급한 내용

            **AI 지시사항 - 매우 중요:**
            
            이 툴은 다음 정보를 제공합니다:
            1. **변경사항 요약**: diff를 분석한 주요 로직 변경점
            2. **상세 정보**: 커밋 목록, 변경 파일, diff 미리보기
            3. **PR 템플릿**: 사용자 저장소의 템플릿 (또는 기본 템플릿)
            
            AI는 위 정보를 활용하여:
            - 템플릿의 각 섹션을 변경사항에 맞게 채워주세요
            - 체크박스 항목은 해당하는 것을 [x]로 체크
            - JIRA 번호 플레이스홀더는 실제 티켓으로 교체
            - 빈 섹션은 diff와 요약을 참고하여 작성
            - 템플릿 구조는 유지하면서 내용만 채워주세요

            **다음 필수 액션:**
            사용자가 PR 생성을 확인하면 반드시 'create_pr_confirmed' 툴을 즉시 호출하세요.

            **사용자 응답 처리:**
            1. **생성 확인:** "네", "확인", "생성해줘", "y", "yes" 등
               → create_pr_confirmed 즉시 호출
               - title: 생성된 PR 제목 그대로 전달
               - body: 완성된 PR 본문 그대로 전달
               - base_branch: 이전 단계에서 받은 base_branch 전달

            2. **수정 요청:** "제목을 ~로 바꿔줘", "본문에 ~를 추가해줘" 등
               → 내용을 수정한 후 다시 사용자에게 확인 요청
               → 확인되면 create_pr_confirmed 호출

            3. **취소:** "아니요", "취소", "그만" 등
               → 워크플로우 종료, PR 생성하지 않음

            **중요:** title, body, base_branch는 반드시 정확히 전달해야 합니다.

            **AI 중요 지시사항:**
            - working_dir 파라미터에 현재 작업 디렉토리를 반드시 전달하세요
            - <env>Working directory: ...</env>에서 확인 가능
        """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("base_branch", buildJsonObject {
                    put("type", "string")
                    put("description", "base 브랜치 (STEP 2에서 선택된 값) - REQUIRED")
                })
                put("jira_ticket", buildJsonObject {
                    put("type", "string")
                    put("description", "JIRA 티켓 번호 (예: PROJ-1234, 또는 '없음') - REQUIRED")
                })
                put("additional_context", buildJsonObject {
                    put("type", "string")
                    put("description", "추가 컨텍스트 (선택)")
                })
                put("working_dir", buildJsonObject {
                    put("type", "string")
                    put("description", "현재 작업 디렉토리 경로 (AI가 <env>에서 전달) - REQUIRED")
                })
            },
            required = listOf("base_branch", "jira_ticket", "working_dir")
        )
    )
) { request ->
    val workingDir = request.arguments?.get("working_dir")?.jsonPrimitive?.content
        ?: return@RegisteredTool CallToolResult(
            content = listOf(TextContent(text = "❌ working_dir이 필요합니다.")),
            isError = true
        )

    val baseBranch = request.arguments?.get("base_branch")?.jsonPrimitive?.content ?: context.defaultBaseBranch
    val jiraTicket = request.arguments?.get("jira_ticket")?.jsonPrimitive?.content ?: ""
    val additional = request.arguments?.get("additional_context")?.jsonPrimitive?.content ?: ""

    // Git 정보 수집
    val currentBranch = context.gitService.getCurrentBranch(workingDir).getOrElse { "" }
    val changedFiles = context.gitService.getChangedFiles(workingDir, baseBranch, currentBranch).getOrElse { emptyList() }
    val commits = context.gitService.getCommits(workingDir, baseBranch, currentBranch).getOrElse { emptyList() }
    val commitCount = context.gitService.getCommitCount(workingDir, baseBranch, currentBranch).getOrElse { 0 }

    // Diff 가져오기
    val diff = context.gitService.getDiff(workingDir, baseBranch, currentBranch).getOrElse { "" }
    val diffLines = diff.lines()
    val diffPreview = if (diffLines.size > 300) {
        diffLines.take(300).joinToString("\n") + "\n\n... (총 ${diffLines.size}줄 중 300줄만 표시)"
    } else {
        diff
    }

    // PR 제목 생성
    val firstCommit = commits.firstOrNull() ?: "변경사항"
    val prTitle = if (jiraTicket.isNotEmpty() && jiraTicket != "없음") {
        "[$jiraTicket] $firstCommit"
    } else {
        firstCommit
    }

    // 파일 타입별 분류 (변경사항 분석용)
    val filesByExtension = changedFiles.groupBy { it.substringAfterLast(".", "other") }
    val testFiles = changedFiles.filter {
        it.contains("test", ignoreCase = true) || it.contains("spec", ignoreCase = true)
    }
    val configFiles = changedFiles.filter {
        it.endsWith(".yaml") || it.endsWith(".yml") || it.endsWith(".properties") || it.endsWith(".json")
    }

    // PR 템플릿 로드 (동적)
    val prTemplate = context.loadPrTemplate(workingDir)

    // 결과 생성
    val result = """
📝 **PR 내용 생성 준비 완료**

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
## 📊 변경사항 요약 (AI 분석 요청)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

아래 Diff를 분석하여 **주요 로직 변경사항을 2-3문장으로 요약**해주세요.
이 요약은 PR 본문 작성에 활용됩니다.

**분석 관점:**
- 어떤 기능이 추가/수정/삭제되었는가?
- 핵심 비즈니스 로직 변경점은?
- 리뷰어가 주의깊게 봐야 할 부분은?

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
## 📋 변경사항 상세 정보
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

**브랜치 정보:**
- 현재 브랜치: `$currentBranch`
- Base 브랜치: `$baseBranch`
- JIRA 티켓: ${if (jiraTicket.isNotEmpty() && jiraTicket != "없음") jiraTicket else "없음"}

**커밋 (${commitCount}개):**
${commits.take(10).joinToString("\n") { "- $it" }}
${if (commits.size > 10) "... 외 ${commits.size - 10}개" else ""}

**변경된 파일 (${changedFiles.size}개):**
${changedFiles.take(15).joinToString("\n") { "- $it" }}
${if (changedFiles.size > 15) "... 외 ${changedFiles.size - 15}개" else ""}

**파일 유형별 분류:**
${filesByExtension.entries.take(5).joinToString("\n") { (ext, files) -> "- .$ext: ${files.size}개" }}
${if (testFiles.isNotEmpty()) "\n- 테스트 파일: ${testFiles.size}개 (${testFiles.take(3).joinToString(", ")})" else ""}
${if (configFiles.isNotEmpty()) "\n- 설정 파일: ${configFiles.size}개 (${configFiles.take(3).joinToString(", ")})" else ""}

**코드 변경사항 (Diff):**
```diff
$diffPreview
```

${if (additional.isNotEmpty()) "**추가 컨텍스트:** $additional\n" else ""}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
## 📄 PR 템플릿
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

아래 템플릿을 위의 변경사항 정보를 활용하여 채워주세요:

```markdown
$prTemplate
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
## ✍️ AI 작업 지시사항
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. **먼저** 위 Diff를 분석하여 주요 변경사항을 요약하세요
2. **그 다음** 요약과 상세 정보를 활용하여 템플릿을 채우세요:
   - JIRA 플레이스홀더(XXX 등) → 실제 티켓번호로 교체: `$jiraTicket`
   - 체크박스 `[ ]` → 해당하는 항목은 `[x]`로 변경
   - 빈 섹션 → diff 분석 내용으로 작성
3. **완성된 PR을 보여주세요:**

**📌 PR 제목:**
```
$prTitle
```

**📄 PR 본문:**
(템플릿을 채운 완성본)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

**다음 단계:** 사용자가 확인하면 `create_pr_confirmed` 툴을 호출하세요.
    """.trimIndent()

    CallToolResult(content = listOf(TextContent(text = result)))
}