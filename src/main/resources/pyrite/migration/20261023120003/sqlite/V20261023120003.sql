PRAGMA defer_foreign_keys = ON;

CREATE TABLE "pyrite_new_pyrite_account" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL, "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT, "kind" TEXT NOT NULL, CONSTRAINT "pk_pyrite_account" PRIMARY KEY ("uid"));

INSERT INTO "pyrite_new_pyrite_account" ("uid", "name", "parent", "display_name", "labels", "revision", "create_time", "update_time", "delete_time", "purge_time", "kind") SELECT "uid", "name", "parent", "display_name", "labels", "revision", "create_time", "update_time", "delete_time", "purge_time", "kind" FROM "pyrite_account";

DROP TABLE "pyrite_account";

ALTER TABLE "pyrite_new_pyrite_account" RENAME TO "pyrite_account";

CREATE UNIQUE INDEX "ux_pyrite_account_name" ON "pyrite_account" ("name");

CREATE TABLE "pyrite_new_pyrite_identifier" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL, "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT, "kind" TEXT NOT NULL, "value" TEXT NOT NULL, "verified" INTEGER NOT NULL, "primary" INTEGER NOT NULL, CONSTRAINT "pk_pyrite_identifier" PRIMARY KEY ("uid"));

INSERT INTO "pyrite_new_pyrite_identifier" ("uid", "name", "parent", "display_name", "labels", "revision", "create_time", "update_time", "delete_time", "purge_time", "kind", "value", "verified", "primary") SELECT "uid", "name", "parent", "display_name", "labels", "revision", "create_time", "update_time", "delete_time", "purge_time", "kind", "value", "verified", "primary" FROM "pyrite_identifier";

DROP TABLE "pyrite_identifier";

ALTER TABLE "pyrite_new_pyrite_identifier" RENAME TO "pyrite_identifier";

CREATE UNIQUE INDEX "ux_pyrite_identifier_name" ON "pyrite_identifier" ("name");

CREATE INDEX "ix_pyrite_identifier_parent" ON "pyrite_identifier" ("parent");

CREATE UNIQUE INDEX "ux_pyrite_identifier_kind_value" ON "pyrite_identifier" ("kind", "value");

CREATE TABLE "pyrite_new_pyrite_factor" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL, "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT, "kind" TEXT NOT NULL, "state" TEXT NOT NULL, "secret" BLOB NOT NULL, "credential_id" TEXT NOT NULL, "counter" INTEGER NOT NULL, "user_verified" INTEGER NOT NULL, CONSTRAINT "pk_pyrite_factor" PRIMARY KEY ("uid"));

INSERT INTO "pyrite_new_pyrite_factor" ("uid", "name", "parent", "display_name", "labels", "revision", "create_time", "update_time", "delete_time", "purge_time", "kind", "state", "secret", "credential_id", "counter", "user_verified") SELECT "uid", "name", "parent", "display_name", "labels", "revision", "create_time", "update_time", "delete_time", "purge_time", "kind", "state", "secret", "credential_id", "counter", "user_verified" FROM "pyrite_factor";

DROP TABLE "pyrite_factor";

ALTER TABLE "pyrite_new_pyrite_factor" RENAME TO "pyrite_factor";

CREATE UNIQUE INDEX "ux_pyrite_factor_name" ON "pyrite_factor" ("name");

CREATE INDEX "ix_pyrite_factor_parent" ON "pyrite_factor" ("parent");

CREATE INDEX "ix_pyrite_factor_credential_id" ON "pyrite_factor" ("credential_id");

CREATE TABLE "pyrite_new_pyrite_session" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL, "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT, "token_mac" BLOB NOT NULL, "expire_time" TEXT NOT NULL, "idle_expire_time" TEXT NOT NULL, CONSTRAINT "pk_pyrite_session" PRIMARY KEY ("uid"));

INSERT INTO "pyrite_new_pyrite_session" ("uid", "name", "parent", "display_name", "labels", "revision", "create_time", "update_time", "delete_time", "purge_time", "token_mac", "expire_time", "idle_expire_time") SELECT "uid", "name", "parent", "display_name", "labels", "revision", "create_time", "update_time", "delete_time", "purge_time", "token_mac", "expire_time", "idle_expire_time" FROM "pyrite_session";

DROP TABLE "pyrite_session";

ALTER TABLE "pyrite_new_pyrite_session" RENAME TO "pyrite_session";

CREATE UNIQUE INDEX "ux_pyrite_session_name" ON "pyrite_session" ("name");

CREATE INDEX "ix_pyrite_session_parent" ON "pyrite_session" ("parent");

CREATE TABLE "pyrite_new_pyrite_flow" ("id" TEXT NOT NULL, "definition" TEXT NOT NULL, "account" TEXT, "identifier" TEXT, "revision" INTEGER NOT NULL, "stage" INTEGER NOT NULL, "proofs" TEXT NOT NULL, "answers" INTEGER NOT NULL, "expire_time" TEXT NOT NULL, "code_mac" BLOB, "code_expire_time" TEXT, "challenge" BLOB, "factor" TEXT, "request_id" TEXT, "response" BLOB, "done" INTEGER NOT NULL, CONSTRAINT "pk_pyrite_flow" PRIMARY KEY ("id"));

INSERT INTO "pyrite_new_pyrite_flow" ("id", "definition", "account", "identifier", "revision", "stage", "proofs", "answers", "expire_time", "code_mac", "code_expire_time", "challenge", "factor", "request_id", "response", "done") SELECT "id", "definition", "account", "identifier", "revision", "stage", "proofs", "answers", "expire_time", "code_mac", "code_expire_time", "challenge", "factor", "request_id", "response", "done" FROM "pyrite_flow";

DROP TABLE "pyrite_flow";

ALTER TABLE "pyrite_new_pyrite_flow" RENAME TO "pyrite_flow";

CREATE TABLE "pyrite_new_pyrite_key" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL, "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT, "purpose" TEXT NOT NULL, "algorithm" TEXT NOT NULL, "rotation_period" INTEGER NOT NULL, "next_rotation_time" TEXT, "primary_version" TEXT NOT NULL, CONSTRAINT "pk_pyrite_key" PRIMARY KEY ("uid"));

INSERT INTO "pyrite_new_pyrite_key" ("uid", "name", "parent", "display_name", "labels", "revision", "create_time", "update_time", "delete_time", "purge_time", "purpose", "algorithm", "rotation_period", "next_rotation_time", "primary_version") SELECT "uid", "name", "parent", "display_name", "labels", "revision", "create_time", "update_time", "delete_time", "purge_time", "purpose", "algorithm", "rotation_period", "next_rotation_time", "primary_version" FROM "pyrite_key";

DROP TABLE "pyrite_key";

ALTER TABLE "pyrite_new_pyrite_key" RENAME TO "pyrite_key";

CREATE UNIQUE INDEX "ux_pyrite_key_name" ON "pyrite_key" ("name");

CREATE INDEX "ix_pyrite_key_parent" ON "pyrite_key" ("parent");

CREATE TABLE "pyrite_new_pyrite_key_version" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL, "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT, "number" INTEGER NOT NULL, "state" TEXT NOT NULL, "algorithm" TEXT NOT NULL, "public_key" TEXT, "sealed" BLOB NOT NULL, "public_key_info" BLOB, CONSTRAINT "pk_pyrite_key_version" PRIMARY KEY ("uid"));

INSERT INTO "pyrite_new_pyrite_key_version" ("uid", "name", "parent", "display_name", "labels", "revision", "create_time", "update_time", "delete_time", "purge_time", "number", "state", "algorithm", "public_key", "sealed", "public_key_info") SELECT "uid", "name", "parent", "display_name", "labels", "revision", "create_time", "update_time", "delete_time", "purge_time", "number", "state", "algorithm", "public_key", "sealed", "public_key_info" FROM "pyrite_key_version";

DROP TABLE "pyrite_key_version";

ALTER TABLE "pyrite_new_pyrite_key_version" RENAME TO "pyrite_key_version";

CREATE UNIQUE INDEX "ux_pyrite_key_version_name" ON "pyrite_key_version" ("name");

CREATE INDEX "ix_pyrite_key_version_parent" ON "pyrite_key_version" ("parent");
