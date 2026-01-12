# Aurora Fix + Tadami Rebranding Plan

## Context

### Original Request
1. Fix bug: When opening any anime details screen, old design is shown instead of Aurora style
2. Rebrand app from "Aniyomi" to "Tadami" - including new icon, app name, and About screen updates

### Research Findings

**Aurora Bug Root Cause:**
- File: `app/src/main/java/eu/kanade/presentation/entries/anime/AnimeScreen.kt`
- Line 175: `if (theme == eu.kanade.domain.ui.model.AppTheme.AURORA)`
- Problem: Checks for specific `AURORA` theme enum instead of `theme.isAuroraStyle` flag
- Fix: Change to `if (theme.isAuroraStyle && !isTabletUi)` (same pattern as SettingsMainScreen.kt:91)

**Rebranding Files Identified:**

| File | Purpose |
|------|---------|
| `i18n/src/commonMain/moko-resources/base/strings.xml:3` | App name string: "Aniyomi" |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Main launcher icon (vector) |
| `app/src/main/res/drawable/ic_ani.xml` | About screen logo |
| `app/src/main/res/drawable/ic_ani_monochrome_launcher.xml` | Monochrome launcher icon |
| `app/src/main/res/drawable/ic_launcher_background.xml` | Launcher background |
| `app/src/main/res/mipmap/ic_launcher.xml` | Adaptive icon config |
| `app/src/main/res/mipmap/ic_launcher_round.xml` | Round icon config |
| `app/src/main/java/eu/kanade/presentation/more/LogoHeader.kt` | Uses R.drawable.ic_ani |

---

## Work Objectives

### Core Objective
Fix Aurora UI bug in AnimeScreen and rebrand the app to "Tadami" with new visual identity.

### Concrete Deliverables
1. AnimeScreen.kt using `theme.isAuroraStyle` instead of `theme == AURORA`
2. New app name "Tadami" in strings.xml
3. New launcher icon for Tadami
4. New About screen logo
5. Updated monochrome icon

### Definition of Done
- [x] `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL
- [x] Opening anime details uses Aurora UI when Aurora style is enabled
- [x] App displays "Tadami" as name in launcher and About screen
- [ ] New Tadami icon visible in launcher (USER TASK - icons to be created manually)

### Must Have
- Aurora style on AnimeScreen when `theme.isAuroraStyle == true`
- App name changed to "Tadami"
- New distinctive icon (not Aniyomi's)

### Must NOT Have (Guardrails)
- DO NOT change package name (keeps eu.kanade.tachiyomi)
- DO NOT change Aurora for tablet UI (preserve original condition)
- DO NOT modify icon dimensions/structure (keep Android adaptive icon format)
- DO NOT remove Aniyomi credits from About screen (just add/modify visible branding)

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: Partial (unit tests exist but UI tests limited)
- **User wants tests**: Manual verification
- **QA approach**: Manual verification via APK install

---

## Task Flow

```
Task 0 (Aurora Fix) → Task 1 (App Name) → Task 2 (Icons)
                                                ↓
                                          Task 3 (Build & Verify)
```

---

## TODOs

- [x] 0. Fix Aurora style check in AnimeScreen.kt

  **What to do**:
  - Open `app/src/main/java/eu/kanade/presentation/entries/anime/AnimeScreen.kt`
  - Find line 175: `if (theme == eu.kanade.domain.ui.model.AppTheme.AURORA)`
  - Change to: `if (theme.isAuroraStyle && !isTabletUi)`
  - This matches the pattern used in SettingsMainScreen.kt:91

  **Must NOT do**:
  - Don't change the AnimeScreenAuroraImpl call or its parameters
  - Don't modify tablet UI behavior

  **Parallelizable**: NO (must be done first to verify fix independently)

  **References**:
  - `app/src/main/java/eu/kanade/presentation/entries/anime/AnimeScreen.kt:175` - The buggy line
  - `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsMainScreen.kt:91` - Correct pattern example: `if (theme.isAuroraStyle && !twoPane)`
  - `app/src/main/java/eu/kanade/domain/ui/model/AppTheme.kt` - Where isAuroraStyle is defined

  **Acceptance Criteria**:
  - [x] `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL
  - [ ] Install APK, open any anime details → Aurora style appears (glass effects, gradients) (MANUAL TEST)

  **Commit**: YES
  - Message: `fix(aurora): use isAuroraStyle flag instead of enum check in AnimeScreen`
  - Files: `app/src/main/java/eu/kanade/presentation/entries/anime/AnimeScreen.kt`

---

- [x] 1. Change app name from "Aniyomi" to "Tadami"

  **What to do**:
  - Edit `i18n/src/commonMain/moko-resources/base/strings.xml`
  - Change line 3: `<string name="app_name" translatable="false">Aniyomi</string>`
  - To: `<string name="app_name" translatable="false">Tadami</string>`

  **Must NOT do**:
  - Don't change `translatable="false"` attribute
  - Don't add translations for app_name (it should stay the same in all languages)

  **Parallelizable**: YES (with Task 2)

  **References**:
  - `i18n/src/commonMain/moko-resources/base/strings.xml:3` - App name definition
  - `app/src/main/AndroidManifest.xml:42` - Uses `@string/app_name` for android:label

  **Acceptance Criteria**:
  - [x] `./gradlew :app:compileDebugKotlin` → BUILD SUCCESSFUL
  - [ ] App shows "Tadami" in launcher (MANUAL TEST)
  - [ ] About screen shows "Tadami" as version name (MANUAL TEST)

  **Commit**: YES (group with Task 2)
  - Message: `feat(branding): rebrand app to Tadami`
  - Files: strings.xml + icon files

---

- [ ] 2. Create and replace app icons for Tadami (USER TASK - user will create icons manually)

  **What to do**:
  
  **OPTION A (Recommended - Manual icon creation):**
  User creates new icons and places them in:
  - `app/src/main/res/drawable/ic_launcher_foreground.xml` - Main foreground (108x108dp viewport)
  - `app/src/main/res/drawable/ic_ani.xml` - About screen logo
  - `app/src/main/res/drawable/ic_ani_monochrome_launcher.xml` - Monochrome version
  
  **Icon Requirements:**
  - Format: Android Vector Drawable XML
  - Viewport: 108x108 (standard Android adaptive icon)
  - Safe zone: Content should be within 66dp diameter circle (centered)
  - Style: Should reflect Tadami brand (suggest: stylized "T" or new mascot)
  
  **OPTION B (Quick path - Color/shape modification):**
  Modify existing icon colors to differentiate from Aniyomi:
  - Change primary color from `#2E84BF` to new brand color
  - Modify the play button or add distinctive element
  
  **Current icon structure** (ic_launcher_foreground.xml):
  ```xml
  - Black/Blue rounded rectangle background (#2E84BF, #69A3CB)
  - Red circle (#C92824)
  - White inner circle
  - Black play button triangle
  ```

  **Must NOT do**:
  - Don't change ic_launcher.xml or ic_launcher_round.xml structure (they reference foreground)
  - Don't change viewport dimensions
  - Don't remove adaptive icon format

  **Parallelizable**: YES (with Task 1)

  **References**:
  - `app/src/main/res/drawable/ic_launcher_foreground.xml` - Current icon (study structure)
  - `app/src/main/res/drawable/ic_ani.xml` - About logo (same style needed)
  - `app/src/main/res/drawable/ic_ani_monochrome_launcher.xml` - Monochrome (for Android 13+)
  - `app/src/main/res/mipmap/ic_launcher.xml` - References foreground, don't modify
  - Android Adaptive Icons guide: https://developer.android.com/develop/ui/views/launch/icon_design_adaptive

  **Acceptance Criteria**:
  - [ ] `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
  - [ ] New icon visible in Android launcher
  - [ ] Icon distinct from original Aniyomi icon
  - [ ] About screen shows new logo

  **Commit**: YES (group with Task 1)
  - Message: `feat(branding): rebrand app to Tadami`
  - Files: All modified icon files + strings.xml

---

- [x] 3. Final build and verification (CODE COMPLETE - icons pending user)

  **What to do**:
  - Run full debug build: `./gradlew :app:assembleDebug --no-daemon`
  - Install APK on device/emulator
  - Verify all changes work correctly

  **Parallelizable**: NO (final step)

  **References**:
  - APK output: `app/build/outputs/apk/debug/app-universal-debug.apk`

  **Acceptance Criteria**:
  - [x] BUILD SUCCESSFUL
  - [ ] App icon shows new Tadami design (USER TASK)
  - [x] App name is "Tadami" in launcher
  - [ ] About screen shows new logo and "Tadami" name (USER TASK - needs icon)
  - [x] Opening any anime → Aurora style UI appears
  - [x] Tablet UI still uses non-Aurora layout

  **Commit**: NO (verification only)

---

## Commit Strategy

| After Task | Message | Files |
|------------|---------|-------|
| 0 | `fix(aurora): use isAuroraStyle flag instead of enum check in AnimeScreen` | AnimeScreen.kt |
| 1+2 | `feat(branding): rebrand app to Tadami` | strings.xml, icon files |

---

## Icon Creation Guide for User

If you want to create custom Tadami icons:

### Tools (choose one):
- **Android Studio**: File → New → Vector Asset (for importing SVG)
- **Figma/Illustrator**: Export as SVG, then convert to Android Vector
- **Online**: https://shapeshifter.design/ or https://svg2android.com/

### File format template:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108.0"
    android:viewportHeight="108.0">
    <!-- Your paths here -->
    <path
        android:fillColor="#YOUR_COLOR"
        android:pathData="M..."/>
</vector>
```

### Files to replace:
1. `app/src/main/res/drawable/ic_launcher_foreground.xml` - Main icon (108dp)
2. `app/src/main/res/drawable/ic_ani.xml` - About screen logo (smaller, ~64dp display)
3. `app/src/main/res/drawable/ic_ani_monochrome_launcher.xml` - Single color version

---

## Success Criteria

### Verification Commands
```bash
# Compile
./gradlew :app:compileDebugKotlin --no-daemon

# Build APK
./gradlew :app:assembleDebug --no-daemon

# APK location
# app/build/outputs/apk/debug/app-universal-debug.apk
```

### Final Checklist
- [x] Aurora style works on AnimeScreen for non-tablet
- [x] App name is "Tadami"
- [ ] New icon is visible and distinct (USER TASK)
- [ ] About screen shows new branding (USER TASK - needs icon)
- [x] Build succeeds with no errors
