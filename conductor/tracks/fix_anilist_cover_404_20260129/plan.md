# Implementation Plan - Fix AniList Large Cover 404

## Phase 1: Replication & Analysis
- [x] Task: Create a reproduction test case.
    - [x] Create a unit/integration test that mocks the AniList API response with a known entry causing the 404 on the large image.
    - [x] Verify that the current implementation fails (returns 404 or shows broken image).
- [x] Task: Analyze the current AniList image fetching logic.
    - [x] Locate the code responsible for parsing the AniList API response and selecting the image URL.
    - [x] Identify where the network request is made and where errors are currently handled.

## Phase 2: Implementation
- [x] Task: Implement fallback logic in the data layer.
    - [x] Write Tests: Update the test case to expect the fallback URL when the large URL fails.
    - [x] Implement Feature: Modify the repository/data source to check for the 404 error (or pre-emptively check availability if possible, otherwise use a try-catch/retry mechanism with the fallback URL).
- [x] Task: Verify fallback integration.
    - [x] Write Tests: Ensure the fallback logic works seamlessly with the UI's image loading component (Coil).
    - [x] Implement Feature: Ensure the UI observes the correct state change.

## Phase 3: Verification
- [~] Task: Run manual verification.
    - [ ] Test with the specific anime/manga that caused the issue.
    - [ ] Test with a normal entry (happy path) to ensure no regressions.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Verification' (Protocol in workflow.md)
