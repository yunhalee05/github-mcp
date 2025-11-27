package com.yunhalee.github_mcp.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class GitService(private val workingDir: String = ".") {

    suspend fun getCurrentBranch(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            executeCommand("git", "branch", "--show-current").trim()
        }
    }

    suspend fun getBranches(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val output = executeCommand("git", "branch", "-r")
            output.lines()
                .filter { it.contains("origin/") && !it.contains("HEAD") }
                .map { it.trim().removePrefix("origin/") }
        }
    }

    suspend fun getDiff(baseBranch: String, currentBranch: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            executeCommand("git", "diff", "origin/$baseBranch...$currentBranch")
        }
    }

    suspend fun getChangedFiles(baseBranch: String, currentBranch: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val output = executeCommand("git", "diff", "--name-only", "origin/$baseBranch...$currentBranch")
            output.lines().filter { it.isNotBlank() }
        }
    }

    suspend fun getCommits(baseBranch: String, currentBranch: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val output = executeCommand("git", "log", "origin/$baseBranch..$currentBranch", "--pretty=format:%s")
            output.lines().filter { it.isNotBlank() }
        }
    }

    suspend fun getCommitCount(baseBranch: String, currentBranch: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            executeCommand("git", "rev-list", "--count", "origin/$baseBranch..$currentBranch").trim().toInt()
        }
    }

    suspend fun pushBranch(branch: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            executeCommand("git", "push", "-u", "origin", branch)
        }
    }

    suspend fun fetchBranch(branch: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            executeCommand("git", "fetch", "origin", branch)
        }
    }

    suspend fun checkRemoteBranchExists(branch: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            try {
                executeCommand("git", "ls-remote", "--exit-code", "--heads", "origin", branch)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun getRepositoryInfo(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val remoteUrl = executeCommand("git", "config", "--get", "remote.origin.url").trim()
            val owner: String
            val repo: String

            // Parse GitHub URL
            val regex = """github\.com[:/](.+?)/(.+?)(?:\.git)?$""".toRegex()
            val matchResult = regex.find(remoteUrl)

            if (matchResult != null) {
                owner = matchResult.groupValues[1]
                repo = matchResult.groupValues[2]
            } else {
                throw IllegalStateException("Cannot parse GitHub repository URL: $remoteUrl")
            }

            mapOf(
                "owner" to owner,
                "repo" to repo,
                "remoteUrl" to remoteUrl
            )
        }
    }

    private fun executeCommand(vararg command: String): String {
        val process = ProcessBuilder(*command)
            .directory(File(workingDir))
            .redirectErrorStream(true)
            .start()

        val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.readText()
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("Command failed with exit code $exitCode: ${command.joinToString(" ")}\nOutput: $output")
        }

        return output
    }
}