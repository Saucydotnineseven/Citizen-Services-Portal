# Home Affairs Citizen Services Portal

A student prototype that demonstrates a citizen-facing Home Affairs services experience across an Android app and a browser presentation demo. The project is independent and is **not an official Department of Home Affairs service**.

Use fictional information only. The Android app connects to the deployed API for account and service workflows; do not enter real ID numbers, passwords, contact details, biometric data, or payment details.

## Present the project

The Render service serves the presentation page, browser demo, Android APK, and API:

- Presentation page: <https://citizen-services-portal.onrender.com/>
- Interactive browser demo: <https://citizen-services-portal.onrender.com/demo>
- Android APK: <https://citizen-services-portal.onrender.com/download/app.apk>
- Database health: <https://citizen-services-portal.onrender.com/api/health/db>

The browser demo runs in a normal desktop or mobile browser without Android Studio. Its account and application examples are fictional and stay in the page; demo actions do not call the API or persist data. The APK is a debug build for Android phones or an Android emulator, not a Windows application or a Play Store release.

## Project structure

| Path | Purpose |
| --- | --- |
| `android/app` | Native Android client written in Java with XML layouts. |
| `android/api` | Node.js API package used by the Render service. |
| `android/api/api/api/server.js` | Express entry point; serves the presentation page, browser demo, APK download, and API routes. |
| `android/api/api/api/dbroutes.js` | Account, application, appointment, payment-demo, notification, and staff API routes. |
| `android/api/api/api/schema.sql` | MySQL schema and demonstration seed data. |
| `android/api/api/api/public` | Public presentation and browser demo pages plus the downloadable APK. |
| `artifacts/` and `lib/` | Separate pnpm/Replit workspace components, generated API packages, and shared database packages. They are not the backend used by the Android APK's Render URL. |

The Android client calls `https://citizen-services-portal.onrender.com`. The Render app's root directory should be `android/api`; from there `npm start` runs `api/api/server.js`.

## Run the Android app

Requirements: Android Studio, JDK supported by the Gradle wrapper, Android SDK Platform 35, and an Android device with USB debugging or an emulator.

From the `android` directory in PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is created at `android/app/build/outputs/apk/debug/app-debug.apk`. To install on a connected device or emulator:

```powershell
.\gradlew.bat installDebug
```

If more than one device is connected, select the desired device in Android Studio's Run target selector, or install the built APK using `adb -s DEVICE_SERIAL install -r app\build\outputs\apk\debug\app-debug.apk` from the `android` directory.

## Render API deployment

The Render web service should use:

- **Branch:** `main`
- **Root directory:** `android/api`
- **Build command:** `npm install`
- **Start command:** `npm start`

The service needs these environment variables in Render's environment settings:

| Variable | Purpose |
| --- | --- |
| `DB_HOST` | MySQL host. |
| `DB_PORT` | MySQL port. |
| `DB_USER` | Database user. |
| `DB_PASSWORD` | Database password. |
| `DB_NAME` | Database name. |
| `DB_CA` or `DB_CA_FILE` | Database TLS CA value or CA filename. |
| `JWT_SECRET` | Long random secret used to sign authentication tokens. |
| `PORT` | Set automatically by Render. |

Keep credentials in Render's secret environment settings. Do not commit `.env` files, passwords, database URLs, or signing keys. The local `.env` file is ignored by Git. The database health route is `/api/health/db`.

### Enable the staff/admin portal

The Android login page has a separate **Staff and admin sign in** panel. `OFFICIAL` accounts can review and advance applications. `ADMIN` accounts also get searchable user accounts, suspend/reactivate controls, and an audit log. Account IDs and emails are masked in the user list; administrator roles cannot be changed from the app. Regular self-registration always creates a citizen account.

Before deploying the admin-enabled API, run this additive migration from the Render Shell while the service root directory is `android/api`:

```sh
npm run migrate:admin
```

Create or identify a trusted test account, then promote that existing account from the Render Shell:

```sh
npm run promote:admin -- admin@example.test
```

Use fictional account details for this prototype. Run the migration before deploying the code that reads `account_status`; suspended accounts are denied on every protected API request. The promotion command changes only the matching existing account and must only be run by a trusted operator.
The database setup script is intended only for an empty development/demo database. From `android/api/api/api`, it can be run with `node setup-db.js` after configuring the environment and ensuring the target database is safe to initialize. Avoid running schema setup against an existing production database without a reviewed migration plan and backup.

## Browser presentation demo

Open `/demo` on the Render URL. The page provides sample dashboard, service, application-progress, appointment-preview, notification, and security-notes screens. It uses local hard-coded sample records, does not call API endpoints, and does not save entered appointment preview values.

To update the browser page or downloadable APK, commit the changed files under `android/api/api/api/public`, push to the branch Render watches, and deploy the latest commit in Render. Confirm the page, `/demo`, `/download/app.apk`, and `/api/health/db` after deployment.

## API overview

The Android app uses HTTPS API routes for:

- Account registration and login: `/api/register`, `/api/login`.
- Service catalogue: `/api/services`.
- Applications and status tracking: `/api/applications`, `/api/applications/mine`.
- Appointment requests and appointment history: `/api/appointment-requests`, `/api/appointments/mine`.
- Demo payments, notifications, and staff workflow: `/api/payments`, `/api/notifications/mine`, `/api/staff/applications`.

Most user-specific routes require a bearer token. The demo payment endpoint simulates a payment and must not be treated as a real payment processor.

## Security and privacy before real use

This prototype is not ready to process real citizen information. The Android account flow sends identity and contact fields to the live API; the API stores account data in MySQL and bcrypt-hashes passwords. Use made-up test data during presentations.

Current code protections include user-scoped application and appointment queries, session-bound Android API requests, no-store API responses, a two-hour JWT lifetime, generic API errors, restricted browser CORS, and login/registration rate limits. The rate-limit counters are in memory on one Render process; use a shared store before running multiple service instances.

Before any real deployment or collection of personal information:

1. Get an appropriate security and privacy review, define a lawful purpose, minimize collected fields, set retention/deletion rules, and plan for incidents and data-subject requests. POPIA section 19 requires reasonable safeguards and regular verification/update of those safeguards.
2. Verify authorization for every user and record. Add stronger staff authentication, session revocation, monitoring, and security tests for cross-account access.
3. Replace the debug APK with a signed release build and review how it is distributed. Do not collect real payment information in the prototype.
4. Use strong server-side secrets, least-privilege database credentials, encrypted backups, verified TLS, dependency updates, and a documented recovery process.

Useful references: [POPIA (Act 4 of 2013), especially section 19](https://www.justice.gov.za/legislation/acts/2013-004.pdf), [OWASP API Security Top 10](https://api-security.owasp.org/editions/2023/en/0x11-t10/), and [OWASP Password Storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
## Repository workspace

The repository root also contains a pnpm workspace used by the Replit scaffold (`artifacts/`, `lib/`, `scripts/`). The root `package.json` has workspace typecheck/build commands; these are separate from building/installing the Android app and from deploying `android/api` to Render.
