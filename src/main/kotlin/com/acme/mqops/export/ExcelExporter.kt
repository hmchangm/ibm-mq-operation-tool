package com.acme.mqops.export

import com.acme.mqops.mq.MessageRow
import jakarta.enterprise.context.ApplicationScoped
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@ApplicationScoped
class ExcelExporter {
    private val timestampFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    fun export(rows: List<MessageRow>): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Messages")
        val headerRow = sheet.createRow(0)
        listOf("JMS Message ID", "Correlation ID", "Timestamp", "Expiration", "Priority", "Type", "Preview")
            .forEachIndexed { i, h -> headerRow.createCell(i).setCellValue(h) }
        rows.forEachIndexed { idx, msg ->
            val row = sheet.createRow(idx + 1)
            row.createCell(0).setCellValue(msg.jmsMessageId)
            row.createCell(1).setCellValue(msg.correlationId ?: "")
            row.createCell(2).setCellValue(msg.timestamp?.let { formatMs(it) } ?: "")
            row.createCell(3).setCellValue(msg.expiration?.let { formatMs(it) } ?: "")
            row.createCell(4).setCellValue(msg.priority?.toString() ?: "")
            row.createCell(5).setCellValue(msg.type)
            row.createCell(6).setCellValue(msg.preview)
        }
        return ByteArrayOutputStream().also { workbook.write(it); workbook.close() }.toByteArray()
    }

    private fun formatMs(epochMs: Long): String =
        timestampFormatter.format(Instant.ofEpochMilli(epochMs)) + " UTC"
}
