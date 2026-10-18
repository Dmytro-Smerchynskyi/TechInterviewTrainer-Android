# Google Play subscriptions

Product: `interview-trainer-pro`. Auto-renewing base plans: `weekly`, `monthly`.
Prices and regular base-plan offer tokens come from Google Play ProductDetails, never from app constants.

## Backend contract

- `GET /me/subscription` replaces `GET /me/usage` for quotas and subscription state.
- `billingAccountId` must be stable for the signed-in account and suitable for Google's obfuscated account ID.
- `POST /billing/google/verify` receives purchaseToken, productId, and basePlanId when known.
- Verification and subscription GET share SubscriptionResponse. Only their authenticated responses grant Pro or credits.
- Verification must be idempotent: retries and Restore Purchases can submit the same token again.
- Google Purchase exposes product IDs and purchase tokens, but **does not expose the purchased base-plan ID**. After process death or on another device, basePlanId may be absent in /verify; the backend must resolve it from the Google Play Developer API purchase token.
- Returning optional `basePlanId` in both responses lets the app mark the current weekly/monthly plan. Without it the app displays generic Pro status and leaves final duplicate-plan validation to Google Play.
- The new subscription response omits the questions allowance. That allowance is preserved from dashboard only while the plan matches; dashboard is refreshed after verification.

## Purchase lifecycle

1. Refresh subscription identity immediately before checkout; pass billingAccountId to setObfuscatedAccountId.
2. Refresh ProductDetails and select the regular offer for the chosen base plan.
3. For plan switching, pass the existing purchase token and use WITHOUT_PRORATION replacement. Google Play displays the final terms.
4. PENDING never grants access, verifies, or acknowledges.
5. For PURCHASED, encrypt and persist the purchase before sending /verify. After successful server verification, acknowledge only if needed.
6. Verification/acknowledgement failures retain the pending entry for foreground retries with backoff and recovery on the next app launch/resume.
7. Automatic recovery is scoped to billingAccountId. Explicit Restore Purchases allows the backend to decide whether an existing/legacy purchase can be linked.
8. Logout clears the queue with the rest of local account data. Purchases remain recoverable from Google Play; another app account is not automatically credited.

Purchase tokens are encrypted in the existing backup-excluded session preferences. Verification request/response and subscription metadata are excluded from HTTP logging. Tokens and Activity objects are never stored in SavedStateHandle.

## End-to-end testing

Publish a signed build to a Play Console test track, activate both base plans, and use a license tester with the same Google Play account. The backend must have Google Play Developer API subscription/order access configured before real verification can succeed. Pub/Sub notifications and automatic renewals are backend responsibilities; Android never calls /billing/google/notifications.

Check successful weekly/monthly purchases, cancellation, pending payment completion, restore on another device, account-link rejection, verification/acknowledgement network failures, plan replacement, and backend-driven expiry/revocation. The app's Manage in Google Play link opens Google's subscription management page.
