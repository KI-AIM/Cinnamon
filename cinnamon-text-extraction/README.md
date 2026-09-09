# Cinnamon Text Extraction

Small Instructor-based service for extracting structured values from free text.
It reads the same `CINNAMON_LLM_PROFILE_*` environment variables as
`cinnamon-synthetization`.

## API

```http
GET /actuator/health
GET /profiles
POST /configuration
POST /extract
```

Example request:

```json
{
  "profile": "Ollama Qwen3 8B (local)",
  "text": "The patient is 42 years old and has asthma.",
  "fields": [
    {"name": "age", "type": "integer", "description": "Patient age"},
    {"name": "diagnosis", "type": "string", "description": "Known diagnosis"}
  ]
}
```

Supported extraction field types are `boolean`, `date`, `date_time`, `decimal`, `integer`, and `string`.
String fields can define `allowed_values`; numeric fields can define `min_value` and `max_value`;
date and date-time fields require a `format`. Cinnamon's free-text type is only available as the source column.
Missing values are returned as `null`.

Fields can use type `object` with primitive nested `fields`. Instructor extracts each one as a list. In the table
response all lists are appended, `object_type` identifies their configured field, document-level values are
repeated, and `source_row_index` identifies the original row. A document without extracted objects still produces
one row with empty object values. Nested fields with the same name share a column and must have compatible types.

`POST /extract-table` accepts the extraction configuration plus `rows` containing `row_index` and `text`.
It extracts each non-empty text in a separate LLM request and returns consistently shaped `columns` and
`rows`, ready for a tabular frontend. The additional `evidence.rows` artifact contains, for every row and
field, the normalized `value`, its `value_span`, an exact source-text `evidence` quote and its
`evidence_span`. Spans use zero-based, end-exclusive character offsets and are checked against the source
text before they are returned. `TEXT_EXTRACTION_LLM_TIMEOUT_SECONDS` controls the timeout per text;
`TEXT_EXTRACTION_REQUEST_TIMEOUT_SECONDS` controls the Gunicorn timeout for the full table request.
