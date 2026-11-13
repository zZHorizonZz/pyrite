package dev.horizon.pyrite.conformance;

/**
 * What Pyrite promises, one behavior each. A module names the requirement that each of its checks holds Pyrite to.
 */
public enum Requirement {

  // Accounts and identifiers
  ACCOUNT_KIND("An account is HUMAN or SERVICE."),
  ACCOUNT_ID("An account id is taken once."),
  ACCOUNT_OWNER("An account owns itself, and nobody else reads it."),
  IDENTIFIER_FORMAT("An identifier is stored normalized and unverified, and only in a format of a declared kind."),
  IDENTIFIER_OWNER("Only who administers an account adds identifiers to it."),
  IDENTIFIER_UNIQUE("An identifier belongs to one account, whatever its case."),
  IDENTIFIER_LIST("An account lists its identifiers, and nobody else does."),

  // Sessions
  SESSION_PRINCIPAL("An access token gives its subject, account, session and expiry."),
  SESSION_ISSUE("Each sign-in issues a session and tokens of its own."),
  SESSION_CREDENTIAL("An authentication without a credential is invalid."),
  SESSION_FORGERY("A changed, cut, borrowed or malformed token does not authenticate or refresh."),
  SESSION_IDLE("A session ends when it goes unrefreshed for its idle lifetime; each refresh extends it."),
  SESSION_REFRESH("A refresh trades a refresh token for a new access token and a new refresh token."),
  PROVIDER_SIGN_UP("An exchange that creates accounts creates one for an unlinked provider account."),
  PROVIDER_LINK_ONLY("An exchange that does not create accounts refuses an unlinked provider account."),
  PROVIDER_LINK("A signed-in account links a provider account, whose sign-in then reaches it."),
  PROVIDER_LINK_UNIQUE("No provider account is linked to two accounts."),
  PROVIDER_CALLBACK("A callback of another state, nonce, PKCE challenge or exchange is wrong."),
  PROVIDER_EMAIL_SIGN_IN("A provider account of a verified email signs in to its account, and is linked to it."),
  PROVIDER_EMAIL_CONFIRM("A provider account of a verified email asks for a factor of its account, then is linked."),
  PROVIDER_EMAIL_VERIFIED("Only an email that both the provider and Pyrite verified reaches an account."),
  FACTOR_DELETE("Its account removes a factor; a removed provider account can be linked again."),
  FACTOR_LAST("An account keeps its last factor that signs in on its own."),
  SESSION_REFRESH_REUSE("A refresh token works once; one that comes back after its trade ends its session."),
  ACCESS_TOKEN_CLAIMS("An access token is a JWT of the access token key, for its subject and session, for five minutes."),
  SESSION_LIFETIME("A session ends at its lifetime, however often it is used."),
  SESSION_STORAGE("A session whose account cannot be read fails as an internal error, not as signed out."),
  SESSION_READ("A session is read by its account only."),
  SESSION_END("An ended session, or one of a deleted account, does not authenticate or refresh."),
  SESSION_OWNER("Only its account ends a session."),
  PURGE_SESSION("A purge deletes ended sessions."),

  // Exchanges
  EXCHANGE_DEFINITION("An exchange is of a declared definition."),
  EXCHANGE_IDENTIFIER("An exchange that takes an identifier needs a valid one, and finds its kind when none is named."),
  EXCHANGE_SUBJECT("An exchange that takes an account needs a subject that is one."),
  EXCHANGE_REVISION("An answer at a stale revision is aborted."),
  EXCHANGE_RETRY("A retry of an answer gets the same response, and does nothing more."),
  EXCHANGE_ANSWER("An answer that the stage does not take, or that does not parse, is invalid."),
  EXCHANGE_WRONG("A wrong answer is denied and leaves the exchange where it was."),
  EXCHANGE_ANSWERS("An exchange takes a limited number of answers; after them, even the right one is refused."),
  EXCHANGE_DONE("A done exchange takes no more answers."),
  EXCHANGE_EXPIRY("An expired exchange takes no answer."),
  EXCHANGE_NAME("An exchange name that is malformed is invalid, and one that is unknown is not found."),
  EXCHANGE_RESULT("An exchange that enrols a factor names it and its account in its result."),
  EXCHANGE_STORAGE("An answer whose account cannot be read fails as an internal error, not as wrong."),

  // Codes and recovery
  CODE_DELIVERY("A code of six digits goes to the address, through the interceptor when there is one."),
  CODE_OWNER("An account proves only its own addresses."),
  CODE_PROOF("The right code verifies the address; a wrong one, or another exchange's, does not."),
  CODE_EXPIRY("A code expires after its lifetime."),
  RECOVERY_ADDRESS("An unknown or unverified address gets no recovery code."),
  RECOVERY_PASSWORD("A recovery proves the code first, then sets a new password and ends every session."),

  // Passwords
  PASSWORD_SIGN_IN("The right password signs in, however the address is cased or padded; any other does not."),
  PASSWORD_ENUMERATION("An unknown address looks the same as a known one to whoever signs in."),
  PASSWORD_ACCOUNT("A password of another account does not sign in."),
  PASSWORD_LENGTH("A password has 8 to 128 characters."),
  PASSWORD_REPLACE("A new password replaces the old one."),

  // Second factors
  TOTP_PROVISIONING("A TOTP enrolment gives an otpauth URI with SHA-1, six digits, thirty seconds and its own secret."),
  TOTP_ACTIVATION("A wrong first code activates nothing."),
  TOTP_SIGN_IN("An account with TOTP signs in with a password and a code, not with the password alone."),
  TOTP_WINDOW("A code of one step either side is taken; two steps away is not."),
  TOTP_REPLAY("A code is taken once, and no older code after it."),
  TOTP_WRONG("A code that is malformed, or of another secret, is wrong."),
  FACTOR_KIND("Two answers of the same kind are one factor."),
  RECOVERY_CODES("Ten recovery codes replace the old ones, and each is a second factor once."),
  PASSKEY_ENROLMENT("A passkey enrols with the creation options that the exchange gives, of the relying party."),
  PASSKEY_EXCLUSION("An enrolment excludes the passkeys that the account has."),
  PASSKEY_SIGN_IN("A passkey of the account signs in with the request options that the exchange gives."),
  PASSKEY_CHALLENGE("An answer to the challenge of another exchange is wrong."),
  PASSKEY_ORIGIN("An answer from an origin that the relying party does not name is wrong."),
  PHISHING_RESISTANCE("A phishing-resistant stage takes a passkey, and no password."),

  // Objects
  OBJECT_CREATE("A resource is made known once, and inherits what is granted on its parent."),
  OBJECT_PERMISSION("Creating under a parent needs the create permission on it."),
  OBJECT_PARENT("A parent is known, and of a parent type of the resource."),
  OBJECT_NAME("A name is of a declared type, with an id that OpenFGA takes."),
  OBJECT_DELETE("A resource is forgotten with its grants, only when nothing is under it, by who may grant on it."),
  OBJECT_MISSING("A resource that is not known is not found."),
  OBJECT_LIST("A subject lists the objects that it has a permission on, through their parents too."),

  // Grants
  GRANT_WRITE("A grant gives its relation, to every user for a wildcard, and granting twice changes nothing."),
  GRANT_REVOKE("A revoke takes the relation back, and revoking twice changes nothing."),
  GRANT_GROUP("A group passes on what is granted to it, through nested groups."),
  GRANT_CYCLE("A grant that makes a cycle of groups is refused."),
  GRANT_PERMISSION("Granting and revoking need the grant permission."),
  GRANT_RELATION("Only a relation that takes grantees is granted."),
  GRANT_GRANTEE("A grantee is of a type that the relation takes."),
  GRANT_CONDITION("A condition is declared, taken by the relation, and given values of its parameter types."),
  GRANT_LIST("Grants are listed by relation and grantee, a page at a time, to who may grant."),

  // Checks and lists
  ACCESS_CHECK("A subject checks its own permissions, which are relations of the type."),
  ACCESS_PRINCIPAL("Checking another principal needs the grant permission, and a principal of the form type:id."),
  ACCESS_HIERARCHY("A grant on a parent reaches what is under it, and so does a block."),
  ACCESS_CONDITION("A conditional grant holds while its condition does, on the values that the grant holds."),
  SUBJECT_LIST("The subjects of a permission include members of groups and ancestors, and no blocked principal."),
  CHANGE_LIST("Changes are listed oldest first, to who may grant on their resource."),
  AGREEMENT_CHECK("A check answers as OpenFGA does."),
  AGREEMENT_LIST("A list of objects holds exactly what OpenFGA allows."),
  AGREEMENT_SUBJECTS("A list of subjects holds exactly OpenFGA's users."),

  // Declarations
  DECLARATION_LATEST("The latest declaration is the one that every service reads."),
  DECLARATION_RULES("A declaration that breaks a rule is refused, and the latest stays."),
  DECLARATION_READ("A declaration is read back by its id, as it was written."),
  DECLARATION_NONE("Before any declaration, every call fails its precondition."),

  // Keys
  KEY_CREATE("A key has an algorithm and a period that is not negative, and is created by who may create there."),
  KEY_PURPOSE("A key does only what its algorithm is for."),
  KEY_NAME("A name that is not a key's is invalid, and a missing key is not found."),
  KEY_PERMISSION("Each use of a key needs its own relation on it."),
  KEY_ENCRYPTION("A key decrypts what it encrypted, of any size, and no two encryptions are alike."),
  KEY_TAMPERING("A ciphertext that was changed, or is not this key's for this associated data, is refused."),
  KEY_ROTATION("A rotation makes a new primary version, and old versions still decrypt and verify."),
  KEY_ROTATION_RACE("Rotations at the same time each make a version of their own, numbered without a gap."),
  KEY_SCHEDULE("A key rotates once after its period, which a change at its revision restarts."),
  KEY_VERSION_STATE("A disabled version works again once enabled, a destroyed one never, and the primary is neither."),
  KEY_SIGNATURE("A detached signature verifies only its data, with the key and version that made it."),
  KEY_JWT("A JWT names its key version, and verifies with the published public key."),
  KEY_REQUEST("A request names what to sign or verify."),
  JWT_CLAIMS("A JWT of another issuer, audience or type is rejected."),
  JWT_TIME("An expired or early JWT is rejected, within the leeway, or the clock skew when none is given."),
  JWT_FORGERY("A forged JWT is rejected, with the reason why."),

  // Handlers
  HANDLER_EXCHANGE_START("An interceptor of the start can refuse an exchange."),
  HANDLER_STAGE_END("Interceptors of stages run each time that an answer meets a stage."),
  HANDLER_EXCHANGE_END("Interceptors of the end run before the declared steps, and can stop the exchange before any of them."),
  HANDLER_REMOVE("A removed interceptor no longer runs."),
  HANDLER_EXCHANGE_FAILURE("Interceptors of failures hear of each wrong answer, which fails all the same."),
  HANDLER_CODE("An interceptor that cannot send a code stops the exchange."),
  HANDLER_ACCOUNT("The handlers of accounts hear of each account created and deleted."),
  HANDLER_SESSION("The handlers of sessions hear of each session issued and ended."),
  HANDLER_THROW("A handler that throws changes nothing of what happened.");

  private final String text;

  Requirement(String text) {
    this.text = text;
  }

  public String text() {
    return text;
  }

  @Override
  public String toString() {
    return name() + ": " + text;
  }
}
