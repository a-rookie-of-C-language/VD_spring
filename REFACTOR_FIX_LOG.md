# Refactor and Fix Log

This document records each refactor/fix batch with the reason and implementation approach.

## 2026-05-30 Batch 1: Migration, Auth, Secret, and Frontend Baseline

### Why

- Flyway was enabled in configuration but missing from Maven dependencies, so database migrations would not run.
- `V1__init.sql` inserted columns that were not created yet and hard-coded a schema name, making empty-database startup unreliable.
- Activity update allowed any authenticated user to update an activity and overwrite its `functionary`.
- Login cached successful responses using `studentNo|password`, retaining plain credentials in memory and risking stale tokens after logout.
- Development config and README contained real credentials/secrets, and `token.txt` contained a JWT.
- Frontend typecheck failed because Vitest globals were not declared, and the API base URL was hard-coded to localhost.

### How

- Added `flyway-core` and `flyway-mysql` to `pom.xml`.
- Aligned `V1__init.sql` with mapper/entity fields and removed hard-coded `vd.` prefixes.
- Added owner/admin check before activity update and preserved the existing `functionary`.
- Removed login response cache and generated tokens directly after credential verification.
- Moved database, RabbitMQ, mail, and JWT values to environment placeholders and deleted/ignored `token.txt`.
- Added Vitest global types in the frontend TypeScript config and restored environment-aware API base URL selection.

## 2026-05-30 Batch 2: File Path Hardening and Error Semantics

### Why

- File download, preview, info, and delete paths were resolved in multiple places and some paths could resolve outside the upload root.
- Raw `/attachments/**` static access exposed uploaded attachments without authentication.
- Missing static resources were falling through to a generic 500 response.
- Some invalid request parameters, empty login credentials, and file upload validation errors returned weak or misleading behavior.

### How

- Centralized path normalization and upload-root boundary checks in file service/controller logic.
- Removed anonymous access and static resource mapping for `/attachments/**`; attachment access now goes through controlled endpoints.
- Added `NoResourceFoundException`, invalid date, and invalid parameter handling in the global exception handler.
- Added explicit empty-login validation and corrected personal-hour attachment deletion to call `deleteAttachment`.
- Added tests for attachment visibility, activity update authorization, invalid query dates, empty login, path traversal, and filename sanitization.

## 2026-05-30 Batch 3: Pending Activity Status Consistency

### Why

- The pending activity migration defaulted `status` to `PENDING`, but Java uses the `ActivityStatus` enum which has no `PENDING` value.
- Reading rows with `PENDING` would break enum mapping.

### How

- Changed the migration default/comment to `UnderReview`.
- Set `PendingActivity.status` explicitly when creating pending activity imports.

## 2026-05-30 Batch 4: Attachment Controller Template Method

### Why

- Download and preview endpoints had two nearly identical implementations.
- Duplicated file validation, cache header, content type, and response-building logic made future security fixes easy to apply to one endpoint but miss the other.

### How

- Replaced duplicated endpoint bodies with a single private `serveFile(relativePath, request, inline)` method.
- Kept endpoint behavior explicit: `/download` passes `inline=false`, `/preview` passes `inline=true`.
- Preserved the same path validation, conditional caching, content type resolution, and content-disposition behavior.

## 2026-05-30 Batch 5: Activity DTO Mapping Deduplication

### Why

- `ActivityDTO` had two entity-conversion methods with almost identical builder logic.
- Any future field added to one conversion path could easily be missed in the other.

### How

- Made `toEntity(zone)` delegate to `toEntity(this.id, zone)`.
- Kept the existing public API while leaving one canonical mapping implementation.

## 2026-05-30 Batch 6: Attachment Endpoint Error Handling Simplification

### Why

- Activity attachment endpoints caught broad `Exception` values and rethrew generic `RuntimeException`.
- Delete/info endpoints were wrapping service methods that already return stable boolean/null outcomes.
- Broad catches made expected not-found and validation behavior harder to reason about.

### How

- Narrowed upload error handling to validation failures and `IOException`.
- Removed unnecessary try/catch blocks from delete/info endpoints.
- Kept not-found responses as `BusinessException.notFound(...)` so global error handling stays consistent.

## 2026-05-30 Batch 7: Token Response Builder Extraction

### Why

- Login and refresh flows both assembled JWT claims and token response maps by hand.
- Duplicated token response construction risks field drift between initial login and token refresh.

### How

- Extracted `buildTokenResponse(UserDTO)` and `buildTokenResponse(UserDTO, int tokenVersion)` in `UserController`.
- Kept login responses enriched with user profile fields while refresh responses return only refreshed token fields.
- Centralized access/refresh token generation and JWT claims in one place.

## 2026-05-30 Batch 8: Pagination Boundary Normalization

### Why

- Several controllers and services calculated `page`, `pageSize`, and `offset` independently.
- Some paths accepted negative or very large `pageSize` values, which could reach mapper `LIMIT/OFFSET` calls or overflow offset math.
- A generated placeholder comment remained in `PendingActivityParticipantRepository`, adding noise to a mapper interface.

### How

- Added `PaginationUtils` with shared defaults, page/pageSize normalization, maximum page-size caps, and overflow-safe offset calculation.
- Replaced duplicated pagination bounds in activity, pending activity, suggestion, personal hour request, monitoring, batch import, and my-activity flows.
- Kept the activity cursor lookahead behavior by allowing one extra row above the normal page-size cap for cursor queries.
- Added unit coverage for pagination defaults, lower bounds, maximum caps, and offset overflow.
- Removed the stale placeholder comment from `PendingActivityParticipantRepository`.

## 2026-05-30 Batch 9: Monitoring Sort and Identity URL Bounds

### Why

- Monitoring user-stat sorting accepted only a small set of mapper-supported fields, but invalid sort directions on a valid field were interpreted as ascending.
- The HTTP campus identity adapter inserted `studentNo` directly into the configured URL template, so slash, query, or space characters could change the requested path instead of being treated as data.

### How

- Normalized monitoring sort fields and sort direction in `MonitoringService`; only `asc` is accepted as ascending, and every other direction defaults to `desc`.
- URL-encoded `studentNo` before replacing `{studentNo}` in the identity HTTP path template.
- Added unit tests for monitoring paging/sort normalization and identity endpoint encoding.

## 2026-05-30 Batch 10: Batch Import Activity Name Parsing

### Why

- Batch import activity-name splitting used a mojibake-damaged regex string with an unclosed character class.
- Importing a row with multiple activity names could throw at runtime instead of producing pending import records.
- Activity names with hour suffixes needed one canonical normalization rule that supports both ASCII and Chinese punctuation.

### How

- Replaced ad hoc `String.split(...)` usage with a compiled `ACTIVITY_SEPARATOR_PATTERN` for comma, semicolon, newline, Chinese comma, enumeration comma, and Chinese semicolon.
- Replaced the damaged hour-suffix matcher with `NORMALIZED_HOUR_SUFFIX_PATTERN`, supporting ASCII parentheses/brackets, Chinese full-width brackets, and hour suffixes such as `hours`, `h`, and Chinese "hours" text.
- Removed the unused damaged parser method and added unit tests for split-and-normalize behavior during `submitForReview`.

## 2026-05-30 Batch 11: Batch Import Invalid Row Preservation

### Why

- `submitForReview` detected an empty activity name but then iterated over an empty activity list.
- That meant no invalid pending record was stored, and `invalidRecords` did not reflect the bad row.
- Reviewers and users could lose visibility into why an uploaded row was rejected.

### How

- When a row has no split activity names, the service now keeps one placeholder activity entry so the normal pending-record creation path still runs.
- The generated record is marked `INVALID` with the existing row-level validation error.
- Added a unit test that verifies empty activity names produce one invalid pending record and update result counts.

## 2026-05-30 Batch 12: Null-Safe Authorization Guards

### Why

- `AuthorizationGuards.isAdmin` assumed every caller passed a non-null principal with a non-null role.
- If an endpoint configuration changes or a controller is called directly in tests, missing authentication could produce a `NullPointerException` instead of a controlled forbidden response.

### How

- Made `isAdmin` return `false` for null principal or null role.
- Made admin role checks case-insensitive so `ADMIN`/`SUPERADMIN` authorities and enum-style role strings are handled consistently.
- Made `requireSelfOrAdmin` reject a null principal through the existing `BusinessException.forbidden(...)` path.
- Added unit tests covering null principal/role behavior.

## 2026-05-30 Batch 13: Cover Image URL Consistency

### Why

- `FileUploadService.getCoverImageUrl` returned `/files/covers/...`, while the frontend utility wraps cover paths as `/files/preview?path=...`.
- Passing `/files/covers/...` into the frontend wrapper made the preview endpoint look for `uploads/files/covers/...`, so uploaded covers could fail to load.
- The backend should expose the stored upload-relative path and let the frontend choose the preview/download endpoint.

### How

- Changed `getCoverImageUrl` to return the normalized upload-relative path, for example `/covers/demo.png`.
- Kept the existing upload-root boundary check before returning the path.
- Added a service test covering both `/covers/demo.png` and `covers/demo.png` inputs.

## 2026-05-30 Batch 14: Excel Import Missing Student Number Preservation

### Why

- `ExcelParserService.parseBatchImportRecords` skipped every row with an empty student number.
- The service layer already has validation for missing student numbers, but skipped rows never reached that validation path.
- Users and reviewers could lose visibility into malformed rows that had a name/activity but no student number.

### How

- Changed the parser to skip only rows where all relevant cells are blank.
- Preserved non-blank rows even when `studentNo` is missing, leaving it as `null` for service-layer validation.
- Added an Excel parser unit test that builds a workbook with a missing-student-number row and verifies it is retained.

## 2026-05-30 Batch 15: File Upload Storage Template

### Why

- Cover and attachment uploads each created directories, copied streams, logged success, and built relative paths by hand.
- Cover and attachment deletion also duplicated path resolution and deletion logic.
- Upload subdirectory configuration was not explicitly checked against the upload root, so a bad config value could write files outside the intended root.

### How

- Added `storeFile(...)` as a shared template for directory resolution, stream copy, logging, and relative path construction.
- Added `deleteFile(...)` as a shared deletion helper used by cover and attachment deletion.
- Added `resolveUploadDirectory(...)` to create upload directories only after confirming they normalize inside the configured upload root.
- Added a unit test that rejects an upload directory configured outside the root.

## 2026-05-30 Batch 16: Attachment ETag Hash Modernization

### Why

- Attachment ETags were generated with MD5.
- The value is not used as a password hash, but keeping weak hashes in shared utility code is unnecessary and can trigger security-review noise.
- The controller also had unused date/time imports left behind after earlier response-header refactoring.

### How

- Switched ETag digest generation from MD5 to SHA-256 while preserving quoted HTTP ETag formatting.
- Removed unused date/time imports and the unused formatter constant.
- Added a controller unit test that verifies generated ETags are quoted 64-character SHA-256 hex strings.

## 2026-05-30 Batch 17: Stored Attachment Filename Length Bound

### Why

- Attachment uploads preserve a sanitized slice of the original filename in the stored filename.
- Very long client filenames could produce filesystem-path errors even when the file type and size are valid.
- Filename sanitization should bound both character safety and length.

### How

- Added `MAX_STORED_FILENAME_BASE_LENGTH` and capped the preserved sanitized base name to 80 characters.
- Kept the UUID prefix and original extension behavior unchanged.
- Added a unit test that uploads a long filename and verifies the stored preserved base is bounded and the file is written.

## 2026-05-30 Batch 18: Activity Update Review-State Preservation

### Why

- `ActivityService.updateActivity` converted the incoming DTO directly to an entity.
- Multipart edit requests usually do not include `status`, so the mapper could write `NULL` to `activities.status`.
- Editing a failed-review activity should resubmit it for review and clear stale review metadata instead of leaving the old rejection reason attached.

### How

- Set updated activities to `UnderReview` before mapping to the persistence entity.
- Cleared `rejectedReason`, `reviewedAt`, and `reviewedBy` on edit.
- Updated the activity mapper to persist `rejected_reason` during full activity updates.
- Added a service unit test that verifies a failed-review activity is resubmitted and stale review fields are cleared.

## 2026-05-30 Batch 19: Review-Gated Activity Status Scheduling

### Why

- `createActivity` and `updateActivity` scheduled future status tasks while the activity was still `UnderReview`.
- The status listener intentionally skips protected review states, then marks the task done, so a pre-approval task could be consumed and lost before the activity is approved.
- Status automation should start only after an activity passes review.

### How

- Removed status-task scheduling from create and update paths.
- Kept scheduling in the existing approval path after the activity leaves `UnderReview`.
- Added a service test proving activity creation with future times does not schedule status tasks before review approval.

## 2026-05-30 Batch 20: Startup Status Sync Null-Time Guard

### Why

- `ActivityStartupSynchronizer.changeStatus` assumed all scheduling timestamps were present.
- One legacy or malformed activity with missing time fields could throw a `NullPointerException` during startup synchronization.
- A single bad row should not prevent other activities from being synchronized.

### How

- Added a null-time guard before calculating status transitions.
- Missing required timestamps now leave the activity status unchanged.
- Added a unit test covering the missing-time case.

## 2026-05-30 Batch 21: Activity Status Message Null-State Guard

### Why

- `ActivityStatusListener.handle` assumed both the database current status and message target status were non-null.
- A malformed message or legacy row with a null status could throw during handling and create unnecessary retry/dead-letter churn.
- Missing state data is not a valid transition and should be skipped without mutating the activity.

### How

- Added an explicit guard for null current or target status before transition checks.
- Logged the skipped update with current/target status context.
- Added listener unit tests covering null current status and null target status.

## 2026-05-30 Batch 22: Startup Status Sync Query Simplification

### Why

- Startup status synchronization only needs base activity status/time fields.
- It used `activityMapper.listAll()`, whose result map loads attachments and participants for every activity.
- That added unnecessary startup work and risked N+1 collection queries during synchronization.

### How

- Switched startup synchronization to `activityMapper.listAllBase()`.
- Kept the existing status calculation and scheduling behavior unchanged.
- Added a unit test proving startup synchronization uses the base query.

## 2026-05-30 Batch 23: Token Response User Data Validation

### Why

- `UserController.buildTokenResponse` assumed the loaded user always had a non-empty student number and role.
- A malformed user row could cause a `NullPointerException` while generating JWT claims.
- Token generation should fail with a controlled error before calling JWT utilities when required identity data is missing.

### How

- Added `validateTokenUser(UserDTO)` and reused it from both login and refresh token response builders.
- The validation rejects null user, blank student number, or missing role with `USER_TOKEN_DATA_INVALID`.
- Added a controller unit test proving a missing role is rejected before token generation.

## 2026-05-30 Batch 24: Personal Hour Review Reason Guard

### Why

- The controller required a rejection reason for personal-hour requests, but the service method itself did not.
- Direct service callers or future endpoints could reject a request with a blank reason.
- Review invariants should live in the service layer as well as at the HTTP boundary.

### How

- Added a service-layer `REASON_REQUIRED` check before rejecting a personal-hour request.
- Kept approval behavior unchanged.
- Added a unit test proving blank rejection reasons do not reach the status update mapper.

## 2026-05-30 Batch 25: Activity Participant Normalization

### Why

- Updating activity participants deleted all existing participants and inserted only the submitted list.
- If the submitted list omitted the functionary, the functionary could be removed from the participant table even though other logic treats the functionary as a protected participant.
- Null, blank, or duplicate participant values could also reach mapper calls.

### How

- Added a participant normalization helper that trims values, removes blanks/nulls, deduplicates while preserving order, and includes the functionary first.
- Used the helper in both create and update participant paths.
- Added a service unit test proving participant replacement keeps the functionary and filters invalid/duplicate values.

## 2026-05-30 Batch 26: Activity Status Task Invalid Target Guard

### Why

- `ActivityStatusTaskService.dispatch` parsed persisted `target_status` with `ActivityStatus.valueOf` inside the publish retry path.
- A malformed database value could be treated like a transient RabbitMQ publish failure and repeatedly retried.
- Invalid task data cannot become publishable through retry and should be marked as a clear terminal task failure.

### How

- Added an explicit target-status parser before message construction.
- Marked tasks with null, blank, or unknown target status as `DEAD` with a clear error message and skipped RabbitMQ publishing.
- Added a service unit test proving invalid target status is dead-lettered without calling `RabbitTemplate.send`.

## 2026-05-30 Batch 27: Pending Activity Principal Guard

### Why

- Several `PendingActivityController` paths read `principal.getStudentNo()` after only checking admin status.
- If the controller is called without a principal, those paths can throw `NullPointerException` and surface as a 500 instead of a controlled authorization error.
- The authenticated student-number invariant was repeated at call sites instead of centralized.

### How

- Added `AuthorizationGuards.requireStudentNo` to validate that a principal and non-blank student number are present.
- Reused the guard in pending-activity query, listing, batch import, review, detail, and delete paths before passing student numbers to services.
- Made `requireSelfOrAdmin` use the same null-safe student-number guard.
- Added unit tests for missing identity rejection and missing-principal pending-activity query behavior.

## 2026-05-30 Batch 28: Volunteer Hour Grant Input Normalization

### Why

- `VolunteerHourGrantService` passed raw participant values into hour-grant logic.
- Duplicate or whitespace-padded student numbers could cause repeated grant attempts, noisy duplicate-key handling, and inconsistent mapper calls.
- Missing activities and personal-hour requests threw generic `IllegalArgumentException("NOT_FOUND")` instead of the project's structured business exception.

### How

- Added shared student-number normalization for single-user grants.
- Added ordered participant normalization that trims values, drops blanks, and deduplicates before batch grants.
- Replaced source-record `NOT_FOUND` paths with `BusinessException.notFound("NOT_FOUND")`.
- Added service tests covering participant normalization and structured not-found failures.

## 2026-05-30 Batch 29: Activity Controller Principal Guard

### Why

- Several `ActivityController` write and personal endpoints directly dereferenced `principal.getStudentNo()`.
- Direct controller calls or unexpected authentication gaps could surface as `NullPointerException`/500 instead of a controlled authorization failure.
- The review endpoint relied only on web security matchers for admin enforcement, leaving direct controller calls weaker than the HTTP boundary.

### How

- Reused `AuthorizationGuards.requireStudentNo` in create, enroll, unenroll, review, my-activities, import, and my-status paths.
- Kept public activity query behavior null-safe without forcing authentication logic into the query branch.
- Added controller-level `requireAdmin` before activity review service calls.
- Added controller unit tests proving missing principals and non-admin review attempts are rejected before service calls.

## 2026-05-30 Batch 30: Personal Hour Request Principal Guard

### Why

- `PersonalHourRequestController` still dereferenced `principal.getStudentNo()` in submit, review, my-list, and delete paths.
- Missing or malformed authentication data could become `NullPointerException`/500, or pass a blank reviewer/applicant student number into the service.
- The controller already used `AuthorizationGuards` for admin/self checks, so student-number validation should be centralized there too.

### How

- Reused `AuthorizationGuards.requireStudentNo` before submitting requests, reviewing requests, listing the current user's requests, and deleting requests.
- Preserved existing admin and self-or-admin checks for pending lists and request detail access.
- Added direct controller unit tests proving missing principals and blank admin student numbers are rejected before service calls.

## 2026-05-30 Batch 31: Suggestion Controller Principal and Body Guards

### Why

- `SuggestionController` directly read `principal.getStudentNo()` in create and my-list endpoints.
- Missing authentication data could cause `NullPointerException`/500 or pass an invalid student number into suggestion queries.
- Null JSON request bodies for create/reply paths could also throw before the controller returned a structured bad-request response.

### How

- Reused `AuthorizationGuards.requireStudentNo` before creating suggestions and listing the current user's suggestions.
- Added explicit `INVALID_REQUEST_BODY` checks for create and reply request bodies.
- Added direct controller unit tests proving missing principals and null request bodies are rejected before service calls.

## 2026-05-30 Batch 32: User Controller Principal Guard

### Why

- `UserController.getUser` directly dereferenced `principal.getStudentNo()`.
- `logout` only checked for a null principal and still allowed a blank student number to reach token-version mutation.
- User identity validation should be consistent with the other authenticated controllers.

### How

- Reused `AuthorizationGuards.requireStudentNo` before loading the current user and before incrementing token version on logout.
- Removed the local null-only logout check in favor of the shared identity guard.
- Added controller unit tests proving missing principals and blank student numbers are rejected before service calls.

## 2026-05-30 Batch 33: Centralized Superadmin Guard

### Why

- `MonitoringController` carried its own superadmin check and duplicated role/authority inspection logic.
- Authorization rules were split between controller-local code and `AuthorizationGuards`, making future privileged endpoints easier to implement inconsistently.
- MockMvc tests rely on Spring Security authorities while direct controller calls can pass `UserPrincipal`, so the shared guard needs to support both sources.

### How

- Added `AuthorizationGuards.isSuperAdmin` and `requireSuperAdmin(UserPrincipal, Authentication)`.
- Kept the Spring Security `ROLE_SUPERADMIN` authority fallback in the shared guard for web-test and framework compatibility.
- Replaced the controller-local superadmin logic in `MonitoringController` with the shared guard.
- Added guard unit tests for principal-role success, authority fallback success, and non-superadmin rejection.

## 2026-05-30 Batch 34: Monitoring Replay Limit Normalization

### Why

- `MonitoringController.replayDeadTasks` accepted the raw `limit` request parameter and echoed it back in the response.
- `ActivityStatusTaskService` already bounds replay size internally, so the controller response could report a negative or huge limit that was not actually used.
- Request boundary normalization should happen before service calls so behavior and response metadata stay consistent.

### How

- Added controller-level replay limit constants for default, minimum, and maximum values.
- Normalized the replay limit to `1..500` before calling `ActivityStatusTaskService.replayDeadTasks`.
- Reused the normalized value in the response payload.
- Added a direct controller unit test proving a negative limit is clamped before the service call and response.

## 2026-05-30 Batch 35: My Activity Enum String Normalization

### Why

- `MyActivityService` mapped database row values to enums with `Enum.valueOf(val.toString())`.
- If a row value contained surrounding whitespace or case drift, the activity status/type silently became `null` in "my activities" responses.
- Database-facing mapping should tolerate small string-format differences while still ignoring truly unknown values.

### How

- Trimmed enum source strings before mapping.
- Kept exact enum matching first, then added a case-insensitive enum-name fallback.
- Preserved the previous behavior of returning `null` for blank or unknown values.
- Added a service unit test proving whitespace and lowercase status/type values still map to the expected enums.

## 2026-05-30 Batch 36: Excel Formula Cell Evaluation

### Why

- `ExcelParserService` returned formula text for formula cells, for example `1+1` instead of the calculated value `2`.
- Batch import rows using formula-based student numbers or durations could be parsed incorrectly or lose duration values.
- Excel parsing should consume the visible/calculated cell value rather than the formula expression.

### How

- Created a `FormulaEvaluator` per workbook and passed it into cell conversion.
- Evaluated formula cells and converted their calculated string, numeric, boolean, or blank result into the existing string format.
- Kept non-formula cell conversion behavior unchanged.
- Added a parser unit test covering formula-based student number and duration cells.

## 2026-05-30 Batch 37: Monitoring Log Size Normalization

### Why

- `MonitoringController` accepted nullable `Integer size` parameters for system and business logs.
- Direct controller calls with `null` could trigger primitive unboxing failures before reaching the service.
- Oversized log requests were bounded inside services, but the controller boundary still passed inconsistent raw values.

### How

- Added shared controller-level log size normalization with default `50` and range `1..200`.
- Reused the normalized size for both `/logs` and `/business-logs`.
- Added direct controller unit tests covering null system-log size and oversized business-log size.

## 2026-05-30 Batch 38: Suggestion Creation Service Invariants

### Why

- `SuggestionController` validated suggestion title, content, and authenticated student number, but `SuggestionService.createSuggestion` accepted raw values.
- Direct service callers or future endpoints could insert blank titles, blank content, or blank student numbers.
- Creation invariants belong in the service layer as well as the HTTP boundary.

### How

- Added shared text validation in `SuggestionService` for title, content, and student number.
- Trimmed accepted values before building and inserting the suggestion entity.
- Added service unit tests proving blank fields are rejected before mapper insertion and accepted values are normalized.

## 2026-05-30 Batch 39: Suggestion Reply Service Invariant

### Why

- `SuggestionController` validated reply content, but `SuggestionService.replySuggestion` still accepted raw reply text.
- Direct service callers or future endpoints could mark a suggestion replied with blank content.
- Reply invariants should live in the service layer as well as the HTTP boundary.

### How

- Reused the existing service text validator for reply content with `REPLY_CONTENT_REQUIRED`.
- Trimmed accepted reply content before updating the mapper.
- Added service unit tests proving blank replies do not update the mapper and valid replies are normalized before persistence.

## 2026-05-30 Batch 40: Pending Activity Participant Normalization

### Why

- `PendingActivityService.importActivity` only removed exact duplicate participant strings.
- Whitespace-padded student numbers were validated and persisted as-is, and blank participant entries could fail with misleading "user not found" errors.
- Pending import participant handling should match the normalized behavior used by other activity flows.

### How

- Added a small service helper that filters null/blank entries, trims student numbers, and preserves first-seen order while deduplicating.
- Reused the normalized participant list for validation, persistence, and hour-grant calls.
- Added a service unit test proving validation and insertion receive only normalized participant student numbers.

## 2026-05-30 Batch 41: Activity Cover Upload Helper

### Why

- `ActivityService.createActivity` and `updateActivity` duplicated the same cover upload and exception wrapping logic.
- Both paths caught broad `Exception`, which made expected upload failures and unexpected programming errors indistinguishable.
- Cover upload behavior should be shared while preserving the existing bad-request style failure for validation and IO errors.

### How

- Extracted a private `uploadCoverIfPresent` helper used by both create and update flows.
- Narrowed exception wrapping to `IOException` and `IllegalArgumentException`, the expected failures from `FileUploadService.uploadCoverImage`.
- Added service unit tests proving create/update stop before database writes when cover upload fails.

## 2026-05-30 Batch 42: Pending Import Cover Upload Boundary

### Why

- `PendingActivityService.importActivity` also caught broad `Exception` around cover upload.
- Expected upload failures should be handled explicitly, while unrelated runtime failures should not be hidden as upload validation errors.
- The service needed regression coverage proving failed cover upload does not create pending or direct-import activities.

### How

- Extracted `uploadCoverIfPresent` for pending import cover handling.
- Narrowed upload error wrapping to `IOException` and `IllegalArgumentException`, matching `FileUploadService.uploadCoverImage`.
- Added a service unit test proving cover upload failure stops before both pending and activity mapper inserts.

## 2026-05-30 Batch 43: Business Log Query JSON Builder

### Why

- `BusinessOperationLogService.queryRecent` hand-built Elasticsearch JSON with `String.format`.
- Keyword escaping was maintained manually, which made quote, slash, and control-character handling easy to break.
- The service already owns an `ObjectMapper`, so query JSON should be generated from structured nodes instead of string concatenation.

### How

- Replaced manual query JSON formatting with Jackson `ObjectNode`/`ArrayNode` construction.
- Centralized the keyword-search fields in a constant list and removed the custom `escapeJson` helper.
- Added a service unit test capturing the ES query body to prove size clamping, keyword trimming/escaping, and response mapping.

## 2026-05-30 Batch 44: Business Log Buffer Capacity Guard

### Why

- `BusinessOperationLogService.buffer` used the raw `fallbackBufferSize` configuration in its eviction loop.
- If that value was configured as `0` or a negative number, the loop could keep polling an empty queue and never return.
- Logging must not be able to stall a business request because of a bad buffer-size configuration.

### How

- Normalized the effective fallback buffer capacity to at least `1` before evicting old entries.
- Added a null-log guard so invalid log events are ignored instead of failing the business path.
- Added a service unit test proving a `0` configured buffer size still accepts and flushes one business log.

## 2026-05-30 Batch 45: System Log Query JSON Builder

### Why

- `MonitoringService.getRecentLogs` hand-built Elasticsearch query JSON with `String.format`.
- The system-log path duplicated the same manual escaping risk that was removed from business-log querying.
- Keyword input was not trimmed before building the ES query, so surrounding whitespace and control characters could change search behavior.

### How

- Replaced manual JSON formatting with Jackson node construction via `JsonNodeFactory`, `ObjectNode`, and `ArrayNode`.
- Centralized the system-log keyword search fields in a constant list.
- Trimmed nonblank keywords before query construction and added a service unit test proving size clamping, escaping, trimming, and response mapping.

## 2026-05-30 Batch 46: Personal Hour Attachment Upload Helper

### Why

- `PersonalHourRequestService.submitRequest` mixed attachment upload iteration, empty-file filtering, error wrapping, and entity construction in one method.
- The single-line `uploadAttachment` helper added indirection without reducing complexity.
- Attachment upload failure needed explicit service coverage proving the request is not inserted after a failed file write.

### How

- Extracted `collectAttachmentPaths` to handle null/empty files, upload successful files, and wrap `IOException` with the original cause.
- Removed the pass-through `uploadAttachment` helper.
- Added a service unit test proving attachment upload failure stops before request and attachment mapper inserts.

## 2026-05-30 Batch 47: Business Log ES Bootstrap JSON Builder

### Why

- `BusinessOperationLogService` still built Elasticsearch ILM policy and index template bodies with long `String.format` JSON literals.
- These strings were hard to review and easy to break when changing mappings or settings.
- The service already uses Jackson for ES query JSON, so bootstrap JSON should use the same structured construction style.

### How

- Replaced ILM policy and index template JSON literals with `ObjectNode`/`ArrayNode` builders.
- Added a small mapping-property helper to reduce repeated field mapping boilerplate.
- Added a service unit test capturing ES `putJson` bodies to prove retention, shard/replica normalization, index pattern, and key mappings.

## 2026-05-30 Batch 48: Campus Identity JSON Parse Boundary

### Why

- `CampusIdentityServiceImpl.parseExists` caught broad `Exception` while only parsing JSON.
- That could hide programming errors unrelated to malformed identity-provider responses.
- The supported identity response shapes were not directly covered by tests.

### How

- Narrowed the parse failure catch to Jackson `JsonProcessingException`.
- Added service tests for boolean, top-level `exists`, nested `data.exists`, and invalid JSON responses.
- Preserved the existing behavior of returning `false` for malformed provider payloads.

## 2026-05-30 Batch 49: Business Log Serialization Failure Boundary

### Why

- `BusinessOperationLogService.writeDirect` caught broad `Exception` while only local serialization was expected to throw.
- Elasticsearch delivery failures are already represented by `ElasticsearchTemplate.index` returning `false`.
- Catching every exception could hide unrelated programming errors in the logging pipeline.

### How

- Narrowed the write failure catch to Jackson `JsonProcessingException`.
- Added a service unit test proving serialization failure keeps the log buffered and a later flush can retry successfully.
- Preserved the nonblocking logging behavior: failed serialization returns `false` to the flush loop instead of disrupting business requests.

## 2026-05-30 Batch 50: Pending Import Excel Parse Cause Preservation

### Why

- `PendingActivityService.importActivity` wrapped Excel parse failures in `IllegalArgumentException` without preserving the original `IOException`.
- Losing the cause made upload/parse failures harder to diagnose from logs, tests, or API error tracing.
- The failure path needed coverage proving no pending/direct activity is inserted after parse failure.

### How

- Preserved the original `IOException` as the cause when wrapping Excel parse failures.
- Added a service unit test proving the cause is retained and mapper inserts are not called after a parse failure.
- Kept the existing user-facing exception message unchanged.

## 2026-05-30 Batch 51: JWT Parse Exception Boundary

### Why

- `JWTUtils.parseTokenSafe` classified known JWT failures, but its final fallback caught every `Exception`.
- That could hide non-JWT programming/configuration errors behind a generic invalid-token result.
- Token parsing behavior needed focused tests outside controller mocks.

### How

- Narrowed the final fallback to JJWT's `JwtException` hierarchy after the specific expired/malformed/signature cases.
- Added utility tests for empty, malformed, bad-signature, and valid access-token parsing.
- Preserved the public `TokenParseResult.invalid()` behavior for unsupported or otherwise invalid JWTs.

## 2026-05-30 Batch 52: Upload Relative Path Invalid-Character Guard

### Why

- `FileUploadService.normalizeRelativePath` called `Paths.get(path)` directly.
- A user-supplied path containing invalid platform characters could throw `InvalidPathException` before callers returned their normal null/false invalid-path result.
- File preview, file info, and delete paths should treat malformed relative paths consistently as invalid input.

### How

- Caught `InvalidPathException` inside relative-path normalization and returned `null`.
- Added a file-upload service test proving invalid relative paths do not throw from cover URL, file info, or delete flows.
- Preserved existing directory traversal and absolute-path rejection behavior.

## 2026-05-30 Batch 53: Upload File Target Resolver

### Why

- `FileUploadService` resolved any existing path under the upload root, including directories.
- `getCoverImageUrl("/covers")` could therefore expose a directory path as if it were a valid cover file.
- File metadata used `File.length()`, which can silently return `0` when size lookup fails.

### How

- Replaced the existing-path helper with `resolveExistingFilePath`, which only accepts regular files inside the upload root.
- Removed duplicate `Files.exists` checks from cover reading and file-info lookup.
- Switched file metadata size lookup to `Files.size` and narrowed local catches to `IOException`.
- Added a service regression test proving directories are rejected by cover URL, data URL, file info, and delete flows.

## 2026-05-30 Batch 54: Campus Identity HTTP Interrupt Handling

### Why

- `CampusIdentityServiceImpl.existsByHttp` caught broad `Exception` around the HTTP identity-provider call.
- If `HttpClient.send` was interrupted, the service returned `false` but lost the thread interrupted flag.
- Swallowing interruption makes shutdown, cancellation, and request timeout handling less reliable.

### How

- Split `InterruptedException` into its own catch block and restored the interrupted flag with `Thread.currentThread().interrupt()`.
- Narrowed the remaining HTTP boundary catch to `IOException | IllegalArgumentException`.
- Added a service regression test using a mocked `HttpClient` to prove interrupted identity checks return `false` and preserve the interrupted status.

## 2026-05-31 Batch 55: Elasticsearch Index Interrupt Handling

### Why

- `ElasticsearchTemplate.index` caught broad `Exception` around `HttpClient.send`.
- If Elasticsearch indexing was interrupted, the method returned `false` but cleared the thread interrupted flag.
- Losing the interrupted status makes request cancellation and application shutdown less reliable.

### How

- Split `InterruptedException` into its own catch block and restored the interrupted flag before returning `false`.
- Narrowed the remaining non-success catch to `IOException | IllegalArgumentException`.
- Added a focused template unit test with a mocked `HttpClient` proving interrupted index writes preserve `Thread.currentThread().isInterrupted()`.

## 2026-05-31 Batch 56: Elasticsearch Query Exception Boundary

### Why

- `ElasticsearchTemplate.postJson` still caught broad `Exception` after the explicit interrupt path.
- The method only needs to tolerate HTTP I/O failures, malformed Elasticsearch JSON, and bad endpoint arguments.
- Catching every runtime failure could hide programming errors in query construction or response handling.

### How

- Narrowed the query fallback catch to `IOException | IllegalArgumentException`.
- Kept the existing behavior of returning `null` for malformed JSON responses from Elasticsearch.
- Added a focused template unit test proving a `200` response with malformed JSON still maps to `null`.

## 2026-05-31 Batch 57: Excel Parser Exception Boundary

### Why

- `ExcelParserService` caught broad `Exception` around both workbook parsing flows.
- The supported failure boundary is file I/O, encrypted/invalid POI workbooks, or invalid workbook structure.
- Catching every exception could hide unrelated programming errors while still wrapping them as user upload parse failures.

### How

- Narrowed both parser catches to `IOException | EncryptedDocumentException | IllegalArgumentException`.
- Preserved the existing `IOException("Failed to parse Excel file: ...", cause)` contract for malformed uploads.
- Added service tests proving malformed Excel bytes are wrapped with the original cause for student-number and batch-import parsing.

## 2026-05-31 Batch 58: JWT Filter Exception Boundary

### Why

- `JwtAuthenticationFilter` caught broad `Exception` around the whole authentication block.
- That allowed infrastructure or mapper failures to be silently treated as anonymous requests.
- The filter should tolerate invalid JWT input, but it should not hide non-token failures from the request pipeline.

### How

- Narrowed the invalid-token catch to `JwtException | IllegalArgumentException`.
- Kept the existing behavior that malformed JWTs continue the filter chain without authentication.
- Added filter tests proving malformed JWTs are ignored while user lookup failures propagate instead of being swallowed.

## 2026-05-31 Batch 59: WebSocket JWT Handshake Exception Boundary

### Why

- `WebSocketJwtAuthInterceptor` caught broad `Exception` around the whole handshake authentication flow.
- Invalid websocket JWTs should be rejected with `401`, but user lookup or infrastructure failures should not be hidden as token problems.
- The HTTP JWT filter now uses a narrower invalid-token boundary, so websocket authentication should follow the same contract.

### How

- Narrowed the handshake invalid-token catch to `JwtException | IllegalArgumentException`.
- Preserved the behavior that malformed websocket JWTs reject the handshake with `401`.
- Added handshake tests proving malformed JWTs are rejected while user lookup failures propagate instead of being swallowed.

## 2026-05-31 Batch 60: Business Log ES Bootstrap Interrupt Handling

### Why

- `BusinessOperationLogService.ensureEsLifecycleSetupIfNeeded` caught broad `Exception` around Elasticsearch ILM/template bootstrap.
- If `ElasticsearchTemplate.putJson` was interrupted, bootstrap failed quietly and cleared the interrupted flag.
- Losing the interrupted status makes cancellation and shutdown less reliable during startup or scheduled flush.

### How

- Narrowed `ElasticsearchTemplate.putJson` to declare `IOException | InterruptedException` instead of generic `Exception`.
- Split ES bootstrap interruption into its own catch block and restored the interrupted flag.
- Narrowed the remaining bootstrap catch to `IOException | IllegalStateException | IllegalArgumentException`.
- Added a service regression test proving interrupted bootstrap leaves setup incomplete and preserves `Thread.currentThread().isInterrupted()`.

## 2026-05-31 Batch 61: Security Error Response Exception Boundary

### Why

- `SecurityConfig.writeJsonError` caught broad `Exception` while writing fixed JSON authentication and authorization errors.
- The supported failure boundary is servlet writer I/O or Jackson JSON serialization I/O.
- Catching every runtime failure could hide programming errors in the security response path while still returning only the status code.

### How

- Narrowed the catch block to `IOException`.
- Kept the existing fallback behavior of preserving the intended HTTP status if writing the JSON body fails.
- Re-ran focused security MVC tests that cover unauthenticated `401` and forbidden `403` responses.

## 2026-05-31 Batch 62: Attachment ETag Exception Boundary

### Why

- `AttachmentController.generateETag` caught broad `Exception` while generating SHA-256 based ETags.
- The only expected checked failure is an unavailable digest algorithm.
- Catching every runtime failure could hide programming errors in ETag input handling while silently falling back to a weaker hash.

### How

- Narrowed the catch block to `NoSuchAlgorithmException`.
- Preserved the existing fallback ETag behavior for the unlikely case that SHA-256 is unavailable.
- Re-ran the focused controller test that verifies generated ETags use the expected SHA-256 hex format.

## 2026-05-31 Batch 63: Developer Metrics ES Health Interrupt Handling

### Why

- `DeveloperMonitorService.checkElasticsearch` caught broad `Exception` around the Elasticsearch health probe.
- If `HttpClient.send` was interrupted, the probe returned `DOWN` but cleared the thread interrupted flag.
- Losing the interrupted status makes scheduled monitoring less cooperative with shutdown and cancellation.

### How

- Added a focused service regression test proving interrupted ES health checks return `DOWN` and preserve `Thread.currentThread().isInterrupted()`.
- Split `InterruptedException` into its own catch block and restored the interrupted flag.
- Narrowed the remaining ES probe fallback to `IOException | IllegalArgumentException`.

## 2026-05-31 Batch 64: Developer Metrics Middleware Exception Boundaries

### Why

- `DeveloperMonitorService.checkMysql` and `checkRabbitMq` caught broad `Exception` around middleware health probes.
- MySQL probe failures should be limited to SQL connection/validation failures.
- RabbitMQ probe failures should be limited to Spring AMQP failures.
- Catching every runtime failure could hide programming errors while still reporting middleware as `DOWN`.

### How

- Added focused service tests proving MySQL `SQLException` and RabbitMQ `AmqpException` still return `DOWN` details.
- Narrowed MySQL health-check fallback to `SQLException`.
- Narrowed RabbitMQ health-check fallback to `AmqpException`.

## 2026-05-31 Batch 65: Startup Activity Sync Exception Boundary

### Why

- `ActivityStartupSynchronizer.synchronizeActivitiesOnStartup` caught broad `Exception` around startup status synchronization.
- The method does not call APIs that expose checked exceptions; the intended safety boundary is runtime mapper or scheduling failures during application startup.
- Catching every checked exception type makes the startup boundary look broader than it is.

### How

- Added a focused service test proving runtime mapper failures are still logged and not propagated from the startup listener.
- Narrowed the startup listener catch block to `RuntimeException`.
- Kept the existing behavior that startup synchronization failures do not prevent the application from continuing.

## 2026-05-31 Batch 66: Activity Status Task Publish Interrupt Handling

### Why

- `ActivityStatusTaskService.dispatch` caught broad `Exception` around message serialization, RabbitMQ publish, and publisher confirm wait.
- If publisher confirm waiting was interrupted, the task was marked failed but the thread interrupted flag was cleared.
- Losing the interrupted status makes scheduled task recovery less cooperative with shutdown and cancellation.

### How

- Added a focused task-service test proving interrupted publisher confirm waits still mark the task failed and preserve `Thread.currentThread().isInterrupted()`.
- Split `InterruptedException` into its own catch block and restored the interrupted flag.
- Narrowed the remaining publish fallback to `JsonProcessingException | AmqpException | ExecutionException | TimeoutException`.

## 2026-05-31 Batch 67: Activity Status Message Parse Exception Boundary

### Why

- `ActivityStatusListener.parseMessage` caught broad `Exception` for JSON parsing and legacy pipe-format fallback parsing.
- JSON parsing failures are I/O/Jackson payload failures, while legacy fallback failures are invalid enum values or malformed fallback content.
- Catching every runtime failure could hide unrelated parser bugs while silently dropping a message.

### How

- Added focused listener tests for invalid payloads and the legacy `activityId|status` fallback format.
- Narrowed JSON parse fallback to `IOException`.
- Narrowed legacy pipe-format fallback to `IllegalArgumentException`.

## 2026-05-31 Batch 68: Activity Status Auto-Grant Exception Boundary

### Why

- `ActivityStatusListener.handle` caught broad `Exception` around automatic volunteer-hour grants after an activity reaches `ActivityEnded`.
- The grant service does not expose checked exceptions at this call site; the intended boundary is runtime business or persistence failures after the status update has already succeeded.
- Catching every exception type made the post-status-update side effect look broader than it is.

### How

- Added a focused listener test proving auto-grant runtime failures do not propagate after the status update succeeds.
- Narrowed the auto-grant catch block to `RuntimeException`.
- Preserved the existing behavior that status updates remain applied even if automatic hour granting fails.

## 2026-05-31 Batch 69: Activity Status Consume Retry Exception Boundary

### Why

- `ActivityStatusListener.onMessage` caught broad `Exception` around message handling and retry publishing.
- The intended recoverable failures at this boundary are Rabbit/ack I/O failures, Jackson message serialization I/O, and runtime business or persistence failures.
- Catching every exception type could hide unrelated checked failures while routing messages through retry/dead-letter handling.

### How

- Added a focused listener test proving runtime handle failures still publish a retry, mark consume failure, and ack the original message.
- Narrowed the main consume catch and retry-publish catch to `IOException | RuntimeException`.
- Narrowed `publishRetry` to declare `IOException` instead of generic `Exception`.

## 2026-05-31 Batch 70: Developer Metrics Push Exception Boundary

### Why

- `DeveloperMonitorService.pushMetrics` caught broad `Exception` around scheduled metrics snapshotting and push serialization.
- The expected recoverable push failures are Jackson JSON serialization failures and runtime failures from metric collection or broadcasting.
- Catching every checked exception type made the scheduled boundary less precise than the code path requires.

### How

- Added a focused service test proving JSON serialization failures are logged and do not propagate from `pushMetrics`.
- Narrowed the scheduled push catch to `JsonProcessingException | RuntimeException`.
- Preserved the existing behavior that one failed push does not stop later scheduled executions.

## 2026-05-31 Batch 71: Batch Import Row Failure Exception Boundary

### Why

- `BatchImportService.processValidRecords` caught broad `Exception` around each approved import row.
- The row-processing path uses mapper/service calls that fail as runtime exceptions at this boundary.
- Catching every checked exception type made the per-row tolerance boundary broader than the code path requires.

### How

- Added a focused service test proving a runtime participant lookup failure is recorded in `errors` without aborting the batch approval.
- Narrowed the per-row catch block to `RuntimeException`.
- Preserved the existing best-effort import behavior where one bad valid row does not stop the rest of the batch.

## 2026-05-31 Batch 72: Audit Aspect Helper Exception Boundaries

### Why

- `BusinessOperationAspect` caught broad `Exception` in helper paths for result-field inference and JSON snapshot fallback.
- Result-field inference only needs to tolerate reflection lookup/invocation failures.
- Snapshot fallback only needs to tolerate Jackson serialization failures.
- Broad helper catches could hide unrelated runtime bugs while silently producing incomplete audit metadata.

### How

- Added focused aspect tests proving missing getters infer an empty value and JSON serialization failures fall back to `String.valueOf`.
- Narrowed result-field inference fallback to `NoSuchMethodException | IllegalAccessException | InvocationTargetException | SecurityException`.
- Narrowed snapshot fallback to `JsonProcessingException`.

## 2026-05-31 Batch 73: File Upload Validation Consolidation

### Why

- `FileUploadService` duplicated the same file-empty, size, filename, and extension checks across cover-image and attachment uploads.
- The repeated branches made the upload boundary harder to scan and increased the chance of drifting messages or validation behavior between the two paths.
- The allowed-extension collections were only used for membership checks, so the previous mutable/list construction added noise without adding behavior.

### How

- Added small private helpers for empty-file, max-size, original-filename, and allowed-extension validation.
- Reused those helpers from both upload entry points so the validation rules live in one place.
- Switched the allowed format lists to immutable `List.of(...)` constants while preserving the existing error messages and extension order.

## 2026-05-31 Batch 74: Personal Hour Applicant Error Boundary

### Why

- `PersonalHourRequestService.submitRequest` reported a missing applicant with `IllegalArgumentException`.
- The project already models service-layer business failures with `BusinessException`, and `APPLICANT_NOT_FOUND` is an application error code rather than a Java argument-format failure.
- Keeping this as `IllegalArgumentException` routed a normal business rejection through the generic bad-argument handler and made the service boundary less explicit.

### How

- Added a focused service test proving a missing applicant raises `BusinessException` with HTTP 400 and error code `APPLICANT_NOT_FOUND`.
- Replaced the `IllegalArgumentException("APPLICANT_NOT_FOUND")` with `BusinessException.badRequest("APPLICANT_NOT_FOUND")`.
- Preserved the existing behavior that no request row is inserted when the applicant lookup fails.

## 2026-05-31 Batch 75: Pending Activity Participant Error Boundary

### Why

- `PendingActivityService.importActivity` reported a missing participant with `IllegalArgumentException`.
- Participant existence is a service-layer business validation, not a Java argument-format failure.
- The project already exposes business rejections through `BusinessException`, so this branch should return a stable error code through the same boundary as other service validation failures.

### How

- Added a focused service test proving a missing participant raises `BusinessException` with HTTP 400 and error code `PARTICIPANT_NOT_FOUND`.
- Replaced the `IllegalArgumentException("User not found: ...")` with `BusinessException.badRequest("PARTICIPANT_NOT_FOUND")`.
- Preserved the existing behavior that no pending or direct activity row is inserted after participant validation fails.

## 2026-05-31 Batch 76: Activity Status Listener ObjectMapper Injection

### Why

- `ActivityStatusListener` created its own `ObjectMapper` instead of using the application-configured Jackson bean.
- That hard-coded dependency could drift from global serialization settings and made mapper behavior harder to replace in focused tests.
- The listener already receives its other collaborators through constructor injection, so the mapper should follow the same boundary.

### How

- Added `ObjectMapper` as a constructor dependency and removed the internal `new ObjectMapper()`.
- Updated listener tests to provide a mapper explicitly through the constructor.
- Added a focused test proving `parseMessage` delegates to the injected mapper.

## 2026-05-31 Batch 77: Activity Status Task ObjectMapper Injection

### Why

- `ActivityStatusTaskService` created its own `ObjectMapper` with a fully qualified constructor call.
- Tests had to use reflection to replace that private mapper with one that supported Java time serialization.
- This made the service less consistent with the rest of the Spring-managed serialization boundary and harder to test directly.

### How

- Added `ObjectMapper` as a constructor dependency and removed the internal mapper allocation.
- Updated task-service tests to pass a configured mapper through the constructor instead of mutating private state.
- Added a focused test proving dispatch serialization uses the injected mapper and records publish failure when serialization fails.

## 2026-05-31 Batch 78: Security Error Response ObjectMapper Injection

### Why

- `SecurityConfig` kept a static `new ObjectMapper()` for authentication and authorization error JSON.
- Security responses should use the same Spring-managed Jackson configuration as the rest of the application.
- The static helper also made the serialization dependency harder to replace or configure in security-focused tests.

### How

- Added `ObjectMapper` as a constructor dependency alongside the JWT filter.
- Removed the static mapper and changed the JSON error writer to use the injected mapper.
- Preserved the existing security response shape for 401 and 403 responses.

## 2026-05-31 Batch 79: WebSocket Metrics Connection Exception Boundary

### Why

- `SystemMetricsWebSocketHandler.afterConnectionEstablished` declared `throws Exception` even though the method only performs WebSocket close operations that can fail with `IOException`.
- The broad framework override signature made the handler look like it tolerated arbitrary checked failures.
- The connection admission behavior was not directly covered by a focused unit test.

### How

- Added tests for unauthorized websocket rejection and max-connection rejection.
- Narrowed the override declaration to `throws IOException`.
- Preserved the existing behavior that rejected sessions are not registered as active metric clients.

## 2026-05-31 Batch 80: Attachment Path Invalid Character Boundary

### Why

- `AttachmentController.resolveFilePath` returned `null` for invalid path shapes, but `Paths.get(path)` could still throw `InvalidPathException` for illegal characters.
- That runtime exception bypassed the controller's invalid-path branch and could turn a bad download/preview request into an unhandled server error.
- The file upload service already treats invalid path syntax as a rejected path rather than an exceptional server condition.

### How

- Added a focused controller test proving a path with invalid characters returns HTTP 400.
- Wrapped path normalization and root-resolution checks in an `InvalidPathException` guard.
- Preserved the existing behavior where invalid paths map to the controller's bad-request response.

## 2026-05-31 Batch 81: Business Operation Failure Audit Regression

### Why

- `BusinessOperationAspect` intentionally catches `Throwable` so failed annotated operations are still written to the business audit log before the original failure leaves the controller boundary.
- That broad catch is easy to mistake for an over-wide exception boundary during cleanup.
- The existing aspect tests covered successful operation metadata and snapshot fallbacks, but did not lock the failure-audit behavior.

### How

- Added a focused aspect test proving a thrown `RuntimeException` writes one `FAILED` business operation log.
- Verified the failure detail includes the original operation detail and exception type.
- Verified the original exception instance is rethrown and no successful after-change snapshot is recorded.

## 2026-05-31 Batch 82: Elasticsearch HTTP Client Test Boundary

### Why

- `ElasticsearchTemplate` tests replaced the private `final` `HttpClient` through reflection to simulate HTTP failures.
- That made the tests depend on field names and broke encapsulation around the shared Elasticsearch boundary.
- The production constructor already owns the default client setup, so tests only needed a narrow way to provide a mock client.

### How

- Added a package-private constructor that accepts an `HttpClient` while preserving the existing Spring-facing constructor.
- Updated Elasticsearch template tests to inject the mock client directly instead of using `ReflectionTestUtils`.
- Removed unused collection imports from the template.

## 2026-05-31 Batch 83: Service HTTP Client Test Boundaries

### Why

- `CampusIdentityServiceImplTest` and `DeveloperMonitorServiceTest` replaced private `final` `HttpClient` fields through reflection.
- Those tests depended on field names instead of stable construction boundaries, making HTTP-failure coverage brittle.
- Both production services still need to own their default client timeout configuration for Spring wiring.

### How

- Replaced Lombok-generated constructors with explicit public constructors that create the default `HttpClient`.
- Added package-private constructors for tests to inject a mock `HttpClient` directly.
- Updated the identity and developer-monitor tests to stop reflecting into `httpClient` while preserving interrupted-thread assertions.

## 2026-05-31 Batch 84: JWT Utility Initialization Test Boundary

### Why

- `JWTUtilsTest` configured secrets and expiry values by reflecting into private fields and invoking the private `init` method.
- That made token parsing tests depend on implementation details instead of a stable construction path.
- The application still needs the no-argument Spring bean path with `@Value` field injection and `@PostConstruct` validation.

### How

- Added an explicit no-argument constructor for Spring and a package-private constructor for focused tests.
- Reused the existing initialization validation from the test constructor instead of duplicating secret setup logic.
- Updated `JWTUtilsTest` to build configured instances without `ReflectionTestUtils`.

## 2026-05-31 Batch 85: Campus Identity HTTP Behavior Test Boundary

### Why

- `CampusIdentityServiceImplTest` still reflected into provider/base-url/path fields to configure HTTP-mode tests.
- It also invoked the private `parseExists` helper directly, so response parsing coverage was coupled to a private method name.
- The behavior that matters to callers is `existsByStudentNo`, including accepted HTTP response shapes and malformed JSON fallback.

### How

- Extended the package-private test constructor to accept provider, base URL, and path template while preserving the Spring public constructor.
- Rewrote parsing tests to return mocked HTTP responses and assert through `existsByStudentNo`.
- Removed `ReflectionTestUtils` from the campus identity test class.

## 2026-05-31 Batch 86: Developer Monitor Health Test Boundary

### Why

- `DeveloperMonitorServiceTest` invoked private health-check helpers directly and reflected into ES config/cache fields.
- That coupled middleware health coverage to private method names and internal cache storage.
- The public behavior is exposed through `snapshot()`, which reports MySQL, RabbitMQ, and Elasticsearch health together.

### How

- Initialized package-private test constructors with the same default ES and websocket settings used by `@Value`.
- Rewrote health-check tests to mock dependencies and assert middleware status through `snapshot()`.
- Removed cache priming and all `ReflectionTestUtils` usage from the developer monitor test class.

## 2026-05-31 Batch 87: WebSocket Handler Connection Limit Test Boundary

### Why

- `SystemMetricsWebSocketHandlerTest` reflected into the private `maxConnections` field to exercise the connection-limit branch.
- That made the test depend on Spring-injected field names instead of a stable construction boundary.
- The production component still needs the default no-argument Spring path with `@Value` configuration.

### How

- Added an explicit no-argument constructor for Spring and a package-private constructor that accepts `maxConnections`.
- Updated the max-connection rejection test to use the package-private constructor instead of `ReflectionTestUtils`.
- Preserved the runtime behavior that the effective connection limit is bounded to at least one.

## 2026-05-31 Batch 88: Monitoring Log Index Test Boundary

### Why

- `MonitoringServiceTest` reflected into the private `esIndexPattern` field to verify log-query behavior with a test index pattern.
- That tied the test to a Spring-injected field name instead of a stable service construction boundary.
- Production still needs the normal constructor path where `@Value` can supply the configured index pattern.

### How

- Replaced Lombok constructor generation with explicit constructors.
- Kept the public constructor using the same default index pattern as the `@Value` fallback.
- Added a package-private constructor for tests to provide a custom index pattern without `ReflectionTestUtils`.

## 2026-05-31 Batch 89: Startup Synchronizer Dev Mode Test Boundary

### Why

- `ActivityStartupSynchronizerTest` reflected into the private `devModeTrigger` field to exercise startup behavior in dev mode.
- That coupled the test to a Spring-injected field name rather than a stable construction boundary.
- The production component still needs the public constructor path where `@Value` can provide the configured flag.

### How

- Replaced Lombok constructor generation with explicit constructors.
- Kept the public constructor defaulting dev mode to `false`, matching the `@Value` fallback.
- Added a package-private constructor for tests to provide `devModeTrigger` directly without `ReflectionTestUtils`.

## 2026-05-31 Batch 90: File Upload Path Configuration Test Boundary

### Why

- `FileUploadServiceTest` reflected into private upload path fields to set the upload root and subdirectories.
- Those tests depended on Spring-injected field names instead of a stable construction boundary.
- The production service still needs a no-argument Spring path where `@Value` can override the default upload paths.

### How

- Added an explicit no-argument constructor with the same defaults as the `@Value` fallbacks.
- Added a package-private constructor for tests to provide base, cover, and attachment paths directly.
- Updated upload-service tests to stop using `ReflectionTestUtils` for path configuration.

## 2026-05-31 Batch 91: Business Log Configuration Test Boundary

### Why

- `BusinessOperationLogServiceTest` reflected into multiple private `@Value` fields to configure index names, ILM settings, and fallback buffer sizes.
- One test also read the private bootstrap flag directly to prove interrupted bootstrap did not mark setup complete.
- The observable behavior can be verified through constructor-provided settings and retry behavior instead of private field access.

### How

- Replaced Lombok constructor generation with explicit constructors.
- Kept the public constructor using the same defaults as the `@Value` fallbacks.
- Added a package-private constructor for tests to provide business log ES and fallback settings directly.
- Rewrote the interrupted-bootstrap test to prove setup is retried after the interrupt instead of reading `esBootstrapDone`.

## 2026-05-31 Batch 92: Attachment Controller Test Boundary

### Why

- `AttachmentControllerTest` invoked the private `generateETag` helper and reflected into the private `basePath` field.
- That coupled coverage to implementation details instead of the public file-serving behavior.
- The controller still needs the normal Spring path where `@Value` can override the upload root.

### How

- Added an explicit default constructor with the same upload-root fallback used by `@Value`.
- Added a package-private constructor so tests can provide a temporary upload root without reflection.
- Rewrote the ETag coverage to call `previewFile` and assert the public response status and ETag header.

## 2026-05-31 Batch 93: Business Operation Aspect Behavior Tests

### Why

- `BusinessOperationAspectTest` invoked private `snapshot` and `inferFromResult` helpers directly.
- Those tests coupled audit coverage to private helper names instead of the observable log entry produced by the aspect.
- The public behavior already exposes both paths through `logOperation` and the captured `BusinessOperationLogVO`.

### How

- Rewrote the serialization-fallback test to mock `ObjectMapper`, run `logOperation`, and assert fallback text in the written log.
- Rewrote the missing-getter inference test to return a `Result` with an opaque object and assert empty target fields in the written log.
- Removed `ReflectionTestUtils` from the business-operation aspect test class.

## 2026-05-31 Batch 94: Activity Status Listener Message Boundary

### Why

- `ActivityStatusListenerTest` invoked the private `parseMessage` helper and reflected into the private `self` field.
- That coupled message-consumption coverage to implementation details instead of observable AMQP handling behavior.
- The listener still needs Spring to replace `self` with the transactional proxy when available.

### How

- Initialized `self` to `this` in the constructor so plain unit tests can exercise `onMessage` without reflective field injection.
- Rewrote invalid-payload and legacy-format coverage through `onMessage`, asserting nack/ack and status-update behavior.
- Rewrote injected-`ObjectMapper` coverage through `onMessage`, asserting mapper use, status update, mark-done, and ack behavior.

## 2026-05-31 Batch 95: Business Operation Aspect Test Helper Deduplication

### Why

- `BusinessOperationAspectTest` duplicated join-point setup across success and throwing helper methods.
- The repeated signature and argument stubbing made the test support code noisier than the behavior under test.
- The two public helper names are still useful because they describe the success and failure paths.

### How

- Added a shared `baseJoinPoint` helper for method lookup, signature stubbing, and argument stubbing.
- Kept `mockJoinPoint` and `mockThrowingJoinPoint` as small semantic wrappers around the shared setup.
- Left production code unchanged and preserved the existing audit-behavior assertions.

## 2026-05-31 Batch 96: Business Operation Aspect Log Capture Helper

### Why

- `BusinessOperationAspectTest` repeated the same `ArgumentCaptor` setup in every assertion path.
- The repeated capture boilerplate obscured the different audit fields each test actually cares about.
- This is test-support duplication only; production behavior should remain unchanged.

### How

- Added a `writtenLog` helper that verifies `BusinessOperationLogService.write` and returns the captured log VO.
- Replaced repeated captor setup in the success, fallback, inference, and failure tests.
- Left each test's behavior-specific assertions intact.

## 2026-05-31 Batch 97: Activity Status Listener AMQP Message Helper

### Why

- `ActivityStatusListenerTest` repeated raw AMQP `Message` construction in message-consumption tests.
- The repeated byte conversion and empty `MessageProperties` setup distracted from the ack/nack and status-update assertions.
- This is test-support duplication only; listener behavior should remain unchanged.

### How

- Added small `amqpMessage` helpers for text and byte payloads.
- Replaced repeated inline `new Message(..., new MessageProperties())` setup in the on-message tests.
- Kept the retry test's custom properties inline because it specifically asserts attempt-header behavior.

## 2026-05-31 Batch 98: Activity Status Listener Activity Fixture Helper

### Why

- `ActivityStatusListenerTest` repeated activity fixture construction with the same id and varying statuses.
- The repeated builder setup made state-transition tests noisier than the status being exercised.
- This is test fixture duplication only; listener behavior should remain unchanged.

### How

- Added an `activityWithStatus` helper that returns the standard `a1` test activity.
- Replaced repeated inline `Activity.builder()` setup in message and handle tests.
- Preserved each test's status-specific setup and assertions.

## 2026-05-31 Batch 99: Activity Status Listener Message Fixture Helper

### Why

- `ActivityStatusListenerTest` repeated `ActivityStatusUpdateMessage` construction with the same event id, activity id, attempt, and source.
- The repeated constructor arguments made the target status under test harder to scan.
- This is test fixture duplication only; listener behavior should remain unchanged.

### How

- Added an `updateMessage` helper that accepts only the target status.
- Replaced repeated inline status-message construction in object-mapper and handle tests.
- Removed an unused assertion import left behind by earlier cleanup.

## 2026-05-31 Batch 100: Business Operation Log Test Mapper Fixture

### Why

- `BusinessOperationLogServiceTest` repeatedly created plain `ObjectMapper` instances for normal JSON parsing and service construction.
- The repeated setup added noise around the Elasticsearch request-body assertions.
- The serialization-failure test still needs its own spy mapper so its stubbing remains isolated.

### How

- Added a shared test `ObjectMapper` fixture for ordinary mapper use.
- Replaced repeated inline mapper construction in query, bootstrap, and helper setup paths.
- Left the spy mapper in the serialization-failure test local to that test.

## 2026-05-31 Batch 101: Business Operation Log JSON Assertion Locals

### Why

- `BusinessOperationLogServiceTest` parsed the same captured Elasticsearch template body multiple times in one test.
- The repeated `readTree` calls made the assertions harder to scan and obscured which body each assertion was checking.
- This is test readability cleanup only; production behavior should remain unchanged.

### How

- Parsed the captured ILM and template request bodies once into named `JsonNode` locals.
- Reused those locals for the policy, index-pattern, settings, and mapping assertions.
- Left the asserted Elasticsearch request structure unchanged.

## 2026-05-31 Batch 102: Business Operation Log Query JSON Assertion Local

### Why

- `BusinessOperationLogServiceTest` parsed the same captured search request body twice in the query test.
- The repeated `readTree` calls made the query size and escaped-keyword assertions less direct.
- This is test readability cleanup only; production behavior should remain unchanged.

### How

- Parsed the captured query request body once into a named `JsonNode` local.
- Reused that local for the size and escaped `match_phrase` assertions.
- Left the asserted search request structure unchanged.

## 2026-05-31 Batch 103: Business Operation Log Assertion Import Consistency

### Why

- `BusinessOperationLogServiceTest` used a fully qualified `Assertions.assertTrue` call while the rest of the file used static assertion imports.
- The one-off fully qualified assertion made the interrupted-bootstrap test read differently from nearby tests.
- This is test readability cleanup only; production behavior should remain unchanged.

### How

- Added a static `assertTrue` import.
- Replaced the fully qualified assertion call with `assertTrue`.
- Left the interrupted-bootstrap behavior and assertions unchanged.

## 2026-05-31 Batch 104: Activity Service Test Construction Helper

### Why

- `ActivityServiceTest` repeated `ActivityService` construction with the same default mocked collaborators.
- The repeated constructor setup made each test spend more space on dependencies than on the behavior being verified.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added small `newService` helpers for default, custom upload-service, and custom status-task-service cases.
- Replaced repeated inline `ActivityService` construction in the activity-service tests.
- Preserved each test's behavior-specific mocks and assertions.

## 2026-05-31 Batch 105: Activity Service Test Activity Fixture Builder

### Why

- `ActivityServiceTest` repeated the same base `Activity` builder fields across several tests.
- The repeated `id`, `name`, `type`, and `status` setup made the behavior-specific fixture fields harder to spot.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a small `activity(name, status)` builder helper for the shared base activity fields.
- Replaced repeated inline base activity builder setup in the activity-service tests.
- Left behavior-specific fields such as `functionary`, `isFull`, `participants`, and review failure data explicit in each test.

## 2026-05-31 Batch 106: Activity Service Test Import Consistency

### Why

- `ActivityServiceTest` still had fully qualified helper calls inside the test body.
- The one-off `java.util.Arrays.asList` and `org.mockito.ArgumentMatchers.eq` calls made the participant test read differently from nearby Mockito and Java utility usage.
- This is test readability cleanup only; production behavior should remain unchanged.

### How

- Imported `Arrays` for the participant input that intentionally includes `null`.
- Added a static Mockito `eq` import.
- Replaced the fully qualified calls without changing the participant filtering assertion.

## 2026-05-31 Batch 107: Pending Activity Service Test Mockito Import Consistency

### Why

- `PendingActivityServiceTest` used fully qualified Mockito argument matchers inside verification calls.
- The repeated `org.mockito.ArgumentMatchers` prefix added noise to failure-path assertions.
- This is test readability cleanup only; production behavior should remain unchanged.

### How

- Added static imports for Mockito `any` and `anyString`.
- Replaced fully qualified matcher calls in participant insertion and no-insert verifications.
- Left the import, upload-failure, missing-participant, and Excel parse failure assertions unchanged.

## 2026-05-31 Batch 108: Pending Activity Service Test Construction Helper

### Why

- `PendingActivityServiceTest` repeated `PendingActivityService` construction with the same default mocked collaborators.
- The repeated constructor setup made each test spend more lines on unrelated dependencies than on the behavior being verified.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added small `newService` helpers for custom user mapper, activity mapper, upload service, and Excel parser cases.
- Replaced repeated inline service construction in the pending-activity service tests.
- Preserved behavior-specific mocks and assertions in each test.

## 2026-05-31 Batch 109: Pending Activity Service Test Import Request Fixture

### Why

- `PendingActivityServiceTest` repeated the same base `ActivityImportDTO` fields across import tests.
- The repeated name, type, duration, and end-time setup made each test's behavior-specific input harder to see.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a small `importRequest()` builder helper for the shared import DTO fields.
- Replaced repeated inline base import DTO builder setup in the pending-activity service tests.
- Left behavior-specific fields such as participants, cover file, and Excel file explicit in each test.

## 2026-05-31 Batch 110: Campus Identity Service Test Mockito Import Consistency

### Why

- `CampusIdentityServiceImplTest` imported the Mockito `ArgumentMatchers` class only to call generic `any()` in HTTP-client stubbing.
- The explicit class qualifier made the stubbing read differently from the rest of the test suite's static Mockito matcher style.
- This is test readability cleanup only; production behavior should remain unchanged.

### How

- Removed the `ArgumentMatchers` class import.
- Replaced the generic `ArgumentMatchers.any()` calls with a small static-imported `any()` body-handler matcher helper.
- Left the HTTP response-shape, invalid JSON, and interrupted-call assertions unchanged.

## 2026-05-31 Batch 111: Developer Monitor Service Test Mockito Import Consistency

### Why

- `DeveloperMonitorServiceTest` imported the Mockito `ArgumentMatchers` class only for generic HTTP body-handler matchers.
- The explicit class qualifier added noise to Elasticsearch HTTP-client stubbing.
- This is test readability cleanup only; production behavior should remain unchanged.

### How

- Removed the `ArgumentMatchers` class import.
- Added a small `stringBodyHandler()` helper that keeps the static-imported Mockito `any()` matcher typed as `HttpResponse.BodyHandler<String>`.
- Replaced the generic matcher calls in interrupted and healthy Elasticsearch HTTP-client stubs.

## 2026-05-31 Batch 112: Elasticsearch Template Test Mockito Import Consistency

### Why

- `ElasticsearchTemplateTest` imported the Mockito `ArgumentMatchers` class only for generic HTTP body-handler matchers.
- The explicit class qualifier added noise to HTTP-client stubbing in the Elasticsearch template tests.
- This is test readability cleanup only; production behavior should remain unchanged.

### How

- Removed the `ArgumentMatchers` class import.
- Added a small `stringBodyHandler()` helper that keeps the static-imported Mockito `any()` matcher typed as `HttpResponse.BodyHandler<String>`.
- Replaced the generic matcher calls in interrupted index and malformed search response stubs.

## 2026-05-31 Batch 113: Security Matcher Integration Test Mockito Import Consistency

### Why

- `SecurityMatcherIntegrationTest` had one fully qualified Mockito `eq` matcher call in activity update stubbing.
- The one-off fully qualified matcher stood out from the file's existing static Mockito matcher imports.
- This is test readability cleanup only; production behavior should remain unchanged.

### How

- Added a static Mockito `eq` import.
- Replaced the fully qualified activity id matcher in the update-activity stub.
- Left the security matcher requests and assertions unchanged.

## 2026-05-31 Batch 114: Focused Error Status Integration Test Import Consistency

### Why

- `FocusedErrorStatusIntegrationTest` used fully qualified utility and Spring test helper calls inside test bodies.
- The one-off `Optional`, `MockMultipartFile`, and multipart request builder qualifiers made the affected tests read differently from nearby statically imported request builders.
- This is test readability cleanup only; production behavior should remain unchanged.

### How

- Imported `Optional` and `MockMultipartFile`.
- Added a static `multipart` request builder import.
- Replaced the fully qualified login-failure and batch-import request setup calls without changing assertions.

## 2026-05-31 Batch 115: Developer Monitor Service Test AMQP Import Consistency

### Why

- `DeveloperMonitorServiceTest` still had a fully qualified `AmqpException` construction inside the RabbitMQ failure test.
- The fully qualified exception made the assertion setup noisier than nearby imports and stubs.
- This is test readability cleanup only; production behavior should remain unchanged.

### How

- Imported `AmqpException`.
- Replaced the fully qualified RabbitMQ failure exception construction.
- Left the RabbitMQ health-check behavior and assertions unchanged.

## 2026-05-31 Batch 116: Suggestion Service Test Construction Helper

### Why

- `SuggestionServiceTest` repeated `SuggestionService` construction with the same default mocked `UserMapper`.
- The repeated constructor setup made validation and reply tests spend lines on unrelated collaborators.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a small `newService` helper for the shared `SuggestionService` fixture.
- Replaced repeated inline service construction in suggestion service tests.
- Preserved each test's behavior-specific mapper stubs and assertions.

## 2026-05-31 Batch 117: Personal Hour Request Service Test Construction Helper

### Why

- `PersonalHourRequestServiceTest` repeated `PersonalHourRequestService` construction with the same default mocked collaborators.
- The repeated constructor setup made each test spend lines on unrelated dependencies.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added small `newService` helpers for default, custom user-mapper, and custom upload-service cases.
- Replaced repeated inline service construction in the personal-hour-request service tests.
- Preserved behavior-specific mocks and assertions in each test.

## 2026-05-31 Batch 118: Batch Import Service Test Construction Helper

### Why

- `BatchImportServiceTest` repeated `BatchImportService` construction with the same default mocked collaborators.
- The repeated constructor setup obscured which dependencies each test actually configures.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added small `newService` helpers for default, submit-review, and approve-batch cases.
- Replaced repeated inline service construction in batch-import service tests.
- Preserved behavior-specific parser, mapper, identity-service stubs, and assertions.

## 2026-05-31 Batch 119: Volunteer Hour Grant Service Test Construction Helper

### Why

- `VolunteerHourGrantServiceTest` repeated `VolunteerHourGrantService` construction with the same default mocked collaborators.
- The repeated constructor setup made it harder to see which mapper each test actually configures.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added small `newService` helpers for activity-mapper, request-mapper, and grant-record-mapper cases.
- Replaced repeated inline service construction in volunteer-hour-grant service tests.
- Preserved behavior-specific stubs, verifications, and assertions.

## 2026-05-31 Batch 120: Excel Parser Service Test Shared Fixture

### Why

- `ExcelParserServiceTest` repeated the same stateless `ExcelParserService` construction in every test.
- The repeated local setup added noise before each file fixture and assertion block.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a shared `ExcelParserService` field for the test class.
- Removed repeated local service construction from Excel parser tests.
- Preserved all workbook fixtures, malformed-file assertions, and parser calls.

## 2026-05-31 Batch 121: Business Operation Log Service Test Construction Helper

### Why

- `BusinessOperationLogServiceTest` still mixed inline `BusinessOperationLogService` construction with existing helper-based setup.
- The long inline constructors made the index bootstrap tests spend lines on unchanged defaults.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added helper overloads for default service construction and index-bootstrap configuration.
- Replaced the remaining inline service construction in business-operation-log service tests.
- Preserved each test's Elasticsearch stubs, object-mapper behavior, and assertions.

## 2026-05-31 Batch 122: Monitoring Service Test Construction Helper

### Why

- `MonitoringServiceTest` constructed `MonitoringService` inline in each test with repeated default collaborators or log-index configuration.
- The inline setup made the test bodies spend lines on fixture wiring instead of the mapper and Elasticsearch behavior under test.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added small `newService` helpers for default and custom Elasticsearch-template cases.
- Replaced inline service construction in monitoring service tests.
- Preserved paging, query-body, mapper, and Elasticsearch assertions.

## 2026-05-31 Batch 123: Activity Status Task Service Test Construction Helper

### Why

- `ActivityStatusTaskServiceTest` had one remaining inline `ActivityStatusTaskService` construction while nearby tests used a helper.
- The custom `ObjectMapper` setup was behavior-specific, but the service wiring itself was repeated fixture noise.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a `newService` overload that accepts a custom `ObjectMapper`.
- Replaced the remaining inline activity-status-task service construction.
- Preserved the serialization-failure stub, RabbitMQ assertions, and task mapper verification.

## 2026-05-31 Batch 124: File Upload Service Test Construction Helper

### Why

- `FileUploadServiceTest` mostly used a helper for service construction, but the invalid cover-directory case still built the service inline.
- The inline constructor hid the behavior-specific fixture setup inside argument values.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a named helper for the cover-directory-outside-root configuration.
- Replaced the remaining inline file-upload service construction in the invalid configuration test.
- Preserved the upload rejection assertion and mock multipart fixture.

## 2026-05-31 Batch 125: My Activity Service Test Construction Helper

### Why

- `MyActivityServiceTest` constructed `MyActivityService` inline with default mocked collaborators in the test body.
- The inline constructor made the mapper-focused test spend lines on unrelated dependencies.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a small `newService` helper for the my-activity service fixture.
- Replaced inline service construction in the enum-normalization test.
- Preserved the mapper stubs and enum assertions.

## 2026-05-31 Batch 126: Monitoring Service Test Query Body Reuse

### Why

- `MonitoringServiceTest` parsed the captured Elasticsearch query body twice in adjacent assertions.
- The repeated parse made the assertion block noisier than necessary.
- This is test readability cleanup only; production behavior should remain unchanged.

### How

- Parsed the captured query body once into a `JsonNode`.
- Reused that parsed body for size and keyword-query assertions.
- Preserved the mocked Elasticsearch response, captured request body, and result assertions.

## 2026-05-31 Batch 127: Batch Import Service Test Captor Helper

### Why

- `BatchImportServiceTest` repeated the same `ArgumentCaptor` setup to inspect inserted batch-import records.
- The duplicated captor block obscured the record-specific assertions in submit-review tests.
- This is test readability cleanup only; production behavior should remain unchanged.

### How

- Added a `capturedInsertedRecords` helper for `insertRecords` verification and capture.
- Replaced the duplicated captor setup in the two submit-review tests.
- Preserved all inserted-record size, activity-name, validation-status, and result-count assertions.

## 2026-05-31 Batch 128: Activity Service Test Time Fixture

### Why

- `ActivityServiceTest` built one activity creation fixture from four separate `OffsetDateTime.now()` calls.
- The repeated clock reads made the time fixture noisier and less internally consistent than necessary.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Captured a single `now` value before building the activity DTO.
- Derived all enrollment and activity times from that shared timestamp.
- Preserved the create-activity scheduling assertion and mocked collaborators.

## 2026-05-31 Batch 129: Pending Activity Import Fixed End Time

### Why

- `PendingActivityServiceTest` used `OffsetDateTime.now()` in the shared import request fixture.
- The runtime clock read was not part of the behavior under test and made the fixture less deterministic.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a named fixed `IMPORT_END_TIME` constant for pending activity import requests.
- Reused that constant in the shared `importRequest` builder.
- Preserved the participant validation, upload failure, and Excel parse failure assertions.

## 2026-05-31 Batch 130: Authorization Controller Test Time Fixture

### Why

- `AuthorizationControllerTest` built one create-activity request from four separate `OffsetDateTime.now()` calls.
- The repeated clock reads made the request fixture noisier and less internally consistent than necessary.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Captured one `now` value before building the create-activity request.
- Derived the enrollment, start, and expected end times from that shared timestamp.
- Preserved the role authorization assertion and functionary override assertion.

## 2026-05-31 Batch 131: Activity Status Task Fixed Execute Time

### Why

- `ActivityStatusTaskServiceTest` used `LocalDateTime.now()` in three task fixtures.
- The task execution timestamp was not the behavior under assertion in these recovery tests.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a named fixed `EXECUTE_AT` value for recovered task fixtures.
- Reused that timestamp in invalid-status, interrupted-publish, and serialization-failure tests.
- Preserved all mapper, RabbitMQ, interrupted-flag, and serialization assertions.

## 2026-05-31 Batch 132: Authorization Activity Controller Fixture Helper

### Why

- `AuthorizationControllerTest` repeated the same `ActivityController` constructor setup in activity authorization tests.
- Each test only varied the `ActivityService`; the other collaborators were default mocks.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a `newActivityController` helper that accepts the activity service under test.
- Replaced repeated inline `ActivityController` construction in activity create, enroll, review, delete, and update tests.
- Preserved all authorization assertions and service interaction verifications.

## 2026-05-31 Batch 133: Suggestion Controller Fixture Helper

### Why

- `SuggestionControllerTest` repeated the same controller construction in each test.
- The repeated constructor call made the tests spend lines on fixture setup instead of the missing-principal/body assertions.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a `newController` helper for suggestion controller tests.
- Replaced repeated inline `SuggestionController` construction.
- Preserved all missing-principal/body assertions and service non-interaction verifications.

## 2026-05-31 Batch 134: Personal Hour Request Controller Fixture Helper

### Why

- `PersonalHourRequestControllerTest` repeated the same controller construction in each test.
- The repeated constructor call made the tests spend lines on fixture setup instead of authorization/request validation assertions.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a `newController` helper for personal-hour request controller tests.
- Replaced repeated inline `PersonalHourRequestController` construction.
- Preserved all missing-principal, admin-student-number, and service non-interaction verifications.

## 2026-05-31 Batch 135: User Controller Default Jwt Fixture Helper

### Why

- `UserControllerTest` repeated controller construction with a default mocked `JWTUtils`.
- The default JWT collaborator was unrelated to missing-principal and logout validation tests.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a `newController` helper that accepts the user service and supplies a default mocked JWT utility.
- Replaced repeated inline `UserController` construction in tests that do not assert JWT behavior.
- Preserved the login test's explicit JWT mock and no-interactions assertion.

## 2026-05-31 Batch 136: Monitoring Controller Fixture Helpers

### Why

- `MonitoringControllerTest` repeated `MonitoringController` construction with mostly default mocked collaborators.
- Each test only varied one service under assertion, while the other collaborators added setup noise.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow `newController` helpers for monitoring, business-log, and task-service focused tests.
- Replaced repeated inline `MonitoringController` construction.
- Preserved all limit-normalization assertions and service interaction verifications.

## 2026-05-31 Batch 137: Attachment Controller Temp Directory Fixture Helper

### Why

- `AttachmentControllerTest` repeated controller construction from the same temporary root path.
- The repeated `tempDir.toString()` conversion added fixture noise to both preview tests.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a `newController` helper that builds the attachment controller from the test temp directory.
- Replaced repeated inline `AttachmentController` construction.
- Preserved the ETag format assertion and invalid-path bad-request assertion.

## 2026-05-31 Batch 138: Activity Startup Empty List Fixture

### Why

- `ActivityStartupSynchronizerTest` used `Collections.emptyList()` for a simple empty mapper result.
- Other test fixtures in this pass increasingly use concise collection factories for immutable values.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Replaced `Collections.emptyList()` with `List.of()` in the startup synchronization empty-result stub.
- Updated the import from `Collections` to `List`.
- Preserved the startup synchronization mapper verification.

## 2026-05-31 Batch 139: Monitoring Service Empty List Fixture

### Why

- `MonitoringServiceTest` used `Collections.emptyList()` for a simple empty mapper result.
- The explicit `Collections` import added noise beside the existing `List` fixture usage.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Replaced `Collections.emptyList()` with `List.of()` in the user-stats mapper stub.
- Removed the now-unused `Collections` import.
- Preserved the paging, sorting, and recent-log query assertions.

## 2026-05-31 Batch 140: Authorization Controller Empty DTO Lists

### Why

- `AuthorizationControllerTest` used `Collections.emptyList()` for simple empty DTO collection fixtures.
- The test only needs immutable empty values for created activity response fields.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Replaced the activity response `attachment` and `participants` fixtures with `List.of()`.
- Updated the import from `Collections` to `List`.
- Preserved the privileged-create assertion and principal student-number override check.

## 2026-05-31 Batch 141: WebSocket Auth Empty Attributes Fixtures

### Why

- `WebSocketJwtAuthInterceptorTest` used mutable `HashMap` instances for failure-path handshake attributes.
- Both tests exit before the interceptor writes a principal into the attributes map.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Replaced the unused empty `HashMap` attributes fixtures with `Map.of()`.
- Updated the import from `HashMap` to `Map`.
- Preserved the unauthorized status and user-lookup exception assertions.

## 2026-05-31 Batch 142: System Metrics Authenticated Attributes Fixture

### Why

- `SystemMetricsWebSocketHandlerTest` built authenticated session attributes with a mutable `HashMap`.
- The handler only reads the `principal` attribute and does not mutate the map.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Replaced the manual `HashMap` construction and `put` call with `Map.of(...)`.
- Removed the now-unused `HashMap` import.
- Preserved the unauthorized-close and connection-limit assertions.

## 2026-05-31 Batch 143: Batch Import File Fixture Helper

### Why

- `BatchImportServiceTest` repeated mock `MultipartFile` construction with the same original filename.
- Both submit-for-review tests vary parsed records, not the uploaded filename fixture.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `batchFile()` helper that returns a mock file named `batch.xlsx`.
- Replaced the duplicated file mock and filename stubbing in the two submit-for-review tests.
- Preserved record parsing, identity lookup, and inserted-record assertions.

## 2026-05-31 Batch 144: Authorization Guard Authority Fixture Helper

### Why

- `AuthorizationGuardsTest` repeated `TestingAuthenticationToken` construction for authority fallback checks.
- The tests vary only the principal name and granted role.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added an `authenticationWithRole` helper for one-role security authentication fixtures.
- Replaced the duplicated token construction in the super-admin fallback tests.
- Preserved the accept/reject assertions for `ROLE_SUPERADMIN` and `ROLE_ADMIN`.

## 2026-05-31 Batch 145: Monitoring Controller Super Admin Fixture

### Why

- `MonitoringControllerTest` repeated the same super-admin `UserPrincipal` fixture.
- The tests vary service calls and size normalization, not the principal identity.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `superAdmin()` helper for monitoring endpoint authorization fixtures.
- Replaced repeated inline super-admin principals in log retrieval tests.
- Preserved the replay, recent-log, and business-log service verifications.

## 2026-05-31 Batch 146: Activity Cover File Fixture Helper

### Why

- `ActivityServiceTest` repeated non-empty `MultipartFile` setup for cover upload failure paths.
- The tests vary create/update behavior, not the cover-file emptiness fixture.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `coverFile()` helper that returns a non-empty mocked cover file.
- Replaced duplicated cover-file mock and `isEmpty` stubbing in create/update failure tests.
- Preserved upload-failure stubbing and insert/update no-call verifications.

## 2026-05-31 Batch 147: Pending Activity Non-Empty File Fixture

### Why

- `PendingActivityServiceTest` repeated non-empty `MultipartFile` setup in file failure-path tests.
- The tests vary cover upload and Excel parse failures, not file emptiness.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `nonEmptyFile()` helper for mocked non-empty multipart files.
- Replaced duplicated file mock and `isEmpty` stubbing in cover-upload and Excel-parse failure tests.
- Preserved failure-cause assertions and no-insert verifications.

## 2026-05-31 Batch 148: Business Operation Elasticsearch Fixture Helper

### Why

- `BusinessOperationLogServiceTest` repeated `ElasticsearchTemplate` mock creation in every test.
- The tests vary Elasticsearch responses and failures, not the mock construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `esTemplate()` helper for mocked Elasticsearch collaborators.
- Replaced repeated inline `ElasticsearchTemplate` mock creation.
- Preserved query, indexing, ILM/template, interruption, and serialization-retry assertions.

## 2026-05-31 Batch 149: Suggestion Mapper Fixture Helper

### Why

- `SuggestionServiceTest` repeated `SuggestionMapper` mock creation in every test.
- Each test varies service input and mapper stubbing, not mapper construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `suggestionMapper()` helper for fresh mapper mocks.
- Replaced repeated inline `SuggestionMapper` mock creation.
- Preserved validation, trimming, update, and DTO mapping assertions.

## 2026-05-31 Batch 150: Activity Status Listener Channel Fixture

### Why

- `ActivityStatusListenerTest` repeated RabbitMQ `Channel` mock creation across message-consumption tests.
- The tests vary payload handling and ack/nack expectations, not channel construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `channel()` helper for mocked RabbitMQ channels.
- Replaced repeated inline `Channel` mock creation in listener message tests.
- Preserved invalid-payload, legacy-format, injected-mapper, and retry ack/nack verifications.

## 2026-05-31 Batch 151: WebSocket JWT Auth Dependency Fixtures

### Why

- `WebSocketJwtAuthInterceptorTest` repeated JWT utility and user mapper mock creation across handshake tests.
- Each test varies token parsing and user lookup behavior, not dependency construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow `jwtUtils()` and `userMapper()` helpers for fresh dependency mocks.
- Replaced repeated inline dependency mock creation in WebSocket handshake tests.
- Preserved malformed-token rejection and user-lookup exception propagation assertions.

## 2026-05-31 Batch 152: Activity Status Task Dependency Fixtures

### Why

- `ActivityStatusTaskServiceTest` repeated task mapper and RabbitTemplate mock creation across recovery tests.
- Each test varies pending task state, serialization, and publish outcome, not dependency construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow `taskMapper()` and `rabbitTemplate()` helpers for fresh dependency mocks.
- Replaced repeated inline dependency mock creation in activity status task recovery tests.
- Preserved invalid-status dead-lettering, interrupted-publisher retry, and serialization-failure assertions.

## 2026-05-31 Batch 153: Personal Hour Request Dependency Fixtures

### Why

- `PersonalHourRequestServiceTest` repeated request mapper, user mapper, and file upload mock creation across service tests.
- Each test varies applicant lookup, review validation, or upload failure behavior, not dependency construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow `requestMapper()`, `userMapper()`, and `fileUploadService()` helpers for fresh dependency mocks.
- Replaced repeated inline dependency mock creation in personal-hour request tests.
- Preserved applicant-not-found, blank-review-reason, and attachment-upload failure assertions.

## 2026-05-31 Batch 154: Activity Startup Synchronizer Dependency Fixtures

### Why

- `ActivityStartupSynchronizerTest` repeated activity mapper and status task service mock creation across startup sync tests.
- Each test varies base-activity lookup behavior, not dependency construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow `activityMapper()` and `statusTaskService()` helpers for fresh dependency mocks.
- Replaced repeated inline dependency mock creation in activity startup synchronization tests.
- Preserved base-query verification and runtime mapper failure swallowing assertions.

## 2026-05-31 Batch 155: Volunteer Hour Grant Mapper Fixtures

### Why

- `VolunteerHourGrantServiceTest` repeated mapper mock creation across grant and not-found tests.
- Each test varies mapper stubbing and grant behavior, not dependency construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow mapper helpers for fresh user, activity, request, and grant-record mapper mocks.
- Replaced repeated inline mapper mock creation in tests and service factory overloads.
- Preserved participant normalization, missing-entity, and non-ended activity assertions.

## 2026-05-31 Batch 156: Monitoring Service Dependency Fixtures

### Why

- `MonitoringServiceTest` repeated monitoring mapper and Elasticsearch template mock creation across service tests.
- Each test varies paging, sorting, or log-query behavior, not dependency construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow `mapper()` and `esTemplate()` helpers for fresh dependency mocks.
- Replaced repeated inline dependency mock creation in monitoring service tests and factory setup.
- Preserved paging normalization, sort fallback, log size clamping, and escaped keyword query assertions.

## 2026-05-31 Batch 157: User Controller Dependency Fixtures

### Why

- `UserControllerTest` repeated user service and JWT utility mock creation across controller tests.
- Each test varies login payload, principal validation, or token-version behavior, not dependency construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow `userService()` and `jwtUtils()` helpers for fresh dependency mocks.
- Replaced repeated inline dependency mock creation in user controller tests and controller factory setup.
- Preserved invalid-token-data, missing-principal, and blank-student-number assertions.

## 2026-06-01 Batch 158: Authorization Pending Activity Fixtures

### Why

- `AuthorizationControllerTest` repeated pending activity service, batch import service, and controller construction across pending activity authorization tests.
- Each test varies principal ownership and service stubbing, not dependency construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow `pendingActivityService()`, `batchImportService()`, and `newPendingActivityController(...)` helpers.
- Replaced repeated inline pending activity controller setup in authorization tests.
- Preserved submitted-by forcing, missing-principal rejection, and non-owner detail rejection assertions.

## 2026-06-01 Batch 159: Activity Service Dependency Fixtures

### Why

- `ActivityServiceTest` repeated file upload service and activity status task service mock creation across activity service tests.
- Each test varies upload failure, task scheduling, or participant filtering behavior, not dependency construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow `fileUploadService()` and `taskService()` helpers for fresh dependency mocks.
- Replaced repeated inline dependency mock creation in activity service tests and service factory overloads.
- Preserved cover-upload failure, pre-approval scheduling, and participant filtering assertions.

## 2026-06-01 Batch 160: Pending Activity Service Dependency Fixtures

### Why

- `PendingActivityServiceTest` repeated mapper, upload, parser, grant service, and event publisher mock creation across import tests.
- Each test varies participant validation, cover upload, or Excel parse behavior, not dependency construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow helpers for pending activity, activity, user, upload, parser, grant, and publisher mocks.
- Replaced repeated inline dependency mock creation in pending activity import tests and service factory overloads.
- Preserved participant normalization, missing participant, upload failure, and Excel parse failure assertions.

## 2026-06-01 Batch 161: Batch Import Service Dependency Fixtures

### Why

- `BatchImportServiceTest` repeated parser, mapper, identity, grant service, and event publisher mock creation across batch import tests.
- Each test varies parsed records, identity lookup, or row failure behavior, not dependency construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow helpers for Excel parser, user mapper, activity mapper, pending-batch mapper, identity, grant, and publisher mocks.
- Replaced repeated inline dependency mock creation in batch import tests and service factory overloads.
- Preserved activity-name splitting, invalid-record retention, and runtime row failure assertions.

## 2026-06-01 Batch 162: Activity Status Listener Dependency Fixtures

### Why

- `ActivityStatusListenerTest` repeated activity mapper, task service, grant service, and RabbitTemplate mock creation across listener tests.
- Each test varies payload handling, status update behavior, retry, or auto-grant behavior, not dependency construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow helpers for activity mapper, task service, grant service, and RabbitTemplate mocks.
- Replaced repeated inline dependency mock creation in listener tests and factory overloads.
- Preserved invalid-payload, legacy-format, injected-mapper, missing-status, auto-grant failure, and retry assertions.

## 2026-06-01 Batch 163: Activity Service Mapper Fixture

### Why

- `ActivityServiceTest` still repeated activity mapper mock creation across create and update tests.
- The service factory also created the event publisher mock inline, while each test varies upload, scheduling, review, or participant behavior.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow `activityMapper()` and `eventPublisher()` helpers for fresh dependency mocks.
- Replaced repeated inline activity mapper mock creation in activity service tests.
- Preserved review resubmission, pre-approval scheduling, upload failure, and participant filtering assertions.

## 2026-06-01 Batch 164: Authorization Controller Dependency Fixtures

### Why

- `AuthorizationControllerTest` repeated activity service and user service mock creation across authorization tests.
- The activity controller factory also created collaborator mocks inline, while each test varies principal, role, ownership, or service-call behavior.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow helpers for activity, user, file upload, and my-activity service mocks.
- Replaced repeated inline activity service mock creation and controller factory collaborator mocks.
- Preserved self-lookup, pending-activity ownership, activity create, enroll, review, delete, and update authorization assertions.

## 2026-06-01 Batch 165: Monitoring Controller Dependency Fixtures

### Why

- `MonitoringControllerTest` repeated activity status task service mock creation across controller factory overloads.
- Controller factory overloads also created monitoring, business log, and developer monitor collaborators inline, while tests vary request normalization behavior.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow helpers for monitoring, business log, activity status task, and developer monitor service mocks.
- Replaced repeated inline dependency mock creation in monitoring controller tests and factory overloads.
- Preserved dead-task replay limit, monitoring log size, and business log size normalization assertions.

## 2026-06-01 Batch 166: Business Operation Aspect Dependency Fixtures

### Why

- `BusinessOperationAspectTest` repeated business operation log service mock creation across aspect behavior tests.
- Aspect construction also repeated the same real `ObjectMapper` setup where tests vary principal resolution, snapshots, target fields, or failure handling.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow helpers for business operation log service mocks and aspect construction.
- Replaced repeated inline log service and real mapper construction in aspect tests.
- Preserved principal argument, security context, serialization fallback, target extraction, and failure logging assertions.

## 2026-06-01 Batch 167: Developer Monitor Rabbit Fixture

### Why

- `DeveloperMonitorServiceTest` repeated RabbitTemplate mock creation across healthy and failing RabbitMQ health checks.
- Each test varies middleware health behavior or push error handling, not RabbitTemplate construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `rabbitTemplate()` helper for fresh RabbitTemplate mocks.
- Reused the helper in the healthy RabbitMQ fixture and failing RabbitMQ health-check test.
- Preserved JSON push failure, MySQL failure, RabbitMQ failure, and Elasticsearch interruption assertions.

## 2026-06-01 Batch 168: Developer Monitor Push Dependency Fixtures

### Why

- `DeveloperMonitorServiceTest` still repeated WebSocket handler, SSE broadcaster, and request metrics collector mock creation across service construction paths.
- Tests vary metric push and middleware health behavior, not monitor collaborator construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow helpers for WebSocket handler, SSE broadcaster, and request metrics collector mocks.
- Replaced repeated inline monitor collaborator mock creation in push tests and service factories.
- Preserved JSON serialization failure, MySQL failure, RabbitMQ failure, and Elasticsearch interruption assertions.

## 2026-06-01 Batch 169: Campus Identity HTTP Fixtures

### Why

- `CampusIdentityServiceImplTest` repeated HttpClient mock creation across endpoint and HTTP identity check tests.
- The service factory also created the user mapper mock inline, while tests vary URL encoding, response parsing, malformed JSON, or interruption behavior.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow helpers for HttpClient and user mapper mocks.
- Replaced repeated inline HttpClient mock creation and service factory mapper construction.
- Preserved path encoding, boolean and nested response parsing, invalid JSON, and interruption flag assertions.

## 2026-06-01 Batch 170: Elasticsearch Template HTTP Fixtures

### Why

- `ElasticsearchTemplateTest` repeated HttpClient mock creation and template construction across Elasticsearch error-path tests.
- Response mock setup was also inline in the malformed JSON test, while tests vary interruption and parse-failure behavior.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow helpers for Elasticsearch template construction, HttpClient mocks, and response mocks.
- Replaced repeated inline HttpClient and template construction in Elasticsearch template tests.
- Preserved interrupted index handling and malformed search response assertions.

## 2026-06-01 Batch 171: Developer Monitor Middleware Fixtures

### Why

- `DeveloperMonitorServiceTest` still repeated DataSource, Connection, HttpClient, and HttpResponse mock creation across middleware health fixtures.
- Tests vary MySQL, RabbitMQ, Elasticsearch, or push behavior, not basic middleware collaborator construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow helpers for DataSource, Connection, HttpClient, and HttpResponse mocks.
- Reused the helpers in failing MySQL, interrupted Elasticsearch, healthy data source, and healthy HTTP client fixtures.
- Preserved JSON push failure, MySQL failure, RabbitMQ failure, and Elasticsearch interruption assertions.

## 2026-06-01 Batch 172: My Activity Service Dependency Fixtures

### Why

- `MyActivityServiceTest` created mapper and service collaborators inline around the enum normalization behavior under test.
- The service factory constructed activity and user service mocks inline, while the test varies database row normalization only.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow helpers for my-activity mapper, activity service, and user service mocks.
- Replaced inline mapper and service collaborator mock creation in the my-activity service test.
- Preserved status and activity type enum normalization assertions.

## 2026-06-01 Batch 173: Monitoring Controller Local Fixtures

### Why

- `MonitoringControllerTest` still had local inline monitoring and business log service mock creation after controller factory helpers were introduced.
- The tests vary size normalization behavior, not dependency construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Reused existing monitoring and business log service helpers in the local tests.
- Replaced the remaining inline dependency mock creation in monitoring controller test methods.
- Preserved monitoring log and business log size normalization assertions.

## 2026-06-01 Batch 174: Campus Identity Response Fixture

### Why

- `CampusIdentityServiceImplTest` still created HttpResponse mocks inline inside the reusable OK-response fixture.
- The response fixture varies response body content, not the response mock construction itself.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `response()` helper for fresh HttpResponse mocks.
- Reused the helper inside the existing `okResponse(...)` fixture.
- Preserved path encoding, response shape parsing, invalid JSON, and interruption assertions.

## 2026-06-01 Batch 175: User Controller Factory Fixture

### Why

- `UserControllerTest` mixed direct controller construction with an existing controller factory helper.
- The login test varies JWT interaction behavior, not controller construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a controller factory overload that accepts an explicit JWT utility mock.
- Reused the factory in the login test while preserving the default factory for other tests.
- Preserved missing-role login, missing-principal lookup, and blank-student logout assertions.

## 2026-06-01 Batch 176: JWT Filter Fixture Helpers

### Why

- `JwtAuthenticationFilterTest` repeated JWT utility and user mapper mock construction in each test.
- The tests vary JWT parsing and mapper failure behavior, not dependency construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow helpers for JWT utility and user mapper mocks.
- Added a local filter factory that keeps constructor wiring in one place.
- Preserved invalid-token continuation and user-lookup exception assertions.

## 2026-06-01 Batch 177: WebSocket JWT Interceptor Factory

### Why

- `WebSocketJwtAuthInterceptorTest` still constructed the interceptor inline in each test.
- The tests vary token parsing and user lookup behavior, not interceptor wiring.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a local interceptor factory that accepts explicit JWT utility and user mapper mocks.
- Reused the factory in malformed-token and user-lookup-failure tests.
- Preserved unauthorized handshake and exception propagation assertions.

## 2026-06-01 Batch 178: System Metrics WebSocket Handler Factory

### Why

- `SystemMetricsWebSocketHandlerTest` constructed handlers inline in each scenario.
- The tests vary authorization attributes and connection limit behavior, not handler construction.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added local handler factories for default and max-connection construction.
- Reused the factories in unauthorized-session and connection-limit tests.
- Preserved close-status verification and client-count assertions.

## 2026-06-01 Batch 179: Attachment Preview Request Fixture

### Why

- `AttachmentControllerTest` repeated servlet web request construction in preview scenarios.
- The tests vary preview path handling, not servlet request fixture setup.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `webRequest()` helper for preview request fixtures.
- Reused the helper in valid ETag and invalid-path tests.
- Preserved ETag format and bad-request assertions.

## 2026-06-01 Batch 180: Suggestion Controller Service Fixture

### Why

- `SuggestionControllerTest` repeated suggestion service mock construction in each scenario.
- The tests vary missing principal/body guard behavior, not service mock setup.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `suggestionService()` helper for fresh service mocks.
- Reused the helper across create, list, and reply guard tests.
- Preserved no-service-call verification for rejected requests.

## 2026-06-01 Batch 181: Personal Hour Request Controller Service Fixture

### Why

- `PersonalHourRequestControllerTest` repeated request service mock construction in each guard scenario.
- The tests vary missing principal/admin identity behavior, not service mock setup.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `requestService()` helper for fresh request service mocks.
- Reused the helper across submit, review, list, and delete guard tests.
- Preserved no-service-call verification for rejected requests.

## 2026-06-01 Batch 182: Authorization Guards Principal Fixture

### Why

- `AuthorizationGuardsTest` repeated direct user principal construction across role and identity checks.
- The tests vary role/student number inputs, not principal construction mechanics.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `principal(...)` helper for user principal fixtures.
- Reused the helper in admin, missing-student, and super-admin checks.
- Preserved null-principal, role-case, and authority-fallback assertions.

## 2026-06-01 Batch 183: File Upload Multipart Fixture

### Why

- `FileUploadServiceTest` repeated multipart upload fixture construction in upload scenarios.
- The tests vary filename and upload directory behavior, not multipart request setup.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow multipart upload helpers with default and explicit content-type variants.
- Reused the helpers in attachment filename and cover upload rejection tests.
- Preserved sanitized filename, bounded basename, and outside-root rejection assertions.

## 2026-06-01 Batch 184: Excel Parser Multipart Fixture

### Why

- `ExcelParserServiceTest` repeated valid Excel multipart fixture construction in workbook parsing scenarios.
- The tests vary workbook content, not multipart request metadata.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `excelFile(...)` helper for valid workbook multipart fixtures.
- Reused the helper in missing-student-number and formula-cell parsing tests.
- Preserved malformed workbook coverage with its distinct bad-file fixture.

## 2026-06-01 Batch 185: Personal Hour Attachment Fixture

### Why

- `PersonalHourRequestServiceTest` built and configured an attachment mock inline in the upload-failure scenario.
- The test varies upload failure behavior, not non-empty attachment fixture setup.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `attachmentFile()` helper for a non-empty multipart attachment mock.
- Reused the helper in the attachment upload failure test.
- Preserved applicant lookup, upload exception, and no-insert assertions.

## 2026-06-01 Batch 186: Batch Import Record DTO Fixture

### Why

- `BatchImportServiceTest` repeated batch import record DTO boilerplate in submit-for-review scenarios.
- The tests vary activity name and optional duration, not student identity fixture data.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow `batchRecord(...)` helpers for common DTO fixture fields.
- Reused the helpers in split-normalization and empty-activity-name tests.
- Preserved record splitting, invalid record, and batch insert capture assertions.

## 2026-06-01 Batch 187: Suggestion Entity Fixture

### Why

- `SuggestionServiceTest` repeated basic suggestion entity construction in reply scenarios.
- The tests vary reply validation and trimming behavior, not base suggestion fixture setup.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `suggestion(...)` helper for base suggestion entities.
- Reused the helper in blank-reply and trimmed-reply tests.
- Preserved reply rejection, update verification, and DTO reply assertions.

## 2026-06-01 Batch 188: Activity Startup Synchronizer Factory

### Why

- `ActivityStartupSynchronizerTest` repeated synchronizer construction in startup sync scenarios.
- The tests vary dev-mode wiring and mapper behavior, not status task service fixture setup.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow `newSynchronizer(...)` helpers for default and dev-mode constructors.
- Reused the helpers in startup sync query and mapper failure tests.
- Preserved base activity query verification and no-throw failure assertion.

## 2026-06-01 Batch 189: Activity Status Task Fixture

### Why

- `ActivityStatusTaskServiceTest` repeated activity status task entity construction across recovery scenarios.
- The tests vary event id, activity id, target status, and attempt count, not shared execute-at fixture setup.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `statusTask(...)` helper for dispatchable task fixtures.
- Reused the helper in invalid target status, interrupted publish, and serialization failure tests.
- Preserved mark-dead, mark-failed, serialization, and no-publish assertions.

## 2026-06-01 Batch 190: Batch Import User Fixture

### Why

- `BatchImportServiceTest` repeated user entity construction for the same student lookup fixture.
- The tests vary record normalization, invalid activity handling, and approve failure behavior, not user entity setup.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `user(...)` helper for student lookup fixtures.
- Reused the helper in submit-for-review and approve-batch scenarios.
- Preserved inserted-record capture, invalid record counts, and runtime row failure assertions.

## 2026-06-01 Batch 191: Volunteer Hour User Fixture

### Why

- `VolunteerHourGrantServiceTest` repeated user entity construction in imported activity grant setup.
- The test varies student number and starting total hours, not the user builder boilerplate.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `user(...)` helper for student hour fixtures.
- Reused the helper for both imported activity participants.
- Preserved participant normalization, total-hour increment, and grant record insert assertions.

## 2026-06-01 Batch 192: Pending Activity User Fixture

### Why

- `PendingActivityServiceTest` repeated basic user entity construction for participant lookup fixtures.
- The test varies participant normalization and de-duplication, not user builder setup.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `user(...)` helper for participant lookup fixtures.
- Reused the helper for both normalized participants.
- Preserved participant capture, lookup verification, and no-extra-interactions assertion.

## 2026-06-01 Batch 193: Authorization Activity DTO Fixture

### Why

- `AuthorizationControllerTest` repeated existing activity DTO construction in owner authorization scenarios.
- The tests vary delete and update behavior, not the id/functionary fixture setup.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `activity(...)` helper for existing activity DTO fixtures.
- Reused the helper in delete-owner and update-owner authorization tests.
- Preserved non-owner rejection, owner success, and functionary preservation assertions.

## 2026-06-01 Batch 194: Security Matcher Activity DTO Fixture

### Why

- `SecurityMatcherIntegrationTest` repeated owned activity DTO construction in activity owner security scenarios.
- The tests vary delete and update requests, not common id/functionary DTO setup.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added narrow `activity(...)` helpers for owner activity DTO fixtures with optional names.
- Reused the helpers in delete-owner and update-owner security tests.
- Preserved forbidden/ok status assertions and response functionary verification.

## 2026-06-01 Batch 195: Activity Cover Request Fixture

### Why

- `ActivityServiceTest` repeated activity request DTO construction in cover upload failure paths.
- The tests vary create versus update behavior, not the common name/type/cover-file request setup.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `activityRequest(...)` helper for cover upload request DTO builders.
- Reused the helper in create and update cover upload failure tests.
- Preserved upload exception setup and no-insert/no-update assertions.

## 2026-06-01 Batch 196: Activity Update Request Fixture

### Why

- `ActivityServiceTest` repeated activity update request DTO construction in update behavior scenarios.
- The tests vary review-field clearing and participant replacement, not common functionary/name/type request setup.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `updateRequest()` helper for shared activity update DTO builder fields.
- Reused the helper in resubmit-after-failed-review and participant replacement tests.
- Preserved review-field assertions and captured participant replacement assertions.

## 2026-06-01 Batch 197: Personal Hour Request DTO Fixture

### Why

- `PersonalHourRequestServiceTest` repeated personal hour request DTO construction in submit request scenarios.
- The tests vary applicant lookup and attachment upload behavior, not request DTO builder setup.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `request(...)` helper for submit request DTO fixtures with optional files.
- Reused the helper in missing-applicant and attachment upload failure tests.
- Preserved applicant-not-found assertions and no-insert/no-attachment assertions.

## 2026-06-01 Batch 198: Authorization Principal Fixture

### Why

- `AuthorizationControllerTest` repeated `UserPrincipal` fixture construction across authorization scenarios.
- The tests vary student number, role, and username, not the object construction mechanics.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `principal(...)` helper for controller security fixtures.
- Reused the helper in self-access, pending-activity, create, review, delete, and update checks.
- Preserved exception assertions, status assertions, and service interaction verification.

## 2026-06-01 Batch 199: Business Operation Log Fixture

### Why

- `BusinessOperationLogServiceTest` repeated business operation log VO construction in write/flush scenarios.
- The tests vary buffer and serialization behavior, not the common action-only log fixture setup.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `log(...)` helper for action-only business operation log fixtures.
- Reused the helper in fallback-buffer and serialization-retry tests.
- Preserved index verification, no-index-on-failure verification, and retry assertions.

## 2026-06-01 Batch 200: Security Matcher Authentication Fixture

### Why

- `SecurityMatcherIntegrationTest` repeated MockMvc authentication setup with `UserPrincipal` fixtures.
- The tests vary identity and role, not the mechanics of wrapping a principal as request authentication.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `auth(...)` helper returning a MockMvc request post-processor.
- Reused the helper in activity create, query, delete, and update security checks.
- Preserved HTTP status assertions, JSON code assertions, and mocked activity service setup.

## 2026-06-01 Batch 201: Business Operation Aspect Principal Fixture

### Why

- `BusinessOperationAspectTest` repeated `UserPrincipal` fixture construction in principal source scenarios.
- The tests vary where the principal is discovered, not the object construction mechanics.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a narrow `principal(...)` helper for audit aspect principal fixtures.
- Reused the helper in method-argument and security-context principal tests.
- Preserved operator student number, role, IP, and status assertions.

## 2026-06-01 Batch 202: Security Matcher Activity Fixture Reuse

### Why

- `SecurityMatcherIntegrationTest` had a local `activity(...)` helper but still hand-built an equivalent created activity DTO.
- The create-security test varies authorization behavior, not DTO builder mechanics.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Reused the existing `activity(...)` helper for the created activity service response.
- Removed the duplicate inline `ActivityDTO` builder in the create security scenario.
- Preserved forbidden/ok status assertions and response functionary verification.

## 2026-06-01 Batch 203: Authorization Activity Response Fixture

### Why

- `AuthorizationControllerTest` already used an `activity(...)` helper for owner activity fixtures.
- The update-security scenario still hand-built an equivalent activity response DTO with only a name variation.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a name-aware overload of the existing `activity(...)` helper.
- Reused it for the update activity response fixture.
- Preserved non-owner rejection, success status, functionary preservation, and service update verification.

## 2026-06-01 Batch 204: Suggestion Reply Fixture

### Why

- `SuggestionServiceTest` already used a `suggestion(...)` helper for base suggestion fixtures.
- The reply-success scenario still hand-built an equivalent suggestion with reply/status variations.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Added a reply-aware overload of the existing `suggestion(...)` helper.
- Reused it for the replied suggestion returned by the mapper.
- Preserved reply trimming verification and DTO reply-content assertion.

## 2026-06-01 Batch 205: Batch Import Approval Batch Id Fixture

### Why

- `BatchImportServiceTest` repeated the same batch id across approve-service setup, mapper expectations, and fixture entities.
- The test varies row-failure handling, not the mechanics of keeping a batch id literal synchronized.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Introduced a local `batchId` fixture variable inside the approve-runtime-failure test.
- Reused it for pending batch lookup, status update, record lookup, record fixture, and service invocation.
- Preserved row-failure assertions and participant-count assertions.

## 2026-06-01 Batch 206: Activity Status Task Event Id Fixture

### Why

- `ActivityStatusTaskServiceTest` repeated event id literals across task fixtures and mapper verification in recovery scenarios.
- The tests vary task recovery behavior, not the mechanics of keeping event ids synchronized.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Introduced local `eventId` fixture variables in invalid-status, interrupted-publish, and serialization-failure tests.
- Reused each `eventId` for status task setup and mapper verification.
- Preserved mark-dead, mark-failed, no-publish, and interrupted-flag assertions.

## 2026-06-01 Batch 207: Activity Status Listener Message Id Fixtures

### Why

- `ActivityStatusListenerTest` repeated activity and event id literals across message payloads, mapper stubs, and verifications.
- The tests vary message parsing and retry behavior, not the mechanics of keeping fixture ids synchronized.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Introduced local `activityId` and `eventId` fixture variables in listener message scenarios.
- Reused those ids in legacy payloads, JSON payload formatting, mapper stubs, and task-service verification.
- Added a parameterized `updateMessage(...)` helper overload while preserving the existing default helper.

## 2026-06-01 Batch 208: Campus Identity Student Number Fixture

### Why

- `CampusIdentityServiceImplTest` repeated the same student number across response-shape and failure-path calls.
- The tests vary HTTP response handling, not the mechanics of keeping a student number literal synchronized.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Introduced local `studentNo` fixture variables in boolean/nested response, malformed JSON, and interrupted-call tests.
- Reused each `studentNo` for the service invocation under test.
- Preserved endpoint encoding, false-return, and interrupted-flag assertions.

## 2026-06-01 Batch 209: My Activity Student Number Fixture

### Why

- `MyActivityServiceTest` repeated the same student number across count, paged-query, and service-call setup.
- The test varies enum-string normalization, not the mechanics of keeping the student number literal synchronized.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Introduced a local `studentNo` fixture variable in the enum-normalization test.
- Reused it for mapper stubs and the service invocation.
- Preserved page item enum normalization assertions.

## 2026-06-01 Batch 210: Personal Hour Request Id Fixtures

### Why

- `PersonalHourRequestServiceTest` repeated request and applicant ids across stubs, fixtures, and service calls.
- The tests vary blank-reason rejection and upload-failure behavior, not the mechanics of keeping ids synchronized.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Introduced a local `requestId` fixture variable in the blank-review-reason test.
- Introduced a local `studentNo` fixture variable in the attachment-upload-failure test.
- Reused those ids for mapper stubs, entity fixtures, and service invocations.

## 2026-06-01 Batch 211: Volunteer Hour Grant Id Fixtures

### Why

- `VolunteerHourGrantServiceTest` repeated activity and student ids across grant setup, participants, and verifications.
- The tests vary participant normalization and non-ended activity handling, not the mechanics of keeping ids synchronized.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Introduced local activity and student id fixture variables in the imported-activity grant test.
- Reused those ids for user stubs, participant input, service invocation, and mapper verification.
- Introduced local activity and participant id fixtures in the non-ended completed-activity test.

## 2026-06-01 Batch 212: Pending Activity Participant Fixtures

### Why

- `PendingActivityServiceTest` repeated participant student numbers across user stubs, messy participant input, expected normalized output, and verification.
- The test varies participant normalization, not the mechanics of keeping participant ids synchronized.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Introduced local `firstStudentNo` and `secondStudentNo` fixture variables in the participant-normalization test.
- Reused those ids for user lookup stubs, participant input, expected normalized participants, and mapper verification.
- Preserved duplicate/blank/null participant normalization assertions.

## 2026-06-01 Batch 213: JWT Subject Fixture

### Why

- `JWTUtilsTest` repeated the same subject literal while generating access tokens for parse-result scenarios.
- The tests vary token validity and signature behavior, not the mechanics of keeping a subject literal synchronized.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Introduced local `subject` fixture variables in malformed-signature and valid-token tests.
- Reused each `subject` for access-token generation.
- Preserved parse success/failure and token-type assertions.

## 2026-06-01 Batch 214: WebSocket JWT Subject Fixture

### Why

- `WebSocketJwtAuthInterceptorTest` repeated the same subject across claims, user lookup stubbing, and mapper verification.
- The test varies lookup-failure propagation, not the mechanics of keeping the subject literal synchronized.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Introduced a local `subject` fixture variable in the user-lookup-failure test.
- Reused it for claims, mapper stubbing, and verification.
- Preserved unauthorized malformed-token and lookup-failure assertions.

## 2026-06-01 Batch 215: Servlet JWT Subject Fixture

### Why

- `JwtAuthenticationFilterTest` repeated the same subject across claims, user lookup stubbing, and mapper verification.
- The test varies lookup-failure propagation, not the mechanics of keeping the subject literal synchronized.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Introduced a local `subject` fixture variable in the user-lookup-failure test.
- Reused it for claims, mapper stubbing, and verification.
- Preserved invalid-token fallthrough and lookup-failure assertions.

## 2026-06-01 Batch 216: System Metrics WebSocket Session Id Fixtures

### Why

- `SystemMetricsWebSocketHandlerTest` repeated session id literals while setting up connection-limit scenarios.
- The tests vary authorization and max-connection behavior, not the mechanics of keeping session ids synchronized.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Introduced local session id fixture variables in unauthorized and connection-limit tests.
- Reused those ids when creating mocked WebSocket sessions.
- Introduced a local principal student number fixture in the authenticated-attributes helper.
- Preserved close-status and client-count assertions.

## 2026-06-01 Batch 217: Suggestion Id Fixtures

### Why

- `SuggestionServiceTest` repeated suggestion and student ids across create/reply stubs, fixtures, service calls, and verification.
- The tests vary validation and trimming behavior, not the mechanics of keeping ids synchronized.
- This is test fixture cleanup only; production behavior should remain unchanged.

### How

- Introduced local `studentNo` fixture variables in create-related tests.
- Introduced local `suggestionId` and `studentNo` fixture variables in reply-related tests.
- Preserved create validation, DTO trimming, reply validation, and update verification assertions.

## 2026-06-01 Batch 218: Authorization Controller User Lookup Student Fixture

### Why

- `AuthorizationControllerTest` repeated the same student number literals across user setup, service stubbing, controller calls, and rejection checks.
- The test verifies self-lookup authorization, not manual synchronization of repeated string literals.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced local `studentNo` and `otherStudentNo` fixtures in `userLookupAllowsSelfButRejectsOthers`.
- Reused those fixtures for the DTO, service stub, principal setup, controller calls, and rejection path.
- Preserved the self-lookup success assertion and non-self rejection assertion.

## 2026-06-01 Batch 219: Authorization Controller Pending Query Student Fixture

### Why

- `AuthorizationControllerTest` repeated the same non-admin student number across pending activity query setup and service verifications.
- The test verifies submitted-by scoping for non-admin users, not manual synchronization of repeated string literals.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `studentNo` fixture in `pendingActivityQueryForcesSubmittedByForNonAdmin`.
- Reused it for principal setup and both pending activity service verifications.
- Preserved the forced submitted-by behavior assertions.

## 2026-06-01 Batch 220: Authorization Controller Activity Create Student Fixtures

### Why

- `AuthorizationControllerTest` repeated requester and functionary student numbers across activity creation authorization setup, service stubs, and assertions.
- The test verifies role gating and functionary assignment from the principal, not manual synchronization of repeated student number literals.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced local `studentNo` and `functionaryStudentNo` fixtures in `activityCreateRequiresPrivilegedRoleAndUsesPrincipalStudentNo`.
- Reused them for rejection setup, created DTO setup, privileged principal setup, and the final functionary assertion.
- Preserved the unauthorized user rejection and privileged creation assertions.

## 2026-06-01 Batch 221: Authorization Controller Activity Review Fixtures

### Why

- `AuthorizationControllerTest` kept review authorization fixtures inline in the principal and controller call.
- The test verifies that a non-admin user is rejected before the service call, not the mechanics of repeated literal placement.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced local `studentNo` and `activityId` fixtures in `activityReviewRequiresAdminBeforeServiceCall`.
- Reused them for the principal setup and review call.
- Preserved the rejection assertion and no-service-call verification.

## 2026-06-01 Batch 222: Authorization Controller Activity Enroll Id Fixture

### Why

- `AuthorizationControllerTest` kept the enroll activity id inline in the missing-principal rejection path.
- The test verifies that missing principals are rejected before service calls, not the mechanics of inline id literals.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `activityId` fixture in `activityEnrollRejectsMissingPrincipalBeforeServiceCall`.
- Reused it for the enroll call.
- Preserved the rejection assertion and no-service-call verification.

## 2026-06-01 Batch 223: Authorization Controller Activity Delete Id Fixture

### Why

- `AuthorizationControllerTest` repeated the same activity id across delete setup, service stubbing, controller calls, and verification.
- The test verifies owner authorization for deletion, not manual synchronization of repeated id literals.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `activityId` fixture in `activityDeleteRejectsNonOwner`.
- Reused it for the existing activity DTO, service stub, rejected delete call, accepted delete call, and delete verification.
- Preserved non-owner rejection and owner deletion assertions.

## 2026-06-01 Batch 224: Authorization Controller Activity Update Id Fixture

### Why

- `AuthorizationControllerTest` repeated the same activity id across update setup, service stubbing, controller calls, and verification.
- The test verifies owner authorization and functionary preservation for updates, not manual synchronization of repeated id literals.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `activityId` fixture in `activityUpdateRejectsNonOwnerAndPreservesExistingFunctionary`.
- Reused it for existing and updated DTO setup, service stubs, rejected update call, accepted update call, and update verification.
- Preserved non-owner rejection, owner update success, and existing functionary preservation assertions.

## 2026-06-01 Batch 225: Authorization Controller Activity Update Owner Fixture

### Why

- `AuthorizationControllerTest` repeated the owner student number across update DTO setup, owner principal setup, and the preservation assertion.
- The test verifies that owner updates preserve the existing functionary, not manual synchronization of repeated owner literals.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `ownerStudentNo` fixture in `activityUpdateRejectsNonOwnerAndPreservesExistingFunctionary`.
- Reused it for existing and updated DTO setup, owner principal setup, and the preserved functionary assertion.
- Preserved non-owner rejection, owner update success, and update verification.

## 2026-06-01 Batch 226: Authorization Controller Activity Delete Owner Fixture

### Why

- `AuthorizationControllerTest` repeated the owner student number across delete setup and owner principal setup.
- The test verifies non-owner rejection and owner deletion, not manual synchronization of repeated owner literals.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `ownerStudentNo` fixture in `activityDeleteRejectsNonOwner`.
- Reused it for existing activity DTO setup and owner principal setup.
- Preserved non-owner rejection, owner deletion, and delete service verification.

## 2026-06-01 Batch 227: Authorization Controller Pending Activity Detail Id Fixture

### Why

- `AuthorizationControllerTest` repeated the same pending activity id across detail DTO setup, service stubbing, and controller invocation.
- The test verifies non-owner rejection for pending activity detail access, not manual synchronization of repeated id literals.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `pendingActivityId` fixture in `pendingActivityDetailRejectsNonOwner`.
- Reused it for DTO setup, service stubbing, and the rejected controller call.
- Preserved the non-owner rejection assertion.

## 2026-06-01 Batch 228: Authorization Controller Activity Create Name Fixture

### Why

- `AuthorizationControllerTest` repeated the activity name across create request setup and created DTO setup.
- The test verifies privileged creation behavior and principal functionary assignment, not manual synchronization of repeated name literals.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `activityName` fixture in `activityCreateRequiresPrivilegedRoleAndUsesPrincipalStudentNo`.
- Reused it for request and created DTO setup.
- Preserved non-privileged rejection, privileged creation success, and functionary assignment assertions.

## 2026-06-01 Batch 229: Authorization Controller Activity Create Description Fixture

### Why

- `AuthorizationControllerTest` repeated the activity description across create request setup and created DTO setup.
- The test verifies privileged creation behavior and principal functionary assignment, not manual synchronization of repeated description literals.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `activityDescription` fixture in `activityCreateRequiresPrivilegedRoleAndUsesPrincipalStudentNo`.
- Reused it for request and created DTO setup.
- Preserved non-privileged rejection, privileged creation success, and functionary assignment assertions.

## 2026-06-01 Batch 230: Authorization Controller Activity Create Type Fixture

### Why

- `AuthorizationControllerTest` repeated the activity type across create request setup and created DTO setup.
- The test verifies privileged creation behavior and principal functionary assignment, not manual synchronization of repeated type constants.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `activityType` fixture in `activityCreateRequiresPrivilegedRoleAndUsesPrincipalStudentNo`.
- Reused it for request and created DTO setup.
- Preserved non-privileged rejection, privileged creation success, and functionary assignment assertions.

## 2026-06-01 Batch 231: Authorization Controller Activity Update Name Fixture

### Why

- `AuthorizationControllerTest` repeated the updated activity name across update request setup and updated DTO setup.
- The test verifies owner authorization and existing functionary preservation, not manual synchronization of repeated update name literals.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `updatedActivityName` fixture in `activityUpdateRejectsNonOwnerAndPreservesExistingFunctionary`.
- Reused it for update request setup and updated DTO setup.
- Preserved non-owner rejection, owner update success, and update verification.

## 2026-06-01 Batch 232: Authorization Controller Activity Update Type Fixture

### Why

- `AuthorizationControllerTest` embedded the updated activity type directly in the update request setup.
- The test verifies owner authorization and existing functionary preservation, not the concrete spelling of the type fixture inside the builder chain.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `updatedActivityType` fixture in `activityUpdateRejectsNonOwnerAndPreservesExistingFunctionary`.
- Reused it for update request setup.
- Preserved non-owner rejection, owner update success, and update verification.

## 2026-06-01 Batch 233: Authorization Controller User Lookup Username Fixture

### Why

- `AuthorizationControllerTest` repeated the self user's username across user DTO setup and self principal setup.
- The test verifies self lookup authorization, not manual synchronization of repeated username literals.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `username` fixture in `userLookupAllowsSelfButRejectsOthers`.
- Reused it for user DTO setup and self principal setup.
- Preserved self lookup success and other-user rejection assertions.

## 2026-06-01 Batch 234: Authorization Controller User Lookup Other Username Fixture

### Why

- `AuthorizationControllerTest` embedded the rejected user's username directly in the other-user principal setup.
- The test verifies student number authorization boundaries, not the concrete spelling of the rejected user's display name.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `otherUsername` fixture in `userLookupAllowsSelfButRejectsOthers`.
- Reused it for the rejected other-user principal setup.
- Preserved self lookup success and other-user rejection assertions.

## 2026-06-01 Batch 235: Authorization Controller User Lookup Role Fixture

### Why

- `AuthorizationControllerTest` repeated the user role across self and rejected principal setup in the user lookup authorization test.
- The test verifies student number authorization boundaries, not manual synchronization of repeated role literals.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `userRole` fixture in `userLookupAllowsSelfButRejectsOthers`.
- Reused it for self and rejected principal setup.
- Preserved self lookup success and other-user rejection assertions.

## 2026-06-01 Batch 236: Authorization Controller Pending Activity Query Username Fixture

### Why

- `AuthorizationControllerTest` embedded the non-admin principal username directly in the pending activity query test.
- The test verifies submitted-by filtering by student number, not the concrete spelling of the display name.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `username` fixture in `pendingActivityQueryForcesSubmittedByForNonAdmin`.
- Reused it for the non-admin principal setup.
- Preserved submitted-by count and paged-list verification.

## 2026-06-01 Batch 237: Authorization Controller Pending Activity Query Role Fixture

### Why

- `AuthorizationControllerTest` embedded the non-admin principal role directly in the pending activity query test.
- The test verifies submitted-by filtering by student number, not the concrete spelling of the role fixture inside the principal setup.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `userRole` fixture in `pendingActivityQueryForcesSubmittedByForNonAdmin`.
- Reused it for the non-admin principal setup.
- Preserved submitted-by count and paged-list verification.

## 2026-06-01 Batch 238: Authorization Controller Pending Activity Detail Other Username Fixture

### Why

- `AuthorizationControllerTest` embedded the rejected user's username directly in the pending activity detail principal setup.
- The test verifies owner-only access by student number, not the concrete spelling of the rejected user's display name.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `otherUsername` fixture in `pendingActivityDetailRejectsNonOwner`.
- Reused it for the rejected other-user principal setup.
- Preserved the non-owner rejection assertion.

## 2026-06-01 Batch 239: Authorization Controller Pending Activity Detail Role Fixture

### Why

- `AuthorizationControllerTest` embedded the rejected user's role directly in the pending activity detail principal setup.
- The test verifies owner-only access by student number, not the concrete spelling of the role fixture inside the principal setup.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `userRole` fixture in `pendingActivityDetailRejectsNonOwner`.
- Reused it for the rejected other-user principal setup.
- Preserved the non-owner rejection assertion.

## 2026-06-01 Batch 240: Authorization Controller Pending Activity Detail Owner Fixture

### Why

- `AuthorizationControllerTest` embedded the pending activity owner's student number directly in the DTO setup.
- The test verifies that a different principal cannot read another student's pending activity, not the concrete spelling of the owner fixture.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `ownerStudentNo` fixture in `pendingActivityDetailRejectsNonOwner`.
- Reused it for the DTO submitted-by setup.
- Preserved the non-owner rejection assertion.

## 2026-06-01 Batch 241: Authorization Controller Activity Create Id Fixture

### Why

- `AuthorizationControllerTest` embedded the created activity id directly in the response DTO fixture.
- The create-authorization test verifies role gating and principal student-number assignment, not the concrete activity id spelling.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `activityId` fixture in `activityCreateRequiresPrivilegedRoleAndUsesPrincipalStudentNo`.
- Reused it for the created DTO id setup.
- Preserved the role rejection, successful creation, and functionary assignment assertions.

## 2026-06-01 Batch 242: Authorization Controller Activity Create Rejected Role Fixture

### Why

- `AuthorizationControllerTest` embedded the rejected principal's role directly in the activity-create authorization assertion.
- The test verifies that ordinary users cannot create activities, not the concrete spelling of the role fixture inside the principal setup.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `userRole` fixture in `activityCreateRequiresPrivilegedRoleAndUsesPrincipalStudentNo`.
- Reused it for the rejected ordinary-user principal setup.
- Preserved the role rejection, successful creation, and functionary assignment assertions.

## 2026-06-01 Batch 243: Authorization Controller Activity Create Rejected Username Fixture

### Why

- `AuthorizationControllerTest` embedded the rejected principal's username directly in the activity-create authorization assertion.
- The test verifies that ordinary users cannot create activities, not the concrete spelling of the display-name fixture.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `username` fixture in `activityCreateRequiresPrivilegedRoleAndUsesPrincipalStudentNo`.
- Reused it for the rejected ordinary-user principal setup.
- Preserved the role rejection, successful creation, and functionary assignment assertions.

## 2026-06-01 Batch 244: Authorization Controller Activity Create Functionary Role Fixture

### Why

- `AuthorizationControllerTest` embedded the successful principal's role directly in the activity-create call.
- The test verifies privileged activity creation and principal student-number assignment, not the concrete spelling of the role fixture inside the principal setup.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `functionaryRole` fixture in `activityCreateRequiresPrivilegedRoleAndUsesPrincipalStudentNo`.
- Reused it for the successful functionary principal setup.
- Preserved the role rejection, successful creation, and functionary assignment assertions.

## 2026-06-01 Batch 245: Authorization Controller Activity Create Functionary Username Fixture

### Why

- `AuthorizationControllerTest` embedded the successful principal's display name directly in the activity-create call.
- The test verifies privileged activity creation and principal student-number assignment, not the concrete spelling of the display-name fixture.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `functionaryUsername` fixture in `activityCreateRequiresPrivilegedRoleAndUsesPrincipalStudentNo`.
- Reused it for the successful functionary principal setup.
- Preserved the role rejection, successful creation, and functionary assignment assertions.

## 2026-06-01 Batch 246: Authorization Controller Activity Review Rejected Role Fixture

### Why

- `AuthorizationControllerTest` embedded the rejected principal's role directly in the activity-review authorization assertion.
- The test verifies that ordinary users cannot review activities, not the concrete spelling of the role fixture inside the principal setup.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `userRole` fixture in `activityReviewRequiresAdminBeforeServiceCall`.
- Reused it for the rejected ordinary-user principal setup.
- Preserved the pre-service-call rejection and no-service-interaction verification.

## 2026-06-01 Batch 247: Authorization Controller Activity Review Rejected Username Fixture

### Why

- `AuthorizationControllerTest` embedded the rejected principal's display name directly in the activity-review authorization assertion.
- The test verifies that ordinary users cannot review activities, not the concrete spelling of the display-name fixture.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `username` fixture in `activityReviewRequiresAdminBeforeServiceCall`.
- Reused it for the rejected ordinary-user principal setup.
- Preserved the pre-service-call rejection and no-service-interaction verification.

## 2026-06-01 Batch 248: Authorization Controller Activity Delete Other Student Fixture

### Why

- `AuthorizationControllerTest` embedded the non-owner principal's student number directly in the activity-delete rejection assertion.
- The test verifies owner-only deletion, not the concrete spelling of the rejected student-number fixture.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `otherStudentNo` fixture in `activityDeleteRejectsNonOwner`.
- Reused it for the rejected non-owner principal setup.
- Preserved the non-owner rejection, owner deletion, and service verification.

## 2026-06-01 Batch 249: Authorization Controller Activity Delete Functionary Role Fixture

### Why

- `AuthorizationControllerTest` embedded the functionary role directly in both activity-delete principal setups.
- The test verifies owner-only deletion, not the concrete spelling of the role fixture inside each principal.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `functionaryRole` fixture in `activityDeleteRejectsNonOwner`.
- Reused it for both rejected non-owner and successful owner principal setups.
- Preserved the non-owner rejection, owner deletion, and service verification.

## 2026-06-01 Batch 250: Authorization Controller Activity Delete Other Username Fixture

### Why

- `AuthorizationControllerTest` embedded the rejected non-owner principal's display name directly in the activity-delete assertion.
- The test verifies owner-only deletion, not the concrete spelling of the rejected display-name fixture.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `otherUsername` fixture in `activityDeleteRejectsNonOwner`.
- Reused it for the rejected non-owner principal setup.
- Preserved the non-owner rejection, owner deletion, and service verification.

## 2026-06-01 Batch 251: Authorization Controller Activity Delete Owner Username Fixture

### Why

- `AuthorizationControllerTest` embedded the successful owner principal's display name directly in the activity-delete call.
- The test verifies owner-only deletion, not the concrete spelling of the owner display-name fixture.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `ownerUsername` fixture in `activityDeleteRejectsNonOwner`.
- Reused it for the successful owner principal setup.
- Preserved the non-owner rejection, owner deletion, and service verification.

## 2026-06-01 Batch 252: Authorization Controller Activity Update Other Student Fixture

### Why

- `AuthorizationControllerTest` embedded the rejected non-owner principal's student number directly in the activity-update assertion.
- The test verifies owner-only update and functionary preservation, not the concrete spelling of the rejected student-number fixture.
- This is test fixture cleanup only; production behavior is unchanged.

### How

- Introduced a local `otherStudentNo` fixture in `activityUpdateRejectsNonOwnerAndPreservesExistingFunctionary`.
- Reused it for the rejected non-owner principal setup.
- Preserved the non-owner rejection, successful owner update, and service verification.
