package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.access.v1.ListGrantsRequest;
import dev.horizon.pyrite.access.v1.ListGrantsResponse;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists the grants on an object page by page, and checks that each page holds at most its size, and that the pages
 * hold each grantee once.
 */
public record ExpectGrantPages(String subject, String object, int pageSize, List<String> grantees)
    implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    List<String> listed = new ArrayList<>();
    String token = "";
    do {
      ListGrantsResponse page = environment.pyrite().access.listGrants(ListGrantsRequest.newBuilder()
          .setSubject(subject).setObject(object).setPageSize(pageSize).setPageToken(token).build()).await(TIMEOUT);
      assertThat(page.getGrantsCount()).as("the size of a page").isLessThanOrEqualTo(pageSize);
      page.getGrantsList().forEach(grant -> listed.add(grant.getGrantee()));
      token = page.getNextPageToken();
    } while (!token.isEmpty());
    assertThat(listed).containsExactlyInAnyOrderElementsOf(grantees);
  }
}
