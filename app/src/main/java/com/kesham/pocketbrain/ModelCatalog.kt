package com.kesham.pocketbrain

/** What a model is best at, shown as a tag in the Model Manager. */
enum class ModelCapability(val label: String) {
    CHAT("Chat"),
    REASONING("Reasoning"),
    CODING("Coding"),
}

/**
 * A model PocketBrain can fetch by itself.
 *
 * Every entry must be **ungated** on Hugging Face: the app never handles access tokens, so a
 * gated model (Gemma 3 1B among them) can only arrive via push_model.sh. [sizeBytes] and
 * [sha256] come from the Hugging Face LFS metadata and are both checked after downloading.
 */
data class CatalogEntry(
    val model: Model,
    val capability: ModelCapability,
    val sizeBytes: Long,
    val sha256: String,
) {
    val displayName: String get() = model.displayName
    val fileName: String get() = model.fileName
    val url: String get() = model.url
}

/** Adding a downloadable model is one entry in [entries]. */
object ModelCatalog {

    val entries: List<CatalogEntry> = listOf(
        CatalogEntry(
            model = Model.DEEPSEEK_R1_DISTILL_QWEN_1_5_B,
            capability = ModelCapability.REASONING,
            sizeBytes = 1_861_094_737L,
            sha256 = "31e6e3fdcb846e20e34f9f51b584f272b0869bf50bfd74dd2da03740854531f7",
        ),
        CatalogEntry(
            model = Model.PHI_4_MINI_INSTRUCT,
            capability = ModelCapability.CODING,
            sizeBytes = 3_944_275_882L,
            sha256 = "e494f9e827fbf47ac271d67dde77308f4a7683ad1ff630ab5bec926f17573b5f",
        ),
        CatalogEntry(
            model = Model.QWEN2_1_5B_INSTRUCT,
            capability = ModelCapability.CHAT,
            sizeBytes = 1_597_913_616L,
            sha256 = "8d867a7c93a6acf2892f08e0174e2f6f351ad256b7e3cfb6d6cd9c89794b42e0",
        ),
        CatalogEntry(
            model = Model.QWEN2_0_5B_INSTRUCT,
            capability = ModelCapability.CHAT,
            sizeBytes = 546_660_344L,
            sha256 = "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2",
        ),
    )

    fun forModel(model: Model): CatalogEntry? = entries.firstOrNull { it.model == model }
}
