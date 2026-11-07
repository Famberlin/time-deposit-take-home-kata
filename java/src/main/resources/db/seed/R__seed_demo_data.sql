-- Demo data for the local profile only (see application-local.yaml).
-- Covers every plan, the 30/31, 45/46 and 365/366 day edges and an unknown plan.
-- Idempotent: rerunning it after a change keeps existing rows and balances.
INSERT INTO time_deposits (id, plan_type, days, balance) VALUES
    (1,  'basic',   30,  10000.00),
    (2,  'basic',   31,  10000.00),
    (3,  'student', 31,   5000.00),
    (4,  'student', 365,  5000.00),
    (5,  'student', 366,  5000.00),
    (6,  'premium', 45,  20000.00),
    (7,  'premium', 46,  20000.00),
    (8,  'premium', 400, 1234567.00),
    (9,  'gold',    100,  1000.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO withdrawals (id, time_deposit_id, amount, date) VALUES
    (1, 2, 500.00,  DATE '2026-09-15'),
    (2, 2, 250.00,  DATE '2026-10-01'),
    (3, 7, 1000.00, DATE '2026-10-20')
ON CONFLICT (id) DO NOTHING;

-- Explicit ids do not advance the identity sequences.
SELECT setval(pg_get_serial_sequence('time_deposits', 'id'), (SELECT MAX(id) FROM time_deposits));
SELECT setval(pg_get_serial_sequence('withdrawals', 'id'), (SELECT MAX(id) FROM withdrawals));
