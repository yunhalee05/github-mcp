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
            [STEP 1/4] PR 생성 워크플로우를 시작합니다.

            **이 툴의 역할:**
            - 현재 Git 상태를 확인합니다
            - 사용 가능한 브랜치 목록을 반환합니다
            - main/master 브랜치에서 실행 시 에러를 반환합니다

            **반환 내용:**
            - 현재 브랜치명
            - 사용 가능한 base 브랜치 목록 (develop, main, master 중 존재하는 것)

            **다음 필수 액션:**
            사용자가 브랜치를 선택하면 반드시 'select_base_branch' 툴을 즉시 호출하세요.
            - 사용자가 숫자(1, 2, 3 등)를 선택하면 해당하는 브랜치명으로 변환하여 전달
            - 사용자가 브랜치명을 직접 입력하면 그대로 전달
            - base_branch 파라미터에 선택된 브랜치명을 전달

            **예시:**
            사용자: "1번 선택" → select_base_branch(base_branch: "develop")
            사용자: "develop" → select_base_branch(base_branch: "develop")

            **AI 중요 지시사항:**
            - working_dir 파라미터에 현재 작업 디렉토리를 반드시 전달하세요
            - <env>Working directory: ...</env>에서 확인 가능
            """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("working_dir", buildJsonObject {
                    put("type", "string")
                    put("description", "현재 작업 디렉토리 경로 (AI가 <env>에서 전달) - REQUIRED")
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

    // Git 저장소 확인
    val currentBranch = context.gitService.getCurrentBranch(workingDir).getOrNull()

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
    val branches = context.gitService.getBranches(workingDir).getOrElse { emptyList() }
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