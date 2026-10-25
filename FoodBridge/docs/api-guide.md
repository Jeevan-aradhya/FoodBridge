# API guide

Interactive docs: `/swagger-ui.html`. All responses are JSON. Errors share one shape:

```json
{ "timestamp": "2026-10-25T10:15:30", "status": 409, "error": "Conflict",
  "message": "Only 5 portions remain. Request a smaller quantity.",
  "path": "/api/donations/7/requests", "fieldErrors": null }
```

Validation errors return `400` with `fieldErrors` (`{"quantity": "Quantity must be at least 1"}`). Business-rule errors are `409`, missing items `404`, wrong role or owner `403`, not logged in `401`/redirect.

## Authentication

Session cookie (`JSESSIONID`) obtained from the website login (`POST /login`). State-changing requests need the CSRF token in header `X-CSRF-TOKEN`.

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | public | Create DONOR / RECIPIENT / VOLUNTEER (never ADMIN) |
| GET | `/api/auth/me` | logged in | Current user |
| GET | `/api/stats/impact` | public | Impact numbers computed from real records |

## Donations and requests

| Method | Path | Who |
|---|---|---|
| GET | `/api/donations?q=&category=&city=&minQuantity=&availability=AVAILABLE\|ALL&withinHours=&sort=deadline\|newest\|quantity&page=&size=` | public |
| GET | `/api/donations/{id}` | public (address only for involved users) |
| GET | `/api/donations/mine` | DONOR |
| POST / PUT / DELETE | `/api/donations`, `/api/donations/{id}` | DONOR (owner) |
| POST | `/api/donations/{id}/requests` body `{"quantity":4,"message":"..."}` | RECIPIENT |
| GET | `/api/requests/my` | RECIPIENT |
| GET | `/api/requests/incoming?status=` | DONOR |
| POST | `/api/requests/{id}/accept` / `reject` | DONOR (owner) |
| POST | `/api/requests/{id}/cancel` | RECIPIENT (owner) |

## Deliveries

| Method | Path | Who |
|---|---|---|
| GET | `/api/deliveries/open` | VOLUNTEER |
| GET | `/api/deliveries/mine` | any role (its own deliveries) |
| POST | `/api/deliveries/{id}/accept` | VOLUNTEER |
| POST | `/api/deliveries/{id}/status` body `{"status":"PICKED_UP"}` | assigned VOLUNTEER |

## Notifications, reports, admin

| Method | Path | Who |
|---|---|---|
| GET | `/api/notifications`, `/api/notifications/unread-count` | logged in |
| POST | `/api/notifications/{id}/read` | logged in |
| GET | `/api/reports/weekly` | DONOR (own) / ADMIN (all) |
| GET | `/api/admin/stats`, `/api/admin/users`, `/api/admin/donations`, `/api/admin/audit` | ADMIN |
| PATCH | `/api/admin/users/{id}/status` body `{"status":"SUSPENDED"}` | ADMIN |
| POST | `/api/admin/donations/{id}/disable` body `{"reason":"..."}`, `/enable` | ADMIN |

Health: `GET /actuator/health`.
