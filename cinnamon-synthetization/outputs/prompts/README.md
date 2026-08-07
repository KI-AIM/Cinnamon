# LLM Prompt Logging

Dieses Verzeichnis enthält die Protokolle aller Prompts und Antworten, die während der LLM-basierten Synthetisierung ausgetauscht wurden.

## Struktur

Die Logs werden in Unterverzeichnissen organisiert, die nach `session_key` benannt sind:

```
outputs/prompts/
├── {session_key}/
│   ├── prompt_20240521_143022_a1b2c3d4.txt
│   ├── prompt_20240521_143045_e5f6g7h8.txt
│   └── ...
├── {other_session_key}/
│   └── ...
└── default/
    └── ... (für Sessions ohne expliziten session_key)
```

## Log-Datei-Format

Jede `.txt`-Datei enthält:

```
================================================================================
LLM PROMPT AND RESPONSE LOG
================================================================================
Timestamp: 2024-05-21 14:30:22
Session ID: abc123def456

METADATA:
----------------------------------------
provider: ollama
model: llama3
temperature: 0.7
top_p: 0.9
max_tokens: 1000

SYSTEM PROMPT:
----------------------------------------
Return only the requested JSON or text content with no extra formatting.

USER PROMPT:
----------------------------------------
[Der vollständige Prompt, der an das LLM gesendet wurde]

LLM RESPONSE:
----------------------------------------
[Die vollständige Antwort des LLM]

================================================================================
```

## Verwendungszweck

Diese Logs dienen dazu:

1. **Nachvollziehbarkeit**: Verstehen, welche Prompts das LLM erhält
2. **Evaluation**: Die Qualität der LLM-Antworten bewerten
3. **Debugging**: Probleme bei der Generierung identifizieren
4. **Optimierung**: Prompt-Templates verbessern

## Aktivierung

Die Prompt-Protokollierung wird automatisch aktiviert, wenn:

1. Ein LLM-basierter Synthesizer verwendet wird
2. Ein `session_key` im Request bereitgestellt wird

Beispiel für einen Request mit Session-Key:
```
POST /start_synthetization_process/llm_tabular
Content-Type: multipart/form-data

session_key: my-session-123
callback: http://localhost:8000/callback
attribute_config: [file]
algorithm_config: [file]
data: [file]
```

## Unterstützte LLM-Synthesizer

- `llm_tabular` - LLM Dataset Generator
- `llm_nearest_neighbor_knowledge_grounded_text_synthesis` - LLM Knowledge-grounded Text Synthesis

## Konfiguration

Die Protokollierung kann durch folgende Umgebungsvariablen gesteuert werden (zukünftig):

- `CINNAMON_LLM_ENABLE_PROMPT_LOGGING=true/false` - Protokollierung aktivieren/deaktivieren
- `CINNAMON_LLM_LOG_DIR` - Benutzerdefiniertes Log-Verzeichnis

## Datenschutz

⚠️ **Wichtig**: Die Log-Dateien können sensible Informationen enthalten:
- Originaldaten aus Few-Shot-Beispielen
- Generierte synthetische Daten
- Potenziell personenbezogene Informationen

Sicherstellen, dass:
- Log-Verzeichnisse angemessen geschützt sind
- Logs regelmäßig bereinigt werden
- Keine Logs in öffentliche Repositories gelangen
