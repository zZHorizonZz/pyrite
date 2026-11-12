# Accounts

An account is a person or a service, the resource `pyrite.dev/Account`, with its identifiers, factors and sessions
under it. The authentication service creates accounts, proves through the flows of your declaration that a caller
holds one, and issues the sessions that authenticate later calls. Pyrite makes the codes and checks the answers; your
application renders the challenges and sends the codes. See [About accounts and flows](about-accounts-and-flows.md).

## Features

**Passwords and sessions.** Create accounts, add email addresses to them, enrol a password, sign in, and exchange the
session token for the principal on each call. See [Sign in with a password](sign-in-with-a-password.md).

**Second factors.** Require a TOTP code from an authenticator app besides the password, and give out recovery codes
for when the app is lost. See [Add a second factor](add-a-second-factor.md).

**Passkeys.** Enrol a WebAuthn passkey in the browser, and sign in with it in a phishing-resistant stage. See
[Add a passkey](add-a-passkey.md).

**Verified identifiers.** Send a code to an address of an account, and mark the address as verified when the person
types it. See [Verify an email address](verify-an-email-address.md).

**Recovery.** Let a person who forgot their password prove their address, set a new password, and end every session
of the account. See [Recover an account](recover-an-account.md).

**Interceptors and observers.** Run your own code at the start, the stages, the end and the failures of flows, send
codes from your application, and hear of each account and session. See [Intercept flows](intercept-flows.md).

The errors of accounts, flows and sessions are in [Troubleshooting accounts](troubleshooting-accounts.md).

## Related

- [Access](../access/README.md)
- [Keys](../keys/README.md)
- [Options](../deployment/options.md)
- [Declaration reference](../declarations/declaration-reference.md)
