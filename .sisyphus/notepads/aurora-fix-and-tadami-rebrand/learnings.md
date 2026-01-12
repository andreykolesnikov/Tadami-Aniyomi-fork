# Learnings - Aurora Fix + Tadami Rebrand

## 2026-01-12 Session: ses_4529bc83bffe7XNjTrtSPLSTAX

### Aurora UI Pattern Discovery

**Problem Found:**
- `AnimeScreen.kt:175` checked `theme == AppTheme.AURORA` (specific enum)
- This only worked if user selected exactly AURORA theme
- Other themes with `isAuroraStyle = true` were ignored

**Correct Pattern:**
```kotlin
if (theme.isAuroraStyle && !isTabletUi) {
    // Use Aurora implementation
}
```

**Reference:** `SettingsMainScreen.kt:91` uses this pattern correctly with `!twoPane`

### App Name Location

**Single source of truth:**
- `i18n/src/commonMain/moko-resources/base/strings.xml:3`
- `app_name` string with `translatable="false"`
- Referenced by AndroidManifest.xml via `@string/app_name`

### Icon Files Structure

**Three files needed for full icon replacement:**

1. **ic_launcher_foreground.xml** (108x108dp viewport)
   - Main adaptive icon foreground
   - Current: Blue bg, red circle, white inner, black play button

2. **ic_ani.xml** (512x512 viewport, 256dp display)
   - About screen logo (used in LogoHeader.kt)
   - White color, tint applied dynamically

3. **ic_ani_monochrome_launcher.xml** (108x108dp viewport)
   - Android 13+ themed icon
   - Single color (black), system applies tint

**DO NOT modify:**
- `mipmap/ic_launcher.xml` - just references foreground
- `mipmap/ic_launcher_round.xml` - just references foreground

### Build Commands

```bash
# Quick compile check
./gradlew :app:compileDebugKotlin --no-daemon

# Full APK build
./gradlew :app:assembleDebug --no-daemon

# APK output
app/build/outputs/apk/debug/app-universal-debug.apk
```
