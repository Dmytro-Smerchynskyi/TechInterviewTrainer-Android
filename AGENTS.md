# Password handling

Keep passwords only in memory. Never put passwords in `rememberSaveable`, `SavedStateHandle`, `Bundle`, preferences, or a database.
Keep password fields across configuration changes using ViewModel memory, without saved-state persistence.
Clear all password fields only after a successful form request and when leaving the form. Keep them after local validation failures, request errors, and cancelled or failed Google reauthentication.
Apply this to login, signup, password change, password setup, and future password recovery/reset screens.
