package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last grant is of a relation on an object to a grantee.
 */
public record ExpectGrant(String object, String relation, String grantee) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    Grant grant = environment.get(Value.GRANT);
    assertThat(grant.getObject()).as("object").isEqualTo(object);
    assertThat(grant.getRelation()).as("relation").isEqualTo(relation);
    assertThat(grant.getGrantee()).as("grantee").isEqualTo(grantee);
  }
}
