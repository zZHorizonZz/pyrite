CREATE TABLE "pyrite_key" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL, "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT, "purpose" TEXT NOT NULL, "algorithm" TEXT NOT NULL, "rotation_period" INTEGER NOT NULL, "next_rotation_time" TEXT, "primary_version" TEXT NOT NULL, CONSTRAINT "pk_pyrite_key" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_key_name" ON "pyrite_key" ("name");

CREATE INDEX "ix_pyrite_key_parent" ON "pyrite_key" ("parent");

CREATE TABLE "pyrite_key_version" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL, "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT, "number" INTEGER NOT NULL, "state" TEXT NOT NULL, "algorithm" TEXT NOT NULL, "public_key" TEXT, "sealed" BLOB NOT NULL, "public_key_info" BLOB, CONSTRAINT "pk_pyrite_key_version" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_key_version_name" ON "pyrite_key_version" ("name");

CREATE INDEX "ix_pyrite_key_version_parent" ON "pyrite_key_version" ("parent");

CREATE TABLE "pyrite_account" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL, "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT, "kind" TEXT NOT NULL, CONSTRAINT "pk_pyrite_account" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_account_name" ON "pyrite_account" ("name");

CREATE TABLE "pyrite_identifier" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL, "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT, "kind" TEXT NOT NULL, "value" TEXT NOT NULL, "verified" INTEGER NOT NULL, "primary" INTEGER NOT NULL, CONSTRAINT "pk_pyrite_identifier" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_identifier_name" ON "pyrite_identifier" ("name");

CREATE INDEX "ix_pyrite_identifier_parent" ON "pyrite_identifier" ("parent");

CREATE UNIQUE INDEX "ux_pyrite_identifier_kind_value" ON "pyrite_identifier" ("kind", "value");

CREATE TABLE "pyrite_factor" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL, "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT, "kind" TEXT NOT NULL, "state" TEXT NOT NULL, "secret" BLOB NOT NULL, "credential_id" TEXT NOT NULL, "counter" INTEGER NOT NULL, "user_verified" INTEGER NOT NULL, CONSTRAINT "pk_pyrite_factor" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_factor_name" ON "pyrite_factor" ("name");

CREATE INDEX "ix_pyrite_factor_parent" ON "pyrite_factor" ("parent");

CREATE INDEX "ix_pyrite_factor_credential_id" ON "pyrite_factor" ("credential_id");

CREATE TABLE "pyrite_session" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT, "display_name" TEXT, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL, "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT, "token_mac" BLOB NOT NULL, "expire_time" TEXT NOT NULL, "idle_expire_time" TEXT NOT NULL, CONSTRAINT "pk_pyrite_session" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_pyrite_session_name" ON "pyrite_session" ("name");

CREATE INDEX "ix_pyrite_session_parent" ON "pyrite_session" ("parent");

CREATE TABLE "pyrite_flow" ("id" TEXT NOT NULL, "definition" TEXT NOT NULL, "account" TEXT, "identifier" TEXT, "revision" INTEGER NOT NULL, "stage" INTEGER NOT NULL, "proofs" TEXT NOT NULL, "answers" INTEGER NOT NULL, "expire_time" TEXT NOT NULL, "code_mac" BLOB, "code_expire_time" TEXT, "challenge" BLOB, "factor" TEXT, "request_id" TEXT, "response" BLOB, "done" INTEGER NOT NULL, CONSTRAINT "pk_pyrite_flow" PRIMARY KEY ("id"));

CREATE TABLE "pyrite_declaration" ("id" TEXT NOT NULL, "declaration" BLOB NOT NULL, "store_id" TEXT NOT NULL, "model_id" TEXT NOT NULL, "create_time" TEXT NOT NULL, CONSTRAINT "pk_pyrite_declaration" PRIMARY KEY ("id"));

CREATE INDEX "ix_pyrite_declaration_create_time" ON "pyrite_declaration" ("create_time");

CREATE TABLE "pyrite_node" ("name" TEXT NOT NULL, "type" TEXT NOT NULL, "parent" TEXT, "create_time" TEXT NOT NULL, CONSTRAINT "pk_pyrite_node" PRIMARY KEY ("name"));

CREATE INDEX "ix_pyrite_node_parent" ON "pyrite_node" ("parent");
