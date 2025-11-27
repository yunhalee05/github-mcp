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
 * PR 생성 워크플로우를 시작하는 Tool
 */
fun createStartPrWorkflowTool(context: ToolContext) = RegisteredTool(
    Tool(
        name = "start_pr_workflow",
        description = """
            PR 생성 워크플로우를 시작합니다.
            현재 Git 상태를 확인하고 사용 가능한 브랜치 목록을 반환합니다.
            사용자에게 base 브랜치를 선택하도록 안내해주세요.
            """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("working_dir", buildJsonObject {
                    put("type", "string")
                    put("description", "작업 디렉토리 경로 (선택사항, 기본값: 환경변수 또는 현재 디렉토리)")
                })
            }
        )
    )
) { request ->
    // 작업 디렉토리 설정
    val workingDir = request.arguments?.get("working_dir")?.jsonPrimitive?.content
    val gitService = context.createGitService(workingDir)

    // Git 저장소 확인
    val currentBranch = gitService.getCurrentBranch().getOrNull()

    if (currentBranch == null) {
        return@RegisteredTool CallToolResult(
            content = listOf(TextContent(text = "❌ Git 저장소가 아닙니다.")),
            isError = true
        )
    }

    // main/master 브랜치 체크
    if (currentBranch in listOf("main", "master")) {
        return@RegisteredTool CallToolResult(
            content = listOf(
                TextContent(
                    text = """
                        ❌ 현재 브랜치가 '$currentBranch'입니다.
                        feature 브랜치를 먼저 생성해주세요:
                        ```
                        git checkout -b feature/your-feature
                        ```
                        """.trimIndent()
                )
            ),
            isError = true
        )
    }

    // 사용 가능한 브랜치 목록
    val branches = gitService.getBranches().getOrElse { emptyList() }
    val commonBases = listOf("develop", "main", "master")
    val availableBases = commonBases.filter { it in branches }

    val result = buildString {
        appendLine("🚀 **PR 생성 워크플로우 시작**")
        appendLine()
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLine()
        appendLine("📌 **현재 상태**")
        appendLine("- 브랜치: `$currentBranch`")
        appendLine()
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLine()
        appendLine("🎯 **Base 브랜치를 선택해주세요:**")
        appendLine()
        availableBases.forEachIndexed { index, branch ->
            val defaultMark = if (branch == context.defaultBaseBranch) " (기본값)" else ""
            appendLine("  ${index + 1}. `$branch`$defaultMark")
        }
        appendLine("  ${availableBases.size + 1}. 직접 입력")
        appendLine()
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLine()
        append("어떤 브랜치로 PR을 생성할까요? (번호 또는 브랜치명)")
    }

    CallToolResult(content = listOf(TextContent(text = result)))
}