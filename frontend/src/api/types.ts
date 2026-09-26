export type JobSource = 'GREENHOUSE' | 'LEVER' | 'ASHBY'
export type RemotePolicy = 'REMOTE' | 'HYBRID' | 'ONSITE' | 'UNSPECIFIED'
export type RequestStatus = 'PENDING' | 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'PARTIALLY_FAILED' | 'CANCELLED'

export interface JobSummary { id:string; title:string; company:string; location:string|null; remotePolicy:RemotePolicy; source:JobSource; postedAt:string|null; firstSeenAt:string; applyUrl:string }
export interface Job extends JobSummary { description:string|null; employmentType:string|null; status:string }
export interface Page<T> { content:T[]; page:number; size:number; totalElements:number; totalPages:number }
export interface Problem { title?:string; detail?:string; status?:number }
export interface Search { id:string; name:string; query:string|null; company:string|null; source:JobSource|null; remotePolicy:RemotePolicy|null; location:string|null; enabled:boolean; createdAt:string }
export interface Alert { id:string; savedSearchId:string; createdAt:string; readAt:string|null; job:JobSummary }
export interface Target { id:string; source:JobSource; sourceAccount:string; company:string; careersUrl:string|null; enabled:boolean; intervalMinutes:number; nextRunAt:string; lastSuccessAt:string|null; lastError:string|null }
export interface Board { id:string; company:string; provider:JobSource; sourceAccount:string; enabled:boolean; pollingIntervalMinutes:number; lastSuccessfulRunAt:string|null; lastRunStatus:RequestStatus|null }
export interface Overview { totalBoards:number; activeBoards:number; totalPostings:number; openPostings:number; failedRunsLast24Hours:number; unpublishedEvents:number; deadLetterMessages:number; lastSuccessfulRunAt:string|null }
export interface Run { id:string; source:JobSource; sourceAccount:string; status:RequestStatus; startedAt:string; completedAt:string|null; discovered:number|null; created:number|null; updated:number|null; unchanged:number|null; closed:number|null; failureMessage:string|null; durationMs:number; throughputPerSecond:number; retryCount:number; correlationId:string }
export interface IngestionRequest { id:string; ingestionTargetId:string; status:RequestStatus; createdAt:string; publishedAt:string|null; startedAt:string|null; finishedAt:string|null; attemptCount:number; lastError:string|null; correlationId:string; retryOfRunId:string|null }
export interface Discovery { id:string; companyName:string; careersUrl:string; status:string; lastCheckedAt:string|null; matchedUrl:string|null; lastError:string|null; reviewedBy:string|null; reviewedAt:string|null }
export interface Summary { enabledTargets:number; disabledTargets:number; dueTargets:number; lastIngestionAt:string|null; pendingIngestionRequests:number; runningIngestionRequests:number; successfulRuns:number; failedRuns:number; failedRunsLast24h:number; postingsCreated:number; postingsUpdated:number; postingsUnchanged:number; postingsClosed:number; rabbitQueueDepth:number; deadLetterMessages:number; unpublishedJobEvents:number; kafkaPublishingFailures:number; oldestUnpublishedEventAt:string|null }
export interface DeadLetter { id:string; requestId:string; targetId:string; failedAt:string; errorMessage:string; payload:string; replayedAt:string|null; replayRequestId:string|null; replayedBy:string|null; replayReason:string|null }
export interface EventPayload { eventId:string; jobId:string; eventType:string; [key:string]:unknown }
export interface AdminEvent { id:string; jobId:string; type:string; schemaVersion:number; status:'PUBLISHED'|'PUBLISHING'|'UNPUBLISHED'|'FAILED'; createdAt:string; publishedAt:string|null; publishAttempts:number; lastPublishAttemptAt:string|null; lastPublishError:string|null; payload:EventPayload }
export interface AdminJobSummary { id:string; title:string; company:string; source:JobSource; sourceAccount:string; sourceJobId:string; status:string; lastSeenAt:string; lastIngestionRunId:string|null }
export interface AdminJob extends AdminJobSummary { location:string|null; description:string|null; employmentType:string|null; remotePolicy:string; applyUrl:string; postedAt:string|null; firstSeenAt:string; fingerprint:string; rawPayload:string|null; events:AdminEvent[] }
export interface AuditEntry { id:string; actor:string; actionType:string; targetType:string; targetId:string; occurredAt:string; beforeValue:string|null; afterValue:string|null; correlationId:string }
