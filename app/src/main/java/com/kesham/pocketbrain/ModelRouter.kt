package com.kesham.pocketbrain

/**
 * Picks the model to answer a prompt by pattern-matching the text. Coding signals win over
 * reasoning ones, since a prompt like "why does this function throw" is better served by the
 * coding model.
 */
object ModelRouter {

    private val codeKeywords = Regex(
        "\\b(function|func|method|class|variable|compile|compiler|runtime|error|exception|" +
            "stacktrace|traceback|nullpointer|npe|segfault|debug|debugging|bug|crash|refactor|" +
            "syntax|regex|api|sdk|json|yaml|xml|query|git|docker|build|deploy|library|dependency|" +
            "algorithm|array|string|boolean|integer|loop|recursion|pointer|thread|async|callback)\\b",
        RegexOption.IGNORE_CASE
    )

    private val codeLanguages = Regex(
        "\\b(kotlin|java|javascript|typescript|python|golang|rust|swift|php|ruby|scala|perl|" +
            "haskell|dart|elixir|sql|html|css|bash|zsh|powershell|node|react|android|compose)\\b" +
            "|c\\+\\+|c#|objective-c",
        RegexOption.IGNORE_CASE
    )

    private val codeFormatting = Regex("`|\\bstack\\s+trace\\b|\\bnull\\s+pointer\\b|[{};]\\s*$", RegexOption.IGNORE_CASE)

    private val reasoningKeywords = Regex(
        "\\b(why|how come|explain|reason|reasoning|prove|proof|derive|deduce|solve|calculate|" +
            "compute|analyse|analyze|analysis|compare|contrast|tradeoff|trade-off|justify|" +
            "implication|conclude|infer)\\b|\\bstep[ -]by[ -]step\\b|\\bwalk me through\\b|" +
            "\\bthink (it )?through\\b|\\bwork (it )?out\\b",
        RegexOption.IGNORE_CASE
    )

    private val mathExpression = Regex(
        "\\d+\\s*(?:[+\\-*/^%]|x|×|÷|times|plus|minus|divided by|multiplied by)\\s*\\d+",
        RegexOption.IGNORE_CASE
    )

    private val clauseJoiners = Regex(
        "\\b(and|but|because|since|although|however|whereas|if|then|while|unless)\\b",
        RegexOption.IGNORE_CASE
    )

    fun route(prompt: String): Model = when {
        isCoding(prompt) -> Model.PHI_4_MINI_INSTRUCT
        isReasoning(prompt) -> Model.DEEPSEEK_R1_DISTILL_QWEN_1_5_B
        else -> DEFAULT_MODEL
    }

    private fun isCoding(prompt: String): Boolean =
        codeFormatting.containsMatchIn(prompt) ||
            codeKeywords.containsMatchIn(prompt) ||
            codeLanguages.containsMatchIn(prompt)

    private fun isReasoning(prompt: String): Boolean =
        reasoningKeywords.containsMatchIn(prompt) ||
            mathExpression.containsMatchIn(prompt) ||
            isLongMultiClauseQuestion(prompt)

    private fun isLongMultiClauseQuestion(prompt: String): Boolean {
        if (!prompt.contains('?')) return false
        val wordCount = prompt.trim().split(Regex("\\s+")).size
        val clauseCount = prompt.count { it == ',' } + clauseJoiners.findAll(prompt).count()
        return wordCount >= 25 || clauseCount >= 2
    }
}
