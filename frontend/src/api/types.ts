export type JobSource = 'GREENHOUSE' | 'LEVER' | 'ASHBY'
export type RemotePolicy = 'REMOTE' | 'HYBRID' | 'ONSITE' | 'UNSPECIFIED'
export type RequestStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface JobSummary { id:string; title:string; company:string; location:string|null; remotePolicy:RemotePolicy; source:JobSource; postedAt:string|null; firstSeenAt:string; applyUrl:string }
export interface Job extends JobSummary { description:string|null; employmentType:string|null; status:string }
export interface Page<T> { content:T[]; page:number; size:number; totalElements:number; totalPages:number }
export interface Problem { title?:string; detail?:string; status?:number }
export interface Search { id:string; name:string; query:string|null; company:string|null; source:JobSource|null; remotePolicy:RemotePolicy|null; location:string|null; enabled:boolean; createdAt:string }
export interface Alert { id:string; savedSearchId:string; createdAt:string; readAt:string|null; job:JobSummary }
export interface Target { id:string; source:JobSource; sourceAccount:string; company:string; careersUrl:string|null; enabled:boolean; intervalMinutes:number; nextRunAt:string; lastSuccessAt:string|null; lastError:string|null }
export interface Run { id:string; source:JobSource; sourceAccount:string; status:RequestStatus; startedAt:string; completedAt:string|null; discovered:number|null; created:number|null; updated:number|null; unchanged:number|null; closed:number|null; failureMessage:string|null }
export interface IngestionRequest { id:string; ingestionTargetId:string; status:RequestStatus; createdAt:string; startedAt:string|null; finishedAt:string|null; attemptCount:number; lastError:string|null }
export interface Discovery { id:string; companyName:string; careersUrl:string; status:string; lastCheckedAt:string|null; matchedUrl:string|null; lastError:string|null }
export interface Summary { enabledTargets:number; dueTargets:number; pendingIngestionRequests:number; runningIngestionRequests:number; failedRunsLast24h:number; unpublishedJobEvents:number; oldestUnpublishedEventAt:string|null }
