# InterviewPrep - Practice Interview Platform

A full-stack mock interview platform with a Spring Boot API, MongoDB persistence, and a static HTML/CSS/JavaScript frontend.

## Tech Stack

| Layer | Technology |
| --- | --- |
| Frontend | HTML, CSS, vanilla JavaScript |
| Backend | Spring Boot 3, Java 17, Maven |
| Database | MongoDB |
| Deployment | Render Web Service + Render Static Site |

## Project Structure

```text
Interview/
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/interview/platform/
│       └── resources/application.properties
├── frontend/
│   ├── css/
│   ├── js/
│   │   ├── app.js
│   │   ├── dashboard.js
│   │   └── env.js
│   ├── pages/dashboard.html
│   └── index.html
└── render.yaml
```

## Local Development

### Prerequisites

- Java 17
- Maven 3.9+
- MongoDB connection string
- Optional: Node.js, only if you want to serve the static frontend locally

### Backend

From the repository root:

```bash
cd backend
$env:MONGO_URI="mongodb+srv://USER:PASSWORD@HOST/interview_platform"
mvn spring-boot:run
```

On macOS/Linux, use:

```bash
export MONGO_URI="mongodb+srv://USER:PASSWORD@HOST/interview_platform"
mvn spring-boot:run
```

The API listens on `PORT` when it is provided by the host, otherwise it defaults to `8080`.

### Frontend

For local development, `frontend/js/env.js` points to `http://localhost:8080` by default:

```js
window.INTERVIEW_API_BASE = window.INTERVIEW_API_BASE || 'http://localhost:8080';
```

You can open `frontend/index.html` directly in a browser, or serve the folder:

```bash
npx serve frontend
```

If your backend runs somewhere else, update `frontend/js/env.js` locally or override `window.INTERVIEW_API_BASE` before loading `app.js` and `dashboard.js`.

## Environment Variables

### Backend

| Variable | Required | Default | Description |
| --- | --- | --- | --- |
| `PORT` | No | `8080` | HTTP port. Render sets this automatically. |
| `MONGO_URI` | Yes | none | MongoDB connection URI. |
| `MONGO_DATABASE` | No | `interview_platform` | MongoDB database name. |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | No | local dev origins + `https://*.onrender.com` | Comma-separated allowed frontend origins. |
| `JWT_SECRET` | Yes in production | development fallback | HMAC signing secret, at least 32 characters. |
| `JWT_ACCESS_TOKEN_MINUTES` | No | `45` | Access token lifetime. |
| `JWT_REFRESH_TOKEN_DAYS` | No | `14` | Refresh token lifetime. |
| `SENDGRID_API_KEY` | Yes for email | none | SendGrid API key used for OTP and reset emails. |
| `SENDGRID_FROM_EMAIL` | Yes for email | `no-reply@interviewprep.local` | Verified SendGrid sender address. |
| `FRONTEND_URL` | Yes for reset links | `http://localhost:5500` | Public frontend URL used in password reset emails. |
| `JAVA_OPTS` | No | `-XX:MaxRAMPercentage=75.0` | JVM options used by the Docker container. |

### Frontend

| Variable | Required | Description |
| --- | --- | --- |
| `INTERVIEW_API_HOST` | Render-managed | Render injects the backend host into the static build. |

During Render deployment, the frontend build command writes `frontend/js/env.js` with the deployed backend URL.

## Render Deployment

This repository includes a Render Blueprint in `render.yaml`.

It creates:

- `interview-platform-api`: Dockerized Spring Boot Web Service from `backend/`
- `interview-platform-frontend`: Static Site from `frontend/`

### Step-by-Step

1. Push this repository to GitHub.
2. In Render, choose **New +** then **Blueprint**.
3. Connect the GitHub repository.
4. Render will detect `render.yaml` and create both services.
5. Set `MONGO_URI`, `JWT_SECRET`, and the SendGrid variables on `interview-platform-api`.
6. Deploy the blueprint.
7. After the first deployment, confirm:
   - Backend health: `https://<api-service>.onrender.com/api/health`
   - Frontend: `https://<frontend-service>.onrender.com`
8. If you rename services or use custom domains, update `CORS_ALLOWED_ORIGIN_PATTERNS` on the backend to include the frontend origin.

## Deployment Flow

1. Render builds the backend with `backend/Dockerfile`.
2. The Docker build caches Maven dependencies, packages the Spring Boot jar, and runs it on a slim Java 17 runtime image as a non-root user.
3. Render provides `PORT`; Spring Boot binds through `server.port=${PORT:8080}`.
4. Render builds the static frontend from `frontend/`.
5. The frontend build writes `js/env.js` with the backend service URL from Render's `fromService` reference.
6. Browser requests go from the static frontend to the deployed backend through `window.INTERVIEW_API_BASE`.
7. Spring CORS allows configured frontend origins through `CORS_ALLOWED_ORIGIN_PATTERNS`.

## Useful Commands

Build the backend:

```bash
cd backend
mvn clean package
```

Build the backend Docker image:

```bash
cd backend
docker build -t interview-platform-api .
```

Run the Docker image locally:

```bash
docker run --rm -p 8080:8080 `
  -e MONGO_URI="mongodb+srv://USER:PASSWORD@HOST/interview_platform" `
  interview-platform-api
```

Use backslashes instead of backticks on macOS/Linux.

## API Overview

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/health` | Health check |
| `POST` | `/api/auth/register` | Register, hash password, create JWT pair, send OTP |
| `POST` | `/api/auth/login` | Login with JWT + refresh token response |
| `POST` | `/api/auth/refresh` | Exchange refresh token for a new token pair |
| `POST` | `/api/auth/logout` | Revoke refresh token |
| `POST` | `/api/auth/send-otp` | Send verification OTP |
| `POST` | `/api/auth/resend-otp` | Rate-limited OTP resend |
| `POST` | `/api/auth/verify-otp` | Verify OTP and activate account |
| `POST` | `/api/auth/forgot-password` | Send reset email if account exists |
| `POST` | `/api/auth/reset-password` | Validate reset token and update password |
| `POST` | `/api/users/register` | Register a user |
| `POST` | `/api/users/login` | Login |
| `GET` | `/api/users` | List users |
| `GET` | `/api/users/interviewers?skill=` | Browse interviewers |
| `GET` | `/api/interviewers/search` | Search/filter/sort/paginate interviewers |
| `GET` | `/api/interviewers/{id}` | Interviewer profile |
| `GET` | `/api/interviewers/{id}/availability` | Available slots with booked slots removed |
| `GET` | `/api/interviewers/top-rated` | Top-rated interviewers |
| `GET` | `/api/interviewers/recommended` | Recommended interviewers |
| `POST` | `/api/interviewers/{id}/favorite` | Toggle saved interviewer |
| `POST` | `/api/bookings` | Multi-step booking endpoint that creates a session and meeting link |
| `POST` | `/api/sessions` | Create a session |
| `GET` | `/api/sessions/interviewer/{id}` | Interviewer sessions |
| `GET` | `/api/sessions/interviewee/{id}` | Interviewee sessions |
| `PATCH` | `/api/sessions/{id}/confirm` | Confirm a session |
| `PATCH` | `/api/sessions/{id}/complete` | Complete a session |
| `PATCH` | `/api/sessions/{id}/cancel` | Cancel a session |
| `POST` | `/api/feedback` | Submit feedback |
| `GET` | `/api/feedback` | List feedback |
| `GET` | `/api/notifications?userId=` | Notification dropdown data |
| `PATCH` | `/api/notifications/{id}/read` | Mark notification as read |

## MongoDB Collections

`users` now stores profile/discovery fields (`company`, `currentRole`, `bio`, `avatarUrl`, `language`, `yearsExperience`, `averageRating`, `reviewCount`, `completedInterviews`, `priceCents`, `availability`, `favoriteInterviewerIds`, `isVerified`, `createdAt`, `lastLogin`) and `passwordHash` instead of plain passwords.

`sessions` now includes booking fields (`interviewType`, `durationMinutes`, `meetingLink`, `meetingStatus`, `status`, `createdAt`, `updatedAt`) and indexed interviewer/time/status lookups to prevent double booking.

New collections: `refresh_tokens`, `verification_otps`, `password_reset_tokens`, and `notifications`. OTP/reset/token collections use TTL indexes through `expiresAt`.

## Frontend Architecture

The frontend remains vanilla HTML/CSS/JavaScript. `index.html` is the public SaaS landing page plus auth, OTP, and reset flows. `pages/dashboard.html` is a role-aware workspace. `js/dashboard.js` owns API access, token refresh, discovery search, booking state, session actions, feedback submission, notifications, and mobile navigation. `css/style.css` contains shared design tokens and responsive layouts.

## SendGrid Setup

Create a SendGrid API key with Mail Send permission, verify the sender used by `SENDGRID_FROM_EMAIL`, then set `SENDGRID_API_KEY`, `SENDGRID_FROM_EMAIL`, and `FRONTEND_URL` in Render. When SendGrid is not configured, the backend logs skipped emails instead of failing local development.
