# SwiftDelivery: Intelligent Fleet Dispatch & Logistics Platform

An intelligent, full-stack logistics and automated fleet dispatch system built with Spring Boot, React, and TypeScript. SwiftDelivery dispatches autonomous ground robots and aerial drones across simulated San Francisco service stations, optimizing routes, pricing, and vehicle allocations in real time.

📹 **System Walkthrough & Demo Video**: [Watch Demo on Google Drive](https://drive.google.com/file/d/1-CRVbx-lLL1f8Mzf6vUwMw0xmqiPFqyE/view?usp=sharing)

---

## Core Features

- **Multi-Modal Dispatch Engine (OSRM + Haversine)**:
  - Dynamically calculates real-world road network driving distances via OSRM for ground robots.
  - Computes aerial transit paths using the Haversine formula for drones.
  - Scores and ranks delivery plans across three tiers: *Fastest (Drone)*, *Best Value (Ground Robot)*, and *Off-Peak Eco*.
- **Redis Cache-Aside & Distributed Locks**:
  - **Cache-Aside Routing Quotes**: Caches computationally intensive OSRM road distance estimates and pricing matrices in Redis with a 15-minute TTL, eliminating redundant route calculations.
  - **Distributed Locks**: Employs atomic Redis locks (`SETNX` with TTL and safe Lua-script releases) during peak dispatch windows to lock candidate vehicles, preventing double-allocation race conditions under concurrent orders.
- **Gemini-Powered AI Ordering Copilot**:
  - Leverages Google Gemini API to parse unstructured natural language shipping requests into structured shipment parameters (coordinates, dimensions, weight, urgency).
  - Multi-turn conversation support with exponential backoff retry handling.
- **Relational Data Architecture & RBAC Security**:
  - Normalized schemas modeled with Spring Data JPA and PostgreSQL/H2 for orders, fleet telemetry, stations, and tracking events.
  - Secured endpoints using stateless JWT token authentication and Spring Security Role-Based Access Control (RBAC) across `ADMIN` and `CUSTOMER` roles.
- **Interactive Fleet Management UI**:
  - Modern web console built with React, TypeScript, and Ant Design.
  - Interactive map visualization with waypoint tracking and real-time machine telemetry (battery, speed, coordinates).

---

## System Architecture

```text
               ┌────────────────────────────────────────────────────────┐
               │          React 19 + TypeScript + Ant Design            │
               │   (Interactive Map, AI Copilot Chat, Order Management) │
               └───────────────────────────┬────────────────────────────┘
                                           │ REST APIs + JWT
                                           ▼
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                             Spring Boot 2.7.x Application                                │
│                                                                                          │
│  ┌───────────────────────┐  ┌─────────────────────────┐  ┌────────────────────────────┐  │
│  │   Security & RBAC     │  │  Gemini AI Copilot Client│  │   Simulation Scheduler     │  │
│  │ (Stateless JWT / Auth)│  │ (Structured Extraction)  │  │   (Realtime Telemetry)     │  │
│  └───────────────────────┘  └─────────────────────────┘  └────────────────────────────┘  │
│                                                                                          │
│  ┌────────────────────────────────────────────────────────────────────────────────────┐  │
│  │                           Dispatch & Recommendation Engine                          │  │
│  │             - OSRM Road Routing Service   - Haversine Line Calculator              │  │
│  │             - Triple-Gate Admissibility   - Multi-Criteria Plan Scoring             │  │
│  └───────────────────────────┬────────────────────────────┬───────────────────────────┘  │
└──────────────────────────────┼────────────────────────────┼──────────────────────────────┘
                               │                            │
                               ▼                            ▼
                 ┌───────────────────────────┐┌───────────────────────────┐
                 │       Redis Server        ││     PostgreSQL / H2       │
                 │ - Cache-Aside Quotes      ││ - Orders, Users, Stations │
                 │ - Distributed Lock (SETNX)││ - Vehicle Telemetry Logs  │
                 └───────────────────────────┘└───────────────────────────┘
```

---

## Tech Stack

- **Backend**: Java 11, Spring Boot 2.7, Spring Data JPA, Spring Security, JWT (io.jsonwebtoken), Lombok
- **Caching & Concurrency**: Redis, Spring Data Redis, Lua Distributed Locks
- **Routing & Maps**: Project OSRM API, Haversine Spherical Distance Algorithm
- **AI Integration**: Google Gemini API (`gemini-3.8-flash` / Generative Language API)
- **Database**: PostgreSQL (AWS RDS compatible), H2 (Local zero-config dev fallback)
- **Frontend**: React 19, TypeScript, Ant Design 6, Leaflet / Mapbox, Zustand, Vite

---

## Quick Start

### 1. Prerequisites
- Java 11 JDK or higher
- Maven 3.6+
- Node.js (v18+)
- Redis Server (optional for local demo; has in-memory fallback)

### 2. Configure Backend (`server/`)
The application is pre-configured with safe local defaults (in-memory H2 database with automatic schema/data seeding).

```bash
cd server
mvn clean install -DskipTests
mvn spring-boot:run
```
- Backend runs at: `http://localhost:8080`
- Local H2 Console: `http://localhost:8080/h2-console` (`jdbc:h2:mem:wedelivery`, User: `sa`, Password: *empty*)

*(Optional) To enable Gemini AI Order Copilot, set your API key in environment variables:*
```bash
export GEMINI_API_KEY=your_gemini_api_key
```

### 3. Configure Frontend (`web/`)
```bash
cd web
npm install
npm run dev
```
- Frontend Web Console runs at: `http://localhost:5173`

---

## Core API Endpoints

| Endpoint | Method | Role | Description |
| :--- | :--- | :--- | :--- |
| `/api/auth/login` | `POST` | Public | Authenticates user and returns stateless JWT token. |
| `/api/recommendations` | `POST` | Customer/Admin | Computes multi-plan dispatch quotes (Cache-Aside enabled). |
| `/api/orders` | `POST` | Customer/Admin | Creates order and atomically locks vehicle via Redis distributed lock. |
| `/api/orders/{orderNumber}/track` | `GET` | Customer/Admin | Retrieves live delivery tracking events and telemetry coordinates. |
| `/api/dispatch/vehicles` | `GET` | Admin | Real-time fleet monitor reporting battery, speed, and status. |
| `/api/ai/order-parser` | `POST` | Customer/Admin | Gemini-powered natural language shipment parameter extractor. |

---

## Project Structure

```text
SwiftDelivery/
├── server/                               # Spring Boot Backend
│   ├── src/main/java/com/wedelivery/
│   │   ├── config/                      # SecurityConfig, RedisConfig
│   │   ├── controller/                  # Order, Dispatch, Station, AI, Auth controllers
│   │   ├── dto/                         # Request and response contract objects
│   │   ├── entity/                      # JPA Entities (Order, Vehicle, Station, User)
│   │   ├── repository/                  # Spring Data JPA repositories
│   │   └── service/                     # Dispatch engine, RedisLock, Cache, OSRM, Gemini
│   ├── src/main/resources/              # schema.sql, data.sql, application.yml
│   └── pom.xml                          # Maven dependencies
├── web/                                  # React + TypeScript Frontend
│   ├── src/                             # Pages, Components, Map Views, Stores
│   ├── package.json
│   └── vite.config.js
├── docs/                                 # Architecture diagrams, specifications
├── .gitignore                           # Media and secret filters
└── README.md                            # Documentation and walkthrough guide
```

---

## License
MIT License
