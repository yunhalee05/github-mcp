package com.yunhalee.github_mcp.tool

import com.yunhalee.github_mcp.service.GitHubService
import com.yunhalee.github_mcp.service.GitService

/**
 * Tool 실행에 필요한 컨텍스트 정보
 */
data class ToolContext(
    val defaultWorkingDir: String,
    val defaultBaseBranch: String,
    val jiraPrefix: String,
    val githubService: GitHubService?
) {
    fun createGitService(workingDir: String? = null): GitService {
        return GitService(workingDir ?: defaultWorkingDir)
    }
}