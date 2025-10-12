# SparkPay Bulk Dispute Processor

A Spring Boot microservice for processing bulk CSV uploads for dispute updates using a session-file approach. Files are stored on disk named by the database `session.id`. The service supports preview/edit functionality, proof file uploads, and creates jobs that are processed by background workers.

> **Latest Update**: Enhanced proof file management with realistic workflow - proof files are now uploaded separately via dedicated API endpoints, making the system more user-friendly and scalable.

## Features

- **CSV Upload & Validation**: Upload CSV files with automatic validation and structured error responses
- **Session Management**: Track upload sessions with optimistic locking
- **Preview/Edit**: Preview CSV data and overwrite with edited versions
- **Proof File Management**: Upload, download, and manage proof files for dispute rejections
- **Background Processing**: RabbitMQ-based job processing with workers
- **Atomic File Operations**: Safe file overwrites with backup creation
- **Database Schema Management**: Flyway migrations for database setup
- **CORS Support**: Configurable cross-origin resource sharing for web applications
- **Structured Error Responses**: Detailed validation errors with row, column, and reason information
- **Extensible Design**: Pluggable `DisputeUpdater` interface for custom processing logic

## Proof File Workflow

The system now uses a **realistic proof file management approach**:

### **1. CSV Upload (Decisions Only)**
Upload a clean CSV with just dispute decisions:
```csv
Unique Key,Action
2214B8JO003524000000003524,ACCEPT
2070EXNV012946000000012946,REJECT
```

### **2. Proof File Upload (Separate)**
Upload proof files separately via the proof management API:
```bash
# Upload proof for a specific dispute
curl -X POST http://localhost:8080/api/proofs/upload \
  -F "file=@2070EXNV012946000000012946.pdf"
```

### **3. Validation Process**
- ✅ **ACCEPT actions**: No proof required
- ✅ **REJECT actions**: Must have uploaded proof file
- ✅ **System checks**: Actual file existence, not CSV references

### **4. Benefits of This Approach**
- **Clean CSV format**: No file references cluttering data
- **Real file management**: Actual files with proper metadata
- **Better UX**: Upload files naturally, not as text
- **Scalable**: Large files don't bloat CSV
- **Backward compatible**: Won't break if proof column exists in CSV

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

### 5. API Documentation

Access the interactive API documentation:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **ReDoc**: http://localhost:8080/redoc.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

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

### 7. Upload Proof File

**POST** `/api/proofs/upload`

Upload a proof file for a dispute. The filename should be the dispute's unique code.

```bash
curl -X POST http://localhost:8080/api/proofs/upload \
  -F "file=@2214B8JO003524000000003524.pdf"
```

**Response:**
```json
{
  "success": true,
  "message": "Proof uploaded successfully",
  "uniqueCode": "2214B8JO003524000000003524",
  "filePath": "/path/to/proof/file.pdf"
}
```

### 8. Download Proof File

**GET** `/api/proofs/download/{uniqueCode}`

Download a proof file for a dispute.

```bash
curl http://localhost:8080/api/proofs/download/2214B8JO003524000000003524
```

### 9. Check Proof Status

**GET** `/api/proofs/status/{uniqueCode}`

Check if a proof file exists for a dispute.

```bash
curl http://localhost:8080/api/proofs/status/2214B8JO003524000000003524
```

**Response:**
```json
{
  "uniqueCode": "2214B8JO003524000000003524",
  "proofExists": true,
  "filePath": "/path/to/proof/file.pdf"
}
```

### 10. Delete Proof File

**DELETE** `/api/proofs/delete/{uniqueCode}`

Delete a proof file for a dispute.

```bash
curl -X DELETE http://localhost:8080/api/proofs/delete/2214B8JO003524000000003524
```

### 11. Get Sessions List

**GET** `/api/sessions?page=0&size=10&status=VALIDATED&uploadedBy=admin`

Get a paginated list of sessions with optional filters.

```bash
curl "http://localhost:8080/api/sessions?page=0&size=10&status=VALIDATED"
```

**Response:**
```json
{
  "sessions": [
    {
      "id": 1,
      "uploadedBy": "admin",
      "fileName": "disputes.csv",
      "status": "VALIDATED",
      "totalRows": 100,
      "validRows": 95,
      "invalidRows": 5,
      "createdAt": "2024-10-09T15:30:00",
      "updatedAt": "2024-10-09T15:30:00"
    }
  ],
  "pagination": {
    "currentPage": 0,
    "pageSize": 10,
    "totalPages": 1,
    "totalElements": 1
  }
}
```

### 12. Get Jobs List

**GET** `/api/jobs?page=0&size=10&status=COMPLETED&sessionId=1`

Get a paginated list of jobs with optional filters.

```bash
curl "http://localhost:8080/api/jobs?page=0&size=10&status=COMPLETED"
```

**Response:**
```json
{
  "jobs": [
    {
      "id": 1,
      "sessionId": 1,
      "status": "COMPLETED",
      "totalRows": 100,
      "processedRows": 100,
      "successCount": 95,
      "failureCount": 5,
      "startedAt": "2024-10-09T15:30:00",
      "completedAt": "2024-10-09T15:30:05"
    }
  ],
  "pagination": {
    "currentPage": 0,
    "pageSize": 10,
    "totalPages": 1,
    "totalElements": 1
  }
}
```

## Sample CSV Format

Create a file named `sample_disputes.csv`:

```csv
Unique Key,Action
2214B8JO003524000000003524,ACCEPT
2070EXNV012946000000012946,REJECT
207076AQ001506000000001506,ACCEPT
2070PBU7010998000000010998,REJECT
2057BSPY019789000000019789,ACCEPT
20705RGZ002622000000002622,REJECT
2057XLZH005707000000005707,ACCEPT
20339RSD002783000000002783,REJECT
2214D5VT005499000000005499,ACCEPT
```

### Required Columns

- `Unique Key`: Alphanumeric dispute identifier (e.g., `2214B8JO003524000000003524`)
- `Action`: Must be `ACCEPT` or `REJECT` (case-insensitive)

### Optional Columns

- `Proof(Optional)`: Proof file reference (optional - system will check for uploaded proof files for REJECT actions)

### Validation Rules

1. **Unique Key**: Must be alphanumeric format
2. **Action**: Must be `ACCEPT` or `REJECT` (case-insensitive)
3. **Proof Requirement**: For `REJECT` actions, proof file must be uploaded separately via `/api/proofs/upload` endpoint
4. **File Naming**: Proof files should be named with the dispute's unique code (e.g., `2214B8JO003524000000003524.pdf`)
5. **Backward Compatibility**: CSV can include `Proof(Optional)` column - system will ignore it and check actual uploaded files
6. **Header Flexibility**: Headers are trimmed and case-insensitive for better compatibility

### Structured Error Responses

When validation fails, the API returns structured error objects:

```json
{
  "error": "Validation failed",
  "validationErrors": [
    {
      "row": 0,
      "column": "HEADER",
      "reason": "Missing required column: Unique Key"
    },
    {
      "row": 5,
      "column": "Action",
      "reason": "Invalid action value 'PENDING'. Must be one of: [ACCEPT, REJECT, Accept, Reject]"
    },
    {
      "row": 3,
      "column": "Proof(Optional)",
      "reason": "Proof is mandatory when action is 'REJECT'. Please provide proof file or upload proof separately."
    }
  ]
}
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BULK_FILES_BASE_PATH` | `./uploads` | Base directory for session files |
| `BULK_PROOFS_BASE_PATH` | `./proofs` | Base directory for proof files |
| `BULK_PROOFS_MAX_SIZE_MB` | `10` | Maximum proof file size in MB |
| `BULK_PROOFS_ALLOWED_EXTENSIONS` | `pdf,jpg,jpeg,png,doc,docx` | Allowed proof file extensions |
| `BULK_PROOFS_REPLACE_EXISTING` | `true` | Allow replacing existing proof files |
| `MAX_PREVIEW_ROWS` | `200` | Maximum rows in preview |
| `MAX_UPLOAD_SIZE_MB` | `50` | Maximum file size in MB |
| `SESSION_TTL_DAYS` | `7` | Session cleanup TTL |
| `CORS_ALLOWED_ORIGINS` | `*` | Allowed CORS origins (use `*` for development) |
| `CORS_ALLOWED_METHODS` | `GET,POST,PUT,DELETE,OPTIONS` | Allowed CORS methods |
| `CORS_ALLOWED_HEADERS` | `*` | Allowed CORS headers |
| `CORS_ALLOW_CREDENTIALS` | `false` | Allow CORS credentials |
| `CORS_MAX_AGE` | `3600` | CORS preflight cache time |
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

# Proof Files Storage
bulk.proofs.base-path=${BULK_PROOFS_BASE_PATH:./proofs}
bulk.proofs.max-size-mb=${BULK_PROOFS_MAX_SIZE_MB:10}
bulk.proofs.allowed-extensions=${BULK_PROOFS_ALLOWED_EXTENSIONS:pdf,jpg,jpeg,png,doc,docx}
bulk.proofs.replace-existing=${BULK_PROOFS_REPLACE_EXISTING:true}

# CORS Configuration
cors.allowed-origins=${CORS_ALLOWED_ORIGINS:*}
cors.allowed-methods=${CORS_ALLOWED_METHODS:GET,POST,PUT,DELETE,OPTIONS}
cors.allowed-headers=${CORS_ALLOWED_HEADERS:*}
cors.allow-credentials=${CORS_ALLOW_CREDENTIALS:false}
cors.max-age=${CORS_MAX_AGE:3600}

# Validation
bulk.validation.max-preview-rows=${MAX_PREVIEW_ROWS:200}
bulk.validation.max-upload-size-mb=${MAX_UPLOAD_SIZE_MB:50}
```

### Production CORS Configuration

For production, configure specific allowed origins:

```properties
# Production CORS settings
cors.allowed-origins=https://yourdomain.com,https://app.yourdomain.com
cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
cors.allowed-headers=Content-Type,Authorization,X-Requested-With,Accept,Origin
cors.allow-credentials=true
cors.max-age=3600
```

## Proof File Workflow

### 1. Upload Proof Files

Before or after uploading CSV files, upload proof files for disputes that will be rejected:

```bash
# Upload proof files (filename should be the dispute unique code)
curl -X POST http://localhost:8080/api/proofs/upload \
  -F "file=@2214B8JO003524000000003524.pdf"

curl -X POST http://localhost:8080/api/proofs/upload \
  -F "file=@2070EXNV012946000000012946.jpg"
```

### 2. Upload CSV with REJECT Actions

Upload CSV file with REJECT actions. The system will validate that proof files exist for all REJECT actions:

```bash
curl -X POST http://localhost:8080/api/sessions \
  -F "file=@disputes.csv" \
  -F "uploadedBy=admin"
```

### 3. Job Processing

When the job processes REJECT actions, it will:
1. Check for uploaded proof files
2. Update the `proof_of_reject_uri` field in `tbl_disputes` table
3. Set `resolved_by`, `status`, and `resolved` fields

### 4. Proof File Management

```bash
# Check if proof exists
curl http://localhost:8080/api/proofs/status/2214B8JO003524000000003524

# Download proof file
curl http://localhost:8080/api/proofs/download/2214B8JO003524000000003524

# Delete proof file
curl -X DELETE http://localhost:8080/api/proofs/delete/2214B8JO003524000000003524
```

## Database Schema

The application uses Flyway for database migrations. The schema includes:

- `bulk_dispute_session`: Session tracking with optimistic locking
- `bulk_dispute_job`: Job management and status tracking
- `bulk_dispute_job_audit`: Audit trail for job operations
- `tbl_disputes`: Main disputes table (existing table, updated by job processing)

### Key Fields in `tbl_disputes`:

- `unique_log_code`: Dispute unique identifier
- `status`: Dispute status (0=ACCEPT, 1=REJECT)
- `resolved`: Resolution status (0=unresolved, 1=resolved)
- `resolved_by`: User who resolved the dispute
- `proof_of_reject_uri`: Path to proof file for rejections
- `date_modified`: Last modification timestamp

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

## Recent Updates

### **Proof File Management Improvements**
- **Realistic Workflow**: Proof files are now uploaded separately via dedicated API endpoints
- **Clean CSV Format**: CSV files no longer require proof column references
- **Better Validation**: System checks actual file existence instead of CSV text references
- **Backward Compatible**: Existing CSVs with proof columns still work

### **Enhanced Validation**
- **Header Trimming**: Automatic trimming of CSV headers and data for better compatibility
- **Case-Insensitive**: Headers and actions are now case-insensitive
- **Structured Errors**: Detailed validation errors with row, column, and reason information
- **Flexible Format**: Supports various CSV formats and spacing

### **Improved User Experience**
- **Cleaner Interface**: Simplified CSV format focuses on decisions only
- **Real File Management**: Actual file uploads with proper metadata
- **Better Error Messages**: Clear, actionable validation error messages
- **Scalable Design**: Large proof files don't impact CSV processing

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

5. **Proof File Validation Fails**
   - Ensure proof files are uploaded via `/api/proofs/upload` endpoint
   - Check file naming matches dispute unique codes
   - Verify proof files exist in configured directory
   - Check file permissions and accessibility

6. **CSV Validation Issues**
   - Ensure headers are properly formatted (trimmed, case-insensitive)
   - Check for BOM (Byte Order Mark) in CSV files
   - Verify required columns: `Unique Key`, `Action`
   - For REJECT actions, ensure proof files are uploaded separately

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
