# v26.6.10 — login language registration hotfix

Carried into release candidate: v27.2.4.
Status: v26.6.10.

Goal: when a new user chooses RU/EN on `login.html` before registration, the first logged-in app screen must keep that language instead of being reset by the default profile language.

What changed:

- `login.html` sends `languagePreference` with the registration request.
- `AuthController.RegisterRequest` accepts `languagePreference`.
- New `AppUser` records apply the requested language before save.
- Registration response returns the saved `languagePreference`.
- `RegistrationTest` covers EN registration language persistence.

Compatibility:

- Existing clients that send only `username` and `password` keep working.
- Unknown or empty language values are normalized by `AppUser#setLanguagePreference` to `ru`.
- Existing users are not changed.
