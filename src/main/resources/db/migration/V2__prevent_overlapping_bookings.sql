-- btree_gist dibutuhkan agar kolom biasa (court_id) bisa digabung dengan range di index GiST
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- tambah kondisi 
ALTER TABLE bookings
    ADD CONSTRAINT no_overlapping_active_booking
    EXCLUDE USING gist (
        court_id WITH =,
        tsrange(booking_date + start_time, booking_date + end_time, '[)') WITH &&
    )
    WHERE (status IN ('PENDING_PAYMENT', 'CONFIRMED'));