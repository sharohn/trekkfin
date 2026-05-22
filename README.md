# TrekkFin Ledger

TrekkFin is a distributed financial ledger and wallet system.

## Features
* **Distributed Ledger**: A core ledger for tracking financial transactions.
* **Wallet System**: Manage accounts, balances, and money transfers.
* **Future ML Integration**: Planned machine learning algorithms to automatically categorize payments based on merchant data and descriptions.

## Tech Stack
* **Language**: Java 21
* **Framework**: Spring Boot 3.2.5
* **Database**: PostgreSQL
* **Build Tool**: Maven

## Getting Started

### 1. Prerequisites
* Java 21 or higher
* PostgreSQL running locally

### 2. Database Setup
Create a PostgreSQL database named `trekkfin`:
```sql
CREATE DATABASE trekkfin;
```

Update your database credentials in `src/main/resources/application.properties` if they differ from the defaults:
```properties
spring.datasource.username=postgres
spring.datasource.password=postgres
```

### 3. Build and Run
Build the project using the Maven wrapper:
```bash
./mvnw clean package
```

Run the application:
```bash
./mvnw spring-boot:run
```
The application starts on [http://localhost:8080](http://localhost:8080).

### 4. Health Check
Verify the service is running:
```bash
curl http://localhost:8080/api/health
```
Expected response:
```json
{"status":"UP","message":"TrekkFin Ledger is running"}
```
