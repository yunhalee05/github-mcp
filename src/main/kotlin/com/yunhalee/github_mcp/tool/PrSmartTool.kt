package com.yunhalee.github_mcp.tool

import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 스마트 진입점: 제공된 정보에 따라 적절한 단계로 라우팅
 *
 * 이 툴은 사용자가 제공한 정보의 양에 따라 적절한 워크플로우 단계로 진입합니다.
 * - 정보 없음: start_pr_workflow 실행 (단계별 안내)
 * - 일부 정보: 해당 단계로 바로 진입
 * - 완전 정보: 즉시 PR 생성
 */
fun createPrSmartTool(context: ToolContext) = RegisteredTool(
    Tool(
        name = "create_pr",
        description = """
            GitHub PR 생성 스마트 진입점 (정보량에 따라 자동 라우팅).

            **유연한 사용법:**
            1. 정보 없이 호출 → 단계별 대화형 워크플로우 시작
            2. 부분 정보 제공 → 해당 단계부터 시작
            3. 완전 정보 제공 → 즉시 PR 생성

            **파라미터 (모두 선택사항):**
            - base_branch: Base 브랜치 (예: develop, main)
            - jira_ticket: JIRA 티켓 번호 (예: PROJ-1234, 없으면 "없음")
            - confirmed: true면 확인 없이 즉시 PR 생성 (기본: false)
            - additional_context: 추가 컨텍스트

            **자동 라우팅 규칙:**
            - 파라미터 없음 → start_pr_workflow (브랜치 선택 안내)
            - base_branch만 → select_base_branch (변경사항 분석 + JIRA 요청)
            - base_branch + jira_ticket, confirmed=false → generate_pr_content (PR 내용 생성 + 확인 요청)
            - base_branch + jira_ticket, confirmed=true → 자동으로 PR 내용 생성 후 즉시 create_pr_confirmed 실행

            **사용 예시:**
            ```
            # 단계별 진행
            create_pr() → "브랜치를 선택하세요"

            # 중간 단계부터
            create_pr(base_branch: "develop") → "JIRA 티켓을 입력하세요"

            # 확인 후 생성
            create_pr(base_branch: "develop", jira_ticket: "PROJ-1234") → "PR 내용 확인..."

            # 즉시 생성
            create_pr(base_branch: "develop", jira_ticket: "PROJ-1234", confirmed: true) → "✅ PR 생성 완료"
            ```

            **AI 지시사항:**
            - 사용자가 "PR 생성해줘"만 하면 파라미터 없이 호출
            - 사용자가 브랜치를 언급하면 base_branch 파라미터 포함
            - 사용자가 "바로 생성", "즉시 생성" 등을 언급하면 confirmed=true
            - 이전 호출에서 받은 파라미터는 계속 전달 (누적)
            - working_dir 파라미터에 현재 작업 디렉토리를 반드시 전달하세요 (<env>Working directory: ...</env>)
            """.trimIndent(),
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("working_dir", buildJsonObject {
                    put("type", "string")
                    put("description", "현재 작업 디렉토리 경로 (AI가 <env>에서 전달) - REQUIRED")
                })
                put("base_branch", buildJsonObject {
                    put("type", "string")
                    put("description", "Base 브랜치 (예: develop, main, master) - 선택사항")
                })
                put("jira_ticket", buildJsonObject {
                    put("type", "string")
                    put("description", "JIRA 티켓 번호 (예: PROJ-1234, 없으면 '없음') - 선택사항")
                })
                put("confirmed", buildJsonObject {
                    put("type", "boolean")
                    put("description", "true면 확인 없이 즉시 PR 생성 (기본: false)")
                })
                put("additional_context", buildJsonObject {
                    put("type", "string")
                    put("description", "추가 컨텍스트 (선택사항)")
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
    val baseBranch = request.arguments?.get("base_branch")?.jsonPrimitive?.content
    val jiraTicket = request.arguments?.get("jira_ticket")?.jsonPrimitive?.content
    val confirmed = request.arguments?.get("confirmed")?.jsonPrimitive?.content?.toBoolean() ?: false
    val additionalContext = request.arguments?.get("additional_context")?.jsonPrimitive?.content

    // 라우팅 로직
    when {
        // 시나리오 1: base_branch + jira_ticket + confirmed=true
        // → generate_pr_content로 내용 생성
        // confirmed=true이지만, generate_pr_content는 항상 확인 요청을 반환함
        // AI는 사용자 확인 없이 즉시 create_pr_confirmed를 호출해야 함을 인식
        baseBranch != null && jiraTicket != null && confirmed -> {
            val generateRequest = createRequest(
                mapOf(
                    "working_dir" to workingDir,
                    "base_branch" to baseBranch,
                    "jira_ticket" to jiraTicket,
                    "additional_context" to (additionalContext ?: "")
                )
            )

            // generate_pr_content 툴 호출
            // 반환된 결과를 AI가 보고 자동으로 create_pr_confirmed를 호출하도록 함
            createGeneratePrContentTool(context).handler(generateRequest)
        }

        // 시나리오 2: base_branch + jira_ticket (confirmed=false 또는 미입력)
        // → PR 내용 생성 후 확인 요청
        baseBranch != null && jiraTicket != null -> {
            val generateRequest = createRequest(
                mapOf(
                    "working_dir" to workingDir,
                    "base_branch" to baseBranch,
                    "jira_ticket" to jiraTicket,
                    "additional_context" to (additionalContext ?: "")
                )
            )
            createGeneratePrContentTool(context).handler(generateRequest)
        }

        // 시나리오 3: base_branch만 있음
        // → 변경사항 분석 + JIRA 티켓 요청
        baseBranch != null -> {
            val selectRequest = createRequest(
                mapOf(
                    "working_dir" to workingDir,
                    "base_branch" to baseBranch
                )
            )
            createSelectBaseBranchTool(context).handler(selectRequest)
        }

        // 시나리오 4: 파라미터 없음
        // → 단계별 워크플로우 시작 (브랜치 선택 안내)
        else -> {
            val startRequest = createRequest(
                mapOf("working_dir" to workingDir)
            )
            createStartPrWorkflowTool(context).handler(startRequest)
        }
    }
}

/**
 * CallToolRequest 생성 헬퍼 함수
 */
private fun createRequest(args: Map<String, String>): CallToolRequest {
    val jsonArgs = buildJsonObject {
        args.forEach { (key, value) ->
            if (value.isNotEmpty()) {
                put(key, value)
            }
        }
    }

    return CallToolRequest(
        params = CallToolRequestParams(
            name = "",
            arguments = jsonArgs
        )
    )
}