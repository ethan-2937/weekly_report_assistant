# Submission status XLSX export

- Method/path: `GET /api/files/{week}/submission-status/download`
- Input: ISO week path variable such as `2026-W28`; no request body and no report content accepted.
- Authorization: the existing authenticated-user and service-layer report scope filter is applied before workbook generation.
- Success: `200` with `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` and an ASCII-safe `Content-Disposition` attachment named `submission_status_<week>.xlsx` (UTF-8 filename metadata included by Spring).
- Workbook fields: status, name, department, leader flag, title, report department, submit time, template and compliance fields. User IDs, report IDs, tokens, paths, and report正文 are excluded.
- Errors: unauthenticated requests are `401`; authenticated accounts without a report scope are `403`; invalid or missing weeks/files are `400`/`404`; workbook generation failures are `500`. Error bodies are generic and contain no internal paths or identifiers.
