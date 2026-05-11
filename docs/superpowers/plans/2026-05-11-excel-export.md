# Excel Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a server-side "Export All" button that fetches every message from the selected queue and downloads them as an `.xlsx` file.

**Architecture:** A new `POST /api/export` endpoint in `MqApiResource` calls `MqOperationService.export()` → `gateway.browse(target, Int.MAX_VALUE)`, feeds the `List<MessageRow>` into a new `ExcelExporter` bean (Apache POI) and returns the bytes as a file download. The React frontend adds an Export button to `Toolbar` that POSTs the request and triggers a browser download via a temporary blob URL.

**Tech Stack:** Kotlin/Quarkus 3, Apache POI 5.3.0 (`poi-ooxml`), React 18 + TypeScript, Vite

---

## File Map

**New files:**
- `src/main/kotlin/com/acme/mqops/export/ExcelExporter.kt` — `@ApplicationScoped` bean; converts `List<MessageRow>` → `ByteArray` via POI `XSSFWorkbook`
- `src/test/kotlin/com/acme/mqops/export/ExcelExporterTest.kt` — plain JUnit5 (no Quarkus, no mocks)
- `frontend/src/api/exportQueue.ts` — async function; POSTs to `/api/export`, receives blob, triggers download

**Modified files:**
- `pom.xml` — add `poi-ooxml:5.3.0` dependency
- `src/main/kotlin/com/acme/mqops/service/MqOperationService.kt` — add `export()` method
- `src/main/kotlin/com/acme/mqops/web/MqApiRequest.kt` — add `ExportRequest` data class
- `src/main/kotlin/com/acme/mqops/web/MqApiResource.kt` — add `ExcelExporter` constructor param + `/api/export` endpoint
- `src/test/kotlin/com/acme/mqops/service/MqOperationServiceTest.kt` — add export test
- `src/test/kotlin/com/acme/mqops/web/MqApiResourceTest.kt` — add export endpoint tests
- `frontend/src/App.tsx` — add `exporting` state + `handleExport` + wire to Toolbar
- `frontend/src/components/Toolbar.tsx` — add `onExport` / `exporting` props + Export button

---

### Task 1: Add Apache POI dependency

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add poi-ooxml to pom.xml**

In `pom.xml`, inside `<dependencies>`, add after the IBM MQ `<dependency>` block:

```xml
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.3.0</version>
        </dependency>
```

- [ ] **Step 2: Verify the dependency resolves**

```bash
mvn dependency:resolve -q
```

Expected: `BUILD SUCCESS` with no errors.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add poi-ooxml 5.3.0 for Excel export"
```

---

### Task 2: ExcelExporter (TDD)

**Files:**
- Create: `src/test/kotlin/com/acme/mqops/export/ExcelExporterTest.kt`
- Create: `src/main/kotlin/com/acme/mqops/export/ExcelExporter.kt`

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/com/acme/mqops/export/ExcelExporterTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn test -Dtest=ExcelExporterTest
```

Expected: FAIL — `ExcelExporter` class not found.

- [ ] **Step 3: Implement ExcelExporter**

Create `src/main/kotlin/com/acme/mqops/export/ExcelExporter.kt`:

```kotlin
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
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
mvn test -Dtest=ExcelExporterTest
```

Expected: BUILD SUCCESS, 3 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/acme/mqops/export/ExcelExporter.kt \
        src/test/kotlin/com/acme/mqops/export/ExcelExporterTest.kt
git commit -m "feat: add ExcelExporter bean using Apache POI"
```

---

### Task 3: MqOperationService.export()

**Files:**
- Modify: `src/main/kotlin/com/acme/mqops/service/MqOperationService.kt`
- Modify: `src/test/kotlin/com/acme/mqops/service/MqOperationServiceTest.kt`

- [ ] **Step 1: Write failing test**

In `MqOperationServiceTest.kt`, add this test inside the `MqOperationServiceTest` class after the `browse passes configured limit to gateway` test:

```kotlin
@Test
fun `export calls gateway browse with max limit`() {
    val gateway = FakeGateway()
    val service = MqOperationService(gateway, RecordingAuditLogger())

    service.export("alice", target)

    assertEquals(Int.MAX_VALUE, gateway.lastBrowseLimit)
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
mvn test -Dtest=MqOperationServiceTest
```

Expected: FAIL — `Unresolved reference: export`.

- [ ] **Step 3: Add export() to MqOperationService**

In `MqOperationService.kt`, add this method directly after `browse()`:

```kotlin
fun export(user: String, target: MqTarget): List<MessageRow> = gateway.browse(target, Int.MAX_VALUE)
```

- [ ] **Step 4: Run full service test suite**

```bash
mvn test -Dtest=MqOperationServiceTest
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/acme/mqops/service/MqOperationService.kt \
        src/test/kotlin/com/acme/mqops/service/MqOperationServiceTest.kt
git commit -m "feat: add MqOperationService.export() forwarding to gateway.browse with max limit"
```

---

### Task 4: /api/export endpoint

**Files:**
- Modify: `src/main/kotlin/com/acme/mqops/web/MqApiRequest.kt`
- Modify: `src/main/kotlin/com/acme/mqops/web/MqApiResource.kt`
- Modify: `src/test/kotlin/com/acme/mqops/web/MqApiResourceTest.kt`

- [ ] **Step 1: Add ExportRequest data class**

In `MqApiRequest.kt`, add at the end of the file:

```kotlin
data class ExportRequest(val queueManager: String, val channel: String, val queue: String)
```

- [ ] **Step 2: Write failing endpoint test**

In `MqApiResourceTest.kt`, add inside the class:

```kotlin
@Test
@TestSecurity(user = "alice")
fun `export returns 400 for unknown queue manager`() {
    given()
        .contentType(ContentType.JSON)
        .body("""{"queueManager":"UNKNOWN","channel":"APP_SVRCONN","queue":"DEV.QUEUE.1"}""")
        .`when`().post("/api/export")
        .then()
        .statusCode(400)
}
```

- [ ] **Step 3: Run test to confirm it fails**

```bash
mvn test -Dtest=MqApiResourceTest
```

Expected: FAIL — the new test returns 404 (endpoint not yet defined).

- [ ] **Step 4: Add ExcelExporter to MqApiResource and implement the endpoint**

Replace the class declaration and constructor in `MqApiResource.kt` (the `class MqApiResource(` block through its closing `)`) with:

```kotlin
class MqApiResource(
    private val topology: MqTopologyService,
    private val operations: MqOperationService,
    private val identity: SecurityIdentity,
    private val excelExporter: ExcelExporter
)
```

Add the import alongside the other imports at the top of `MqApiResource.kt`:

```kotlin
import com.acme.mqops.export.ExcelExporter
```

Add the endpoint method after `clean()`:

```kotlin
@POST
@Path("/export")
@Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
fun export(request: ExportRequest): Response =
    withTarget(request.queueManager, request.channel, request.queue) { target ->
        val bytes = excelExporter.export(operations.export(user(), target))
        Response.ok(bytes)
            .header("Content-Disposition", "attachment; filename=\"export.xlsx\"")
            .build()
    }
```

- [ ] **Step 5: Run full API test suite**

```bash
mvn test -Dtest=MqApiResourceTest
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 6: Run all backend tests**

```bash
mvn test
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/acme/mqops/web/MqApiRequest.kt \
        src/main/kotlin/com/acme/mqops/web/MqApiResource.kt \
        src/test/kotlin/com/acme/mqops/web/MqApiResourceTest.kt
git commit -m "feat: add POST /api/export endpoint returning xlsx attachment"
```

---

### Task 5: Frontend exportQueue function

**Files:**
- Create: `frontend/src/api/exportQueue.ts`

- [ ] **Step 1: Create exportQueue.ts**

Create `frontend/src/api/exportQueue.ts`:

```typescript
interface ExportParams {
  queueManager: string
  channel: string
  queue: string
}

export async function exportQueue(params: ExportParams): Promise<void> {
  const res = await fetch('/api/export', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  })
  if (!res.ok) throw new Error(await res.text())
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'export.xlsx'
  a.click()
  URL.revokeObjectURL(url)
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/api/exportQueue.ts
git commit -m "feat: add exportQueue fetch function triggering xlsx download"
```

---

### Task 6: Wire Export button into Toolbar and App

**Files:**
- Modify: `frontend/src/components/Toolbar.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Add onExport and exporting props to Toolbar**

In `frontend/src/components/Toolbar.tsx`, replace the `ToolbarProps` interface with:

```typescript
interface ToolbarProps {
  topology: Topology
  queueManager: string
  channel: string
  queue: string
  onQueueManagerChange: (v: string) => void
  onChannelChange: (v: string) => void
  onQueueChange: (v: string) => void
  onBrowse: () => void
  onPut: () => void
  onClean: () => void
  onExport: () => void
  browsing: boolean
  exporting: boolean
}
```

Replace the `Toolbar` function signature destructuring with:

```typescript
export function Toolbar({
  topology,
  queueManager,
  channel,
  queue,
  onQueueManagerChange,
  onChannelChange,
  onQueueChange,
  onBrowse,
  onPut,
  onClean,
  onExport,
  browsing,
  exporting,
}: ToolbarProps) {
```

Add the Export button immediately after the Clean button in the JSX:

```tsx
<button onClick={onExport} disabled={!canAct || exporting}>
  {exporting ? 'Exporting…' : 'Export'}
</button>
```

- [ ] **Step 2: Wire handleExport into App.tsx**

In `frontend/src/App.tsx`, add the import at the top alongside the other api imports:

```typescript
import { exportQueue } from './api/exportQueue'
```

Add `exporting` state after the existing `useState` declarations:

```typescript
const [exporting, setExporting] = useState(false)
```

Add `handleExport` before the `if (me.isLoading ...)` guard:

```typescript
const handleExport = async () => {
  setExporting(true)
  try {
    await exportQueue({ queueManager, channel, queue })
  } catch (e) {
    setError(e instanceof Error ? e.message : 'Export failed')
  } finally {
    setExporting(false)
  }
}
```

Replace the `<Toolbar ... />` JSX with:

```tsx
<Toolbar
  topology={topology.data}
  queueManager={queueManager}
  channel={channel}
  queue={queue}
  onQueueManagerChange={handleQueueManagerChange}
  onChannelChange={setChannel}
  onQueueChange={setQueue}
  onBrowse={() => browse.mutate({ queueManager, channel, queue })}
  onPut={() => setShowPut(true)}
  onClean={() => setShowClean(true)}
  onExport={handleExport}
  browsing={browse.isPending}
  exporting={exporting}
/>
```

- [ ] **Step 3: Build the frontend**

```bash
cd frontend && npm run build
```

Expected: TypeScript compiles cleanly, output written to `../src/main/resources/META-INF/resources/`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/Toolbar.tsx \
        frontend/src/App.tsx \
        src/main/resources/META-INF/resources/
git commit -m "feat: add Export button to toolbar wired to xlsx download"
```
