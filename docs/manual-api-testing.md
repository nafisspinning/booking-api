# Panduan Tes Manual API Booking (per Role)

Panduan ini berisi langkah-langkah untuk menguji semua endpoint secara manual dengan `curl`,
sebagai **tiga identitas berbeda**:

| Identitas | Artinya |
|---|---|
| `anon`  | Tanpa login (tidak mengirim header `Authorization`) |
| `user`  | Login sebagai user biasa (role `USER`) |
| `admin` | Login sebagai admin (role `ADMIN`) |

Ikuti bagian-bagiannya **secara berurutan**, karena beberapa langkah memakai ID dari langkah sebelumnya
(disimpan di variabel shell seperti `$VENUE`, `$COURT`, `$BOOKING`).

---

## Daftar Isi

1. [Persiapan](#1-persiapan)
2. [Cara membaca status HTTP](#2-cara-membaca-status-http)
3. [Ringkasan hak akses](#3-ringkasan-hak-akses)
4. [Auth](#4-auth)
5. [Venue](#5-venue)
6. [Court](#6-court)
7. [Availability (slot per jam)](#7-availability-slot-per-jam)
8. [Booking](#8-booking)
9. [Menghapus venue & court](#9-menghapus-venue--court)
10. [Cara kerja token (opsional, untuk dipahami)](#10-cara-kerja-token-opsional-untuk-dipahami)
11. [Troubleshooting](#11-troubleshooting)
12. [Membersihkan data tes](#12-membersihkan-data-tes)

---

## 1. Persiapan

### 1.1 Jalankan aplikasi

```bash
./mvnw spring-boot:run
```

Tunggu sampai muncul log `Started BookingApplication`. Pastikan PostgreSQL sudah berjalan.

### 1.2 Pastikan `jq` terpasang

`jq` dipakai untuk mengambil nilai dari JSON (misalnya token dan ID).

```bash
jq --version || brew install jq
```

### 1.3 Siapkan helper `api`

Salin **seluruh blok** ini ke terminal (satu kali per sesi terminal):

```bash
BASE=localhost:8080
J='Content-Type: application/json'

# api <anon|user|admin> <argumen curl lainnya>
#  - body response  -> stdout (bisa di-pipe ke jq)
#  - status HTTP    -> stderr (tetap tampil di layar)
api() {
  local who=$1; shift
  local auth=()
  [ "$who" = user ]  && auth=(-H "Authorization: Bearer $USER_TOKEN")
  [ "$who" = admin ] && auth=(-H "Authorization: Bearer $ADMIN_TOKEN")
  curl -s -w '%{stderr}\n-> HTTP %{http_code}\n' "${auth[@]}" "$@"
}

# tanggal booking: 3 hari dari hari ini (aturan: harus di masa depan, maks 30 hari)
DATE=$(date -v+3d +%F)          # macOS
# DATE=$(date -d '+3 days' +%F) # Linux
echo "DATE=$DATE"
```

> **Kenapa `anon` tidak mengirim header sama sekali?**
> Header `Authorization: Bearer ` yang kosong/rusak akan ditolak **401 bahkan di endpoint publik**.
> Jadi "tidak login" harus berarti header-nya tidak ada.

### 1.4 Ambil token USER dan ADMIN

```bash
# daftar user biasa (abaikan jika email sudah terdaftar → 409)
api anon -X POST $BASE/api/auth/register -H "$J" \
  -d '{"fullName":"Budi","email":"budi@mail.com","password":"booking123"}'

USER_TOKEN=$(curl -s -X POST $BASE/api/auth/login -H "$J" \
  -d '{"email":"budi@mail.com","password":"booking123"}' | jq -er .accessToken) \
  || echo "LOGIN USER GAGAL"

# akun admin berasal dari seed V1001__seed.admin.sql (password khusus development)
ADMIN_TOKEN=$(curl -s -X POST $BASE/api/auth/login -H "$J" \
  -d '{"email":"admin@booking.local","password":"password"}' | jq -er .accessToken) \
  || echo "LOGIN ADMIN GAGAL"

echo "USER_TOKEN=${USER_TOKEN:0:20}..."
echo "ADMIN_TOKEN=${ADMIN_TOKEN:0:20}..."
```

Token berlaku **2 jam**. Jika nanti semua request tiba-tiba 401, ulangi langkah 1.4.

---

## 2. Cara membaca status HTTP

Urutan pengecekan di server selalu: **autentikasi → otorisasi → validasi → aturan bisnis**.
Request berhenti di pengecekan pertama yang gagal.

| Status | Arti | Tahap yang gagal |
|---|---|---|
| **401** Unauthorized | Server **tidak tahu siapa Anda** (token tidak ada / salah / kedaluwarsa) | Autentikasi |
| **403** Forbidden | Server **tahu siapa Anda**, tapi role Anda tidak diizinkan | Otorisasi |
| **400** Bad Request | Format request salah: field kosong, JSON rusak, UUID tidak valid | Validasi |
| **404** Not Found | Data tidak ada (atau bukan milik Anda) | Aturan bisnis |
| **409** Conflict | Bentrok dengan data lain (slot dipesan, nama dipakai, venue masih punya court) | Aturan bisnis |
| **422** Unprocessable Content | Format benar, tapi melanggar aturan (jam tidak bulat, durasi, tanggal, dll.) | Aturan bisnis |
| **500** Internal Server Error | Bug di server — seharusnya tidak pernah terjadi | – |

**Tips penting:** jika admin mendapat **400/404/409/422**, artinya admin **sudah lolos otorisasi**.
Ini cara cepat membuktikan rule keamanan tanpa harus membuat data valid.

Format body error (kecuali 401/403 dari Spring Security):

```json
{
  "title": "Conflict",
  "status": 409,
  "detail": "Slot 11:00-13:00 sudah dipesan",
  "instance": "/api/bookings",
  "code": "SLOT_TAKEN",
  "timestamp": "2026-11-16T01:42:38Z"
}
```

---

## 3. Ringkasan hak akses

Hasil yang **diharapkan** untuk setiap endpoint:

| Endpoint | anon | user | admin |
|---|---|---|---|
| `POST /api/auth/register`, `POST /api/auth/login` | ✅ | ✅ | ✅ |
| `GET /api/me` | 401 | ✅ | ✅ |
| `GET /api/venues`, `GET /api/venues/{id}` | ✅ | ✅ | ✅ |
| `POST /api/venues` | 401 | 403 | ✅ |
| `PUT /api/venues/{id}` | 401 | 403 | ✅ |
| `DELETE /api/venues/{id}` | 401 | 403 | ✅ |
| `POST /api/venues/{id}/courts` | 401 | 403 | ✅ |
| `GET /api/courts`, `GET /api/courts/{id}` | ✅ | ✅ | ✅ |
| `PUT /api/courts/{id}` | 401 | 403 ⚠️ | ✅ |
| `DELETE /api/courts/{id}` (nonaktifkan) | 401 | 403 | ✅ |
| `GET /api/courts/{id}/availability` | ✅ | ✅ | ✅ |
| `POST /api/bookings` | 401 | ✅ | ✅ |
| `GET /api/bookings/me` | 401 | ✅ (milik sendiri) | ✅ (milik sendiri) |
| `PATCH /api/bookings/{id}/cancel` | 401 | ✅ (milik sendiri) | 404 untuk booking orang lain |

> ⚠️ **Bug yang diketahui:** saat panduan ini dibuat, `PUT /api/courts/{id}` masih menghasilkan **200 untuk user**,
> karena `SecurityConfig` belum punya rule `PUT /api/courts/**`. Lihat [bagian 6.3](#63-update-court-admin).

---

## 4. Auth

### 4.1 Register

```bash
api anon -X POST $BASE/api/auth/register -H "$J" \
  -d '{"fullName":"Siti","email":"siti@mail.com","password":"booking123"}'
```

Hasil: **201**, body berisi `accessToken`, `tokenType: "Bearer"`, `expiresIn: 7200` (detik).
User baru selalu mendapat role `USER`.

| Coba ini | Hasil |
|---|---|
| Ulangi perintah di atas (email sama) | **409** `EMAIL_TAKEN` |
| Password < 8 karakter: `"password":"123"` | **400** `VALIDATION_ERROR` + `errors.password` |
| Email tidak valid: `"email":"siti"` | **400** `VALIDATION_ERROR` + `errors.email` |

### 4.2 Login

```bash
api anon -X POST $BASE/api/auth/login -H "$J" \
  -d '{"email":"budi@mail.com","password":"booking123"}'
```

| Coba ini | Hasil |
|---|---|
| Password salah | **401** `UNAUTHORIZED` "email or password is incorrect" |
| Email tidak terdaftar | **401** — pesan sama, sengaja agar orang tidak bisa menebak email mana yang terdaftar |

### 4.3 Profil saya — `GET /api/me`

```bash
api anon  $BASE/api/me     # 401
api user  $BASE/api/me     # 200, "role":"USER"
api admin $BASE/api/me     # 200, "role":"ADMIN"
```

**Yang dipelajari:** endpoint yang sama memberi hasil berbeda tergantung **token siapa** yang dikirim.
Server mengambil ID user dari claim `sub` di dalam token.

---

## 5. Venue

### 5.1 Lihat daftar & detail (publik)

```bash
api anon "$BASE/api/venues?page=0&size=5&sort=name,asc"
```

Hasil **200** untuk semua identitas. Format pagination:

```json
{ "content": [ ... ], "page": 0, "size": 5, "totalElements": 2, "totalPages": 1 }
```

### 5.2 Buat venue (admin)

```bash
VBODY='{"name":"Venue Coba","address":"Jl. Coba 1","openTime":"08:00","closeTime":"22:00"}'

api anon -X POST $BASE/api/venues -H "$J" -d "$VBODY"   # 401
api user -X POST $BASE/api/venues -H "$J" -d "$VBODY"   # 403

VENUE=$(api admin -X POST $BASE/api/venues -H "$J" -d "$VBODY" | jq -r .id)   # 201
echo "VENUE=$VENUE"

api anon $BASE/api/venues/$VENUE    # 200, detail venue
```

> Jika `VENUE=null`, kemungkinan nama venue sudah dipakai atau token admin kedaluwarsa.
> Jalankan perintah admin tanpa `| jq ...` untuk melihat pesan errornya.

| Coba ini (sebagai admin) | Hasil |
|---|---|
| `"openTime":"22:00","closeTime":"08:00"` | **422** `INVALID_HOURS` |
| `"name":""` | **400** `VALIDATION_ERROR` |
| `GET /api/venues/abc` | **400** — `abc` bukan UUID |
| `GET /api/venues/00000000-0000-7000-8000-000000000000` | **404** `NOT_FOUND` |

### 5.3 Update venue (admin)

```bash
VBODY2='{"name":"Venue Coba","address":"Jl. Coba 2","openTime":"08:00","closeTime":"22:00"}'

api user  -X PUT $BASE/api/venues/$VENUE -H "$J" -d "$VBODY2"   # 403
api admin -X PUT $BASE/api/venues/$VENUE -H "$J" -d "$VBODY2"   # 200, address berubah
```

---

## 6. Court

### 6.1 Tambah court ke venue (admin)

```bash
CBODY='{"name":"Lapangan 1","sportType":"FUTSAL","pricePerHour":100000}'

api user -X POST $BASE/api/venues/$VENUE/courts -H "$J" -d "$CBODY"   # 403

COURT=$(api admin -X POST $BASE/api/venues/$VENUE/courts -H "$J" -d "$CBODY" | jq -r .id)   # 201
echo "COURT=$COURT"
```

| Coba ini (sebagai admin) | Hasil |
|---|---|
| Ulangi perintah yang sama (nama court sama di venue yang sama) | seharusnya **409** `COURT_NAME_TAKEN` — ⚠️ saat ini **500** (lihat catatan di bawah) |
| `"pricePerHour":0` | **400** `VALIDATION_ERROR` (harus positif) |
| `"sportType":"GOLF"` | **400** — bukan nilai enum (`FUTSAL`, `BADMINTON`, `BASKETBALL`, `TENNIS`) |

> ⚠️ **Bug yang diketahui:** `VenueService.addCourt` dan `VenueService.updateCourt` memakai `'%d'` untuk nama court
> (`String`) di pesan `COURT_NAME_TAKEN`, sehingga nama duplikat menghasilkan **500**
> (`IllegalFormatConversionException: d != java.lang.String`). Perbaikan: ganti `%d` menjadi `%s`.

### 6.2 Cari court (publik)

```bash
api anon "$BASE/api/courts?sportType=FUTSAL&page=0&size=5"
api anon $BASE/api/courts/$COURT
```

> Di Swagger, kosongkan field `sort` (isi contohnya `["string"]` akan membuat request gagal).
> Format sort yang benar: `sort=pricePerHour,desc`.

### 6.3 Update court (admin)

```bash
api user  -X PUT $BASE/api/courts/$COURT -H "$J" \
  -d '{"name":"Lapangan 1","sportType":"FUTSAL","pricePerHour":1000}'     # seharusnya 403
api admin -X PUT $BASE/api/courts/$COURT -H "$J" \
  -d '{"name":"Lapangan 1","sportType":"FUTSAL","pricePerHour":120000}'   # 200
```

> ⚠️ **Bug yang diketahui:**
> - Perintah **user** di atas saat ini menghasilkan **200** (user bisa mengubah harga!).
>   Perbaikan: tambahkan `.requestMatchers(HttpMethod.PUT, "/api/courts/**").hasRole("ADMIN")` di `SecurityConfig`.
> - Update court ke nama yang sudah dipakai court lain di venue yang sama menghasilkan **500**
>   (seharusnya **409** `COURT_NAME_TAKEN`) — bug `%d` yang sama seperti di [bagian 6.1](#61-tambah-court-ke-venue-admin).

Jalankan ulang perintah user setelah memperbaiki — hasilnya harus berubah menjadi 403.

---

## 7. Availability (slot per jam)

Slot **tidak disimpan di database**; dihitung saat diminta dari jam buka–tutup venue, per **1 jam**.

```bash
api anon "$BASE/api/courts/$COURT/availability?date=$DATE"
```

Hasil **200**:

```json
[
  {"start":"08:00:00","end":"09:00:00","available":true},
  {"start":"09:00:00","end":"10:00:00","available":true},
  ...
  {"start":"21:00:00","end":"22:00:00","available":true}
]
```

Slot menjadi `available:false` jika: waktunya sudah lewat, sudah dibooking (`PENDING_PAYMENT`/`CONFIRMED`),
atau court nonaktif.

| Coba ini | Hasil |
|---|---|
| Tanpa `?date=...` | **400** "Required parameter 'date' is not present" |
| Court tidak ada: `/api/courts/00000000-0000-7000-8000-000000000000/availability?date=$DATE` | seharusnya **404** — ⚠️ saat ini **500** karena `%d` untuk UUID di `AvailabilityService` |

---

## 8. Booking

### 8.1 Buat booking

```bash
BBODY="{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"10:00\",\"endTime\":\"12:00\"}"

api anon -X POST $BASE/api/bookings -H "$J" -d "$BBODY"     # 401

BOOKING=$(api user -X POST $BASE/api/bookings -H "$J" -d "$BBODY" | jq -r .id)   # 201
echo "BOOKING=$BOOKING"
```

Contoh response **201**:

```json
{
  "id": "01a1df16-...",
  "code": "BK-20261119-AF56B4",
  "courtName": "Lapangan 1",
  "venueName": "Venue Coba",
  "date": "2026-11-19",
  "startTime": "10:00:00",
  "endTime": "12:00:00",
  "totalPrice": 240000.00,
  "status": "PENDING_PAYMENT",
  "expiresAt": "2026-11-16T02:17:09Z"
}
```

**Bagaimana data ini terbentuk:**

| Field | Asal |
|---|---|
| `code` | `BK-` + tanggal main + 6 karakter acak |
| `totalPrice` | `pricePerHour × jumlah jam` (contoh: 120.000 × 2) |
| `status` | Selalu `PENDING_PAYMENT` saat dibuat |
| `expiresAt` | Waktu dibuat + 15 menit (batas bayar). Dalam **UTC** — tambah 7 jam untuk WIB |

Cek availability lagi — slot 10–11 dan 11–12 sekarang `available:false`:

```bash
api anon "$BASE/api/courts/$COURT/availability?date=$DATE" | jq -c '.[] | select(.available==false)'
```

### 8.2 Kasus error saat membuat booking

Jalankan sebagai `user`. Ubah hanya bagian yang disebut:

```bash
book() { api user -X POST $BASE/api/bookings -H "$J" -d "$1"; }

# Slot bentrok (11–13 beririsan dengan 10–12)                     → 409 SLOT_TAKEN
book "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"11:00\",\"endTime\":\"13:00\"}"

# Jam tidak bulat                                                  → 422 INVALID_SLOT
book "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"14:30\",\"endTime\":\"16:00\"}"

# Durasi lebih dari 4 jam                                          → 422 INVALID_DURATION
book "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"13:00\",\"endTime\":\"18:00\"}"

# Jam selesai sebelum jam mulai                                    → 422 INVALID_DURATION
book "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"15:00\",\"endTime\":\"14:00\"}"

# Di luar jam operasional (venue buka 08:00)                       → 422 OUTSIDE_OPERATING_HOURS
book "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"06:00\",\"endTime\":\"08:00\"}"

# Tanggal sudah lewat                                              → 422 INVALID_DATE
book "{\"courtId\":\"$COURT\",\"date\":\"2025-01-01\",\"startTime\":\"10:00\",\"endTime\":\"12:00\"}"

# Lebih dari 30 hari ke depan                                      → 422 INVALID_DATE
book "{\"courtId\":\"$COURT\",\"date\":\"$(date -v+60d +%F)\",\"startTime\":\"10:00\",\"endTime\":\"12:00\"}"

# Court tidak ada                                                  → 404 NOT_FOUND
book "{\"courtId\":\"00000000-0000-7000-8000-000000000000\",\"date\":\"$DATE\",\"startTime\":\"10:00\",\"endTime\":\"12:00\"}"

# Field kosong                                                     → 400 VALIDATION_ERROR (+ daftar errors)
book "{\"courtId\":\"$COURT\"}"

# JSON rusak: UUID tanpa tanda kutip                               → 400 "Failed to read request"
book "{\"courtId\":$COURT,\"date\":\"$DATE\",\"startTime\":\"10:00\",\"endTime\":\"12:00\"}"
```

**Urutan pengecekan** di server: validasi body (400) → court ada? (404) → aturan jam/tanggal (422) → slot bentrok (409).

### 8.3 Admin juga bisa booking

```bash
api admin -X POST $BASE/api/bookings -H "$J" \
  -d "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"14:00\",\"endTime\":\"15:00\"}"   # 201
```

`POST /api/bookings` hanya mensyaratkan **login** (`anyRequest().authenticated()`), tanpa cek role.
Booking tercatat atas nama admin sendiri.

### 8.4 Riwayat booking saya

```bash
api anon  $BASE/api/bookings/me                  # 401
api user  "$BASE/api/bookings/me?page=0&size=5"  # booking 10–12 milik user
api admin "$BASE/api/bookings/me?page=0&size=5"  # hanya booking 14–15 milik admin
```

**Yang dipelajari:** endpoint sama, data berbeda — server memfilter berdasarkan `sub` di token.

### 8.5 Batalkan booking

```bash
api anon  -X PATCH $BASE/api/bookings/$BOOKING/cancel   # 401
api admin -X PATCH $BASE/api/bookings/$BOOKING/cancel   # 404 — admin bukan pemiliknya
api user  -X PATCH $BASE/api/bookings/$BOOKING/cancel   # 200, "status":"CANCELLED"
api user  -X PATCH $BASE/api/bookings/$BOOKING/cancel   # 422 INVALID_STATUS (sudah dibatalkan)
```

> Booking milik orang lain dijawab **404 (bukan 403)** dengan sengaja,
> supaya orang lain tidak bisa mengetahui bahwa ID booking tersebut ada.

Setelah dibatalkan, cek availability lagi — slot 10–12 kembali `available:true`.

| Coba ini | Hasil |
|---|---|
| `PATCH /api/bookings/abc/cancel` | **400** — `abc` bukan UUID |
| `PATCH /api/bookings/00000000-0000-7000-8000-000000000000/cancel` | **404** |

### 8.6 Alur status booking

```
PENDING_PAYMENT ──bayar──▶ CONFIRMED ──selesai main──▶ COMPLETED
      │   └──lewat 15 menit──▶ EXPIRED
      └──cancel──▶ CANCELLED ◀──cancel (≥ 24 jam sebelum main)── CONFIRMED
```

Endpoint pembayaran dan scheduler kedaluwarsa **belum ada**, jadi `CONFIRMED` / `EXPIRED` belum bisa dicapai lewat API.
Untuk mencoba aturan **pembatalan maksimal 24 jam sebelum main** (`CANCEL_TOO_LATE`), simulasikan lewat SQL.

Aturannya: booking `CONFIRMED` hanya bisa dibatalkan jika **sekarang < (jam main − 24 jam)**.
Jadi jam main harus **kurang dari 24 jam dari sekarang**. Caranya: booking **besok pada jam yang sama dengan jam sekarang**.

> Jalankan antara pukul **08:00–21:59**, karena jam main harus berada di dalam jam operasional venue (08:00–22:00).
> Pakai court yang **masih aktif** (jika `$COURT` sudah dinonaktifkan di bagian 9, ambil court lain lewat `GET /api/courts`).

```bash
# 1. booking BESOK, mulai di jam yang sama dengan jam sekarang (misal sekarang 08:50 → besok 08:00–09:00)
TOMORROW=$(date -v+1d +%F)              # Linux: date -d '+1 day' +%F
H=$((10#$(date +%H)))
START=$(printf '%02d:00' $H); END=$(printf '%02d:00' $((H+1)))

B2=$(api user -X POST $BASE/api/bookings -H "$J" \
  -d "{\"courtId\":\"$COURT\",\"date\":\"$TOMORROW\",\"startTime\":\"$START\",\"endTime\":\"$END\"}" | jq -r .id)
echo "B2=$B2"

# 2. ubah status menjadi CONFIRMED langsung di database (password DB: booking)
PGPASSWORD=booking psql -h localhost -U booking -d booking \
  -c "UPDATE bookings SET status='CONFIRMED', expires_at=NULL WHERE id='$B2';"

# 3. coba batalkan → 422 CANCEL_TOO_LATE
api user -X PATCH $BASE/api/bookings/$B2/cancel
```

> Kalau Anda booking besok di jam yang **lebih siang** dari jam sekarang (misal sekarang 08:50, booking besok 10:00),
> pembatalan **berhasil** — karena jaraknya masih lebih dari 24 jam. Itu perilaku yang benar.

---

## 9. Menghapus venue & court

Urutan di bawah ini penting untuk memahami perilakunya:

```bash
api user  -X DELETE $BASE/api/courts/$COURT    # 403
api admin -X DELETE $BASE/api/venues/$VENUE    # 409 VENUE_HAS_COURTS (masih punya court)
api admin -X DELETE $BASE/api/courts/$COURT    # 204, court DINONAKTIFKAN (bukan dihapus)
api anon  $BASE/api/courts/$COURT              # masih ada, "active": false
api admin -X DELETE $BASE/api/venues/$VENUE    # tetap 409
```

**Kenapa venue tetap tidak bisa dihapus?**
`DELETE /api/courts/{id}` hanya mengubah `active=false`; barisnya tetap ada di tabel `courts`.
Venue yang pernah punya court tidak bisa dihapus lewat API — ini menjaga riwayat booking tetap utuh.

Venue **tanpa court** bisa dihapus:

```bash
V2=$(api admin -X POST $BASE/api/venues -H "$J" \
  -d '{"name":"Venue Hapus","address":"Jl. X","openTime":"08:00","closeTime":"22:00"}' | jq -r .id)
api admin -X DELETE $BASE/api/venues/$V2       # 204
api anon  $BASE/api/venues/$V2                 # 404
```

---

## 10. Cara kerja token (opsional, untuk dipahami)

### 10.1 Lihat isi token

Token JWT terdiri dari 3 bagian: `header.payload.signature`. Payload hanya di-encode Base64, **bukan dienkripsi**:

```bash
echo $ADMIN_TOKEN | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null; echo
```

```json
{"iss":"booking-api","sub":"01a1dc44-...","role":["ADMIN"],"exp":1794798952,"iat":1794791752,"email":"admin@booking.local"}
```

| Claim | Kegunaan |
|---|---|
| `sub` | ID user — dipakai controller lewat `jwt.getSubject()` |
| `role` | Diubah menjadi authority `ROLE_ADMIN` / `ROLE_USER` untuk rule `hasRole(...)` |
| `exp` | Waktu kedaluwarsa (Unix timestamp) |

### 10.2 Kenapa token tidak bisa dipalsukan

```bash
# token dirusak 1 karakter → 401 invalid_token: Invalid signature
api anon $BASE/api/me -H "Authorization: Bearer ${USER_TOKEN%?}x" -D - -o /dev/null | grep -i www-authenticate
```

Signature dibuat dengan `app.jwt.secret` yang hanya diketahui server. Mengubah payload (misalnya `USER` → `ADMIN`)
membuat signature tidak cocok, sehingga ditolak. Satu-satunya cara menjadi admin adalah **login sebagai admin**.

### 10.3 Alur di server

```
Request "Authorization: Bearer eyJ..."
  → BearerTokenAuthenticationFilter   ambil token dari header
  → JwtDecoder                        cek signature + exp           (gagal → 401)
  → JwtAuthenticationConverter        claim "role" → ROLE_ADMIN
  → authorizeHttpRequests             cocokkan rule hasRole(...)    (gagal → 403)
  → Controller                        @AuthenticationPrincipal Jwt
  → Service                           validasi & aturan bisnis      (400/404/409/422)
```

### 10.4 Mengganti token di browser (Swagger)

Swagger UI: http://localhost:8080/swagger-ui/index.html

Tanpa tombol *Authorize*, header bisa ditambahkan dengan ekstensi **ModHeader** (Firefox/Chrome):
isi `Authorization` = `Bearer <token>`, tambahkan filter URL `localhost:8080`,
lalu ganti nilainya untuk berpindah antara user dan admin.

Hubungan token terminal dan Swagger dijelaskan di [swagger-vs-terminal-token.md](swagger-vs-terminal-token.md).

---

## 11. Troubleshooting

| Gejala | Penyebab | Solusi |
|---|---|---|
| Terminal menampilkan `quote>` dan menunggu | Ada tanda kutip "pintar" (`’ ” “`) hasil copy dari Notes/Word | `Ctrl+C`, ketik ulang dengan kutip lurus `'` dan `"` |
| `TOKEN` berisi `null` | Login gagal, `jq` mengembalikan `null` | Gunakan `jq -er`, jalankan login tanpa `jq` untuk melihat error |
| Semua request 401 | Token kedaluwarsa (2 jam) atau variabel hilang di terminal baru | Ulangi [langkah 1.4](#14-ambil-token-user-dan-admin) |
| Endpoint publik ikut 401 | Mengirim header `Authorization: Bearer ` kosong | Untuk anonim, jangan kirim header sama sekali (`api anon ...`) |
| 400 "Failed to read request" | JSON tidak valid (kutip hilang, koma berlebih, enter di tengah string) | Cek JSON, misalnya dengan `echo "$BBODY" \| jq .` |
| 422 `INVALID_DATE` padahal tanggal benar | `DATE` lebih dari 30 hari atau sudah lewat | Jalankan ulang perintah `DATE=...` di langkah 1.3 |
| `curl: (7) Failed to connect` | Aplikasi belum/tidak berjalan | `./mvnw spring-boot:run` |
| `/swagger-ui.html` 401 | Path ini belum di-`permitAll` | Buka `/swagger-ui/index.html`, atau tambahkan `"/swagger-ui.html"` ke rule permit |

Lihat status + header lengkap dengan `-i` (header response) atau `-v` (request dan response):

```bash
api user -i $BASE/api/me
```

---

## 12. Membersihkan data tes

Data tes dibuat di database lokal. Hapus jika tidak diperlukan:

```bash
PGPASSWORD=booking psql -h localhost -U booking -d booking <<'SQL'
-- booking milik user tes
DELETE FROM bookings WHERE user_id IN (
  SELECT id FROM users WHERE email IN ('budi@mail.com','siti@mail.com')
     OR email LIKE 'tes%@example.com' OR email LIKE 'flow%@example.com' OR email LIKE 'matrix%@example.com'
     OR email LIKE 'doc%@example.com');
-- booking di court tes
DELETE FROM bookings WHERE court_id IN (
  SELECT c.id FROM courts c JOIN venues v ON v.id = c.venue_id
  WHERE v.name LIKE 'Venue Coba%' OR v.name LIKE 'Venue Tes%');
-- court & venue tes
DELETE FROM courts WHERE venue_id IN (
  SELECT id FROM venues WHERE name LIKE 'Venue Coba%' OR name LIKE 'Venue Tes%');
DELETE FROM venues WHERE name LIKE 'Venue Coba%' OR name LIKE 'Venue Tes%';
-- user tes
DELETE FROM users WHERE email IN ('budi@mail.com','siti@mail.com')
   OR email LIKE 'tes%@example.com' OR email LIKE 'flow%@example.com' OR email LIKE 'matrix%@example.com'
   OR email LIKE 'doc%@example.com';
SQL
```

> Periksa dulu apa yang akan terhapus dengan mengganti `DELETE FROM` menjadi `SELECT * FROM` pada tiap baris.
