# Bulk Delete by ID Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Delete by ID…" modal that lets users paste JMS Message IDs (one per line, copied from the Excel export), confirm, and bulk-delete them via a single server round-trip, returning a deleted/already-gone summary.

**Architecture:** A new `POST /api/bulk-delete` endpoint calls `MqOperationService.bulkDelete()`, which loops `gateway.delete()` per ID, audits each result, and returns `BulkDeleteResult(deleted, alreadyGone)`. The React frontend adds a `BulkDeleteModal` (input → confirm → result states) triggered by a "Delete by ID…" Toolbar button.

**Tech Stack:** Kotlin/Quarkus 3, React 18 + TypeScript, @tanstack/react-query

---

## File Map

**New files:**
- `frontend/src/components/BulkDeleteModal.tsx` — three-state modal: input (textarea), confirm (count + queue), result (summary)
- `frontend/src/api/useBulkDelete.ts` — mutation hook POSTing to `/api/bulk-delete`

**Modified files:**
- `src/main/kotlin/com/acme/mqops/web/MqApiRequest.kt` — add `BulkDeleteRequest` data class
- `src/main/kotlin/com/acme/mqops/service/MqOperationService.kt` — add `BulkDeleteResult` data class + `bulkDelete()` method
- `src/main/kotlin/com/acme/mqops/web/MqApiResource.kt` — add `/api/bulk-delete` endpoint
- `src/test/kotlin/com/acme/mqops/service/MqOperationServiceTest.kt` — extend `FakeGateway` with `missingIds`; add 3 bulk delete tests
- `src/test/kotlin/com/acme/mqops/web/MqApiResourceTest.kt` — add 400 test for bulk-delete
- `frontend/src/components/Toolbar.tsx` — add `onBulkDelete` prop + button
- `frontend/src/App.tsx` — add `showBulkDelete` state + `<BulkDeleteModal>`

---

### Task 1: BulkDeleteRequest + BulkDeleteResult + service method (TDD)

**Files:**
- Modify: `src/main/kotlin/com/acme/mqops/web/MqApiRequest.kt`
- Modify: `src/main/kotlin/com/acme/mqops/service/MqOperationService.kt`
- Modify: `src/test/kotlin/com/acme/mqops/service/MqOperationServiceTest.kt`

- [ ] **Step 1: Add BulkDeleteRequest to MqApiRequest.kt**

In `src/main/kotlin/com/acme/mqops/web/MqApiRequest.kt`, add at the end of the file:

```kotlin
data class BulkDeleteRequest(val queueManager: String, val channel: String, val queue: String, val jmsMessageIds: List<String>)
```

- [ ] **Step 2: Write failing tests**

In `src/test/kotlin/com/acme/mqops/service/MqOperationServiceTest.kt`:

**a)** Replace the existing `FakeGateway` class with this version (adds `missingIds` param; all existing tests remain compatible):

```kotlin
private class FakeGateway(
    private val deleteMissing: Boolean = false,
    private val cleanCount: Int = 0,
    private val putFailure: Exception? = null,
    private val missingIds: Set<String> = emptySet()
) : MqGateway {
    var lastBrowseLimit: Int = 0
    var lastPutBody: String = ""

    override fun browse(target: MqTarget, limit: Int): List<MessageRow> {
        lastBrowseLimit = limit
        return emptyList()
    }

    override fun delete(target: MqTarget, jmsMessageId: String): Boolean {
        if (jmsMessageId in missingIds) return false
        return !deleteMissing
    }

    override fun putText(target: MqTarget, body: String) {
        putFailure?.let { throw it }
        lastPutBody = body
    }

    override fun clean(target: MqTarget): Int = cleanCount
}
```

**b)** Add these three tests inside the `MqOperationServiceTest` class:

```kotlin
@Test
fun `bulkDelete returns all deleted when no IDs are missing`() {
    val audit = RecordingAuditLogger()
    val service = MqOperationService(FakeGateway(), audit)

    val result = service.bulkDelete("alice", target, listOf("ID:1", "ID:2", "ID:3"))

    assertEquals(BulkDeleteResult(deleted = 3, alreadyGone = 0), result)
    assertEquals(3, audit.entries.count { it.startsWith("delete:success") })
}

@Test
fun `bulkDelete counts already-gone IDs separately`() {
    val audit = RecordingAuditLogger()
    val service = MqOperationService(FakeGateway(missingIds = setOf("ID:2")), audit)

    val result = service.bulkDelete("alice", target, listOf("ID:1", "ID:2", "ID:3"))

    assertEquals(BulkDeleteResult(deleted = 2, alreadyGone = 1), result)
    assertEquals(2, audit.entries.count { it.startsWith("delete:success") })
    assertEquals(1, audit.entries.count { it.startsWith("delete:not_found") })
}

@Test
fun `bulkDelete returns all already-gone when all IDs are missing`() {
    val service = MqOperationService(FakeGateway(missingIds = setOf("ID:1", "ID:2")), RecordingAuditLogger())

    val result = service.bulkDelete("alice", target, listOf("ID:1", "ID:2"))

    assertEquals(BulkDeleteResult(deleted = 0, alreadyGone = 2), result)
}
```

- [ ] **Step 3: Run tests to confirm they fail**

```bash
mvn test -Dtest=MqOperationServiceTest
```

Expected: FAIL — `Unresolved reference: bulkDelete` and `Unresolved reference: BulkDeleteResult`.

- [ ] **Step 4: Add BulkDeleteResult and bulkDelete() to MqOperationService.kt**

At the bottom of `src/main/kotlin/com/acme/mqops/service/MqOperationService.kt` (outside and after the class closing brace), add:

```kotlin
data class BulkDeleteResult(val deleted: Int, val alreadyGone: Int)
```

Inside the `MqOperationService` class, add `bulkDelete()` after `export()`:

```kotlin
fun bulkDelete(user: String, target: MqTarget, jmsMessageIds: List<String>): BulkDeleteResult {
    var deleted = 0
    var alreadyGone = 0
    for (id in jmsMessageIds) {
        try {
            if (gateway.delete(target, id)) {
                audit.delete(user, target, id, "success")
                deleted++
            } else {
                audit.delete(user, target, id, "not_found")
                alreadyGone++
            }
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ex
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            audit.delete(user, target, id, "failure", errorSummary(ex))
            throw ex
        }
    }
    return BulkDeleteResult(deleted, alreadyGone)
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
mvn test -Dtest=MqOperationServiceTest
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/acme/mqops/web/MqApiRequest.kt \
        src/main/kotlin/com/acme/mqops/service/MqOperationService.kt \
        src/test/kotlin/com/acme/mqops/service/MqOperationServiceTest.kt
git commit -m "feat: add MqOperationService.bulkDelete() with per-ID audit"
```

---

### Task 2: /api/bulk-delete endpoint

**Files:**
- Modify: `src/main/kotlin/com/acme/mqops/web/MqApiResource.kt`
- Modify: `src/test/kotlin/com/acme/mqops/web/MqApiResourceTest.kt`

- [ ] **Step 1: Write failing test**

In `src/test/kotlin/com/acme/mqops/web/MqApiResourceTest.kt`, add inside the class:

```kotlin
@Test
@TestSecurity(user = "alice")
fun `bulk-delete returns 400 for unknown queue manager`() {
    given()
        .contentType(ContentType.JSON)
        .body("""{"queueManager":"UNKNOWN","channel":"APP_SVRCONN","queue":"DEV.QUEUE.1","jmsMessageIds":["ID:1"]}""")
        .`when`().post("/api/bulk-delete")
        .then()
        .statusCode(400)
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
mvn test -Dtest=MqApiResourceTest
```

Expected: FAIL — new test gets 404 (endpoint not yet defined).

- [ ] **Step 3: Add the endpoint to MqApiResource.kt**

Add this import alongside existing imports at the top of `src/main/kotlin/com/acme/mqops/web/MqApiResource.kt`:

```kotlin
import com.acme.mqops.service.BulkDeleteResult
```

Add this method after `export()`:

```kotlin
@POST
@Path("/bulk-delete")
fun bulkDelete(request: BulkDeleteRequest): BulkDeleteResult =
    withTarget(request.queueManager, request.channel, request.queue) { target ->
        operations.bulkDelete(user(), target, request.jmsMessageIds)
    }
```

- [ ] **Step 4: Run full API test suite**

```bash
mvn test -Dtest=MqApiResourceTest
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 5: Run all backend tests**

```bash
mvn test
```

Expected: BUILD SUCCESS, 35 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/acme/mqops/web/MqApiResource.kt \
        src/test/kotlin/com/acme/mqops/web/MqApiResourceTest.kt
git commit -m "feat: add POST /api/bulk-delete endpoint"
```

---

### Task 3: Frontend useBulkDelete hook

**Files:**
- Create: `frontend/src/api/useBulkDelete.ts`

- [ ] **Step 1: Create useBulkDelete.ts**

Create `frontend/src/api/useBulkDelete.ts`:

```typescript
import { useMutation } from '@tanstack/react-query'

interface BulkDeleteParams {
  queueManager: string
  channel: string
  queue: string
  jmsMessageIds: string[]
}

interface BulkDeleteResult {
  deleted: number
  alreadyGone: number
}

export function useBulkDelete(
  onSuccess: (result: BulkDeleteResult) => void,
  onError: (msg: string) => void
) {
  return useMutation<BulkDeleteResult, Error, BulkDeleteParams>({
    mutationFn: async (params) => {
      const res = await fetch('/api/bulk-delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params),
      })
      if (!res.ok) throw new Error(await res.text())
      return res.json()
    },
    onSuccess,
    onError: (e) => onError(e.message),
  })
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/api/useBulkDelete.ts
git commit -m "feat: add useBulkDelete mutation hook"
```

---

### Task 4: BulkDeleteModal component

**Files:**
- Create: `frontend/src/components/BulkDeleteModal.tsx`

- [ ] **Step 1: Create BulkDeleteModal.tsx**

Create `frontend/src/components/BulkDeleteModal.tsx`:

```typescript
import { useState } from 'react'
import type { CSSProperties } from 'react'
import { useBulkDelete } from '../api/useBulkDelete'

interface BulkDeleteModalProps {
  queueManager: string
  channel: string
  queue: string
  onClose: () => void
  onError: (msg: string) => void
}

type Phase =
  | { name: 'input' }
  | { name: 'confirm'; ids: string[] }
  | { name: 'result'; deleted: number; alreadyGone: number }

function parseIds(raw: string): string[] {
  return raw
    .trim()
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean)
}

export function BulkDeleteModal({
  queueManager,
  channel,
  queue,
  onClose,
  onError,
}: BulkDeleteModalProps) {
  const [text, setText] = useState('')
  const [phase, setPhase] = useState<Phase>({ name: 'input' })

  const bulkDelete = useBulkDelete(
    (result) =>
      setPhase({ name: 'result', deleted: result.deleted, alreadyGone: result.alreadyGone }),
    (msg) => {
      onError(msg)
      onClose()
    }
  )

  if (phase.name === 'input') {
    const ids = parseIds(text)
    return (
      <div style={overlayStyle}>
        <div style={modalStyle}>
          <h3 style={{ margin: '0 0 12px' }}>Delete by ID</h3>
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Paste JMS Message IDs, one per line…"
            rows={10}
            style={{ width: '100%', fontFamily: 'monospace', fontSize: 12, boxSizing: 'border-box' }}
          />
          <div style={{ display: 'flex', gap: 8, marginTop: 12, justifyContent: 'flex-end' }}>
            <button onClick={onClose}>Cancel</button>
            <button
              onClick={() => setPhase({ name: 'confirm', ids })}
              disabled={ids.length === 0}
              style={{ color: '#d33f49' }}
            >
              Review ({ids.length})
            </button>
          </div>
        </div>
      </div>
    )
  }

  if (phase.name === 'confirm') {
    const { ids } = phase
    return (
      <div style={overlayStyle}>
        <div style={modalStyle}>
          <h3 style={{ margin: '0 0 12px' }}>Confirm Deletion</h3>
          <p style={{ margin: '0 0 12px' }}>
            About to delete <strong>{ids.length}</strong> message
            {ids.length !== 1 ? 's' : ''} from <code>{queue}</code>. Proceed?
          </p>
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <button onClick={() => setPhase({ name: 'input' })}>Back</button>
            <button
              onClick={() =>
                bulkDelete.mutate({ queueManager, channel, queue, jmsMessageIds: ids })
              }
              disabled={bulkDelete.isPending}
              style={{ color: '#d33f49' }}
            >
              {bulkDelete.isPending ? 'Deleting…' : `Delete ${ids.length}`}
            </button>
          </div>
        </div>
      </div>
    )
  }

  const { deleted, alreadyGone } = phase
  const total = deleted + alreadyGone
  const summary =
    alreadyGone > 0
      ? `Deleted ${deleted} of ${total}. ${alreadyGone} were already gone.`
      : `Deleted ${deleted} of ${total}.`

  return (
    <div style={overlayStyle}>
      <div style={modalStyle}>
        <h3 style={{ margin: '0 0 12px' }}>Done</h3>
        <p style={{ margin: '0 0 12px' }}>{summary}</p>
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <button onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  )
}

const overlayStyle: CSSProperties = {
  position: 'fixed',
  inset: 0,
  background: 'rgba(0,0,0,0.4)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  zIndex: 10,
}

const modalStyle: CSSProperties = {
  background: '#fff',
  borderRadius: 6,
  padding: 24,
  minWidth: 420,
  maxWidth: 560,
  width: '100%',
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/BulkDeleteModal.tsx
git commit -m "feat: add BulkDeleteModal with input/confirm/result states"
```

---

### Task 5: Wire Toolbar and App

**Files:**
- Modify: `frontend/src/components/Toolbar.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Add onBulkDelete prop and button to Toolbar.tsx**

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
  onBulkDelete: () => void
  browsing: boolean
  exporting: boolean
}
```

Replace the `Toolbar` function destructuring with:

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
  onBulkDelete,
  browsing,
  exporting,
}: ToolbarProps) {
```

Add the "Delete by ID…" button immediately after the Export button in the JSX:

```tsx
<button onClick={onBulkDelete} disabled={!canAct}>
  Delete by ID…
</button>
```

- [ ] **Step 2: Wire BulkDeleteModal into App.tsx**

In `frontend/src/App.tsx`, add the import alongside other component imports:

```typescript
import { BulkDeleteModal } from './components/BulkDeleteModal'
```

Add `showBulkDelete` state after the existing `useState` declarations:

```typescript
const [showBulkDelete, setShowBulkDelete] = useState(false)
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
  onBulkDelete={() => setShowBulkDelete(true)}
  browsing={browse.isPending}
  exporting={exporting}
/>
```

Add `<BulkDeleteModal>` after the `{showClean && <CleanModal ... />}` block:

```tsx
{showBulkDelete && (
  <BulkDeleteModal
    queueManager={queueManager}
    channel={channel}
    queue={queue}
    onClose={() => setShowBulkDelete(false)}
    onError={setError}
  />
)}
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
git commit -m "feat: add Delete by ID button wired to BulkDeleteModal"
```
