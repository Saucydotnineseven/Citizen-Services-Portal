# Android and Render project

See the [repository README](../README.md) for the full project overview, browser presentation demo, Render setup, Android build instructions, and security notes.

This folder contains the Java/XML Android client and the Express/MySQL API deployed to Render. The API's Render service root directory is `android/api`; its source, browser presentation page, and public APK download are under `api/api/api`.

## Recent app updates

- The Android label and in-app header use the Home Affairs Assistant App name and a custom HA app mark.
- Registration supports South African citizens (13-digit ID) and foreign nationals (passport or travel-document number).
- Foreign-national accounts can access an asylum application preparation demo. It does not submit an asylum claim; users must follow current official Home Affairs instructions.
- Applicants can attach PDF, JPEG, or PNG supporting documents (up to 5 MB each and 10 per application). The live prototype stores those files in its MySQL database. Use fictional or fully redacted files only; do not upload real identity or asylum documents.
- API actions display an indeterminate progress bar at the top of the screen.

The API startup migration creates the document storage table and adds the demo asylum preparation service to existing databases. Deploy the API changes to Render before testing uploads from a newly built app.
