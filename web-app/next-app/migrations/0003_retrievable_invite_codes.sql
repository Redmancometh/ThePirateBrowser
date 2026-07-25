ALTER TABLE registration_invites ADD COLUMN code_plain TEXT;

CREATE UNIQUE INDEX registration_invites_code_plain_idx
    ON registration_invites(code_plain)
    WHERE code_plain IS NOT NULL;
