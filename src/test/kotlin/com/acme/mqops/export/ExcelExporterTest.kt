package com.acme.mqops.export

import com.acme.mqops.mq.MessageRow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class ExcelExporterTest {
    private val exporter = ExcelExporter()

    @Test
    fun `header row has expected columns`() {
        val bytes = exporter.export(emptyList())
        val sheet = XSSFWorkbook(ByteArrayInputStream(bytes)).getSheetAt(0)
        val headers = (0..6).map { sheet.getRow(0).getCell(it).stringCellValue }
        assertEquals(
            listOf("JMS Message ID", "Correlation ID", "Timestamp", "Expiration", "Priority", "Type", "Preview"),
            headers
        )
    }

    @Test
    fun `data row contains message fields with timestamps formatted as UTC`() {
        val row = MessageRow(
            jmsMessageId = "ID:abc",
            correlationId = "corr-1",
            timestamp = 0L,
            expiration = 86400000L,
            priority = 4,
            type = "TextMessage",
            preview = "hello world"
        )
        val bytes = exporter.export(listOf(row))
        val sheet = XSSFWorkbook(ByteArrayInputStream(bytes)).getSheetAt(0)
        val dataRow = sheet.getRow(1)
        assertEquals("ID:abc", dataRow.getCell(0).stringCellValue)
        assertEquals("corr-1", dataRow.getCell(1).stringCellValue)
        assertEquals("1970-01-01 00:00:00 UTC", dataRow.getCell(2).stringCellValue)
        assertEquals("1970-01-02 00:00:00 UTC", dataRow.getCell(3).stringCellValue)
        assertEquals("4", dataRow.getCell(4).stringCellValue)
        assertEquals("TextMessage", dataRow.getCell(5).stringCellValue)
        assertEquals("hello world", dataRow.getCell(6).stringCellValue)
    }

    @Test
    fun `null fields produce empty string cells`() {
        val row = MessageRow(
            jmsMessageId = "ID:xyz",
            correlationId = null,
            timestamp = null,
            expiration = null,
            priority = null,
            type = "BytesMessage",
            preview = "[payload preview unsupported]"
        )
        val bytes = exporter.export(listOf(row))
        val sheet = XSSFWorkbook(ByteArrayInputStream(bytes)).getSheetAt(0)
        val dataRow = sheet.getRow(1)
        assertEquals("", dataRow.getCell(1).stringCellValue)
        assertEquals("", dataRow.getCell(2).stringCellValue)
        assertEquals("", dataRow.getCell(3).stringCellValue)
        assertEquals("", dataRow.getCell(4).stringCellValue)
    }
}
