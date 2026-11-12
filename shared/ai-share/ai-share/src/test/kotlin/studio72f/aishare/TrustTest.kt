package studio72f.aishare

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustTest {
    private val tofuKey = Trust.PACKAGES.getValue("com.studio72f.tofu").first()
    private val testKey = Trust.TEST_CERTS.first()

    @Test fun aKnownAppWithItsPlayKeyIsTrusted() = assertTrue(Trust.decide("com.studio72f.tofu", setOf(tofuKey), "com.android.shell"))

    @Test fun anotherAppsKeyDoesNotCount() =
        assertFalse(Trust.decide("com.studio72f.remerit", setOf(tofuKey), "com.android.shell"))

    @Test fun theBuildMachinesKeyCountsForTestInstalls() = assertTrue(Trust.decide("com.studio72f.hypermind", setOf(testKey), null))

    @Test fun aPlayInstallCountsBeforeTheKeyIsKnown() = assertTrue(Trust.decide("com.studio72f.eigen", setOf("AA"), Trust.PLAY_STORE))

    @Test fun aSideloadedCopyOfAnUnknownKeyIsNot() = assertFalse(Trust.decide("com.studio72f.eigen", setOf("AA"), "com.android.shell"))

    @Test fun aStrangerIsNeverTrusted() {
        assertFalse(Trust.decide("com.example.fake", setOf(tofuKey, testKey), Trust.PLAY_STORE))
    }

    @Test fun keysAreUppercaseColonFingerprints() {
        (Trust.PACKAGES.values.flatten() + Trust.TEST_CERTS).forEach { assertTrue(it, Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}").matches(it)) }
    }
}
