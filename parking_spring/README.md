# Smart Parking Website (Spring Boot)

This module contains the web application and backend API for the Smart Parking System.

## What this part does

- Shows parking spot status (Free / Occupied / Reserved)
- Allows users to reserve a parking spot using a vehicle number
- Receives status updates from ESP32 devices through REST APIs
- Stores spot and reservation data in PostgreSQL

## Tech Stack

- Java + Spring Boot
- Spring Data JPA
- Thymeleaf (HTML templates)
- PostgreSQL

## Project Location

Main Spring Boot project is inside:

`parking_spring/demo`

## Run the Website

1. Open terminal in the `parking_spring/demo` folder
2. Configure database settings in `src/main/resources/application.properties`
3. Run the app:

For Linux/macOS:

```bash
./mvnw spring-boot:run
```

For Windows (PowerShell/cmd):

```bash
mvnw.cmd spring-boot:run
```

4. Open browser:

`http://localhost:8080`

## Important Folders

- `src/main/java/com/app/demo/controller` - web and API controllers
- `src/main/java/com/app/demo/model` - entities
- `src/main/java/com/app/demo/repository` - JPA repositories
- `src/main/resources/templates` - Thymeleaf HTML pages
- `src/main/resources/application.properties` - app and DB configuration

## Notes

- Make sure PostgreSQL is running before starting the app.
- ESP32 and Python ANPR modules can send or consume data from this backend.
