package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.Account;
import dev.horizon.pyrite.authentication.v1.AddIdentifierRequest;
import dev.horizon.pyrite.authentication.v1.AuthenticationGrpcClient;
import dev.horizon.pyrite.authentication.v1.ContinueFlowRequest;
import dev.horizon.pyrite.authentication.v1.CreateAccountRequest;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.authentication.v1.StartFlowRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import java.util.UUID;

/**
 * Creates an account with an email address and a password, as its own subject.
 *
 * @param id the id of the account, which is also the id of its subject {@code user:id}
 */
public record CreateAccount(String id, String email, String password) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    AuthenticationGrpcClient authentication = environment.pyrite().authentication;
    String subject = "user:" + id;
    authentication.createAccount(CreateAccountRequest.newBuilder().setSubject(subject).setAccountId(id)
        .setKind(Account.Kind.HUMAN).build()).await(TIMEOUT);
    authentication.addIdentifier(AddIdentifierRequest.newBuilder().setSubject(subject).setAccount("accounts/" + id)
        .setKind("email").setValue(email).build()).await(TIMEOUT);
    Flow enrolment = authentication.startFlow(StartFlowRequest.newBuilder().setSubject(subject)
        .setDefinition("enrol-password").build()).await(TIMEOUT);
    authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(enrolment.getName())
        .setRevision(enrolment.getRevision()).setRequestId(UUID.randomUUID().toString())
        .setAnswer(Any.pack(PasswordAnswer.newBuilder().setPassword(password).build())).build()).await(TIMEOUT);
  }
}
