# FixMyRide Service Manager

FixMyRide is a local Spring Boot and Thymeleaf application designed for an upper-level undergraduate Java course. A user can register a customer and vehicle, select one of three service types, submit an immediate or scheduled request, inspect the generated estimate and checklist, advance the job through a guarded workflow, search the service history, and view stream-generated reports.

The project deliberately includes an in-process H2 database, but excludes networking, deployment, security, and multithreaded processing. The service-bay boundary is ready for a later concurrency module without making concurrent state the website's source of truth.

## Run the application

Requirements:

- JDK 21 or newer
- Maven 3.9 or newer

From this directory:

```shell
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). Three mechanics and three illustrative service jobs are loaded on startup. The H2 console is available at [http://localhost:8080/h2-console](http://localhost:8080/h2-console) with JDBC URL `jdbc:h2:mem:fixmyride`, user `sa`, and a blank password.

Run the automated tests with:

```shell
mvn test
```

## Concepts demonstrated

| Java/course topic | Where to study it |
|---|---|
| Abstraction and inheritance | `ServiceJob` is an abstract JPA entity; `OilChange`, `TireService`, and `EngineRepair` specialize it. |
| Interfaces | `Billable`, `Schedulable`, and `Inspectable` describe independent capabilities. |
| Encapsulation and state | `ServiceJob.transitionTo` enforces the workflow instead of exposing a status setter. |
| Polymorphism | Controllers, repositories, queue, reports, and templates work with `ServiceJob` while overridden methods calculate distinct results. |
| Generics | `ServiceRepository<T extends ServiceJob>` supplies a bounded generic persistence contract. |
| Collections | The waiting bay uses `Deque<ServiceJob>` through a `WaitingJobQueue` interface. |
| Maps | `MechanicDirectory` creates a `Map<Long, Mechanic>`; reports build status, type, and workload maps. |
| Streams | Job search, revenue reduction, grouping, average duration, filtering, and workload reports. |
| Regular expressions | VIN, license plate, phone, and generated `OIL-1000`/`TIRE-1001`/`ENG-1002` service codes. |
| Validation | Jakarta Bean Validation plus a class-level `@ValidServiceRequest` constraint for subtype-specific rules. |
| Exceptions | Invalid transitions, unknown jobs, unavailable mechanics, and conflicting vehicle ownership. |
| Database | Spring Data JPA, entity relationships, transactions, inheritance mapping, and local H2. |
| MVC web application | Focused controllers, application services, request DTO, Thymeleaf forms, fragments, and server-side error handling. |
| Testing | Fast domain tests, a Spring service integration test, and full template/MVC smoke tests. |

## Architecture

```text
Browser / Thymeleaf
        │
        ▼
MVC controllers ── validated ServiceRequestForm
        │
        ▼
Application services ── ServiceJobFactory ── polymorphic domain model
        │                                      │
        ├── Spring Data repositories ──────────┴── H2 (authoritative state)
        │
        └── WaitingJobQueue ── JobProcessor ── SequentialJobProcessor
                                         future: ExecutorJobProcessor
```

The package responsibilities are:

- `domain`: entities, value enums, capability interfaces, calculations, checklists, and workflow rules.
- `web` and `validation`: the form DTO and cross-field validation.
- `repository`: Spring Data interfaces, including the bounded generic repository.
- `service`: use cases, subtype factory, service-code generation, map/stream reports, and transaction boundaries.
- `processing`: the replaceable FIFO queue and synchronous service-bay processor.
- `controller`: HTTP routing and translation between views and application services.
- `config`: seeded classroom data only.

## Suggested classroom sequence

1. Run `ServiceJobPolymorphismTest` and identify dynamic dispatch in `map(ServiceJob::calculateCost)`.
2. Trace a request from `service-job-form.html` to `ServiceJobController`, validation, `ServiceRequestService`, and `ServiceJobFactory`.
3. Compare the three cost and checklist algorithms. Add a fourth subtype such as `BrakeService` without changing `ReportService`.
4. Inspect the single-table inheritance data through the H2 console.
5. Add a report using `filter`, `groupingBy`, and `summarizingLong`.
6. Trigger an invalid status transition in a test and explain why domain state is protected.
7. Complete the concurrency extension below after the synchronous design is understood.

## Future concurrency extension

Concurrency is intentionally not active in this version. A later lab can add it in a contained module:

1. Implement `BlockingWaitingJobQueue` with `BlockingQueue<Long>` or `BlockingQueue<ServiceJob>`.
2. Implement `ExecutorJobProcessor` with a fixed `ExecutorService`, one worker per simulated service bay.
3. Keep job transitions transactional and reload each queued job from `ServiceJobRepository` before updating it.
4. Add optimistic locking (`@Version`) to `ServiceJob` so the database prevents two workers from claiming the same job.
5. Use `CompletableFuture` only for simulated diagnostics, not as authoritative job storage.
6. Activate the new beans under a Spring profile such as `concurrent-lab`; leave sequential mode as the deterministic default for web and unit tests.

That extension preserves the existing controllers because they depend only on `JobProcessor` and `WaitingJobQueue`.

## Current scope decisions

- H2 is in memory, so restarting the application restores the three seed jobs.
- There is no login or role-based authorization yet.
- Scheduling records a requested time but does not detect overlapping appointments yet; that makes a good transactions/exceptions exercise.
- There are no external recall/parts HTTP clients and no Docker/cloud configuration in this phase.
