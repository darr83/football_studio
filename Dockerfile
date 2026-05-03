FROM node:20-alpine

WORKDIR /app

# Install backend dependencies first for better layer caching.
COPY backend/package*.json ./backend/
WORKDIR /app/backend
RUN npm ci --omit=dev

# Copy backend source.
COPY backend/src ./src
COPY backend/.env.example ./.env.example

ENV NODE_ENV=production
EXPOSE 3000

CMD ["npm", "start"]
