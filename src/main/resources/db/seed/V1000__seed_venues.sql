WITH inserted_venues AS (
    INSERT INTO venues (id, name, address, open_time, close_time)
    VALUES
        (uuidv7(), 'Arena Futsal Senayan', 'Jl. Pintu Satu Senayan, Jakarta', '08:00', '23:00'),
        (uuidv7(), 'GOR Badminton Cempaka', 'Jl. Cempaka Putih Raya 12, Jakarta', '07:00', '22:00')
    RETURNING id, name
)
INSERT INTO courts (venue_id, name, sport_type, price_per_hour)
SELECT
    v.id,
    c.name,
    c.sport_type,
    c.price_per_hour
FROM inserted_venues v
JOIN (
    VALUES
        ('Arena Futsal Senayan', 'Lapangan A', 'FUTSAL', 150000),
        ('Arena Futsal Senayan', 'Lapangan B', 'FUTSAL', 175000),
        ('GOR Badminton Cempaka', 'Court 1', 'BADMINTON', 60000),
        ('GOR Badminton Cempaka', 'Court 2', 'BADMINTON', 60000),
        ('GOR Badminton Cempaka', 'Court 3', 'BADMINTON', 75000)
) AS c(venue_name, name, sport_type, price_per_hour)
    ON v.name = c.venue_name;