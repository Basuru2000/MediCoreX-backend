# MediCoreX Backend

Spring Boot backend API for Healthcare Inventory Management System.

## Technologies
- Spring Boot 3.2.0
- Spring Security with JWT
- MySQL Database
- Maven

## Setup
1. Configure MySQL database
2. Update `application.properties`
3. Run: `mvn spring-boot:run`

## API Endpoints
- `/api/auth/*` - Authentication
- `/api/users/*` - User management
- `/api/products/*` - Product management (coming soon)

## Default Users
- admin/admin123 (Hospital Manager)
- staff/staff123 (Pharmacy Staff)
- procurement/proc123 (Procurement Officer)