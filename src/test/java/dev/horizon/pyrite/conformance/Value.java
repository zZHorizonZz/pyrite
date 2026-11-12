package dev.horizon.pyrite.conformance;

import com.google.protobuf.ByteString;
import com.webauthn4j.test.client.ClientPlatform;
import dev.horizon.pyrite.FlowContext;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.authentication.v1.Account;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.Identifier;
import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.authentication.v1.Session;
import dev.horizon.pyrite.conformance.condition.AgreementWorld;
import dev.horizon.pyrite.crypto.v1.EncryptResponse;
import dev.horizon.pyrite.crypto.v1.Key;
import dev.horizon.pyrite.crypto.v1.KeyVersion;
import dev.horizon.pyrite.crypto.v1.VerifyResponse;
import dev.horizon.pyrite.schema.v1.ReadDeclarationResponse;
import java.util.List;

/**
 * A value that a step leaves in the environment for the steps after it, of a type that the compiler checks.
 *
 * @param name what the value is, for a step that finds none
 * @param <T> the type of the value
 */
public record Value<T>(String name) {

  public static final Value<String> SESSION_TOKEN = new Value<>("the token of the last session");
  public static final Value<String> SESSION = new Value<>("the name of the last session");
  public static final Value<Principal> PRINCIPAL = new Value<>("the principal of the last authentication");
  public static final Value<Session> SESSION_READ = new Value<>("the session that the last read gave");
  public static final Value<Long> PURGE_COUNT = new Value<>("the number of rows that the last purge deleted");
  public static final Value<Flow> FLOW = new Value<>("the last flow");
  public static final Value<Flow> FLOW_START = new Value<>("the last flow as it started");
  public static final Value<Flow> OTHER_FLOW = new Value<>("a flow kept aside");
  public static final Value<Identifier> IDENTIFIER = new Value<>("the last identifier added");
  public static final Value<List<Identifier>> IDENTIFIERS = new Value<>("the identifiers of the last list");
  public static final Value<Account> ACCOUNT = new Value<>("the last account read");
  public static final Value<Boolean> ALLOWED = new Value<>("the answer of the last check");
  public static final Value<byte[]> TOTP_SECRET = new Value<>("the secret of the last TOTP enrolment");
  public static final Value<byte[]> OTHER_TOTP_SECRET = new Value<>("a TOTP secret kept aside");
  public static final Value<List<String>> RECOVERY_CODES = new Value<>("the recovery codes of the last enrolment");
  public static final Value<List<String>> OTHER_RECOVERY_CODES = new Value<>("recovery codes kept aside");
  public static final Value<List<FlowContext>> INTERCEPTIONS = new Value<>("the contexts that an interceptor saw");
  public static final Value<List<String>> ACCOUNT_CREATES = new Value<>("the accounts that the handler heard of as"
      + " created");
  public static final Value<List<String>> ACCOUNT_DELETES = new Value<>("the accounts that the handler heard of as"
      + " deleted");
  public static final Value<List<String>> SESSION_ISSUES = new Value<>("the sessions that the handler heard of as"
      + " issued");
  public static final Value<List<String>> SESSION_ENDS = new Value<>("the sessions that the handler heard of as"
      + " ended");
  public static final Value<Grant> GRANT = new Value<>("the last grant");
  public static final Value<List<String>> GRANTS = new Value<>("the grants of the last list, as relation and grantee");
  public static final Value<List<String>> SUBJECTS = new Value<>("the subjects of the last list");
  public static final Value<List<String>> OBJECTS = new Value<>("the objects of the last list");
  public static final Value<List<String>> CHANGES = new Value<>("the changes of the last list, as relation, grantee"
      + " and operation");
  public static final Value<AgreementWorld> WORLD = new Value<>("the resources and grants that Pyrite and OpenFGA"
      + " both hold");
  public static final Value<ReadDeclarationResponse> DECLARATION = new Value<>("the last declaration read");
  public static final Value<String> DECLARATION_ID = new Value<>("the id of the last declaration written");
  public static final Value<Key> KEY = new Value<>("the last key created, read, changed or rotated");
  public static final Value<Key> KEY_BEFORE = new Value<>("a key kept aside, as it was");
  public static final Value<List<KeyVersion>> KEY_VERSIONS = new Value<>("the versions of the last list");
  public static final Value<EncryptResponse> ENCRYPTION = new Value<>("the response of the last encryption");
  public static final Value<ByteString> CIPHERTEXT = new Value<>("the ciphertext of the last encryption");
  public static final Value<ByteString> OLD_CIPHERTEXT = new Value<>("a ciphertext kept aside");
  public static final Value<ByteString> PLAINTEXT = new Value<>("the plaintext of the last decryption");
  public static final Value<ByteString> SIGNATURE = new Value<>("the signature of the last data signed");
  public static final Value<ByteString> OTHER_SIGNATURE = new Value<>("a signature of another key");
  public static final Value<ByteString> SIGNATURE_FORGERY = new Value<>("a signature changed from the last one");
  public static final Value<String> TOKEN = new Value<>("the last token signed or forged");
  public static final Value<ClientPlatform> PASSKEY_CLIENT = new Value<>("the browser and authenticator of passkeys");
  public static final Value<VerifyResponse> VERIFICATION = new Value<>("the verdict of the last verification");
}
