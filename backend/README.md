# Football Live Backend

This backend polls https://sports.bzzoiro.com/ every 20 seconds and exposes competition/date-filtered score data for the Android app.

## 1) Setup

1. Install Node.js 20+.
2. Copy `.env.example` to `.env`.
3. Put your BSD API key in `SPORTS_API_KEY`.
4. Install dependencies:

```bash
npm install
```

## 2) Run

```bash
npm run dev
```

The server starts at `http://localhost:3000` by default.

## 3) Endpoints

- `GET /health`
- `GET /api/scores`
- Query params:
	- `mode=today-live|date|live`
	- `date=YYYY-MM-DD` (required for `mode=date`)
	- `competitionKey=premier-league|championship|fa-cup|carabao-cup|champions-league|europa-league`
- `GET /api/scores/stream` (server-sent events)

## Notes

- Sync interval is controlled by `SYNC_INTERVAL_MS` (default `20000`).
- Date tabs are preloaded in backend memory for the range `today - 7` to `today + 7` for fast switching.

## Deploy To Railway

This repository is prepared for Railway deployment from the `backend` folder.

### Files already included

- `Dockerfile`
- `.dockerignore`
- `railway.json`
- `.env.railway.example`

### Steps

1. Push this project to GitHub.
2. In Railway, create a new project and connect the GitHub repo.
3. Set the service Root Directory to `backend`.
4. Add environment variables from `.env.railway.example`.
5. Deploy.

### Required env vars in Railway

- `PORT=3000`
- `SPORTS_API_BASE_URL=https://sports.bzzoiro.com/api`
- `SPORTS_API_KEY=...`
- `SPORTS_API_TIMEZONE=Europe/London`
- `SYNC_INTERVAL_MS=20000`

### Verify after deploy

- `https://<your-service>.up.railway.app/health`
- `https://<your-service>.up.railway.app/api/scores?mode=today-live&competitionKey=premier-league`
