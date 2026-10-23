CREATE TABLE "acme_organization" ("uid" varchar(36) NOT NULL, "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255), "display_name" varchar(255), "labels" jsonb NOT NULL, "revision" bigint NOT NULL, "create_time" timestamptz NOT NULL, "update_time" timestamptz NOT NULL, "delete_time" timestamptz, "purge_time" timestamptz, CONSTRAINT "pk_acme_organization" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_acme_organization_name" ON "acme_organization" ("name");

COMMENT ON TABLE "acme_organization" IS 'An organization: the resource acme.dev/Organization, at the top.';

CREATE TABLE "acme_group" ("uid" varchar(36) NOT NULL, "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255), "display_name" varchar(255), "labels" jsonb NOT NULL, "revision" bigint NOT NULL, "create_time" timestamptz NOT NULL, "update_time" timestamptz NOT NULL, "delete_time" timestamptz, "purge_time" timestamptz, CONSTRAINT "pk_acme_group" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_acme_group_name" ON "acme_group" ("name");

CREATE INDEX "ix_acme_group_parent" ON "acme_group" ("parent");

COMMENT ON TABLE "acme_group" IS 'A group of an organization: the resource acme.dev/Group.';

CREATE TABLE "acme_project" ("uid" varchar(36) NOT NULL, "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255), "display_name" varchar(255), "labels" jsonb NOT NULL, "revision" bigint NOT NULL, "create_time" timestamptz NOT NULL, "update_time" timestamptz NOT NULL, "delete_time" timestamptz, "purge_time" timestamptz, "billing_account" varchar(64), CONSTRAINT "pk_acme_project" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_acme_project_name" ON "acme_project" ("name");

CREATE INDEX "ix_acme_project_parent" ON "acme_project" ("parent");

COMMENT ON TABLE "acme_project" IS 'A project of an organization: the resource acme.dev/Project.';

COMMENT ON COLUMN "acme_project"."billing_account" IS 'The billing account that pays for the project.';

CREATE TABLE "acme_role" ("uid" varchar(36) NOT NULL, "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255), "display_name" varchar(255), "labels" jsonb NOT NULL, "revision" bigint NOT NULL, "create_time" timestamptz NOT NULL, "update_time" timestamptz NOT NULL, "delete_time" timestamptz, "purge_time" timestamptz, CONSTRAINT "pk_acme_role" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_acme_role_name" ON "acme_role" ("name");

CREATE INDEX "ix_acme_role_parent" ON "acme_role" ("parent");

COMMENT ON TABLE "acme_role" IS 'A role that an organization defines at runtime: the resource acme.dev/Role.';

CREATE TABLE "acme_binding" ("uid" varchar(36) NOT NULL, "name" varchar(255) COLLATE "C" NOT NULL, "parent" varchar(255), "display_name" varchar(255), "labels" jsonb NOT NULL, "revision" bigint NOT NULL, "create_time" timestamptz NOT NULL, "update_time" timestamptz NOT NULL, "delete_time" timestamptz, "purge_time" timestamptz, CONSTRAINT "pk_acme_binding" PRIMARY KEY ("uid"));

CREATE UNIQUE INDEX "ux_acme_binding_name" ON "acme_binding" ("name");

CREATE INDEX "ix_acme_binding_parent" ON "acme_binding" ("parent");

COMMENT ON TABLE "acme_binding" IS 'A role bound to members: the resource acme.dev/Binding.';
