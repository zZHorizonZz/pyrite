CREATE TABLE "pyrite_vault" ("name" varchar(16) NOT NULL, "sealed" bytea NOT NULL, "create_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT "pk_pyrite_vault" PRIMARY KEY ("name"));

COMMENT ON TABLE "pyrite_vault" IS 'The vault key, encrypted under the root key. It encrypts the material of every version, so rotating the root key
encrypts this one row again.';

CREATE TABLE "pyrite_key" ("name" varchar(63) COLLATE "C" NOT NULL, "purpose" varchar(16) NOT NULL, "algorithm" varchar(16), "rotation_millis" bigint NOT NULL, "pending_millis" bigint NOT NULL, "lifetime_millis" bigint, "revision" bigint NOT NULL, "create_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT "pk_pyrite_key" PRIMARY KEY ("name"));

COMMENT ON TABLE "pyrite_key" IS 'A key. Every change of the key or of one of its versions moves its revision.';

COMMENT ON COLUMN "pyrite_key"."purpose" IS 'ENCRYPTION, MAC or SIGNING.';

COMMENT ON COLUMN "pyrite_key"."algorithm" IS 'The algorithm of a signing key: RS256, ES256 or EDDSA.';

COMMENT ON COLUMN "pyrite_key"."rotation_millis" IS 'How often the key gets a new primary version, in milliseconds; 0 for never.';

COMMENT ON COLUMN "pyrite_key"."pending_millis" IS 'How long a new version is pending before it becomes primary, in milliseconds.';

COMMENT ON COLUMN "pyrite_key"."lifetime_millis" IS 'How long a version works after it is made, in milliseconds; none for a version that works until it is disabled.';

CREATE TABLE "pyrite_version" ("key_name" varchar(63) COLLATE "C" NOT NULL, "number" bigint NOT NULL, "origin" varchar(16) NOT NULL, "state" varchar(16) NOT NULL, "state_time" timestamptz, "create_time" timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, "sealed" bytea, "public_key" bytea, "expire_time" timestamptz, CONSTRAINT "pk_pyrite_version" PRIMARY KEY ("key_name", "number"));

COMMENT ON TABLE "pyrite_version" IS 'A version of a key.';

COMMENT ON COLUMN "pyrite_version"."number" IS 'The number of the version in its key: 1 for the first, and one more for each after it.';

COMMENT ON COLUMN "pyrite_version"."origin" IS 'GENERATED, EXPORTED or UPLOADED.';

COMMENT ON COLUMN "pyrite_version"."state" IS 'PENDING, PRIMARY, LIVE, DISABLED, EXPIRED or DESTROYED.';

COMMENT ON COLUMN "pyrite_version"."state_time" IS 'When a pending version becomes primary, since when a primary is primary, or when a version was disabled, expired or
was destroyed. None for a live version.';

COMMENT ON COLUMN "pyrite_version"."sealed" IS 'The secret material, encrypted under the vault key: the AES or HMAC key, or the PKCS#8 private key. None for a
public key alone and for a destroyed version.';

COMMENT ON COLUMN "pyrite_version"."public_key" IS 'The X.509 SubjectPublicKeyInfo of a signing version.';

COMMENT ON COLUMN "pyrite_version"."expire_time" IS 'When the version expires, as the key''s validity said when it was made. None for a version that never does.';

ALTER TABLE "pyrite_version" ADD CONSTRAINT "fk_pyrite_version_key_name" FOREIGN KEY ("key_name") REFERENCES "pyrite_key" ("name") ON DELETE CASCADE;
