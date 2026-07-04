# Storage Health Ranker (Web Version)

Storage Health Ranker has been modernized from a Java desktop application into a full-stack web application.

- **Frontend**: Next.js 14, React, Tailwind CSS, Recharts
- **Backend**: Spring Boot 3.2, Java 21
- **Database**: PostgreSQL (Supabase)

## Key Feature: Browser-based File Scanning
This application uses the modern **File System Access API** to scan your local storage directly from the browser. No files are uploaded to the server — only metadata (name, size, type, dates) is sent to the backend for analysis.

*Note: This feature is only supported in Chrome and Edge.*

## Local Development

### 1. Database Setup
The backend requires a PostgreSQL database. The easiest way to get one for free is to use [Supabase](https://supabase.com).
1. Create a Supabase project.
2. Get the connection string from Settings > Database.
3. Set the following environment variables in your terminal before running the backend:
   ```bash
   export DB_URL="jdbc:postgresql://db.<your-project>.supabase.co:5432/postgres"
   export DB_USERNAME="postgres"
   export DB_PASSWORD="your-password"
   ```

### 2. Run the Backend
```bash
cd backend
mvn spring-boot:run
```
Flyway will automatically create the schema (`V1` to `V4`) on the first run. The API runs on `http://localhost:8080`.

### 3. Run the Frontend
```bash
cd web-frontend
npm install
npm run dev
```
Open `http://localhost:3000` in your browser.

## AWS Deployment Guide

### Backend (AWS Elastic Beanstalk)
1. Build the backend JAR:
   ```bash
   mvn clean package -DskipTests
   ```
2. Create an AWS Elastic Beanstalk environment (Platform: Java 21).
3. Upload the `Dockerfile` and the `target/backend-*.jar` file.
4. In Elastic Beanstalk Configuration > Software, set the environment variables:
   - `DB_URL`
   - `DB_USERNAME`
   - `DB_PASSWORD`
   - `CORS_ALLOWED_ORIGINS` (set this to your Next.js domain, e.g., `https://main.xxxx.amplifyapp.com`)

### Frontend (AWS Amplify)
1. Push your code to GitHub.
2. Go to AWS Amplify Console > Create new app > Host web app.
3. Connect your GitHub repository.
4. Set the build command to:
   ```yaml
   version: 1
   frontend:
     phases:
       preBuild:
         commands:
           - cd web-frontend
           - npm ci
       build:
         commands:
           - npm run build
     artifacts:
       baseDirectory: web-frontend/.next
       files:
         - '**/*'
     cache:
       paths:
         - web-frontend/node_modules/**/*
   ```
5. In Amplify Environment Variables, set `NEXT_PUBLIC_API_URL` to your Elastic Beanstalk API URL (e.g., `http://my-env.eba-xxxx.us-east-1.elasticbeanstalk.com`).
