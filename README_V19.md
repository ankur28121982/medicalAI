# Medical Report AI V19

V19 generates each clarification question together with question-specific answer controls in the same extraction LLM call. The backend validates the controls and falls back to a text answer instead of showing generic Yes/No for a location, duration, severity, amount, timing, or similar question.

Every analysis remains independent. Saved reports/chat history remain viewable, but no saved report, old chat, prior run, or previous report ID is used as AI context. Only current-run files, typed details, profile fields and current-run answers are analyzed.
