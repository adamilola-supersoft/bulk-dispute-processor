# SparkPay Bulk Dispute Processor

A Spring Boot microservice for processing bulk CSV uploads for dispute updates using a session-file approach. Files are stored on disk named by the database `session.id`. The service supports preview/edit functionality and creates jobs that are processed by background workers.

## Features

- **CSV Upload & Validation**: Upload CSV files with automatic validation
- **Session Management**: Track upload sessions with optimistic locking
- **Preview/Edit**: Preview CSV data and overwrite with edited versions
- **Background Processing**: RabbitMQ-based job processing with workers
- **Atomic File Operations**: Safe file overwrites with backup creation
- **Database Schema Management**: Flyway migrations for database setup
- **Extensible Design**: Pluggable `DisputeUpdater` interface for custom processing logic

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   REST API      │    │   File Storage  │    │   Database      │
│   (Spring MVC)  │───▶│   (Disk)        │    │   (MySQL)       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   RabbitMQ      │    │   Worker        │    │   Flyway        │
│   (Message      │◀───│   (Job          │    │   (Schema       │
│    Queue)       │    │    Processor)   │    │    Migration)   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.6+
- MySQL 8.0+
- Docker & Docker Compose (for infrastructure)

### 1. Start Infrastructure

```bash
# Start MySQL and RabbitMQ
docker-compose up -d

# Verify services are running
docker-compose ps
```

### 2. Configure Database

Create the database and user:

```sql
CREATE DATABASE bulk_dispute_db;
CREATE USER 'bulkuser'@'localhost' IDENTIFIED BY 'bulkpwd';
GRANT ALL PRIVILEGES ON bulk_dispute_db.* TO 'bulkuser'@'localhost';
FLUSH PRIVILEGES;
```

### 3. Run the Application

```bash
# Build and run
mvn clean install
mvn spring-boot:run

# Or run the JAR
java -jar target/sparkpay.bulk_dispute_processor-0.0.1-SNAPSHOT.jar
```

The application will start on `http://localhost:8080`

### 4. Verify Setup

```bash
# Check application health
curl http://localhost:8080/actuator/health

# Check RabbitMQ management UI
open http://localhost:15672
# Username: admin, Password: admin123
```

## API Documentation

### 1. Upload CSV and Create Session

**POST** `/api/sessions`

Upload a CSV file and create a new session.

```bash
curl -X POST http://localhost:8080/api/sessions \
  -F "file=@sample_disputes.csv" \
  -F "uploadedBy=admin"
```

**Response:**
```json
{
  "sessionId": 1,
  "preview": [
    {
      "dispute_id": "123",
      "action": "ACCEPT",
      "reason": "Resolved",
      "notes": "Good customer"
    }
  ],
  "totalRows": 100,
  "version": 0
}
```

### 2. Preview Session Data

**GET** `/api/sessions/{sessionId}/preview?rows=50&offset=0`

Get a preview of the CSV data.

```bash
curl "http://localhost:8080/api/sessions/1/preview?rows=50&offset=0"
```

**Response:**
```json
{
  "sessionId": 1,
  "preview": [...],
  "totalRows": 100,
  "version": 0
}
```

### 3. Overwrite Session File

**PUT** `/api/sessions/{sessionId}/file`

Overwrite the session file with edited content (requires If-Match header for optimistic locking).

```bash
curl -X PUT http://localhost:8080/api/sessions/1/file \
  -H "If-Match: 0" \
  -F "file=@edited_disputes.csv"
```

**Response:**
```json
{
  "sessionId": 1,
  "version": 1,
  "totalRows": 100
}
```

### 4. Confirm Session and Create Job

**POST** `/api/sessions/{sessionId}/confirm`

Confirm the session and create a background job.

```bash
curl -X POST http://localhost:8080/api/sessions/1/confirm
```

**Response:**
```json
{
  "jobId": 1
}
```

### 5. Check Job Status

**GET** `/api/jobs/{jobId}/status`

Get the status of a background job.

```bash
curl http://localhost:8080/api/jobs/1/status
```

**Response:**
```json
{
  "jobId": 1,
  "status": "COMPLETED",
  "totalRows": 100,
  "processedRows": 100,
  "successCount": 95,
  "failureCount": 5,
  "errorReportPath": "/uploads/1_errors_20241009_153000.csv",
  "startedAt": "2024-10-09T15:30:00",
  "completedAt": "2024-10-09T15:30:05"
}
```

### 6. Download Session File

**GET** `/api/sessions/{sessionId}/file`

Download the original session file.

```bash
curl http://localhost:8080/api/sessions/1/file
```

## Sample CSV Format

Create a file named `sample_disputes.csv`:

```csv
dispute_id,action,reason,notes
123,ACCEPT,Resolved,Good customer
456,REJECT,Fraud detected,Multiple chargebacks
789,ACCEPT,Resolved,Technical issue
101,REJECT,Invalid claim,No supporting documentation
```

### Required Columns

- `dispute_id`: Numeric dispute identifier
- `action`: Must be `ACCEPT` or `REJECT`

### Optional Columns

- `reason`: Reason for the action
- `notes`: Additional notes

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BULK_FILES_BASE_PATH` | `./uploads` | Base directory for session files |
| `MAX_PREVIEW_ROWS` | `200` | Maximum rows in preview |
| `MAX_UPLOAD_SIZE_MB` | `50` | Maximum file size in MB |
| `SESSION_TTL_DAYS` | `7` | Session cleanup TTL |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/bulk_dispute_db` | Database URL |
| `SPRING_DATASOURCE_USERNAME` | `bulkuser` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `bulkpwd` | Database password |
| `SPRING_RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `SPRING_RABBITMQ_PORT` | `5672` | RabbitMQ port |
| `SPRING_RABBITMQ_USERNAME` | `admin` | RabbitMQ username |
| `SPRING_RABBITMQ_PASSWORD` | `admin123` | RabbitMQ password |

### Application Properties

The application uses `application.properties` with environment variable overrides:

```properties
# Database Configuration
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/bulk_dispute_db}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:bulkuser}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:bulkpwd}

# File Storage
bulk.files.base-path=${BULK_FILES_BASE_PATH:./uploads}

# Validation
bulk.validation.max-preview-rows=${MAX_PREVIEW_ROWS:200}
bulk.validation.max-upload-size-mb=${MAX_UPLOAD_SIZE_MB:50}
```

## Database Schema

The application uses Flyway for database migrations. The schema includes:

- `bulk_dispute_session`: Session tracking with optimistic locking
- `bulk_dispute_job`: Job management and status tracking
- `bulk_dispute_job_audit`: Audit trail for job operations

## Testing

### Unit Tests

```bash
# Run unit tests
mvn test

# Run with coverage
mvn test jacoco:report
```

### Integration Tests

```bash
# Start infrastructure
docker-compose up -d

# Run integration tests
mvn test -Dtest=*IntegrationTest
```

### Manual Testing

1. **Upload Valid CSV**:
   ```bash
   curl -X POST http://localhost:8080/api/sessions \
     -F "file=@sample_disputes.csv" \
     -F "uploadedBy=test"
   ```

2. **Preview Data**:
   ```bash
   curl "http://localhost:8080/api/sessions/1/preview?rows=10"
   ```

3. **Confirm and Process**:
   ```bash
   curl -X POST http://localhost:8080/api/sessions/1/confirm
   curl http://localhost:8080/api/jobs/1/status
   ```

## Development

### Project Structure

```
src/main/java/com/supersoft/sparkpay/bulk_dispute_processor/
├── config/                 # Configuration classes
├── controller/             # REST controllers
├── domain/                 # Domain models
├── repository/             # Data access layer
├── service/               # Business logic
└── worker/                # Background job processing
```

### Adding Custom Dispute Updaters

Implement the `DisputeUpdater` interface:

```java
@Service
public class CustomDisputeUpdater implements DisputeUpdater {
    @Override
    public ProcessingResult processRow(Map<String, String> row) {
        // Custom processing logic
        return ProcessingResult.success();
    }
}
```

### Database Migrations

Add new migrations to `src/main/resources/db/migration/`:

```sql
-- V2__add_new_column.sql
ALTER TABLE bulk_dispute_session ADD COLUMN new_column VARCHAR(255);
```

## Monitoring

### Logs

```bash
# Application logs
tail -f logs/bulk_dispute_processor.log

# Docker logs
docker-compose logs -f
```

### Health Checks

```bash
# Application health
curl http://localhost:8080/actuator/health

# Database health
curl http://localhost:8080/actuator/health/db

# RabbitMQ health
curl http://localhost:8080/actuator/health/rabbit
```

## Troubleshooting

### Common Issues

1. **Database Connection Failed**
   - Verify MySQL is running: `docker-compose ps`
   - Check credentials in `application.properties`
   - Ensure database exists: `CREATE DATABASE bulk_dispute_db;`

2. **RabbitMQ Connection Failed**
   - Verify RabbitMQ is running: `docker-compose ps`
   - Check management UI: http://localhost:15672
   - Verify credentials match configuration

3. **File Upload Fails**
   - Check `bulk.files.base-path` directory exists
   - Verify write permissions
   - Check file size limits

4. **Job Processing Fails**
   - Check RabbitMQ queues: http://localhost:15672
   - Review worker logs
   - Verify file paths are accessible

### Debug Mode

```bash
# Enable debug logging
export LOGGING_LEVEL_COM_SUPERSOFT=DEBUG
mvn spring-boot:run
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:
1. Check the troubleshooting section
2. Review application logs
3. Verify infrastructure services are running
4. Check database and RabbitMQ connectivity
