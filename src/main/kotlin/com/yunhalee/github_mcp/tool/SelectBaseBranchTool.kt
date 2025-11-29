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
 * Base 브랜치를 선택하고 변경사항을 분석하는 Tool
 */
fun createSelectBaseBranchTool(context: ToolContext) = RegisteredTool(
    Tool(
        name = "select_base_branch",
        description = """
            [STEP 2/4] 사용자가 선택한 base 브랜치를 설정하고 변경사항을 분석합니다.

            **이 툴의 역할:**
            - 선택된 base 브랜치가 원격 저장소에 존재하는지 확인
            - origin/base_branch를 fetch
            - base_branch와 현재 브랜치의 차이점 분석
            - 변경된 파일 목록, 커밋 목록을 반환

            **필수 입력:**
            - base_branch: 사용자가 선택한 base 브랜치명 (예: develop, main, master)

            **반환 내용:**
            - 현재 브랜치명
            - 변경된 파일 개수 및 목록 (파일 확장자별로 그룹화)
            - 커밋 개수 및 목록 (최대 10개 미리보기)

            **다음 필수 액션:**
            사용자가 JIRA 티켓 번호를 입력하면 반드시 'generate_pr_content' 툴을 즉시 호출하세요.
            - base_branch: 이전 단계에서 선택된 브랜치 (반드시 기억하고 전달)
            - jira_ticket: 사용자가 입력한 JIRA 티켓 번호
            - additional_context: 사용자가 추가로 언급한 내용이 있으면 전달 (선택사항)

            **예시:**
            사용자: "PROJ-1234" → generate_pr_content(base_branch: "develop", jira_ticket: "PROJ-1234")
            사용자: "없음" → generate_pr_content(base_branch: "develop", jira_ticket: "없음")

            **중요:** base_branch는 이 단계에서 받은 값을 다음 단계로 반드시 전달해야 합니다.

            **AI 중요 지시사항:**
            - working_dir 파라미터에 현재 작업 디렉토리를 반드시 전달하세요
            - <env>Working directory: ...</env>에서 확인 가능
            """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("base_branch", buildJsonObject {
                    put("type", "string")
                    put("description", "사용자가 선택한 base 브랜치 (예: develop, main, master) - REQUIRED")
                })
                put("working_dir", buildJsonObject {
                    put("type", "string")
                    put("description", "현재 작업 디렉토리 경로 (AI가 <env>에서 전달) - REQUIRED")
                })
            },
            required = listOf("base_branch", "working_dir")
        )
    )
) { request ->
    val workingDir = request.arguments?.get("working_dir")?.jsonPrimitive?.content
        ?: return@RegisteredTool CallToolResult(
            content = listOf(TextContent(text = "❌ working_dir이 필요합니다.")),
            isError = true
        )

    val baseBranch = request.arguments?.get("base_branch")?.jsonPrimitive?.content
        ?: return@RegisteredTool CallToolResult(
            content = listOf(TextContent(text = "❌ base_branch가 필요합니다.")),
            isError = true
        )

    // 브랜치 존재 확인
    val branchExists = context.gitService.checkRemoteBranchExists(workingDir, baseBranch).getOrElse { false }
    if (!branchExists) {
        val branches = context.gitService.getBranches(workingDir).getOrElse { emptyList() }
        return@RegisteredTool CallToolResult(
            content = listOf(
                TextContent(
                    text = """
                        ❌ `$baseBranch` 브랜치가 존재하지 않습니다.
                        사용 가능한 브랜치: ${branches.take(10).joinToString(", ")}
                    """.trimIndent()
                )
            ),
            isError = true
        )
    }

    // 변경사항 분석
    context.gitService.fetchBranch(workingDir, baseBranch)
    val currentBranch = context.gitService.getCurrentBranch(workingDir).getOrElse { "" }
    val changedFiles = context.gitService.getChangedFiles(workingDir, baseBranch, currentBranch).getOrElse { emptyList() }
    val commits = context.gitService.getCommits(workingDir, baseBranch, currentBranch).getOrElse { emptyList() }
    val commitCount = context.gitService.getCommitCount(workingDir, baseBranch, currentBranch).getOrElse { 0 }

    if (changedFiles.isEmpty()) {
        return@RegisteredTool CallToolResult(
            content = listOf(TextContent(text = "❌ `origin/$baseBranch`와 비교할 변경사항이 없습니다.")),
            isError = true
        )
    }

    // 파일 타입별 분류
    val filesByType = changedFiles.groupBy { file ->
        file.substringAfterLast(".", "other")
    }

    val result = buildString {
        appendLine("✅ **Base 브랜치 선택됨: `$baseBranch`**")
        appendLine()
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLine()
        appendLine("📊 **변경사항 요약**")
        appendLine("- 현재 브랜치: `$currentBranch`")
        appendLine("- 변경 파일: ${changedFiles.size}개")
        appendLine("- 커밋: ${commitCount}개")
        appendLine()
        appendLine("📝 **변경된 파일**")
        filesByType.forEach { (ext, files) ->
            val preview = files.take(3).joinToString(", ")
            val more = if (files.size > 3) " 외 ${files.size - 3}개" else ""
            appendLine("  📁 .$ext (${files.size}개): $preview$more")
        }
        appendLine()
        appendLine("📦 **커밋 목록**")
        commits.take(10).forEach { commit ->
            appendLine("- $commit")
        }
        if (commits.size > 10) {
            appendLine("  ... 외 ${commits.size - 10}개")
        }
        appendLine()
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLine()
        appendLine("🎫 **작업 티켓 번호를 입력해주세요**")
        append("(예: ${context.jiraPrefix}-1234, 없으면 '없음' 입력)")
    }

    CallToolResult(content = listOf(TextContent(text = result)))
}