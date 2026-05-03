# Football Studio

This workspace contains:

- `backend` - Node.js service that pulls BSD API data every 20 seconds.
- `android-app` - Android Studio project that displays live/results/fixtures tabs.

## Quick start

1. Configure backend API key:
   - Copy `backend/.env.example` to `backend/.env`
   - Set `SPORTS_API_KEY=YOUR_KEY`
2. Start backend:

```bash
cd backend
npm install
npm run dev
```

3. Open `android-app` in Android Studio.
4. Run the app on emulator or device.

By default Android emulator uses `http://10.0.2.2:3000/` to reach the backend.

## Deploy Backend Online (Railway)

1. Push repo to GitHub.
2. In Railway, create a project from the repo.
3. Set Root Directory to `backend`.
4. Add env vars from `backend/.env.railway.example`.
5. Deploy and copy your Railway URL (for example `https://your-service.up.railway.app/`).

## Wire Android Release To Online Backend

Set `backend.productionBaseUrl` in `android-app/gradle.properties`:

```properties
backend.productionBaseUrl=https://your-service.up.railway.app/
```

## One-click open (Windows)

From the workspace root you can run:

```powershell
.\open-android-app.ps1
```

Or double-click `open-android-app.bat`.
