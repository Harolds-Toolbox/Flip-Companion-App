package uk.co.flipwhatsapphangup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLabelsTest {
    @Test fun greekOngoingCallIsNotIncoming() {
        assertFalse(CallLabels.incomingText("Κλήση σε εξέλιξη"))
        assertTrue(CallLabels.ongoingText("Κλήση σε εξέλιξη"))
    }

    @Test fun greekCallActionsAndVideoMarker() {
        assertTrue(CallLabels.answer("Απάντηση"))
        assertTrue(CallLabels.hangup("Τερματισμός"))
        assertTrue(CallLabels.video("Εισερχόμενη βιντεοκλήση"))
        assertFalse(CallLabels.answer("Βίντεο", video = false))
        assertTrue(CallLabels.answer("Βίντεο", video = true))
    }
}
