# Decisions - Aurora Fix + Tadami Rebrand

## 2026-01-12 Session: ses_4529bc83bffe7XNjTrtSPLSTAX

### Decision: Icons left for user

**Context:** User explicitly requested to create icons manually.

**Decision:** Task 2 (icon replacement) marked as USER TASK. Code changes complete, user will:
1. Create new icon designs
2. Convert to Android Vector Drawable XML
3. Replace the 3 icon files
4. Rebuild APK

### Decision: Tablet UI excluded from Aurora

**Context:** Original code had `if (theme == AURORA)` without tablet check.

**Decision:** Added `&& !isTabletUi` to match SettingsMainScreen pattern.
- Aurora UI designed for phone screens
- Tablet uses different layout (two-pane)
- Consistent with other Aurora implementations in codebase
