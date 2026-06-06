# LendLink: Peer-to-Peer (P2P) Community Lending Platform

**LendLink** is a comprehensive Android application designed to facilitate secure and efficient item sharing within local communities. By connecting "Lenders" who have underutilized items with "Borrowers" who need them temporarily, LendLink promotes sustainability and community trust.

---

## 🚀 Key Features & Functionality

### 1. Dual-Role Architecture
*   **Lender Mode**: Full inventory management, earnings tracking, and custom category creation.
*   **Borrower Mode**: Item discovery marketplace, credit-based wallet, and active rental tracking.
*   **Dynamic UI**: Role-specific dashboards that adapt instantly based on user login.

### 2. Trust & Handshake System (QR Technology)
*   **Secure QR Generation**: Every item listed by a lender generates a unique, secure QR code.
*   **Physical Verification**: Borrowers must physically meet the lender and scan the item's QR code to initiate the transaction, ensuring the item's existence and condition.
*   **Scan Validation**: The app prevents accidental rentals by verifying the scanned item matches the intended listing.

### 3. Financial Ecosystem
*   **Virtual Wallet**: Integrated credit system managing "LendLink Credits." 
*   **Automated Payments**: Instant transfer of credits from Borrower to Lender upon successful QR scan.
*   **Penalty & Incentive Logic**: Built-in tracking for return deadlines with automated penalty calculations for overdue items.

### 4. Advanced Conflict Resolution (Damage Module)
*   **3-Stage Resolution Workflow**: 
    1. **Reporting**: Lenders can capture photos and describe damage upon item return.
    2. **Negotiation**: Integrated messaging logic allowing both parties to agree on a repair charge.
    3. **Resolution**: Locking items in a "Negotiating" or "Damaged" state until final payment or agreement is reached.
*   **Damage History**: A permanent, archived log of all past disputes for accountability.

### 5. Geospatial Integration
*   **Interactive Location Selection**: Users set their physical location during registration using an integrated OpenStreetMap/Leaflet interface.
*   **Localized Discovery**: Borrowers can view item locations to choose the most convenient lender.

---

## 🛠 Technical Stack
*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (Modern, Declarative UI)
*   **Backend**: Firebase (Authentication, Real-time Database, Cloud Storage)
*   **Local Storage**: Room Persistence Library (Offline caching and history)
*   **Hardware APIs**: CameraX (Photo capture & Scanning), Google Play Location Services.
*   **Background Processing**: WorkManager (Handling return reminders and penalties).

---

## 📂 Detailed Page Breakdown

| Page | Primary Functions & Logic |
| :--- | :--- |
| **Splash Screen** | Entry point with session restoration and custom scale-spring animation. |
| **Registration** | Dual-role setup with real-time uniqueness validation and GPS address fetching. |
| **Lender Dashboard** | Inventory hub with automated status chips and high-priority return request banners. |
| **Borrower Dashboard** | Marketplace with real-time keyword search, category filtering, and live credit wallet. |
| **Add/Edit Item** | Managed inventory entry with integrated CameraX for listing photography. |
| **Available Item Detail** | Pre-rental view featuring lender geospatial data, item condition, and price inspection. |
| **Active Item Detail** | Live rental tracking with deadline timers, overdue alerts, and Return Request workflow. |
| **QR Scanner** | Security-hardened camera scanner that validates Item IDs before initiating transactions. |
| **Damage Details** | Dispute resolution center with role-based logic (Borrower: Pay/Negotiate; Lender: Resolve). |
| **Borrower History** | Complete ledger of spend categories (Rentals, Penalties, Damage) and activity logs. |
| **Lender History** | Detailed record of earnings, borrower contact history, and historical item status. |
| **Notifications** | Centralized activity hub with an unread badge system and real-time alerts. |
| **Profile** | Identity manager with CameraX selfie integration for profile image updates. |

---

## 👥 The teamLendLink Group
*   **Hansini Kawindi (ID: 2577506)**: Firebase, Auth, Data Layers, Profile.
*   **Aale Magar Rijan (ID: 2577510)**: Lender UI, QR System, Category Management.
*   **Shivakoti Pawan (ID: 2577509)**: App Shell, Borrower UI, Damage Module, WorkManager.
*   **Robin Abu Saleh (ID: 2577503)**: Transactions, Notifications, Ledgers, Deployment.
