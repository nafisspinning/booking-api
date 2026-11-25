-- Payment dimiliki oleh booking (1 booking : 1 payment), sesuai entity Payment (@OneToOne booking).
-- Pemilik payment cukup diturunkan dari bookings.user_id, jadi kolom user_id di payments dihapus.
ALTER TABLE payments DROP COLUMN user_id;

ALTER TABLE payments
    ADD COLUMN booking_id UUID NOT NULL
        CONSTRAINT fk_payments_booking REFERENCES bookings (id),
    ADD CONSTRAINT uq_payments_booking UNIQUE (booking_id);
