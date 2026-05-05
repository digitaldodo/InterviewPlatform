# InterviewPrep – Practice Interview Platform

A full-stack platform connecting interviewees with experienced interviewers for structured mock interview sessions.

## Tech Stack
| Layer     | Technology                       |
|-----------|------------------------------------|
| Frontend  | HTML · CSS (Vanilla) · JavaScript  |
| Backend   | Spring Boot 3 (Java 17)            |
| Database  | MongoDB                            |

## Project Structure
```
Interview/
├── frontend/
│   ├── inde.html          # Landing page
│   ├── css/
│   │   └── style.css       # Design system (dark mode)
│   ├── js/
│   │   ├── app.js          # Landing page logic
│   │   └── dashboard.js    # Dashboard logic
│   └── pages/
│       └── dashboard.html  # Dashboard (Overview, Schedule, Sessions, Feedback, Profile)
│
└── backend/
    ├── pom.xml
    └── src/main/
        ├── java/com/interview/platform/
        │   ├── Application.java
        │   ├── controller/
        │   │   ├── UserController.java
        │   │   ├── SessionController.java
        │   │   └── FeedbackController.java
        │   ├── model/
        │   │   ├── User.java
        │   │   ├── Session.java
        │   │   └── Feedback.java
        │   ├── repository/
        │   │   ├── UserRepository.java
        │   │   ├── SessionRepository.java
        │   │   └── FeedbackRepository.java
        │   └── service/
        │       ├── UserService.java
        │       ├── SessionService.java
        │       └── FeedbackService.java
        └── resources/
            └── application.properties
```

## REST API

### Users
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/users/register` | Register new user |
| POST | `/api/users/login`    | Login (returns user object) |
| GET  | `/api/users/{id}`     | Get user by ID |
| GET  | `/api/users/interviewers?skill=` | Browse/search interviewers |
| PUT  | `/api/users/{id}`     | Update profile |

### Sessions
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST  | `/api/sessions`                    | Create/request session |
| GET   | `/api/sessions/interviewer/{id}`   | Sessions for an interviewer |
| GET   | `/api/sessions/interviewee/{id}`   | Sessions for an interviewee |
| PATCH | `/api/sessions/{id}/confirm`       | Confirm session |
| PATCH | `/api/sessions/{id}/complete`      | Complete session |
| PATCH | `/api/sessions/{id}/cancel`        | Cancel session |

### Feedback
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/feedback`                       | Submit feedback |
| GET  | `/api/feedback/interviewee/{id}`      | Feedback received by interviewee |
| GET  | `/api/feedback/session/{sessionId}`   | Feedback for a session |

## Running Locally

### Backend
```bash
cd backend
mvn spring-boot:run
```
Set `MONGO_URI` before starting the backend. The backend listens on `PORT` when provided, otherwise `8080`.

### Frontend
Open `frontend/index.html` directly in a browser, or serve with:
```bas
npx serve frontend
```

For local frontend testing against a separate backend, set `window.INTERVIEW_API_BASE` in `frontend/js/env.js`. Render writes this file automatically during the static site build.

## Render Deployment

This repo includes `render.yaml` with two services:

- `interview-platform-api`: Dockerized Spring Boot backend from `backend/`
- `interview-platform-frontend`: Static frontend from `frontend/`

Required Render environment variables:

- Backend: `MONGO_URI`
- Frontend: `INTERVIEW_API_BASE`, for example `https://interview-platform-api.onrender.com`
