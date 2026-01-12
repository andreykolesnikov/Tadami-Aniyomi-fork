# Blockers - Aurora Fix + Tadami Rebrand

## 2026-01-12 Session: ses_4529bc83bffe7XNjTrtSPLSTAX

### BLOCKER: Icon Creation (Task 2)

**Status:** BLOCKED - Waiting for user action

**What is blocked:**
- Task 2: Create and replace app icons for Tadami
- 4 acceptance criteria in the plan depend on new icons

**Why blocked:**
User explicitly requested to create icons manually:
> "иконку создам сам, просто потом скажешь после модификаций кода, что мне нужно будет доделать и все"

**What user needs to do:**

1. **Create 3 icon files** in Android Vector Drawable XML format:

   | File | Viewport | Purpose |
   |------|----------|---------|
   | `ic_launcher_foreground.xml` | 108×108 | Main launcher icon |
   | `ic_ani.xml` | 512×512 | About screen logo |
   | `ic_ani_monochrome_launcher.xml` | 108×108 | Android 13+ themed icon |

2. **Place files in:**
   ```
   app/src/main/res/drawable/
   ```

3. **Rebuild APK:**
   ```bash
   ./gradlew :app:assembleDebug --no-daemon
   ```

4. **Test on device**

**Code changes completed:**
- [x] Aurora fix in AnimeScreen.kt
- [x] App name changed to "Tadami"
- [x] Build successful

**Remaining after user creates icons:**
- [ ] Replace icon files
- [ ] Rebuild
- [ ] Manual verification on device
