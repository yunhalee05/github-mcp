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
        // 스마트 진입점 (권장)
        createPrSmartTool(context),

        // 기존 4단계 워크플로우 (단계별 제어가 필요한 경우)
        createStartPrWorkflowTool(context),
        createSelectBaseBranchTool(context),
        createGeneratePrContentTool(context),
        createCreatePrConfirmedTool(context),

        // 유틸리티
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