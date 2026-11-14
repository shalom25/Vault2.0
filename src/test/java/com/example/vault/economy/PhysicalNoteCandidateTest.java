package com.example.vault.economy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PhysicalNoteCandidateTest {
    private static final Set<String> SERIAL_LABELS = Set.of(
            "Serial #:", "Serial:", "Número de série:", "序列号", "क्रम संख्या:");

    @Test
    void fluffyMachinesAcbUpgradeCardMustPassThroughUnchanged() {
        assertFalse(PhysicalNoteCandidate.hasSignedLegacyLore(List.of(
                "Right-click an Advanced Charging Bench to upgrade it",
                "Charge speed: +5J",
                "Capacity: +128J",
                "Energy consumption: +10J"), SERIAL_LABELS));
    }

    @Test
    void otherPaperItemsWithRedeemOrRightClickInstructionsMustPass() {
        assertFalse(PhysicalNoteCandidate.hasSignedLegacyLore(List.of(
                "Right click to redeem your reward",
                "Amount: 20", "Currency: coins", "Issuer: NPC"), SERIAL_LABELS));
        assertFalse(PhysicalNoteCandidate.hasSignedLegacyLore(List.of(
                "Serial #: ABC123", "Right click to redeem"), SERIAL_LABELS));
        assertFalse(PhysicalNoteCandidate.hasSignedLegacyLore(List.of(
                "SIG: not-real", "Right click to redeem"), SERIAL_LABELS));
    }

    @Test
    void legitimateLegacyNoteWithSerialAndSignatureIsRecognized() {
        assertTrue(PhysicalNoteCandidate.hasSignedLegacyLore(List.of(
                "Currency: default", "Amount: 100.0", "Issuer: Test", "Serial #: a1b2c3d4",
                "Right click to redeem", "SIG: QnV0Tm90QVNpZ25hdHVyZQ"), SERIAL_LABELS));
    }

    @Test
    void tamperedOrForgedNoteWithSignedNoteMarkersStillReachesValidator() {
        assertTrue(PhysicalNoteCandidate.hasSignedLegacyLore(List.of(
                "Serial #: tampered-id", "SIG: forged-value"), SERIAL_LABELS));
        assertTrue(PhysicalNoteCandidate.hasSignedLegacyLore(List.of(
                "Serial: tampered-id", "HMAC: invalid"), SERIAL_LABELS));
    }

    @Test
    void multilingualExistingNotesStillReachValidator() {
        assertTrue(PhysicalNoteCandidate.hasSignedLegacyLore(List.of(
                "Número de série: 12345", "SIG: invalid"), SERIAL_LABELS));
        assertTrue(PhysicalNoteCandidate.hasSignedLegacyLore(List.of(
                "序列号 12345", "SIG: invalid"), SERIAL_LABELS));
        assertTrue(PhysicalNoteCandidate.hasSignedLegacyLore(List.of(
                "क्रम संख्या: 12345", "SIG: invalid"), SERIAL_LABELS));
    }

    @Test
    void emptySignatureOrSerialCannotClaimNoteIdentity() {
        assertFalse(PhysicalNoteCandidate.hasSignedLegacyLore(List.of(
                "Serial #: abc", "SIG: "), SERIAL_LABELS));
        assertFalse(PhysicalNoteCandidate.hasSignedLegacyLore(List.of(
                "Serial #: ", "SIG: value"), SERIAL_LABELS));
        assertFalse(PhysicalNoteCandidate.hasSignedLegacyLore(List.of(
                "Serial:", "SIG: value"), SERIAL_LABELS));
        assertFalse(PhysicalNoteCandidate.hasSignedLegacyLore(
                List.of("Serial #: abc", "SIG: value"), Set.of()));
        assertFalse(PhysicalNoteCandidate.hasSignedLegacyLore(null, SERIAL_LABELS));
    }
}
