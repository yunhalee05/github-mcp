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
        description = "현재 Git 브랜치를 확인합니다.",
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

    val result = gitService.getCurrentBranch()
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