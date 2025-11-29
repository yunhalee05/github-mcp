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
            [STEP 4/4 - 최종] 사용자가 확인한 후 실제로 GitHub PR을 생성합니다.

            **이 툴의 역할:**
            - 현재 브랜치를 원격 저장소에 push (아직 push되지 않은 경우)
            - Git remote URL에서 owner/repo 정보 추출
            - GitHub API를 사용하여 PR 생성
            - 생성된 PR URL 반환

            **실행 조건:**
            사용자가 다음과 같이 명확히 확인한 경우에만 실행하세요:
            - "네", "확인", "생성해줘", "y", "yes", "OK", "좋아" 등

            **필수 입력:**
            - title: STEP 3에서 생성된 PR 제목 (수정된 경우 수정된 버전)
            - body: STEP 3에서 생성된 PR 본문 (수정된 경우 수정된 버전)
            - base_branch: STEP 2에서 선택된 base 브랜치 (반드시 이전 단계 값 전달)

            **선택 입력:**
            - working_dir: 작업 디렉토리 경로

            **반환 내용:**
            - 성공 시: PR URL, PR 번호, PR 제목
            - 실패 시: 에러 메시지 (GITHUB_TOKEN 미설정, 브랜치 push 실패, PR 생성 실패 등)

            **워크플로우 종료:**
            이 툴이 성공적으로 실행되면 PR 생성 워크플로우가 완료됩니다.
            사용자에게 생성된 PR URL을 알려주고 워크플로우를 종료하세요.

            **주의사항:**
            - GITHUB_TOKEN 환경변수가 반드시 설정되어 있어야 합니다
            - 현재 브랜치가 원격에 push되어 있어야 합니다 (자동으로 push 시도)
            - base_branch가 원격 저장소에 존재해야 합니다

            **중요:** title, body, base_branch는 이전 단계에서 받은 정확한 값을 전달해야 합니다.

            **AI 중요 지시사항:**
            - working_dir 파라미터에 현재 작업 디렉토리를 반드시 전달하세요
            - <env>Working directory: ...</env>에서 확인 가능
            """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("title", buildJsonObject {
                    put("type", "string")
                    put("description", "PR 제목 (STEP 3에서 생성된 값 또는 수정된 값) - REQUIRED")
                })
                put("body", buildJsonObject {
                    put("type", "string")
                    put("description", "PR 본문 (STEP 3에서 생성된 값 또는 수정된 값) - REQUIRED")
                })
                put("base_branch", buildJsonObject {
                    put("type", "string")
                    put("description", "base 브랜치 (STEP 2에서 선택된 값) - REQUIRED")
                })
                put("working_dir", buildJsonObject {
                    put("type", "string")
                    put("description", "현재 작업 디렉토리 경로 (AI가 <env>에서 전달) - REQUIRED")
                })
            },
            required = listOf("title", "body", "base_branch", "working_dir")
        )
    )
) { request ->
    if (context.githubService == null) {
        return@RegisteredTool CallToolResult(
            content = listOf(TextContent(text = "❌ GITHUB_TOKEN 환경변수가 설정되지 않았습니다.")),
            isError = true
        )
    }

    val workingDir = request.arguments?.get("working_dir")?.jsonPrimitive?.content
        ?: return@RegisteredTool CallToolResult(
            content = listOf(TextContent(text = "❌ working_dir이 필요합니다.")),
            isError = true
        )

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
    val head = context.gitService.getCurrentBranch(workingDir).getOrElse {
        return@RegisteredTool CallToolResult(
            content = listOf(TextContent(text = "❌ 현재 브랜치를 확인할 수 없습니다.")),
            isError = true
        )
    }

    // 원격 브랜치 push 확인 및 push
    val branchExists = context.gitService.checkRemoteBranchExists(workingDir, head).getOrElse { false }
    if (!branchExists) {
        val pushResult = context.gitService.pushBranch(workingDir, head)
        if (pushResult.isFailure) {
            return@RegisteredTool CallToolResult(
                content = listOf(TextContent(text = "❌ 브랜치 push 실패: ${pushResult.exceptionOrNull()?.message}")),
                isError = true
            )
        }
    }

    // Repository 정보 가져오기
    val repoInfo = context.gitService.getRepositoryInfo(workingDir).getOrElse {
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