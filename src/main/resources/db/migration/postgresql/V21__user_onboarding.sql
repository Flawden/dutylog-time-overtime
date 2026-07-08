-- First-run onboarding state. Existing users should not be interrupted after upgrade.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS onboarding_completed BOOLEAN NOT NULL DEFAULT TRUE;

-- New JPA-created users explicitly store false until they finish or skip onboarding.
