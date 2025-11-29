package com.yunhalee.github_mcp.component

import java.io.File

/**
 * 템플릿 로더
 *
 * PR 템플릿을 다음 우선순위로 로드합니다:
 * 1. 실행 저장소 내 .github/PULL_REQUEST_TEMPLATE.md
 * 2. 실행 저장소 내 .github/pull_request_template.md
 * 3. 환경변수로 지정된 경로 (PR_TEMPLATE_PATH)
 * 4. 기본 템플릿 반환
 */
class TemplateLoader(
    private val customTemplatePath: String? = null
) {
    /**
     * PR 템플릿을 로드합니다.
     *
     * @param workingDir 작업 디렉토리 (Git 저장소 루트)
     * @return 로드된 PR 템플릿 문자열
     */
    fun loadPrTemplate(workingDir: String): String {
        // 템플릿 탐색 우선순위
        val templatePaths = listOf(
            "$workingDir/.github/PULL_REQUEST_TEMPLATE.md",
            "$workingDir/.github/pull_request_template.md",
            customTemplatePath
        )

        for (path in templatePaths) {
            path?.let {
                val file = File(it)
                if (file.exists() && file.isFile) {
                    return file.readText()
                }
            }
        }

        return DEFAULT_PR_TEMPLATE
    }

    companion object {
        /**
         * 기본 PR 템플릿 (저장소에 템플릿이 없을 경우 사용)
         */
        private val DEFAULT_PR_TEMPLATE = """
        ## 🛠 작업 내용

        - JIRA:

        ## 📝 변경 사항

        - [ ] 새로운 기능
        - [ ] 기존 기능 수정 or improve
        - [ ] Bug fix
        - [ ] 리팩토링
        - [ ] 문서작성
        - [ ] 설정값 변경

        ## ✔️ 체크리스트

        - [ ] 단위 테스트 작성완료
        - [ ] Local 테스트 완료

        ## 🙏🏻 리뷰 포인트 (To Reviewers)

        """.trimIndent()
    }
}