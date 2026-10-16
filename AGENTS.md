# Password handling

Keep passwords only in memory. Never put passwords in `rememberSaveable`, `SavedStateHandle`, `Bundle`, preferences, or a database.
Clear all password fields on every form submission attempt, including local validation failures, and when leaving the form.
Apply this to login, signup, password change, password setup, and future password recovery/reset screens.
