# Migration Database dengan Flyway

Dokumen ini menjelaskan:

1. cara kerja Flyway di proyek ini,
2. isi setiap file migration (`V1`, `V2`, dan seed),
3. kenapa aplikasi gagal start setelah `V2__prevent_overlapping_bookings.sql` ditambahkan, dan cara memperbaikinya.

Versi yang dipakai: Flyway 12.4, PostgreSQL 18.4.

---

## 1. Flyway itu apa?

Flyway adalah *version control* untuk skema database. Perubahan skema tidak dilakukan manual lewat psql, melainkan ditulis sebagai file SQL bernomor. Flyway menjalankan file-file itu **berurutan, masing-masing tepat satu kali**.

### 1.1 Konfigurasi (`application.yaml`)

```yaml
spring:
  flyway:
    locations: classpath:db/migration, classpath:db/seed
  jpa:
    hibernate:
      ddl-auto: validate
```

- `locations`: dua folder dipindai dan isinya **digabung menjadi satu urutan versi**.
- `ddl-auto: validate`: Hibernate **tidak membuat/mengubah tabel**. Hibernate hanya mencocokkan entity dengan tabel hasil Flyway. Jika ada kolom yang tidak cocok, aplikasi gagal start. Jadi Flyway adalah satu-satunya sumber skema.

### 1.2 Format nama file

```
V1000__seed_venues.sql
│└─┬─┘└┬┘└────┬────┘
│  │   │      └── deskripsi (underscore → spasi di tabel history)
│  │   └── pemisah: DUA underscore
│  └── nomor versi (dibandingkan sebagai angka: 2 < 10 < 1000)
└── V = versioned migration (jalan sekali)
```

### 1.3 Apa yang terjadi saat aplikasi start

```
Spring Boot start
  └─ FlywayAutoConfiguration
       1. buat/baca tabel  flyway_schema_history
       2. VALIDATE: bandingkan file di classpath ↔ baris di history
            - checksum file yang sudah dijalankan berubah?  → GAGAL
            - ada file bernomor LEBIH KECIL dari versi terakhir
              yang belum dijalankan?                       → GAGAL (kecuali outOfOrder)
       3. MIGRATE: jalankan file yang versinya > versi terakhir, urut dari kecil
       4. catat setiap file ke flyway_schema_history (versi, checksum, sukses)
  └─ Hibernate (ddl-auto: validate) mengecek entity vs tabel
  └─ Tomcat start
```

Jika langkah 2 atau 3 gagal, `entityManagerFactory` ikut gagal dibuat dan **aplikasi berhenti**.

Melihat history:

```bash
PGPASSWORD=booking psql -h localhost -U booking -d booking \
  -c "select installed_rank, version, description, checksum, success from flyway_schema_history order by installed_rank"
```

---

## 2. Isi migration di proyek ini

| File | Folder | Isi |
|---|---|---|
| `V1__init_schema.sql` | `db/migration` | Tabel `users`, `venues`, `courts`, `bookings`, `payments` beserta index dan CHECK |
| `V2__prevent_overlapping_bookings.sql` | `db/migration` | Constraint anti-bentrok untuk booking (solusi race condition) |
| `V1000__seed_venues.sql` | `db/seed` | 2 venue contoh beserta lapangannya |
| `V1001__seed.admin.sql` | `db/seed` | Akun `admin@booking.local` (password `password`, **khusus development**) |

Rencana penomoran: **skema di bawah 1000, seed mulai 1000**. Rencana ini menjadi sumber masalah di §3.

### 2.1 V1: skema awal (ringkasan)

- Semua `id` bertipe `UUID DEFAULT uuidv7()`. UUIDv7 berurutan waktu, sehingga lebih ramah index dibanding UUIDv4.
- `venues`: `CHECK (open_time < close_time)`.
- `courts`: `UNIQUE (venue_id, name)`, karena nama lapangan tidak boleh kembar dalam satu venue.
- `bookings`: `code UNIQUE`, `CHECK (start_time < end_time)`, kolom `version` untuk optimistic locking, index `(court_id, booking_date)` untuk query ketersediaan.

### 2.2 V2: constraint anti-bentrok

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings
    ADD CONSTRAINT no_overlapping_active_booking
    EXCLUDE USING gist (
        court_id WITH =,
        tsrange(booking_date + start_time, booking_date + end_time, '[)') WITH &&
    )
    WHERE (status IN ('PENDING_PAYMENT', 'CONFIRMED'));
```

Baca seperti ini: *"Tolak baris baru jika ada baris lain dengan `court_id` **sama** (`=`) DAN rentang waktunya **beririsan** (`&&`), dengan syarat keduanya berstatus aktif."*

| Bagian | Arti |
|---|---|
| `EXCLUDE` | Generalisasi `UNIQUE`. `UNIQUE` hanya bisa membandingkan dengan `=`, sedangkan `EXCLUDE` bisa memakai operator apa saja, termasuk `&&` (overlap) |
| `USING gist` | Index GiST mendukung pencarian rentang (`&&`). B-tree biasa tidak bisa |
| `btree_gist` | Ekstensi agar kolom biasa (`court_id` UUID dengan `=`) bisa ikut di index GiST. Ekstensi ini *trusted*, jadi user `booking` (bukan superuser) boleh memasangnya |
| `booking_date + start_time` | `DATE + TIME` menghasilkan `TIMESTAMP`, misalnya `2026-11-21 19:00` |
| `tsrange(a, b, '[)')` | Rentang waktu: awal **termasuk**, akhir **tidak termasuk**. Karena itu 19:00–21:00 dan 21:00–22:00 **tidak** bentrok |
| `WHERE status IN (...)` | *Partial constraint*: booking `CANCELLED`/`EXPIRED` diabaikan, sehingga slot yang dibatalkan bisa dipesan lagi |

**Kenapa perlu di database, padahal sudah ada `existsOverlap` di Java?**
`existsOverlap` adalah *check-then-act*. Dua request bersamaan bisa sama-sama lolos pengecekan sebelum salah satunya INSERT. Constraint database dievaluasi **saat INSERT** dengan locking internal PostgreSQL, sehingga hanya satu yang berhasil. Yang kalah mendapat SQLState `23P01`. `BookingService.create()` menangkapnya lewat `saveAndFlush` + `isOverlapViolation()`, lalu mengubahnya menjadi **409 `SLOT_TAKEN`**.

> **Jebakan:** Hibernate hanya mengenali nama constraint untuk UNIQUE/FK/NOT NULL/CHECK. Untuk `EXCLUDE` (SQLState `23P01`),
> `ConstraintViolationException.getConstraintName()` bernilai **`null`**. Di log terlihat sebagai `constraint [null]`.
> Pengecekan berdasarkan nama dari Hibernate selalu gagal sehingga user mendapat 500.
> Karena itu `isOverlapViolation()` membaca `SQLException` asli dari driver: SQLState `23P01` + nama constraint di pesan error.

---

## 3. Kenapa aplikasi gagal start?

Pesan error:

```
FlywayValidateException: Validate failed: Migrations have failed validation
Detected resolved migration not applied to database: 2.
To ignore this migration, set -ignoreMigrationPatterns='*:ignored'.
To allow executing this migration, set -outOfOrder=true.
```

### 3.1 Penyebab utama: V2 datang "terlambat"

Isi `flyway_schema_history` di database Anda:

```
installed_rank | version | description
             1 | 1       | init schema
             2 | 1000    | seed venues
             3 | 1001    | seed.admin     ← versi terakhir = 1001
```

File di classpath sekarang: `1, 2, 1000, 1001`.

```
waktu ─────────────────────────────────────────────────────────►
  V1 ✔      V1000 ✔     V1001 ✔                  V2 (file baru)
                                   versi DB = 1001     │
                                                       ▼
                         2 < 1001 dan belum pernah dijalankan
                         → Flyway: "ada migration yang terlewat" → GAGAL
```

Flyway menganggap versi adalah **garis waktu**. Database sudah berada di versi 1001, lalu muncul versi 2 yang belum pernah dijalankan. Secara default Flyway menolak menjalankannya, karena migration yang terlewat bisa berarti ada yang salah (misalnya hasil merge branch). Flyway tidak menebak; ia berhenti.

Masalah ini **akan selalu muncul** dengan skema penomoran "skema < 1000, seed ≥ 1000" selama seed sudah terpasang. Setiap migration skema baru (V3, V4, ...) akan bernomor lebih kecil dari 1001.

### 3.2 Masalah kedua yang tersembunyi: data sudah bentrok

Walaupun Flyway dipaksa menjalankan V2, migration itu tetap **gagal** saat ini:

```
ERROR: could not create exclusion constraint "no_overlapping_active_booking"
DETAIL: Key (01a1c95c-..., ["2026-11-21 19:00","2026-11-21 21:00"))
        conflicts with key (01a1c95c-..., ["2026-11-21 19:00","2026-11-21 21:00"))
```

PostgreSQL memeriksa **semua baris yang sudah ada** saat constraint dibuat. Di tabel `bookings` ada **10 booking `PENDING_PAYMENT` milik `budi@mail.com`** pada court yang sama, tanggal 2026-11-21 pukul 19:00–21:00, semuanya dibuat pada 10:47:37.82 (selisih mikrodetik).

Itu hasil uji race condition yang dijalankan sebelum constraint aktif. Hasil ini sekaligus **bukti** bahwa `existsOverlap` saja tidak cukup: 10 request paralel, dan 10 kali lolos.

> Di PostgreSQL, DDL bersifat transaksional. Migration yang gagal di-rollback seluruhnya, termasuk `CREATE EXTENSION`, dan tidak dicatat di history. Jadi aman untuk dicoba lagi setelah data dibersihkan.

---

## 4. Cara memperbaiki

Langkah 4.1 dan 4.2 sudah diuji di dalam transaksi yang di-`ROLLBACK`. Hasilnya: 9 baris dibatalkan, constraint berhasil dibuat, dan INSERT bentrok berikutnya ditolak dengan `23P01`.

### 4.1 Bersihkan booking aktif yang bentrok

Untuk setiap kelompok yang bentrok, booking **paling awal** dipertahankan dan sisanya diubah menjadi `CANCELLED`. Data tidak dihapus.

```bash
PGPASSWORD=booking psql -h localhost -U booking -d booking <<'SQL'
UPDATE bookings b SET status = 'CANCELLED'
WHERE status IN ('PENDING_PAYMENT','CONFIRMED')
  AND EXISTS (SELECT 1 FROM bookings o
              WHERE o.court_id = b.court_id AND o.booking_date = b.booking_date
                AND o.status IN ('PENDING_PAYMENT','CONFIRMED')
                AND o.start_time < b.end_time AND b.start_time < o.end_time
                AND (o.created_at, o.id) < (b.created_at, b.id));
SQL
```

Hasil yang diharapkan: `UPDATE 9`.

### 4.2 Izinkan Flyway menjalankan V2 (pilih salah satu)

**Opsi A: `out-of-order` (direkomendasikan untuk proyek ini).** Tambahkan di `application.yaml`:

```yaml
spring:
  flyway:
    locations: classpath:db/migration, classpath:db/seed
    out-of-order: true
```

Flyway akan menjalankan V2 walaupun versi DB sudah 1001. Di database baru (misalnya server production), urutannya tetap alami: `1 → 2 → 1000 → 1001`. Opsi ini mempertahankan pola "skema < 1000, seed ≥ 1000" untuk migration berikutnya.

**Opsi B: reset database development.** Hapus semua tabel lalu biarkan Flyway membangun ulang dari nol. **Semua data uji hilang.**

```bash
PGPASSWORD=booking psql -h localhost -U booking -d booking \
  -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
```

Opsi ini menyelesaikan §3.1 dan §3.2 sekaligus, tetapi hanya untuk database Anda saat ini. Migration skema berikutnya (V3) akan memunculkan masalah yang sama lagi.

**Yang tidak disarankan:**

| Cara | Kenapa |
|---|---|
| Mengganti nama menjadi `V1002__prevent_...` | Bisa jalan, tetapi skema bercampur dengan rentang seed. Di database baru, constraint dibuat **setelah** seed. Selain itu, seed berikutnya harus mulai dari 1003 |
| `ignoreMigrationPatterns='*:ignored'` | V2 **tidak pernah dijalankan**. Aplikasi start, tetapi constraint tidak ada dan race condition tetap terjadi |
| Mengedit `V1__init_schema.sql` | V1 sudah tercatat dengan checksum tertentu. Mengubahnya membuat validasi gagal (`checksum mismatch`) |

### 4.3 Verifikasi

Jalankan ulang aplikasi. Di log harus muncul:

```
Migrating schema "public" to version "2 - prevent overlapping bookings"
Successfully applied 1 migration to schema "public", now at version v2
```

Kemudian:

```bash
PGPASSWORD=booking psql -h localhost -U booking -d booking \
  -c "select version, description, success from flyway_schema_history order by installed_rank" \
  -c "\d bookings" | grep -E "^ +[0-9]|no_overlapping"
```

Setelah itu, uji race condition paralel seharusnya menghasilkan **satu 201** dan sisanya **409 `SLOT_TAKEN`**. Pesan 409-nya ada dua kemungkinan:

- *"sudah dipesan"*: ditolak oleh `existsOverlap`.
- *"baru saja dipesan orang lain"*: ditolak oleh constraint database.

---

## 5. Aturan praktis Flyway

1. **Jangan pernah mengedit migration yang sudah dijalankan.** Buat file baru (`V3__...`) untuk perubahan berikutnya.
2. **Nomor versi tidak boleh dipakai ulang.** Dua file `V2__*` membuat Flyway gagal start.
3. **Constraint baru memeriksa data lama.** Sebelum menambah `UNIQUE`/`EXCLUDE`/`CHECK`/`NOT NULL`, pastikan data yang ada tidak melanggarnya, atau bersihkan data di migration yang sama.
4. **Seed khusus development jangan ikut ke production.** Akun admin dengan password `password` tidak boleh ada di server. Caranya, atur `spring.flyway.locations` per profile, misalnya `application-dev.yaml` memuat `db/seed` sedangkan default hanya `db/migration`.
5. **`flyway repair`** hanya untuk memperbaiki tabel history (misalnya menghapus catatan migration gagal di database yang DDL-nya tidak transaksional). Perintah ini bukan solusi untuk error di §3.
6. Jika ragu, uji migration di dalam transaksi terlebih dahulu:

   ```bash
   PGPASSWORD=booking psql -h localhost -U booking -d booking <<'SQL'
   BEGIN;
   \i src/main/resources/db/migration/V2__prevent_overlapping_bookings.sql
   ROLLBACK;
   SQL
   ```
