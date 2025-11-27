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
 */
fun createGeneratePrContentTool(context: ToolContext) = RegisteredTool(
    Tool(
        name = "generate_pr_content",
        description = """
                JIRA 티켓과 변경사항을 기반으로 PR 제목과 본문을 생성합니다.
                생성된 내용을 사용자에게 보여주고 수정할 부분이 있는지 확인해주세요.
            """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("base_branch", buildJsonObject {
                    put("type", "string")
                    put("description", "base 브랜치")
                })
                put("jira_ticket", buildJsonObject {
                    put("type", "string")
                    put("description", "JIRA 티켓 번호")
                })
                put("additional_context", buildJsonObject {
                    put("type", "string")
                    put("description", "추가 컨텍스트 (선택)")
                })
                put("working_dir", buildJsonObject {
                    put("type", "string")
                    put("description", "작업 디렉토리 경로 (선택사항, 기본값: 환경변수 또는 현재 디렉토리)")
                })
            },
            required = listOf("base_branch", "jira_ticket")
        )
    )
) { request ->
    // 작업 디렉토리 설정
    val workingDir = request.arguments?.get("working_dir")?.jsonPrimitive?.content
    val gitService = context.createGitService(workingDir)

    val baseBranch = request.arguments?.get("base_branch")?.jsonPrimitive?.content ?: context.defaultBaseBranch
    val jiraTicket = request.arguments?.get("jira_ticket")?.jsonPrimitive?.content ?: ""
    val additional = request.arguments?.get("additional_context")?.jsonPrimitive?.content ?: ""

    val currentBranch = gitService.getCurrentBranch().getOrElse { "" }
    val changedFiles = gitService.getChangedFiles(baseBranch, currentBranch).getOrElse { emptyList() }
    val commits = gitService.getCommits(baseBranch, currentBranch).getOrElse { emptyList() }

    // PR 제목 생성
    val firstCommit = commits.firstOrNull() ?: "변경사항"
    val prTitle = if (jiraTicket.isNotEmpty() && jiraTicket != "없음") {
        "[$jiraTicket] $firstCommit"
    } else {
        firstCommit
    }

    // 변경 유형 추론
    val changeTypes = mutableListOf<String>()
    val allText = (changedFiles + commits).joinToString(" ").lowercase()

    if (allText.contains("test")) changeTypes.add("테스트")
    if (changedFiles.any { it.endsWith(".md") || it.endsWith(".txt") }) changeTypes.add("문서작성")
    if (allText.contains("fix")) changeTypes.add("Bug fix")
    if (allText.contains("feat") || allText.contains("add")) changeTypes.add("새로운 기능")
    if (allText.contains("refactor")) changeTypes.add("리팩토링")

    if (changeTypes.isEmpty()) changeTypes.add("기존 기능 수정")

    // PR 본문 생성
    val jiraLine = if (jiraTicket.isNotEmpty() && jiraTicket != "없음") {
        "- JIRA: $jiraTicket"
    } else {
        "- JIRA: 없음"
    }

    val allChangeTypes = listOf("새로운 기능", "기존 기능 수정", "Bug fix", "리팩토링", "문서작성")
    val changeTypeChecks = allChangeTypes.joinToString("\n") { type ->
        if (type in changeTypes) "- [x] $type" else "- [ ] $type"
    }

    val prBody = """
        ## 🛠 작업 내용
        
        $jiraLine
        - $firstCommit
        
        ## 📝 변경 사항
        
        $changeTypeChecks
        
        ## ✔️ 체크리스트
        
        - [ ] 단위 테스트 작성완료
        - [ ] Local 테스트 완료
        
        ## 🙏🏻 주요 변경 파일
        
        ${changedFiles.take(10).joinToString("\n") { "- `$it`" }}
        ${if (changedFiles.size > 10) "- ... 외 ${changedFiles.size - 10}개" else ""}
        
        ## 🙏🏻 리뷰 포인트
        
        - 주요 로직 변경 사항을 확인해주세요
        ${if (additional.isNotEmpty()) "- $additional" else ""}
        """.trimIndent()

    val result = """
        📝 **PR 내용이 생성되었습니다**
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        **📌 PR 제목:**
        ```
        $prTitle
        ```
        
        **📄 PR 본문:**
        
        $prBody
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        이 내용으로 PR을 생성할까요?
        - **수정이 필요하면** 수정할 부분을 말씀해주세요
        - **확인되면** "네" 또는 "생성해줘"라고 말씀해주세요
        - **Draft PR**로 생성하려면 "draft로 생성해줘"라고 말씀해주세요
        """.trimIndent()

    CallToolResult(content = listOf(TextContent(text = result)))
}