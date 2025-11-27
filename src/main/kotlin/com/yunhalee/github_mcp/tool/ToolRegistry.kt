package com.yunhalee.github_mcp.tool

import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import io.modelcontextprotocol.kotlin.sdk.server.Server

/**
 * Tool 레지스트리
 * 모든 RegisteredTool을 중앙에서 관리하고 등록합니다.
 */
class ToolRegistry(private val context: ToolContext) {

    /**
     * 모든 RegisteredTool을 반환합니다.
     */
    fun getAllTools(): List<RegisteredTool> = listOf(
        createStartPrWorkflowTool(context),
        createSelectBaseBranchTool(context),
        createGeneratePrContentTool(context),
        createCreatePrConfirmedTool(context),
        createGetCurrentBranchTool(context)
    )

    /**
     * 모든 Tool을 서버에 등록합니다.
     */
    fun registerAll(server: Server) {
        getAllTools().forEach { registeredTool ->
            server.addTool(registeredTool.tool, registeredTool.handler)
            System.err.println("✓ Registered tool: ${registeredTool.tool.name}")
        }
    }
}