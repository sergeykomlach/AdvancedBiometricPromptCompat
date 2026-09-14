package dev.skomlach.biometric.compat.impl.dialogs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDialogLayoutPolicyTest {
    @Test fun aospContainerCanBeDeclaredAsFrameLayoutButOplusStillNeedsItsOwnContainer() {
        assertTrue(NativeDialogLayoutPolicy.matchesAuthContainer("FrameLayout", false))
        assertTrue(NativeDialogLayoutPolicy.matchesAuthContainer("com.android.systemui.biometrics.AuthContainerView", false))
        assertFalse(NativeDialogLayoutPolicy.matchesAuthContainer("LinearLayout", false))
        assertFalse(NativeDialogLayoutPolicy.matchesAuthContainer("FrameLayout", true))
        assertFalse(NativeDialogLayoutPolicy.matchesAuthContainer("com.oplus.systemui.biometrics.OplusAuthContainerView", false))
        assertTrue(NativeDialogLayoutPolicy.matchesAuthContainer("com.oplus.systemui.biometrics.OplusAuthContainerView", true))
    }

    @Test fun phoneSwitchesPanesWithOrientation() {
        assertFalse(NativeDialogLayoutPolicy.twoPane(false, 411))
        assertTrue(NativeDialogLayoutPolicy.twoPane(true, 411))
    }

    @Test fun unfoldedLargeDisplayKeepsOnePaneInLandscape() {
        assertFalse(NativeDialogLayoutPolicy.twoPane(true, 852))
        assertFalse(NativeDialogLayoutPolicy.twoPane(true, 600))
        assertTrue(NativeDialogLayoutPolicy.twoPane(true, 599))
        assertFalse(NativeDialogLayoutPolicy.twoPane(true, 0))
    }

    @Test fun oemPaneNameSpellingMustMatchBothRequestedPaneAndXmlStructure() {
        for (name in listOf("biometric_prompt_two_pane_layout", "oem_biometric_twopane_dialog")) {
            assertTrue(NativeDialogLayoutPolicy.matchesPane(name, true, true))
            assertFalse(NativeDialogLayoutPolicy.matchesPane(name, false, true))
            assertFalse(NativeDialogLayoutPolicy.matchesPane(name, true, false))
        }
        assertTrue(NativeDialogLayoutPolicy.matchesPane("oem_biometric_onepane_dialog", false, false))
        assertFalse(NativeDialogLayoutPolicy.matchesPane("biometric_onepane_twopane", true, true))
    }

    @Test fun unnamedPaneStillRequiresTheMatchingStructure() {
        assertTrue(NativeDialogLayoutPolicy.matchesPane("oem_biometric_prompt", false, false))
        assertFalse(NativeDialogLayoutPolicy.matchesPane("oem_biometric_prompt", true, false))
    }

    @Test fun enrollmentSettingsAndCredentialScreensAreNotPromptCandidates() {
        for (name in listOf("biometric_enroll", "biometric_settings_dialog", "biometric_credential",
            "biometric_prompt_udfps_accessibility_overlay", "keyguard_biometric_prompt", "fingerprint_settings")) {
            assertFalse(name, NativeDialogLayoutPolicy.isDiscoveryCandidate(name))
        }
        assertTrue(NativeDialogLayoutPolicy.isDiscoveryCandidate("oem_biometric_onepane_dialog"))
        assertTrue(NativeDialogLayoutPolicy.isDiscoveryCandidate("biometric_dialog"))
    }

    @Test fun multipleValidOemLayoutsAreAmbiguousRegardlessOfApkEntryOrder() {
        assertNull(NativeDialogLayoutPolicy.unique(listOf("a", "b")))
        assertNull(NativeDialogLayoutPolicy.unique(listOf("b", "a")))
        assertNull(NativeDialogLayoutPolicy.unique(emptyList<String>()))
        assertEquals("only", NativeDialogLayoutPolicy.unique(listOf("only")))
    }

    @Test fun fingerprintPromptNamesAreDiscoveredWithTheSameExclusionsAsBiometricNames() {
        for (name in listOf("fingerprint_dialog", "oem_fingerprint_prompt", "fingerprint_onepane_dialog")) {
            assertTrue(name, NativeDialogLayoutPolicy.isDiscoveryCandidate(name))
        }
        for (name in listOf("fingerprint_enroll", "fingerprint_settings", "fingerprint_credential",
            "fingerprint_keyguard_view", "fingerprint_accessibility_overlay", "../fingerprint_dialog")) {
            assertFalse(name, NativeDialogLayoutPolicy.isDiscoveryCandidate(name))
        }
    }
}
