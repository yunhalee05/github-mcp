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
 * 현재 Git 브랜치를 확인하는 Tool
 */
fun createGetCurrentBranchTool(context: ToolContext) = RegisteredTool(
    Tool(
        name = "get_current_branch",
        description = """
            [유틸리티 툴] 현재 Git 브랜치를 확인합니다.

            **이 툴의 역할:**
            - 현재 작업 중인 Git 브랜치명을 반환합니다
            - 워크플로우 시작 전 브랜치 확인용으로 사용됩니다

            **사용 시점:**
            - 사용자가 현재 브랜치를 명시적으로 확인하고 싶을 때
            - start_pr_workflow 실행 전 현재 브랜치를 미리 확인하고 싶을 때

            **반환 내용:**
            - 현재 브랜치명 (예: feature/add-login)

            **다음 액션:**
            이 툴은 단순 조회 툴이므로, 다음 단계가 자동으로 연결되지 않습니다.
            사용자가 PR 생성을 원한다면 'start_pr_workflow' 툴을 실행하세요.

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

    val result = context.gitService.getCurrentBranch(workingDir)
    result.fold(
        onSuccess = { branch ->
            CallToolResult(content = listOf(TextContent(text = "현재 브랜치: `$branch`")))
        },
        onFailure = { error ->
            CallToolResult(
                content = listOf(TextContent(text = "❌ Error: ${error.message}")),
                isError = true
            )
        }
    )
}