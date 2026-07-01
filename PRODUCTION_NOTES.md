# Production Notes - Medical Report AI

## Critical risks

| Risk | Control implemented |
|---|---|
| AI misreads report | Shows extracted values before analysis; user can correct |
| Handwritten/blurred report | Model flags unclear/handwritten and backend refuses detailed analysis |
| User treats AI as doctor | Safety note in prompts, UI, and output |
| Medicine advice risk | Prompt blocks medicine names/doses/start/stop instructions |
| Health data leak | Backend encryption support, no Android shared report file storage |
| Cost explosion | Backend upload size limit |
| Abuse/rate spike | App token + per-device/IP rate limit |

## Not yet included

- Real login / user account.
- Cloud object storage.
- PostgreSQL migration.
- Payment.
- Doctor portal.
- Certified medical-device workflow.

For Play Store and real users, keep the app as a report explainer and doctor-prep tool, not a diagnostic medical device.
