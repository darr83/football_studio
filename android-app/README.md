# Football Live Scores (Android)

Android app for six competitions with live, past-result, and future-fixture tabs.

## What it does

- Shows live scores, previous results, and future fixtures.
- Reads from your backend endpoint (`/api/scores`).
- Refreshes data every 20 seconds in-app.

## Configure backend URLs

The app uses different backend URLs for debug and release:

- Debug build: `backend.debugBaseUrl`
- Release build: `backend.productionBaseUrl`

### Debug (local backend)

Set this in `android-app/local.properties`:

```properties
backend.debugBaseUrl=http://10.0.2.2:3000/
```

For a physical phone, replace with your PC LAN IP, for example:

```properties
backend.debugBaseUrl=http://192.168.1.50:3000/
```

### Release (online backend)

Set this in `android-app/gradle.properties`:

```properties
backend.productionBaseUrl=https://your-service.up.railway.app/
```

## Open in Android Studio

1. Open Android Studio.
2. Open the `android-app` folder.
3. Let Gradle sync.
4. Run on emulator or device.
