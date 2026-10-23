package dev.horizon.pyrite;

import com.google.protobuf.TextFormat;
import dev.horizon.pyrite.schema.v1.Declaration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class Acme {

  private Acme() {
  }

  static Declaration declaration() throws IOException {
    Declaration.Builder declaration = Declaration.newBuilder();
    try (InputStream text = Acme.class.getResourceAsStream("/acme/declaration.textproto")) {
      TextFormat.merge(new String(text.readAllBytes(), StandardCharsets.UTF_8), declaration);
    }
    return declaration.build();
  }
}
