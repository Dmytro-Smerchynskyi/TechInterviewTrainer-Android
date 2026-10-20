# Interview Trainer — Account deletion page

Open `index.html` in a browser. The page is a standalone, responsive HTML document:
styles, JavaScript and two app screenshots are embedded. It makes no third-party
requests on page load and can be moved to any static HTTPS host as a single file.
Links to privacy/terms and Google Play are opened only when clicked.

## Confirmed contact and retention

The application owner supplied the following policy:

- Support: support@interviewtrainer.app.
- In-app deletion: account, training and service-held billing data are deleted
  immediately after a successful deletion.
- Email requests: deletion is completed within 90 days of receiving the request.
- Backup copies, including billing-data backups: retained for up to 90 days.
- Firebase Analytics and Crashlytics data: retained for no more than 90 days.
- Records held independently by Google Play remain subject to Google's policies.

The page contains a mailto link with a prefilled subject and request body.
It opens the user's email app; the page itself does not send email.
No contact or retention placeholders remain.

## Hosting

Upload `index.html` to a static HTTPS host. Suggested public URL:
https://interviewtrainer.app/delete-account

After upload, check the public page, the privacy/terms links and the mailto
request route before entering the URL in Google Play Console.

These are documentation changes. They do not configure backend deletion,
backup rotation or Firebase retention. Those systems must implement the policy
supplied by the application owner.

## Screenshots

The embedded light/dark Account Settings screenshots come from this app's
Compose previews captured during the Legal-settings change. They show the same
current deletion location and use demonstration profile information. No real
account was deleted or used to submit a deletion request.

The screenshots are embedded in the HTML; no separate image uploads are needed.

## Sources for the content

- App: AccountScreen.kt / AccountViewModel.kt / AuthRepository.kt / LocalUserData.kt.
- App Analytics event handling: docs/analytics.md.
- Google Play account deletion requirements:
  https://support.google.com/googleplay/android-developer/answer/13327111?hl=en
- Google Play subscription management:
  https://support.google.com/googleplay/answer/7018481?hl=en
