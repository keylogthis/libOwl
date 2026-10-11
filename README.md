# Library Owl

Library Owl is an Android app for cataloguing your personal book collection.
It is a modern rewrite of a book collection manager originally written about twenty years ago.

Package name: `org.thebottle.libOwl`

## Features

- **Add books** by scanning an ISBN barcode, by fast Multi-Scan, or by hand
- **Online lookup** of title, author, publisher, year, pages, genre, description and cover (Open Library and Google Books)
- **Library**: covers, half-star ratings, read status (Unread, Reading, Finished, Read Again), price, notes
- **Search, sort and filter** by title, author, status, rating, genre, cover, loan status
- **Multi-select** to delete books or change read status, rating, price, genre or borrower for many books at once
- **Loans**: record who has a book and when it is due, loan history, and daily due-date reminders
- **Statistics**: pages read, collection value, ratings, top authors/genres, and more
- **Reports**: print or save as PDF, as a table or a detailed list, with the properties you choose
- **Backup and restore** of your library, covers, loans and settings to a folder you choose
- Accent colour and currency symbol settings, light and dark mode, in-app help

Your library is stored only on your device. The app uses the network only for book lookups.

## Building

1. Open the project in Android Studio (Koala or newer) and let Gradle sync.
2. Run it on a device or emulator, or use **Build > Build APK(s)**.

Requirements: JDK 17, Android SDK 35, minimum Android version 8.0 (API 26).

Barcode scanning uses Google's ML Kit barcode scanner, bundled in the app, so it works without Google Play services.

## Installing a release

Download the APK for your phone's processor from the Releases page. Most modern phones need the `arm64-v8a` file.

## License

Copyright is held by the author. Licensed under the Apache License, Version 2.0.
See the [LICENSE](LICENSE) file for details.
