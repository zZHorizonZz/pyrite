CREATE TABLE "acme_organization" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT NOT NULL,
  "display_name" TEXT NOT NULL, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL,
  "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT,
  CONSTRAINT "pk_acme_organization" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_acme_organization_name" ON "acme_organization" ("name");

CREATE TABLE "acme_group" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT NOT NULL,
  "display_name" TEXT NOT NULL, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL,
  "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT,
  CONSTRAINT "pk_acme_group" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_acme_group_name" ON "acme_group" ("name");

CREATE TABLE "acme_project" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT NOT NULL,
  "display_name" TEXT NOT NULL, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL,
  "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT, "billing_account" TEXT,
  CONSTRAINT "pk_acme_project" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_acme_project_name" ON "acme_project" ("name");

CREATE INDEX "ix_acme_project_parent" ON "acme_project" ("parent");

CREATE TABLE "acme_role" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT NOT NULL,
  "display_name" TEXT NOT NULL, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL,
  "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT,
  CONSTRAINT "pk_acme_role" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_acme_role_name" ON "acme_role" ("name");

CREATE TABLE "acme_binding" ("uid" TEXT NOT NULL, "name" TEXT NOT NULL, "parent" TEXT NOT NULL,
  "display_name" TEXT NOT NULL, "labels" TEXT NOT NULL, "revision" INTEGER NOT NULL, "create_time" TEXT NOT NULL,
  "update_time" TEXT NOT NULL, "delete_time" TEXT, "purge_time" TEXT,
  CONSTRAINT "pk_acme_binding" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_acme_binding_name" ON "acme_binding" ("name");
