# Invret — Investment & Returns Manager

Native Kotlin Android prototype. Open this folder in Android Studio, allow Gradle sync, select your connected Android phone, and press **Run**.

## Features in this first build
- Multiple projects and investors
- Non-refundable investment contributions
- Fixed monthly, percentage-of-revenue, percentage-of-positive-profit, and hybrid (fixed + profit share) agreements
- Month-effective agreement changes and historical agreement records
- Monthly revenue and expenses
- Earned, paid, and unpaid investor distributions; cash-on-cash return percentage
- Historical contributions and distributions; import old distributions via simple CSV: `date,mode,amount,notes`
- Finalize months to prevent changes to monthly financial figures; explicit adjustment entries
- SQLite offline database

## Important limitations / precautions
- This is an **untested initial Android Studio source project**, not a signed APK. Build and test before recording real financial data.
- CSV parser handles simple comma-separated values only, not quoted embedded commas. Import only trusted files; amounts are rupees.
- Agreement changes are month-based; mid-month effective dates are not supported.
- New agreement insertion is intended in chronological order; do not insert earlier agreements after later ones.
- Monthly earnings are computed through the current month, including an unfinished month. Verify revenue/expenses before relying on totals.
- A finalized month prevents financial edits, but does not freeze all possible agreement/adjustment edits; full accounting-grade audit locking is future work.
- ROI shown is cumulative cash distributions divided by contributions; XIRR is not yet implemented.
- There is **no export/restore backup yet**. Do not uninstall or clear app storage if you have entered data.
- App uses the system SQLite database rather than Room in this initial build. Room migration can follow once functionality is verified.
- Keep financial records off public GitHub. Only source code should be committed.

## Installation
1. Android Studio → File → Open → select the `Invret` directory.
2. Wait for Gradle sync (internet needed for first dependency download).
3. On phone enable Developer options → USB debugging; connect by USB and authorize computer.
4. Select your device in Android Studio and click Run (green triangle).

Application ID: `com.invret.app`; minimum Android 8.0 (API 26).
