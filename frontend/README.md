# Job Finding AI Agent - Frontend

React TypeScript frontend for the Job Finding AI Agent system.

## Quick Start

```bash
cd frontend
npm install
npm start
```

App opens at `http://localhost:3000`

## Start Backend

```bash
cd java/link-collector
mvn clean package -DskipTests
java -jar target/link-collector-0.1.0.jar api-server
```

Backend runs at `http://localhost:8080/api`

## Features

- User registration and login
- PDF resume upload with AI parsing
- Job matching dashboard with match scores
- AI-generated cover letters
- Real-time job match refresh
