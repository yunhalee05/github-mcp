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
                사용자가 선택한 base 브랜치를 설정하고 변경사항을 분석합니다.
                분석 후 사용자에게 JIRA 티켓 번호를 입력받도록 안내해주세요.
            """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("base_branch", buildJsonObject {
                    put("type", "string")
                    put("description", "사용자가 선택한 base 브랜치")
                })
                put("working_dir", buildJsonObject {
                    put("type", "string")
                    put("description", "작업 디렉토리 경로 (선택사항, 기본값: 환경변수 또는 현재 디렉토리)")
                })
            },
            required = listOf("base_branch")
        )
    )
) { request ->
    // 작업 디렉토리 설정
    val workingDir = request.arguments?.get("working_dir")?.jsonPrimitive?.content
    val gitService = context.createGitService(workingDir)

    val baseBranch = request.arguments?.get("base_branch")?.jsonPrimitive?.content
        ?: return@RegisteredTool CallToolResult(
            content = listOf(TextContent(text = "❌ base_branch가 필요합니다.")),
            isError = true
        )

    // 브랜치 존재 확인
    val branchExists = gitService.checkRemoteBranchExists(baseBranch).getOrElse { false }
    if (!branchExists) {
        val branches = gitService.getBranches().getOrElse { emptyList() }
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
    gitService.fetchBranch(baseBranch)
    val currentBranch = gitService.getCurrentBranch().getOrElse { "" }
    val changedFiles = gitService.getChangedFiles(baseBranch, currentBranch).getOrElse { emptyList() }
    val commits = gitService.getCommits(baseBranch, currentBranch).getOrElse { emptyList() }
    val commitCount = gitService.getCommitCount(baseBranch, currentBranch).getOrElse { 0 }

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