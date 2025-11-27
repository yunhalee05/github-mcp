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
 * 실제로 PR을 생성하는 Tool
 */
fun createCreatePrConfirmedTool(context: ToolContext) = RegisteredTool(
    Tool(
        name = "create_pr_confirmed",
        description = """
            사용자가 확인한 후 실제로 PR을 생성합니다.
            사용자가 '네', '확인', 'y' 등으로 동의한 경우에만 실행해주세요.
            """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("title", buildJsonObject {
                    put("type", "string")
                    put("description", "PR 제목")
                })
                put("body", buildJsonObject {
                    put("type", "string")
                    put("description", "PR 본문")
                })
                put("base_branch", buildJsonObject {
                    put("type", "string")
                    put("description", "base 브랜치")
                })
                put("working_dir", buildJsonObject {
                    put("type", "string")
                    put("description", "작업 디렉토리 경로 (선택사항, 기본값: 환경변수 또는 현재 디렉토리)")
                })
            },
            required = listOf("title", "body", "base_branch")
        )
    )
) { request ->
    if (context.githubService == null) {
        return@RegisteredTool CallToolResult(
            content = listOf(TextContent(text = "❌ GITHUB_TOKEN 환경변수가 설정되지 않았습니다.")),
            isError = true
        )
    }

    // 작업 디렉토리 설정
    val workingDir = request.arguments?.get("working_dir")?.jsonPrimitive?.content
    val gitService = context.createGitService(workingDir)

    val title = request.arguments?.get("title")?.jsonPrimitive?.content
        ?: return@RegisteredTool CallToolResult(
            content = listOf(TextContent(text = "❌ title이 필요합니다.")),
            isError = true
        )

    val body = request.arguments?.get("body")?.jsonPrimitive?.content
        ?: return@RegisteredTool CallToolResult(
            content = listOf(TextContent(text = "❌ body가 필요합니다.")),
            isError = true
        )

    val base = request.arguments?.get("base_branch")?.jsonPrimitive?.content
        ?: return@RegisteredTool CallToolResult(
            content = listOf(TextContent(text = "❌ base_branch가 필요합니다.")),
            isError = true
        )

    // 현재 브랜치 확인
    val head = gitService.getCurrentBranch().getOrElse {
        return@RegisteredTool CallToolResult(
            content = listOf(TextContent(text = "❌ 현재 브랜치를 확인할 수 없습니다.")),
            isError = true
        )
    }

    // 원격 브랜치 push 확인 및 push
    val branchExists = gitService.checkRemoteBranchExists(head).getOrElse { false }
    if (!branchExists) {
        val pushResult = gitService.pushBranch(head)
        if (pushResult.isFailure) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent(text = "❌ 브랜치 push 실패: ${pushResult.exceptionOrNull()?.message}")),
                isError = true
            )
        }
    }

    // Repository 정보 가져오기
    val repoInfo = gitService.getRepositoryInfo().getOrElse {
        return@RegisteredTool CallToolResult(
            content = listOf(TextContent(text = "❌ Repository 정보를 가져올 수 없습니다.")),
            isError = true
        )
    }

    val owner = repoInfo["owner"] ?: return@RegisteredTool CallToolResult(
        content = listOf(TextContent(text = "❌ Repository owner를 확인할 수 없습니다.")),
        isError = true
    )

    val repo = repoInfo["repo"] ?: return@RegisteredTool CallToolResult(
        content = listOf(TextContent(text = "❌ Repository 이름을 확인할 수 없습니다.")),
        isError = true
    )

    // PR 생성
    val result = context.githubService.createPullRequest(owner, repo, title, body, head, base)

    result.fold(
        onSuccess = { pr ->
            CallToolResult(
                content = listOf(
                    TextContent(
                        text = """
                        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                        ✅ **PR이 성공적으로 생성되었습니다!**
                        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                        
                        🔗 **PR URL:** ${pr.html_url}
                        📝 **PR #${pr.number}:** ${pr.title}
                        """.trimIndent()
                    )
                )
            )
        },
        onFailure = { error ->
            CallToolResult(
                content = listOf(TextContent(text = "❌ PR 생성 실패: ${error.message}")),
                isError = true
            )
        }
    )
}