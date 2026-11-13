package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.ContinueExchangeRequest;
import dev.horizon.pyrite.authentication.v1.Exchange;
import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import io.vertx.core.Future;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sends wrong passwords to the last exchange at once, as an attacker who guesses in parallel would, and checks that
 * each is refused: as a wrong answer, or because the exchange took the most answers or moved on.
 */
public record AnswerWrongPasswordsAtOnce(int count) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    Exchange exchange = environment.get(Value.EXCHANGE);
    List<Future<Exchange>> answers = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      answers.add(environment.pyrite().authentication.continueExchange(ContinueExchangeRequest.newBuilder()
          .setExchange(exchange.getName()).setRevision(exchange.getRevision())
          .setRequestId(UUID.randomUUID().toString())
          .setAnswer(Any.pack(PasswordAnswer.newBuilder().setPassword("a wrong guess " + index).build())).build()));
    }
    Future.join(answers).transform(ignored -> Future.succeededFuture()).await(Duration.ofSeconds(30));
    for (Future<Exchange> answer : answers) {
      assertThat(answer.failed()).as("a wrong guess passed").isTrue();
      assertThat(answer.cause()).isInstanceOf(InvalidStatusException.class);
      assertThat(((InvalidStatusException) answer.cause()).actualStatus()).isIn(GrpcStatus.PERMISSION_DENIED,
          GrpcStatus.RESOURCE_EXHAUSTED, GrpcStatus.ABORTED);
    }
  }
}
