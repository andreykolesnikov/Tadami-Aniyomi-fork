# Specification: Fix WorkManager Foreground Service Crash

## Overview
The application is experiencing `android.app.RemoteServiceException$ForegroundServiceDidNotStartInTimeException` crashes on newer Android versions (SDK 36/Android 16 preview). This occurs when `WorkManager` starts a foreground service (`SystemForegroundService`), but the associated `Worker` fails to call `startForeground()` (via `setForegroundAsync()`) within the strict time limit enforced by the OS.

## Goal
Eliminate the `ForegroundServiceDidNotStartInTimeException` by optimizing how Library Update, Download, and Backup workers handle foreground promotion. The primary strategy is to ensure `setForegroundAsync()` is called immediately upon worker execution start and to remove any blocking logic that might delay this call.

## Requirements

### Functional Requirements
1.  **Audit Workers:** Identify all `Worker` or `CoroutineWorker` implementations related to:
    -   Library Updates (`LibraryUpdateJob` / `MangaLibraryUpdateJob` / `AnimeLibraryUpdateJob`)
    -   Downloads (`DownloadManager` related workers)
    -   Backups (`BackupCreateJob`)
2.  **Immediate Foreground Promotion:** In identified workers, ensure that `setForeground()` or `setForegroundAsync()` is the **absolute first** operation performed in `doWork()` or the execution block.
3.  **Remove Pre-Foreground Delays:** Identify and move any initialization logic, database queries, or network checks that currently happen *before* the foreground declaration to occur *after* the service is successfully promoted.
4.  **Graceful Fallback:** If `setForegroundAsync()` fails (e.g., app is in background and restricted), the worker should handle the failure gracefully (e.g., run as a standard background job if possible, or fail/retry) rather than crashing the app.

### Non-Functional Requirements
-   **Stability:** The fix must prevent the specific `RemoteServiceException` from crashing the app.
-   **Performance:** The changes should not negatively impact the execution time of background tasks.

## Out of Scope
-   Updating `androidx.work` library versions (unless the current version is deprecated/buggy and an update is the *only* solution, but code changes are preferred first).
-   Refactoring the entire scheduling system.

## Acceptance Criteria
-   [ ] `LibraryUpdateJob` (and variants) calls `setForeground` immediately.
-   [ ] Download-related workers call `setForeground` immediately.
-   [ ] Backup workers call `setForeground` immediately.
-   [ ] Code review confirms no blocking operations exist before the foreground call.
-   [ ] (Manual Verification) Launching a long-running library update or download does not trigger the crash on Android 12+ devices.
