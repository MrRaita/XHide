# XHide v1.0.4 - Release Notes

Comprehensive code optimizations, security enhancements, accessibility fixes, and architectural improvements in this release were implemented based on the detailed source code review and bug reports provided by @MrRaita. Special thanks for the valuable contributions.

---

## Key Highlights

### Security and Supply Chain
* Release Keystore and Signature Verification: Separated release builds with dedicated signing configurations; added SHA-256 certificate pinning (verifyCallerSignature) in the system hook.
* Privilege Isolation: Restricted privileges exclusively to UID 1000 (System), UID 0 (Root), and signature-verified XHide instances, eliminating forged package name bypasses.
* Dependency Verification: Pinned Gradle 9.4.1 distribution SHA-256 checksum, enforced Maven repository content filters, and cryptographically verified dependencies via gradle/verification-metadata.xml.

### Performance and Memory
* Asynchronous Icon Loading: Removed synchronous icon drawing loops on startup, significantly accelerating list rendering.
* LruCache and Dynamic Scaling: Replaced unbounded maps with a bounded 256-entry LruCache and implemented dynamic icon scaling based on device display density.
* LibXposed Module Detection: Extended discovery to modern LibXposed modules using META-INF/xposed/module.prop without loading DEX/APKs into RAM.
* Search Debounce: Added 200ms debounce to search queries to optimize keyboard input responsiveness.
* Scroll State Hoisting: Preserved scroll positions across tab navigation.
* Real-time Package Receiver: Implemented ACTION_PACKAGE_ADDED and ACTION_PACKAGE_REMOVED dynamic listeners to immediately update the interface and purge uninstalled entries from storage.

### UI, Accessibility, and Reliability
* Synchronization Serialization: Addressed rapid toggle race conditions using Mutex.withLock; automated state rollback on IPC failure.
* Accessibility Semantics: Applied Modifier.toggleable(role = Role.Switch) to prevent duplicate TalkBack announcements and accidental scrolling toggles; replaced fixed heights with flexible heightIn constraints to accommodate large system fonts.
* Locale-Aware Sorting: Integrated Collator with primary strength to ensure proper alphabetical ordering across different languages.
* AMOLED Pure Black: Optimized surface containers to absolute black (#000000) for OLED power saving.
* DayNight Preview Theme: Corrected initial window preview theme to eliminate light mode dark flicker.
* Configuration Preview: Added confirmation dialogs with valid rule and invalid key counters before importing or resetting configurations.
* Interface Streamlining: Replaced cluttered top app bar with native One UI large typography headers across Home, Apps, and Settings tabs; moved manual refresh to the Apps search bar alongside existing pull-to-refresh.
* Responsive Information Layout: Refactored CompactInfoRow from horizontal Row to vertical Column to prevent text squeezing on long values.
