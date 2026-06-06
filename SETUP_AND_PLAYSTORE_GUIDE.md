# LendLink v3 — Complete Setup, Environment & Play Store Guide

---

## SECTION 1 — System Requirements

| Component | Requirement |
|-----------|-------------|
| OS | Windows 10/11 (64-bit), macOS 12+, Ubuntu 20.04+ |
| RAM | 8 GB minimum — 16 GB recommended |
| Disk | 15 GB free (Android Studio + SDK + Emulator) |
| Java | JDK 17 (bundled with Android Studio — no separate install) |
| Internet | Required for Firebase, Gradle, Play Store |

---

## SECTION 2 — Install Android Studio

1. Download **Android Studio Hedgehog 2023.1.1** or newer from https://developer.android.com/studio
2. Run the installer and follow the setup wizard (choose **Standard** setup)
3. The wizard installs: Android Studio, JDK 17, Android SDK, Emulator

### SDK Manager Settings

Go to **Settings → SDK Manager**:

**SDK Platforms tab — install:**
- Android 14 (API 34) ← primary
- Android 13 (API 33) ← compatibility

**SDK Tools tab — verify installed:**
- Android SDK Build-Tools 34.0.0
- Android Emulator
- Android SDK Platform-Tools
- Google Play Services
- Google USB Driver (Windows only)

---

## SECTION 3 — Firebase Project Setup

### 3.1 Create Firebase Project
1. Go to https://console.firebase.google.com
2. Click **Add project** → name: **LendLink** → Create
3. Disable Google Analytics (not needed for this project)

### 3.2 Add Android App
1. Click the **Android icon** → Add app
2. **Package name:** `com.lendlink`  ← EXACT, case-sensitive
3. **App nickname:** LendLink
4. Click **Register app**
5. **Download `google-services.json`**
6. Place it at: `app/google-services.json`  ← CRITICAL PATH

> ⚠️ Without google-services.json in the correct location, the build will fail.

### 3.3 Enable Authentication
1. Firebase Console → **Authentication → Get started**
2. Sign-in method → **Email/Password** → Enable → Save

### 3.4 Enable Realtime Database
1. Firebase Console → **Realtime Database → Create database**
2. Choose **Start in test mode** → Next → Select region → **Enable**

### 3.5 Enable Storage
1. Firebase Console → **Storage → Get started**
2. Start in test mode → Choose same region as database → Done

### 3.6 Google Maps API Key (for location picker)
1. Go to https://console.cloud.google.com
2. Select your project → **APIs & Services → Credentials**
3. **Create Credentials → API Key**
4. Enable: Maps SDK for Android + Geocoding API
5. Copy the key and replace `YOUR_GOOGLE_MAPS_API_KEY` in `AndroidManifest.xml`

### 3.7 Firebase Database Security Rules

In Firebase Console → Realtime Database → **Rules** tab, paste:

```json
{
  "rules": {
    "users": {
      "$uid": { ".read": "$uid === auth.uid", ".write": "$uid === auth.uid" }
    },
    "usernames": {
      ".read": "auth != null",
      "$u": { ".write": "auth != null" }
    },
    "wallets": {
      "$uid": { ".read": "auth != null", ".write": "auth != null" }
    },
    "items": { ".read": "auth != null", ".write": "auth != null" },
    "borrow_records": { ".read": "auth != null", ".write": "auth != null" },
    "return_requests": { ".read": "auth != null", ".write": "auth != null" },
    "categories": {
      "$uid": { ".read": "auth != null", ".write": "$uid === auth.uid" }
    },
    "notifications": {
      "$uid": { ".read": "$uid === auth.uid", ".write": "auth != null" }
    },
    "lender_credit_history": {
      "$uid": { ".read": "$uid === auth.uid", ".write": "auth != null" }
    },
    "borrower_payment_history": {
      "$uid": { ".read": "$uid === auth.uid", ".write": "auth != null" }
    },
    "lend_history": {
      "$uid": { ".read": "$uid === auth.uid", ".write": "auth != null" }
    },
    "borrow_history": {
      "$uid": { ".read": "$uid === auth.uid", ".write": "auth != null" }
    }
  }
}
```

Click **Publish** after pasting.

---

## SECTION 4 — Create & Configure the Project

### 4.1 Create New Project in Android Studio

1. **File → New Project → Empty Activity**
2. **Name:** LendLink
3. **Package name:** `com.lendlink`  ← EXACT
4. **Language:** Kotlin
5. **Minimum SDK:** API 26 (Android 8.0)
6. **Build config language:** Kotlin DSL (build.gradle.kts)
7. Click **Finish**

### 4.2 Replace Files with Provided Source Code

Copy every provided file to its exact path:

| File | Destination |
|------|-------------|
| `google-services.json` | `app/` |
| `build.gradle.kts` (project) | project root |
| `build.gradle.kts` (app) | `app/` |
| `settings.gradle.kts` | project root |
| `gradle.properties` | project root |
| `libs.versions.toml` | `gradle/` |
| `proguard-rules.pro` | `app/` |
| `AndroidManifest.xml` | `app/src/main/` |
| `file_paths.xml` | `app/src/main/res/xml/` |
| `themes.xml` | `app/src/main/res/values/` |
| `strings.xml` | `app/src/main/res/values/` |
| `ic_splash_logo.xml` | `app/src/main/res/drawable/` |
| `ic_launcher_background.xml` | `app/src/main/res/drawable/` |
| All `.kt` files | `app/src/main/java/com/lendlink/` (match subfolder) |

**Kotlin file subfolder map:**
```
MainActivity.kt                    → com/lendlink/
Models.kt                          → com/lendlink/data/model/
AppDatabase.kt                     → com/lendlink/data/local/
AuthRepository.kt                  → com/lendlink/data/repository/
ItemRepository.kt                  → com/lendlink/data/repository/
BorrowRepository.kt                → com/lendlink/data/repository/
ViewModels.kt                      → com/lendlink/viewmodel/
NavGraph.kt                        → com/lendlink/navigation/
Theme.kt                           → com/lendlink/ui/theme/
CommonUi.kt                        → com/lendlink/ui/common/
SplashScreen.kt                    → com/lendlink/ui/splash/
AuthScreens.kt                     → com/lendlink/ui/auth/
LenderScreens.kt                   → com/lendlink/ui/lender/
LenderHistoryScreens.kt            → com/lendlink/ui/lender/
BorrowerScreens.kt                 → com/lendlink/ui/borrower/
BorrowerHistoryScreens.kt          → com/lendlink/ui/borrower/
Workers.kt                         → com/lendlink/worker/
LendLinkMessagingService.kt        → com/lendlink/service/
```

### 4.3 Create Package Directories

In Android Studio, right-click `com.lendlink` in the Project panel:
- **New → Package** → `data.model`
- **New → Package** → `data.local`
- **New → Package** → `data.repository`
- **New → Package** → `viewmodel`
- **New → Package** → `navigation`
- **New → Package** → `ui.theme`
- **New → Package** → `ui.common`
- **New → Package** → `ui.splash`
- **New → Package** → `ui.auth`
- **New → Package** → `ui.lender`
- **New → Package** → `ui.borrower`
- **New → Package** → `worker`
- **New → Package** → `service`

Then right-click each package → **New → Kotlin Class/File** → paste code.

### 4.4 Gradle Sync

1. Click **File → Sync Project with Gradle Files**
2. Wait for sync — first sync downloads ~500 MB
3. Success = "Gradle sync finished" in the status bar

> ⚠️ If sync fails: Check google-services.json is in app/ folder, then **File → Invalidate Caches → Invalidate and Restart**

---

## SECTION 5 — Create Emulator / Use Physical Device

### Emulator
1. **Tools → Device Manager → Create Virtual Device**
2. Select **Pixel 7** → Next
3. API 34 (Android 14) → Download if needed → Next → Finish

### Physical Device (Recommended)
1. Settings → About Phone → tap **Build Number** 7 times
2. Settings → Developer Options → enable **USB Debugging**
3. Connect via USB → allow debugging when prompted
4. Device appears in Android Studio dropdown

---

## SECTION 6 — Run the Project

1. Select your device from the toolbar dropdown
2. Click **▶ Run** (or Shift+F10)
3. First build: 3–8 minutes; subsequent builds: 30–90 seconds

**Expected first launch:**
- Native splash logo (chain-link icon, sky-blue background, ~0.5s from OS)
- Animated LendLink splash screen (3 seconds, white text, sky blue)
- Login page appears

---

## SECTION 7 — Bugs Fixed in This Version

| Bug | Fix Applied |
|-----|-------------|
| Item cannot be added ("product doesn't exist") | `itemId` now generated BEFORE storage upload; Firebase push key created first |
| App crashes on logout | `authVm.logout()` only signs out; nav pops to LOGIN with `popUpTo(0)` clearing backstack |
| Auto-close after logout | `goLogin()` clears full backstack; no stale Activity remains |
| 16KB page size warning | `useLegacyPackaging = false` in `packaging{}` block; `proguard-rules.pro` keeps native libs |
| Registration opens dashboard | `auth.signOut()` called after register; user redirected to Login page |
| Duplicate wallet credits | Per-user wallet nodes: `wallets/{uid}/balance` — each user has own isolated balance |

---

## SECTION 8 — App Features Summary

### Authentication
- Register (Username, Email, Phone, Password, Location, Role)
- After registration → redirected to **Login** (not dashboard)
- Login → role-based routing to Lender or Borrower dashboard
- Logout → always redirects to Login, no crash

### Lender Features
- Dashboard: wallet card (personal balance only), Available tab, Lent tab
- Available tab: category filter row, items list, FAB to add
- Lent tab: items shown (no edit/delete allowed in lent tab)
- Add Item: CameraX photo, name, description, price, category dropdown
- Item Detail (Available): QR generation, Edit, Delete
- Item Detail (Lent): borrower name/phone/location, deadline, send reminder, accept return
- Category Manager: add/edit/delete categories; delete blocked if items exist
- Credit History: earnings log with borrower name and item name per entry
- Lend History: completed lend records with image, dates, borrower details

### Borrower Features
- Dashboard: wallet card (personal balance), active borrows list
- Scan QR: ZXing scanner → confirm dialog with lender info
- Item Detail: lender name/phone/location, deadline, request return
- Payment History: all payments and penalties paid
- Borrow History: completed borrows (only after returned by lender acceptance)

### Common
- Notifications screen: all types (reminder, penalty, return request, confirmed)
- WorkManager: 24h reminder, hourly countdown at ≤10h, 24h penalty deduction

---

## SECTION 9 — Play Store Internal Testing

### 9.1 Developer Account
1. Visit https://play.google.com/console
2. Pay one-time **USD $25** registration fee
3. Complete developer profile

### 9.2 Generate Release Keystore

Run in terminal:
```bash
keytool -genkey -v \
  -keystore lendlink-release.jks \
  -alias lendlink \
  -keyalg RSA -keysize 2048 -validity 10000
```

> ⚠️ SAVE THIS FILE. Losing it = can never update the app on Play Store.

### 9.3 Configure Signing

Create `keystore.properties` in project root:
```
storeFile=/absolute/path/to/lendlink-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=lendlink
keyPassword=YOUR_KEY_PASSWORD
```

Add to `.gitignore`:
```
keystore.properties
*.jks
*.keystore
```

### 9.4 Add SHA-1 to Firebase

```bash
keytool -list -v -keystore lendlink-release.jks -alias lendlink
```

Copy SHA-1 → Firebase Console → Project Settings → Your Android App → **Add fingerprint** → Save

### 9.5 Build Signed AAB

1. **Build → Generate Signed Bundle / APK**
2. Choose **Android App Bundle**
3. Select `lendlink-release.jks`, enter passwords
4. Select **release** build variant → Finish
5. Output: `app/release/app-release.aab`

### 9.6 Publish to Internal Testing

1. Play Console → **Create app** → LendLink
2. Complete all **App content** sections:
   - App access: all functionality available
   - Ads: No ads
   - Content rating: complete questionnaire
   - Target audience: 18+
   - Data safety: declare email, name, location collection
3. **Testing → Internal testing → Create new release**
4. Upload `app-release.aab`
5. Release notes: "LendLink v1.0 — Professor evaluation build"
6. Save and publish

### 9.7 Add Professor as Tester
1. Internal testing → **Testers** tab
2. **Create email list** → name: "Professors"
3. Add professor's Gmail address(es)
4. Click **Save**
5. Share the **opt-in URL** with your professor
6. Professor clicks the URL → finds app on Play Store → installs normally

---

## SECTION 10 — Testing Checklist for Professor

### Splash & Auth
- [ ] Sky-blue splash screen with animated LendLink logo for ~3 seconds
- [ ] Login page appears after splash
- [ ] Register as Lender → redirected to **Login** (not dashboard)
- [ ] Register as Borrower → redirected to **Login** with ₩100,000 note
- [ ] Login as Lender → Lender Dashboard
- [ ] Login as Borrower → Borrower Dashboard with ₩100,000 wallet
- [ ] Duplicate username → error shown
- [ ] Logout → Login page (no crash)

### Lender
- [ ] Add item: camera opens, photo taken, form filled, item published
- [ ] Item appears in Available tab
- [ ] Category filter works
- [ ] Tap item → QR code generated
- [ ] Edit item: changes saved
- [ ] Delete item: removed from list
- [ ] Manage Categories: add/rename/delete; delete blocked if items exist
- [ ] After borrower borrows: item moves to Lent tab in real time
- [ ] Lent item detail shows borrower name, phone, location
- [ ] Send Reminder → borrower receives notification
- [ ] Return request banner appears
- [ ] Accept return → item back in Available, lend history updated

### Borrower
- [ ] Scan FAB opens QR camera
- [ ] Scanning lender QR shows confirm dialog with lender details
- [ ] Confirm borrow: wallet deducted, item on dashboard
- [ ] Item detail shows lender name, phone, location
- [ ] Insufficient credits shows rejection error
- [ ] Borrow from two different lenders simultaneously
- [ ] Request return → lender notified
- [ ] After lender accepts: item removed, borrow history updated

### History
- [ ] Lender Credit History: entries show "₩X payment received from [Borrower] for borrowing [Item]"
- [ ] Lender Credit History: penalty entries show correctly
- [ ] Lender Lend History: returned items with image, dates, borrower details
- [ ] Borrower Payment History: "₩X paid for borrowing [Item] from [Lender]"
- [ ] Borrower Borrow History: returned items only, with lender details

### Multi-user
- [ ] Two lenders can both have items available simultaneously
- [ ] One borrower can borrow from two different lenders
- [ ] Each user's wallet shows only their own balance
