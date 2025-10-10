# Concurrent Upload Test Scenario

## Test Case: Multiple Users Uploading Simultaneously

### Scenario:
- User A uploads `disputes_user_a.csv` (100 disputes)
- User B uploads `disputes_user_b.csv` (50 disputes) 
- User C uploads `disputes_user_c.csv` (200 disputes)

### Expected Behavior:

#### 1. Session Creation:
```
Session 1: User A, file: 1.csv, status: UPLOADED
Session 2: User B, file: 2.csv, status: UPLOADED  
Session 3: User C, file: 3.csv, status: UPLOADED
```

#### 2. Validation:
```
Session 1: VALIDATED (100 rows)
Session 2: VALIDATED (50 rows)
Session 3: VALIDATED (200 rows)
```

#### 3. Job Creation:
```
Job 1: sessionId=1, status=PENDING
Job 2: sessionId=2, status=PENDING
Job 3: sessionId=3, status=PENDING
```

#### 4. RabbitMQ Messages:
```
Queue: bulk.jobs
- Message 1: {jobId: 1, sessionId: 1, filePath: "uploads/1.csv", uploadedBy: "user_a"}
- Message 2: {jobId: 2, sessionId: 2, filePath: "uploads/2.csv", uploadedBy: "user_b"}  
- Message 3: {jobId: 3, sessionId: 3, filePath: "uploads/3.csv", uploadedBy: "user_c"}
```

#### 5. Parallel Processing:
```
Worker 1: Processing Job 1 (User A's disputes)
Worker 2: Processing Job 2 (User B's disputes)
Worker 3: Processing Job 3 (User C's disputes)
```

### Potential Issues:

#### 1. Database Concurrency:
- **Optimistic Locking**: Each session has a `version` field
- **Dispute Updates**: Multiple workers updating `tbl_disputes` simultaneously
- **Race Conditions**: Same dispute processed by different jobs?

#### 2. File System:
- **File Conflicts**: Different sessions use different file names
- **Disk I/O**: Multiple large CSV files being read simultaneously

#### 3. Resource Contention:
- **Database Connections**: Multiple workers hitting database
- **Memory Usage**: Large CSV files in memory
- **CPU Usage**: CSV parsing and validation

### Mitigation Strategies:

#### 1. Database Level:
- **Row-level locking** in dispute updates
- **Transaction isolation** for job processing
- **Unique constraints** to prevent duplicate processing

#### 2. Application Level:
- **Connection pooling** for database access
- **File streaming** instead of loading entire CSV into memory
- **Rate limiting** for upload endpoints

#### 3. Infrastructure Level:
- **Load balancing** for multiple application instances
- **Database replication** for read/write separation
- **Message queue clustering** for high availability
