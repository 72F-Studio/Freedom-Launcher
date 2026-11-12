package studio72f.aishare

/**
 * A model the 72F apps can share. Its [id] is the file's SHA-256, so two apps agree on a model only when they hold
 * the very same bytes; the file name and version are for people and for the apps' own download code.
 */
data class SharedModel(
    val id: String,
    val name: String,
    val fileName: String,
    val version: String,
    val sizeBytes: Long,
    val vision: Boolean = false,
)

/** The Gemma builds 72F hosts on Hugging Face (72fstudio/gemma-3 and gemma-4); the same files Tofu, ReMerit and HyperMind download. */
object SharedModels {
    val GEMMA_3_270M = SharedModel(
        id = "757e9119fa5bd667a2774fb470ac4afcd3190a21c677f8e69a5d6bc908abdd63",
        name = "Gemma 3 270M", fileName = "gemma3-270m-it-q8.litertlm", version = "3.0.0", sizeBytes = 304_005_120L,
    )
    val GEMMA_3_1B = SharedModel(
        id = "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be",
        name = "Gemma 3 1B", fileName = "gemma3-1b-it-int4.litertlm", version = "3.0.0", sizeBytes = 584_417_280L,
    )
    val GEMMA_4_E2B = SharedModel(
        id = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        name = "Gemma 4 E2B", fileName = "gemma-4-E2B-it.litertlm", version = "4.0.0", sizeBytes = 2_588_147_712L, vision = true,
    )
    val GEMMA_4_E4B = SharedModel(
        id = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
        name = "Gemma 4 E4B", fileName = "gemma-4-E4B-it.litertlm", version = "4.0.0", sizeBytes = 3_659_530_240L, vision = true,
    )

    val ALL = listOf(GEMMA_3_270M, GEMMA_3_1B, GEMMA_4_E2B, GEMMA_4_E4B)

    fun byId(id: String?): SharedModel? = ALL.firstOrNull { it.id == id }

    /** The shared model with this file name (for an app whose catalogue keeps no checksum; [AiShare] checks the size). */
    fun byFileName(fileName: String): SharedModel? = ALL.firstOrNull { it.fileName == fileName }

    /** The shared model an app's own download is, matched by file name and checksum. */
    fun byFile(fileName: String, sha256: String): SharedModel? =
        ALL.firstOrNull { it.fileName == fileName && it.id.equals(sha256, ignoreCase = true) }
}
