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
Make sure MongoDB is running on `localhost:27017`.

### Frontend
Open `frontend/index.html` directly in a browser, or serve with:
```bas
npx serve frontend
```
