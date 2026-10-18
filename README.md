# SkillVista — Internship Progress & Skill Analytics (Java / Spring Boot)

A live website for students and mentors. **The database starts completely empty** — there are no
pre-loaded accounts, students, scores or tests. Everything you see is created by real users signing up and using it.

## What you need
* JDK 17 or newer
* Maven 3.9+ (or open the folder in IntelliJ / Eclipse / VS Code and run `SkillVistaApplication`)

## Run it
```
mvn spring-boot:run
```
Then open **http://localhost:8080** in your browser. (The first run downloads the Spring libraries, so it needs internet once.)

Data is stored in the folder `./data/` (an H2 file database), so it is still there after you restart.
To use MySQL instead, open `src/main/resources/application.properties`, comment out the H2 lines and un-comment the MySQL lines.

## How people use it
1. A **mentor** clicks *Create an account → Mentor Account* and signs up **first**.
2. A **student** clicks *Create an account → Student Account*, fills the details and types the **mentor's email** exactly as the mentor signed up
   (the site checks that this mentor exists). They also add their **LinkedIn link, LeetCode link, total LeetCode problems solved** and **upload a resume**.
   That email is what links the student to the mentor – the student then shows up on that mentor's dashboard under their real name.
3. Log in with **Student / Mentor tab + email + password** (no more name box).
4. **My Profile** (both roles): edit name, class & section, links, CGPA, mentor email, replace the resume, change the password.
5. **LAT → LeetCode (student):** upload the question screenshot, the code file and an explanation every day. Each upload adds 1 to the
   LeetCode count on the dashboard (count = problems typed at signup + uploads on SkillVista).
6. **Evaluate Student (mentor):** sees the student's profile (class & section, year, register no., LinkedIn, LeetCode, resume), opens every uploaded
   question/code file, marks each submission *Approved* / *Needs Improvement* and writes feedback. The student sees it on **their dashboard (Mentor Feedback)** and under LAT.
7. **Aptitude / Technical Test (mentor):** add *multiple-choice* questions (marked instantly) and *written* questions (type the correct answer + a rubric and the marks).
   Choose the release day (or "Any day") and publish. Students answer inside a full-screen locked test.
8. **Grade Tests (mentor):** open a student's attempt, compare the written answer with the correct answer and rubric, type marks and a comment, save.
   The student then sees their marks, the correct answers and your comments under **LAT → My Test Results**; the score feeds the overall score.

**Real-time:** the server pushes an event whenever data changes (Server-Sent Events, `/api/stream`),
so a mentor's dashboard updates the moment a student signs up or uploads something – no refresh needed.

## How the score is calculated (shown on the Overall Score page)
Academic (latest CGPA) 15% + Internship (average "knowledge gained" you enter) 25% + LeetCode (problems solved, 100 = full) 20%
+ Aptitude test average 20% + Technical test average 20%.
75+ = High readiness / "On Track", 50–74 = Medium / "Average", below 50 = Low / "Needs Support".

## Project layout
```
src/main/java/com/skillvista
  model/    database tables (Account, Internship, Project, AcademicRecord, LeetSubmission, TestQuestion, TestAttempt, Feedback, ...)
  repo/     Spring Data repositories
  service/  AuthService (PBKDF2 passwords + sessions), MetricsService (scores), ActivityService, EventHub (live updates)
  web/      REST controllers: Auth, Student, Mentor, Test, Stream
src/main/resources/static/index.html   the website (served by Spring Boot at /) - open it through http://localhost:8080, never by double-clicking the file
src/main/resources/application.properties
```

## API summary
| Method | Path | Who |
|---|---|---|
| POST | /api/auth/signup (multipart, with resume), /api/auth/login, /api/auth/logout | anyone |
| GET/PUT | /api/profile ; POST /api/profile/resume ; PUT /api/profile/password | any logged-in user |
| GET | /api/files/resume/{gmail}, /api/files/leet/{id}/{question\|code} | the student and their mentor |
| GET | /api/student/overview | student |
| POST | /api/student/academics, /internships, /projects, /leetcode (multipart) ; PUT /internships/{id}, /projects/{id} | student |
| GET | /api/tests/{aptitude\|technical} ; POST /api/tests/{kind}/submit | student (graded on the server) |
| GET | /api/mentor/overview, /api/mentor/students/{gmail} | mentor |
| POST | /api/mentor/students/{gmail}/feedback, /api/mentor/leetcode/{id}/review | mentor |
| GET | /api/mentor/attempts, /api/mentor/attempts/{id} ; POST /api/mentor/attempts/{id}/grade | mentor |
| GET/PUT | /api/mentor/tests/{aptitude\|technical} | mentor |
| GET | /api/stream?token=... | logged-in user (live updates) |

## Known limits
* Resumes and LeetCode screenshots/code files are saved in the `./uploads/` folder. Internship certificates and project PPTs: only the **file name** is stored.
* The "reminder email" text on the LAT page is informational — no email is sent yet.
* No password-reset flow yet.
