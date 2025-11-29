package com.yunhalee.github_mcp.tool

import com.yunhalee.github_mcp.service.GitHubService
import com.yunhalee.github_mcp.service.GitService
import com.yunhalee.github_mcp.component.TemplateLoader

/**
 * Tool 실행에 필요한 컨텍스트 정보
 */
data class ToolContext(
    val defaultBaseBranch: String,
    val jiraPrefix: String,
    val githubService: GitHubService?,
    val prTemplatePath: String? = null,
    val gitService: GitService = GitService(),
) {

    private val templateLoader: TemplateLoader by lazy {
        TemplateLoader(customTemplatePath = prTemplatePath)
    }

    fun loadPrTemplate(workingDir: String): String {
        return templateLoader.loadPrTemplate(workingDir)
    }
}