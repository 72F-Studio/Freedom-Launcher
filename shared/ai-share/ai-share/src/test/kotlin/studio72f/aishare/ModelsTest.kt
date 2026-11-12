package studio72f.aishare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test fun idsAreSha256AndUnique() {
        SharedModels.ALL.forEach { assertTrue(it.name, Regex("[0-9a-f]{64}").matches(it.id)) }
        assertEquals(SharedModels.ALL.size, SharedModels.ALL.map { it.id }.toSet().size)
    }

    @Test fun anAppsDownloadMatchesOnlyTheSameBytes() {
        val m = SharedModels.GEMMA_3_1B
        assertEquals(m, SharedModels.byFile(m.fileName, m.id.uppercase()))
        assertNull(SharedModels.byFile(m.fileName, SharedModels.GEMMA_3_270M.id))
    }

    @Test fun onlyGemma4SeesImages() = assertEquals(setOf(SharedModels.GEMMA_4_E2B, SharedModels.GEMMA_4_E4B), SharedModels.ALL.filter { it.vision }.toSet())

    @Test fun errorCodesAreStableAndUnique() {
        assertEquals(AiShareError.entries.size, AiShareError.entries.map { it.code }.toSet().size)
        assertEquals(AiShareError.GENERATE, AiShareError.of("nonsense"))
        assertEquals(AiShareError.HOST_GONE, AiShareError.of("AIS-05"))
    }
}
