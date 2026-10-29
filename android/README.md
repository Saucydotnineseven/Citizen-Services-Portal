# Home Affairs Citizen app

This is a native Android Studio project built with Java and XML layouts.

## Open the project

1. Open Android Studio.
2. Choose **Open** and select this `android` folder.
3. Allow Gradle to sync and install the Android SDK platform requested by the project (API 35).
4. Run the `app` configuration on an emulator or Android device.

## Prototype behavior

The interface demonstrates navigation, form validation, search, application tracking, appointment guidance, and sign-out. Sign-in accepts any 13-digit ID number and any non-empty password. Account creation, recovery, applications, and appointment requests show local demo confirmations only.

No details are sent to a server or stored in a database. For production, connect the app to a secure backend API; do not connect an Android APK directly to MySQL or include database credentials in the app.
