# Distributed gRPC Microservices Project

This repository implements a modular, discoverable gRPC system in Java, featuring multiple services and a registry for
dynamic discovery. It extends the provided starter code with two built‑in services (CoffeePot & Sort) and an invented
Vigenère cipher service, fulfilling all assignment requirements.

## Features & Requirements

1. **Echo**: simple request/response (`parrot` RPC)
2. **Joke**: fetch a number of jokes and add new ones (`getJoke`, `setJoke` RPCs)
3. **CoffeePot**: manage brewing lifecycle (`brew`, `getCup`, `brewStatus` RPCs)
4. **Sort**: sort integer lists with three algorithms (MERGE, QUICK, INTERN) (`sort` RPC)
5. **Vigenère Cipher**: encode/decode text and view history (`encode`, `decode`, `history` RPCs)

All RPCs handle both valid and invalid inputs without crashing.

## Getting Started

### Prerequisites

* Java JDK 22
* Gradle 8.12 (via `./gradlew`)
* OS with network loopback support

### Build & Test

```bash
./gradlew clean build
# Compiles code, generates proto stubs, runs unit tests
```

### Running Services

#### 1. Start Registry (optional)

```bash
./gradlew runRegistryServer
# Defaults to localhost:9003
```

#### 2. Start Node (server)

```bash
./gradlew runNode
# Defaults to localhost:9099, no registry
# To register with registry:
./gradlew runNode -PregOn=true -PregHost=localhost -PregPort=9003
```

#### 3. Start Client (interactive)

```bash
# Static mode (direct to node):
./gradlew runClient -Phost=localhost -Pport=9099

# Dynamic (registry‑driven):
./gradlew runClient -Phost=localhost -Pport=9099 -PregOn=true -PregHost=localhost -PregPort=9003
```

#### 4. Auto‑Demo Mode

```bash
./gradlew runClient -Phost=localhost -Pport=9099 -Pauto=1
# Runs a full sequence of valid/invalid scenarios with formatted report
```

## How to Use

1. **Menu-driven client**:

    * On launch, choose from: Echo, Joke, Brew Coffee, Get Cup, Brew Status, Sort, Vigenère Encode, Vigenère Decode,
      Vigenère History.
    * Follow prompts for input values (e.g. message text, number of jokes, list to sort, plaintext/key).
2. **Auto‑Demo**:

    * No interactive input; scenarios run automatically and print a table of test results.

## Requirements Checklist

| Req | Description                                                           | Status |
|-----|-----------------------------------------------------------------------|:------:|
| 1   | `build.gradle`, `README.md`, project compiles & tests                 |   ✓    |
| 2a  | Project & feature description                                         |   ✓    |
| 2b  | Run commands copy‑pasteable                                           |   ✓    |
| 2c  | Usage instructions & prompt details                                   |   ✓    |
| 2d  | Requirements list with fulfillment status                             |   ✓    |
| 3   | `gradle runNode` & `gradle runClient` work by default                 |   ✓    |
| 4   | Two provided services (CoffeePot, Sort) implemented                   |   ✓    |
| 5   | Clear, robust menu‑based client interaction                           |   ✓    |
| 6   | Auto‑demo mode (`-Pauto=1`)                                           |   ✓    |
| 7   | Invented service (Vigenère) with persistent history and multiple RPCs |   ✓    |

---

Feel free to open issues or reach out with questions! Happy gRPC-ing.


---

## Protocol Design

All `.proto` files live under `src/main/proto/`. Each service follows protobuf v3 and generates Java stubs.

| Proto File       | Package    | RPCs                                                                                                                                        | Messages                                                                                                                                                                                                                                                                                                                           |
|------------------|------------|---------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `echo.proto`     | `services` | `parrot( ClientRequest ) → ServerResponse`                                                                                                  | `ClientRequest { string message }`,<br>`ServerResponse { bool isSuccess; string message; string error }`                                                                                                                                                                                                                           |
| `joke.proto`     | `services` | `getJoke( JokeReq ) → JokeRes`,<br>`setJoke( JokeSetReq ) → JokeSetRes`                                                                     | `JokeReq { int32 number }`,<br>`JokeRes { repeated string joke }`,<br>`JokeSetReq { string joke }`,<br>`JokeSetRes { bool ok }`                                                                                                                                                                                                    |
| `coffepot.proto` | `services` | `brew( Empty ) → BrewResponse`,<br>`getCup( Empty ) → GetCupResponse`,<br>`brewStatus( Empty ) → BrewStatusResponse`                        | `BrewResponse { bool isSuccess; string message; string error }`,<br>`GetCupResponse { bool isSuccess; string message; string error }`,<br>`BrewStatusResponse { BrewStatus status }`,<br>`BrewStatus { int32 minutes; int32 seconds; string message }`                                                                             |
| `sort.proto`     | `services` | `sort( SortRequest ) → SortResponse`                                                                                                        | `SortRequest { Algo algo; repeated int32 data }`,<br>`SortResponse { bool isSuccess; repeated int32 data; string error }`,<br>`enum Algo { MERGE, QUICK, INTERN }`                                                                                                                                                                 |
| `vigenere.proto` | `services` | `encode( EncodeRequest ) → EncodeResponse`,<br>`decode( DecodeRequest ) → DecodeResponse`,<br>`history( HistoryRequest ) → HistoryResponse` | `EncodeRequest { string plaintext; string key }`,<br>`EncodeResponse { string ciphertext; bool error; string errorMsg }`,<br>`DecodeRequest { string ciphertext; string key }`,<br>`DecodeResponse { string plaintext; bool error; string errorMsg }`,<br>`HistoryRequest {}`,<br>`HistoryResponse { repeated string operations }` |

Key design points:

- **Empty-like requests**: we define `HistoryRequest {}` instead of importing `google.protobuf.Empty`.
- **Repeated fields**: `JokeRes.joke`, `SortRequest.data`, `SortResponse.data`, `HistoryResponse.operations`.
- **Server-side state**: `CoffeePot` tracks `brewing` and `cupCount`; `Vigenere` tracks in-memory `history`.

---

## 1. Build & Run Commands

### 1.1 Compile & Tests

```bash
./gradlew clean build
```

### 1.2 Start Registry (optional)

```bash
./gradlew runRegistryServer  # default: localhost:9003
```

### 1.3 Start Node

```bash
./gradlew runNode           # default: localhost:9099, no registry
# or with registry registration:
./gradlew runNode -PregOn=true
```

### 1.4 Start Client

```bash
# static mode (no registry):
./gradlew runClient -Phost=localhost -Pport=9099

# dynamic registry-driven flow:
./gradlew runClient -Phost=localhost -Pport=9099 -PregHost=localhost -PregPort=9003 -PregOn=true

# auto-demo (hard-coded scenarios):
./gradlew runClient -Phost=localhost -Pport=9099 -Pauto=1
```

### 1.5 Alternative Unit Tests

```bash
./gradlew test    # verifies all services, including error cases
```

---

## 2. Usage Examples

### Static Menu (no registry)

```
1) Echo             → enter “Hello” → prints back “Hello”
2) Joke             → enter “3” → prints three jokes
3) Brew Coffee      → prints “Brewing started...”, then 30s later pot holds 5 cups
4) Get Cup          → prints “Enjoy your cup!” or error if empty/in-progress
5) Brew Status      → prints remaining brew time or cup count
6) Sort             → enter “5,2,9,1” & choose algo,
                        → prints sorted list
7) Vigenère Encode  → enter plaintext/key → prints ciphertext
8) Vigenère Decode  → enter ciphertext/key → prints plaintext
9) Vigenère History → prints list of all encode/decode ops
```

### Dynamic Flow (with registry)

1. Fetches `GetServices` from registry → lists all available RPCs
2. Select by number → fetches a node via `FindServer` → calls the chosen RPC

### Auto-Demo

Runs all RPCs in sequence, including:

- Valid & invalid cases (e.g. getCup before brewing)
- Encoding/decoding Vigenère with wrong key to show error handling

---

## 3. Completed Requirements

| Req # | Description                                                                                            | Status |
|-------|--------------------------------------------------------------------------------------------------------|--------|
| 1     | Gradle project with `build.gradle`, `README.md`, all sources                                           | ✓      |
| 2     | `gradle runNode` / `gradle runClient` work by default                                                  | ✓      |
| 3     | Two provided services implemented (CoffeePot, Sort)                                                    | ✓      |
| 4     | User interaction: static & dynamic menus, clear prompts                                                | ✓      |
| 5     | Full auto-demo mode (`-Pauto=1`)                                                                       | ✓      |
| 6     | Robustness: RPCs & client calls catch exceptions, never crash                                          | ✓      |
| 7     | Invented service (Vigenère) with ≥2 RPCs, inputs, distinct responses, repeated field, persistent state | ✓      |

---

## 4. Screencast

▶️ Video Link: [YouTube](https://www.youtube.com/playlist?list=PLNJf3PhE4U6D9uje1Qz3t28TqqqIoDkZQ)

---

*Happy gRPC-ing!*
