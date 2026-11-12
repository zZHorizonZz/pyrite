CREATE TABLE "pyrite_account" ("uid" TEXT NOT NULL DEFAULT (lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))), "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL DEFAULT '{}', "revision" INTEGER NOT NULL DEFAULT 1, "create_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), "update_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), "delete_time" TEXT, "purge_time" TEXT, "kind" TEXT NOT NULL, CONSTRAINT "pk_pyrite_account" PRIMARY KEY ("uid"), CONSTRAINT "ck_pyrite_account_kind" CHECK (("kind" IN ('KIND_UNSPECIFIED', 'HUMAN', 'SERVICE'))), CONSTRAINT "fk_pyrite_account_name" FOREIGN KEY ("name") REFERENCES "pyrite_node" ("name") ON DELETE CASCADE);

CREATE UNIQUE INDEX "ux_pyrite_account_name" ON "pyrite_account" ("name");

CREATE TABLE "pyrite_identifier" ("uid" TEXT NOT NULL DEFAULT (lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))), "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL DEFAULT '{}', "revision" INTEGER NOT NULL DEFAULT 1, "create_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), "update_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), "delete_time" TEXT, "purge_time" TEXT, "kind" TEXT NOT NULL, "value" TEXT NOT NULL, "verified" INTEGER NOT NULL, "primary" INTEGER NOT NULL, CONSTRAINT "pk_pyrite_identifier" PRIMARY KEY ("uid"), CONSTRAINT "fk_pyrite_identifier_parent" FOREIGN KEY ("parent") REFERENCES "pyrite_account" ("name") ON DELETE CASCADE);

CREATE UNIQUE INDEX "ux_pyrite_identifier_name" ON "pyrite_identifier" ("name");

CREATE INDEX "ix_pyrite_identifier_parent" ON "pyrite_identifier" ("parent");

CREATE UNIQUE INDEX "ux_pyrite_identifier_kind_value" ON "pyrite_identifier" ("kind", "value");

CREATE TABLE "pyrite_factor" ("uid" TEXT NOT NULL DEFAULT (lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))), "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL DEFAULT '{}', "revision" INTEGER NOT NULL DEFAULT 1, "create_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), "update_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), "delete_time" TEXT, "purge_time" TEXT, "kind" TEXT NOT NULL, "state" TEXT NOT NULL, "secret" BLOB NOT NULL, "credential_id" TEXT NOT NULL, "counter" INTEGER NOT NULL, "user_verified" INTEGER NOT NULL, CONSTRAINT "pk_pyrite_factor" PRIMARY KEY ("uid"), CONSTRAINT "ck_pyrite_factor_kind" CHECK (("kind" IN ('KIND_UNSPECIFIED', 'PASSWORD', 'TOTP', 'PASSKEY', 'RECOVERY_CODES'))), CONSTRAINT "ck_pyrite_factor_state" CHECK (("state" IN ('STATE_UNSPECIFIED', 'PENDING', 'ACTIVE'))), CONSTRAINT "fk_pyrite_factor_parent" FOREIGN KEY ("parent") REFERENCES "pyrite_account" ("name") ON DELETE CASCADE);

CREATE UNIQUE INDEX "ux_pyrite_factor_name" ON "pyrite_factor" ("name");

CREATE INDEX "ix_pyrite_factor_parent" ON "pyrite_factor" ("parent");

CREATE INDEX "ix_pyrite_factor_credential_id" ON "pyrite_factor" ("credential_id");

CREATE TABLE "pyrite_session" ("uid" TEXT NOT NULL DEFAULT (lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))), "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL DEFAULT '{}', "revision" INTEGER NOT NULL DEFAULT 1, "create_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), "update_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), "delete_time" TEXT, "purge_time" TEXT, "token_mac" BLOB NOT NULL, "expire_time" TEXT NOT NULL, "idle_expire_time" TEXT NOT NULL, CONSTRAINT "pk_pyrite_session" PRIMARY KEY ("uid"), CONSTRAINT "fk_pyrite_session_parent" FOREIGN KEY ("parent") REFERENCES "pyrite_account" ("name") ON DELETE CASCADE);

CREATE UNIQUE INDEX "ux_pyrite_session_name" ON "pyrite_session" ("name");

CREATE INDEX "ix_pyrite_session_parent" ON "pyrite_session" ("parent");

CREATE TABLE "pyrite_flow" ("id" TEXT NOT NULL, "definition" TEXT NOT NULL, "account" TEXT, "identifier" TEXT, "revision" INTEGER NOT NULL, "stage" INTEGER NOT NULL, "proofs" TEXT NOT NULL, "answers" INTEGER NOT NULL, "expire_time" TEXT NOT NULL, "code_mac" BLOB, "code_expire_time" TEXT, "challenge" BLOB, "factor" TEXT, "request_id" TEXT, "response" BLOB, "done" INTEGER NOT NULL, CONSTRAINT "pk_pyrite_flow" PRIMARY KEY ("id"), CONSTRAINT "fk_pyrite_flow_account" FOREIGN KEY ("account") REFERENCES "pyrite_account" ("name") ON DELETE CASCADE);

CREATE TABLE "pyrite_declaration" ("id" TEXT NOT NULL, "declaration" BLOB NOT NULL, "store_id" TEXT NOT NULL, "model_id" TEXT NOT NULL, "create_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), CONSTRAINT "pk_pyrite_declaration" PRIMARY KEY ("id"));

CREATE INDEX "ix_pyrite_declaration_create_time" ON "pyrite_declaration" ("create_time");

CREATE TABLE "pyrite_node" ("name" TEXT NOT NULL, "type" TEXT NOT NULL, "parent" TEXT, "create_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), CONSTRAINT "pk_pyrite_node" PRIMARY KEY ("name"), CONSTRAINT "fk_pyrite_node_parent" FOREIGN KEY ("parent") REFERENCES "pyrite_node" ("name") ON DELETE RESTRICT);

CREATE INDEX "ix_pyrite_node_parent" ON "pyrite_node" ("parent");

CREATE TABLE "pyrite_key" ("uid" TEXT NOT NULL DEFAULT (lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))), "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL DEFAULT '{}', "revision" INTEGER NOT NULL DEFAULT 1, "create_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), "update_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), "delete_time" TEXT, "purge_time" TEXT, "purpose" TEXT NOT NULL, "algorithm" TEXT NOT NULL, "rotation_period" INTEGER NOT NULL, "next_rotation_time" TEXT, "primary_version" TEXT NOT NULL, CONSTRAINT "pk_pyrite_key" PRIMARY KEY ("uid"), CONSTRAINT "ck_pyrite_key_purpose" CHECK (("purpose" IN ('PURPOSE_UNSPECIFIED', 'ENCRYPTION', 'MAC', 'SIGNING'))), CONSTRAINT "ck_pyrite_key_algorithm" CHECK (("algorithm" IN ('ALGORITHM_UNSPECIFIED', 'AES256_GCM', 'HMAC_SHA256', 'RS256', 'ES256', 'EDDSA'))), CONSTRAINT "fk_pyrite_key_name" FOREIGN KEY ("name") REFERENCES "pyrite_node" ("name") ON DELETE CASCADE);

CREATE UNIQUE INDEX "ux_pyrite_key_name" ON "pyrite_key" ("name");

CREATE INDEX "ix_pyrite_key_parent" ON "pyrite_key" ("parent");

CREATE TABLE "pyrite_key_version" ("uid" TEXT NOT NULL DEFAULT (lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))), "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL DEFAULT '{}', "revision" INTEGER NOT NULL DEFAULT 1, "create_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), "update_time" TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z'), "delete_time" TEXT, "purge_time" TEXT, "number" INTEGER NOT NULL, "state" TEXT NOT NULL, "algorithm" TEXT NOT NULL, "public_key" TEXT, "sealed" BLOB NOT NULL, "public_key_info" BLOB, CONSTRAINT "pk_pyrite_key_version" PRIMARY KEY ("uid"), CONSTRAINT "ck_pyrite_key_version_state" CHECK (("state" IN ('STATE_UNSPECIFIED', 'ENABLED', 'DISABLED'))), CONSTRAINT "ck_pyrite_key_version_algorithm" CHECK (("algorithm" IN ('ALGORITHM_UNSPECIFIED', 'AES256_GCM', 'HMAC_SHA256', 'RS256', 'ES256', 'EDDSA'))), CONSTRAINT "fk_pyrite_key_version_parent" FOREIGN KEY ("parent") REFERENCES "pyrite_key" ("name") ON DELETE CASCADE);

CREATE UNIQUE INDEX "ux_pyrite_key_version_name" ON "pyrite_key_version" ("name");

CREATE UNIQUE INDEX "ux_pyrite_key_version_parent_number" ON "pyrite_key_version" ("parent", "number");

CREATE TRIGGER "tg_pyrite_account_update" AFTER UPDATE ON "pyrite_account" BEGIN
  UPDATE "pyrite_account" SET "revision" = (OLD."revision" + 1), "update_time" = (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z') WHERE "pyrite_account"."uid" = NEW."uid";
END;

CREATE TRIGGER "tg_pyrite_identifier_update" AFTER UPDATE ON "pyrite_identifier" BEGIN
  UPDATE "pyrite_identifier" SET "revision" = (OLD."revision" + 1), "update_time" = (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z') WHERE "pyrite_identifier"."uid" = NEW."uid";
END;

CREATE TRIGGER "tg_pyrite_factor_update" AFTER UPDATE ON "pyrite_factor" BEGIN
  UPDATE "pyrite_factor" SET "revision" = (OLD."revision" + 1), "update_time" = (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z') WHERE "pyrite_factor"."uid" = NEW."uid";
END;

CREATE TRIGGER "tg_pyrite_session_update" AFTER UPDATE ON "pyrite_session" BEGIN
  UPDATE "pyrite_session" SET "revision" = (OLD."revision" + 1), "update_time" = (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z') WHERE "pyrite_session"."uid" = NEW."uid";
END;

CREATE TRIGGER "tg_pyrite_key_update" AFTER UPDATE ON "pyrite_key" BEGIN
  UPDATE "pyrite_key" SET "revision" = (OLD."revision" + 1), "update_time" = (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z') WHERE "pyrite_key"."uid" = NEW."uid";
END;

CREATE TRIGGER "tg_pyrite_key_version_update" AFTER UPDATE ON "pyrite_key_version" BEGIN
  UPDATE "pyrite_key_version" SET "revision" = (OLD."revision" + 1), "update_time" = (strftime('%Y-%m-%dT%H:%M:%f', 'now') || '000Z') WHERE "pyrite_key_version"."uid" = NEW."uid";
END;
