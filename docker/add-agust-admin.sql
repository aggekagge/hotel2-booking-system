-- Add the Agust demo administrator to an existing Hotel2 database.
-- Username: agust; password: agust@hotel2.
-- Existing accounts are left unchanged if either identity is already present.
USE hotel2_db;

INSERT INTO worker (password, loginName, email, tel, role, hotel, status)
VALUES ('b37c986ff065cf91bb773edb3999e6d51cb57954b188cbef6f29fca870fc4596', 'agust', 'agust@hotel2.com', '0100000003', '1', 'hotel2', 0)
ON DUPLICATE KEY UPDATE userid = worker.userid;

SELECT loginName, email, role, hotel, status
FROM worker
WHERE loginName = 'agust' OR email = 'agust@hotel2.com';
