package com.yunhalee.github_mcp

import com.yunhalee.github_mcp.service.GitHubService
import com.yunhalee.github_mcp.tool.ToolContext
import com.yunhalee.github_mcp.tool.ToolRegistry
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

fun main(): Unit = runBlocking {
    // 환경변수 설정
    val githubToken = System.getenv("GITHUB_TOKEN") ?: ""
    val defaultWorkingDir = System.getenv("WORKING_DIR") ?: System.getProperty("user.dir")
    val defaultBaseBranch = System.getenv("PR_BASE_BRANCH") ?: "develop"
    val jiraPrefix = System.getenv("PR_JIRA_PREFIX") ?: "PROJ"

    val githubService = if (githubToken.isNotEmpty()) GitHubService(githubToken) else null

    System.err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    System.err.println("🚀 GitHub MCP Server")
    System.err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    System.err.println("기본 디렉토리: $defaultWorkingDir")
    System.err.println("GitHub token: ${if (githubToken.isNotEmpty()) "✅ Configured" else "⚠️  Not configured"}")
    System.err.println("기본 Base 브랜치: $defaultBaseBranch")
    System.err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

    // MCP 서버 생성
    val server = Server(
        serverInfo = Implementation(
            name = "github-mcp",
            version = "1.0.0"
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools()
            )
        )
    ) {
        "GitHub 사용자 액션을 대화형으로 생성하는 MCP 서버입니다. Git 변경사항 분석, PR 내용 생성, GitHub API 연동 등의 기능을 제공합니다."
    }

    // Tool Context 생성
    val toolContext = ToolContext(
        defaultWorkingDir = defaultWorkingDir,
        defaultBaseBranch = defaultBaseBranch,
        jiraPrefix = jiraPrefix,
        githubService = githubService
    )


    // Tool 등록
    System.err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    System.err.println("📦 Registering tools...")
    val toolRegistry = ToolRegistry(toolContext)
    toolRegistry.registerAll(server)
    System.err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

    // STDIO Transport로 연결
    System.err.println("✅ Server started successfully")
    System.err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered(),
    )
    runBlocking {
        server.createSession(transport)
        val done = Job()
        server.onClose {
            done.complete()
        }
        done.join()
    }
}