package com.yunhalee.github_mcp.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GitHubService(private val token: String) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }

    suspend fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        body: String,
        head: String,
        base: String
    ): Result<PullRequestResponse> = runCatching {
        val response: HttpResponse = client.post("https://api.github.com/repos/$owner/$repo/pulls") {
            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            contentType(ContentType.Application.Json)
            setBody(
                PullRequestRequest(
                    title = title,
                    body = body,
                    head = head,
                    base = base
                )
            )
        }

        if (response.status.isSuccess()) {
            response.body<PullRequestResponse>()
        } else {
            throw RuntimeException("Failed to create PR: ${response.status} - ${response.bodyAsText()}")
        }
    }

    suspend fun getRepository(owner: String, repo: String): Result<RepositoryResponse> = runCatching {
        val response: HttpResponse = client.get("https://api.github.com/repos/$owner/$repo") {
            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }

        if (response.status.isSuccess()) {
            response.body<RepositoryResponse>()
        } else {
            throw RuntimeException("Failed to get repository: ${response.status} - ${response.bodyAsText()}")
        }
    }

    suspend fun listPullRequests(
        owner: String,
        repo: String,
        state: String = "open"
    ): Result<List<PullRequestResponse>> = runCatching {
        val response: HttpResponse = client.get("https://api.github.com/repos/$owner/$repo/pulls") {
            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            parameter("state", state)
        }

        if (response.status.isSuccess()) {
            response.body<List<PullRequestResponse>>()
        } else {
            throw RuntimeException("Failed to list PRs: ${response.status} - ${response.bodyAsText()}")
        }
    }

    fun close() {
        client.close()
    }

    @Serializable
    data class PullRequestRequest(
        val title: String,
        val body: String,
        val head: String,
        val base: String
    )

    @Serializable
    data class PullRequestResponse(
        val number: Int,
        val title: String,
        val body: String? = null,
        val state: String,
        val html_url: String,
        val created_at: String,
        val updated_at: String,
        val head: BranchInfo,
        val base: BranchInfo
    )

    @Serializable
    data class BranchInfo(
        val ref: String,
        val sha: String
    )

    @Serializable
    data class RepositoryResponse(
        val name: String,
        val full_name: String,
        val description: String? = null,
        val html_url: String,
        val default_branch: String
    )
}