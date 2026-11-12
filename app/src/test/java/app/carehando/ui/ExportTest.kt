package app.carehando.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportTest {
    @Test fun csvEscapesQuotesAndNeutralisesSpreadsheetExpressions() {
        assertEquals("\"New \"\"note\"\"\"", csvCell("New \"note\""))
        assertEquals("\"'  =1+2\"", csvCell("  =1+2"))
        assertEquals("\"plain text\"", csvCell("plain text"))
    }
}
