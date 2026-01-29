# Implementation Plan - Fix WorkManager Foreground Service Crash

## Phase 1: Analysis & Reproduction
- [ ] Task: Audit existing Workers.
    - [ ] Search for `Worker` and `CoroutineWorker` subclasses.
    - [ ] Identify which ones call `setForeground` or `setForegroundAsync`.
    - [ ] Specifically check: `LibraryUpdateJob`, `MangaLibraryUpdateJob`, `AnimeLibraryUpdateJob`, `DownloadManager` related workers, and `BackupCreateJob`.
- [ ] Task: Analyze current `setForeground` timing.
    - [ ] For each identified worker, determine what logic runs *before* the foreground call.
    - [ ] Document any database calls, network requests, or heavy initializations occurring before promotion.

## Phase 2: Implementation (Fixing Workers)
- [x] Task: Refactor Library Update Workers.
    - [x] Write Tests: Create a test case (if possible with Robolectric/MockK) that asserts `setForeground` is called.
    - [x] Implement Feature: Move `setForegroundAsync` to the very top of `doWork` in `LibraryUpdateJob` (and manga/anime variants). Ensure it awaits before doing other work.
- [x] Task: Refactor Download Workers.
    - [x] Write Tests: Create a test case for download worker foreground promotion.
    - [x] Implement Feature: Ensure `DownloadWorker` (or equivalent) promotes to foreground immediately.
- [x] Task: Refactor Backup Workers.
    - [x] Write Tests: Create a test case for backup worker.
    - [x] Implement Feature: Ensure `BackupCreateJob` promotes to foreground immediately.
- [x] Task: Add Graceful Failure Handling.
    - [x] Implement Feature: Wrap `setForegroundAsync` calls in `try-catch` blocks where appropriate to handle `IllegalStateException` or other exceptions if the app is in the background and restricted, logging the error instead of crashing.

## Phase 3: Verification
- [~] Task: Code Review.
    - [x] Verify that no blocking calls exist before `setForegroundAsync`.
- [ ] Task: Manual Verification.
    - [ ] Trigger a library update and monitor logs/behavior.
    - [ ] Trigger a large download and monitor.
    - [ ] Trigger a backup and monitor.
    - [ ] (Optional) Simulate "background restriction" if possible to test graceful failure.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Verification' (Protocol in workflow.md)
