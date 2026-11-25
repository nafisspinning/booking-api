# Panduan Menguji API Booking (untuk Pemula)

Panduan ini mengajak Anda menguji **seluruh fitur** aplikasi booking lapangan dari terminal, **langkah demi langkah**:
mulai dari membuat akun, memesan lapangan, membayar, sampai menguji kesalahan-kesalahan yang mungkin terjadi.

Anda **tidak perlu** memahami kode Java untuk mengikuti panduan ini. Cukup salin perintah, jalankan, lalu bandingkan hasilnya dengan contoh.

---

## Cara membaca panduan ini

Setiap langkah ditulis dengan pola yang sama:

| Tanda | Artinya |
|---|---|
| 🎯 **Tujuan** | Apa yang ingin dibuktikan |
| ▶️ **Jalankan** | Perintah yang disalin ke terminal |
| ✅ **Hasil yang benar** | Contoh output asli. Nilai seperti `id`, `code`, dan token **pasti berbeda** di komputer Anda; yang penting adalah **bentuk** dan **status HTTP**-nya |
| 💡 **Penjelasan** | Kenapa hasilnya seperti itu |
| ⚠️ **Jika berbeda** | Penyebab umum dan solusinya |

Kerjakan **berurutan dari atas ke bawah** dalam **satu tab terminal yang sama**. Banyak langkah memakai nilai yang disimpan oleh langkah sebelumnya.

---

## Daftar isi

- **Bagian A: Kenalan dulu**
  [A1 Gambaran besar](#a1-gambaran-besar) · [A2 Kamus kecil](#a2-kamus-kecil) · [A3 Arti status HTTP](#a3-arti-status-http)
- **Bagian B: Persiapan (sekali saja)**
  [B1 Cek aplikasi](#b1-cek-aplikasi-dan-database) · [B2 Siapkan terminal](#b2-siapkan-terminal)
- **Bagian C: Alur utama, dari daftar sampai lunas**
  [Langkah 1](#langkah-1--daftar-akun) sampai [Langkah 10](#langkah-10--cek-hasil-akhir)
- **Bagian D: Coba sengaja salah**
  [D1 Akun](#d1-akun-dan-login) · [D2 Booking](#d2-booking) · [D3 Pembatalan](#d3-pembatalan) · [D4 Pembayaran](#d4-pembayaran) · [D5 Notifikasi Midtrans](#d5-notifikasi-midtrans-callback)
- **Bagian E: Fitur admin (venue dan lapangan)**
- **Bagian F: Lanjutan** (booking kedaluwarsa, batas pembatalan 24 jam, pemesanan bersamaan, [pembayaran sungguhan di Midtrans Sandbox](#f4-pembayaran-sungguhan-lewat-midtrans-sandbox))
- **Bagian G: Checklist** · **Bagian H: Bersih-bersih** · **Bagian I: Jika ada masalah** · **Lampiran: bug yang diketahui**

---

# Bagian A: Kenalan dulu

## A1. Gambaran besar

Bayangkan aplikasi ini sebagai **loket pemesanan lapangan**. Perjalanan seorang pelanggan:

```
 1. Daftar akun            POST /api/auth/register
 2. Login → dapat "tiket"  POST /api/auth/login          (tiket = token)
 3. Cari lapangan          GET  /api/courts
 4. Lihat jam kosong       GET  /api/courts/{id}/availability
 5. Pesan lapangan         POST /api/bookings            → status: PENDING_PAYMENT (menunggu bayar, 15 menit)
 6. Minta tagihan          POST /api/bookings/{id}/payment  → aplikasi menghubungi Midtrans
 7. Bayar di Midtrans      (di halaman Midtrans)
 8. Midtrans memberi kabar POST /api/payments/callback   → status: CONFIRMED (lunas)
```

Jika langkah 7 tidak dilakukan dalam 15 menit, booking otomatis menjadi **EXPIRED** dan jamnya bisa dipesan orang lain.

Ada dua jenis pengguna:

| Peran | Bisa apa |
|---|---|
| **USER** (pelanggan) | Daftar, login, melihat lapangan, booking, membayar, membatalkan booking miliknya |
| **ADMIN** (pengelola) | Semua yang bisa dilakukan USER, **ditambah** membuat/mengubah venue dan lapangan. Akun admin sudah disediakan: `admin@booking.local` / `password` |

## A2. Kamus kecil

| Istilah | Penjelasan sederhana |
|---|---|
| **API** | Pintu yang dipakai program lain (aplikasi HP, website, atau terminal Anda) untuk berbicara dengan aplikasi ini |
| **Endpoint** | Satu "alamat" di API, misalnya `POST /api/bookings` untuk membuat booking |
| **GET / POST / PUT / PATCH / DELETE** | Jenis permintaan: GET = melihat, POST = membuat, PUT = mengganti, PATCH = mengubah sebagian, DELETE = menghapus |
| **curl** | Program di terminal untuk mengirim permintaan ke API |
| **JSON** | Format data yang dikirim dan diterima, misalnya `{"email":"andi@test.local"}` |
| **Token** | "Tiket masuk" yang didapat setelah login. Dikirim di setiap permintaan lewat header `Authorization: Bearer <token>`. Berlaku **2 jam** |
| **Status HTTP** | Angka tiga digit yang menyatakan hasil permintaan, misalnya `201` = berhasil dibuat |
| **Midtrans** | Layanan pembayaran. Di sini kita memakai mode **Sandbox** (uji coba, tanpa uang sungguhan) |
| **Callback** | Kabar yang **dikirim Midtrans ke aplikasi kita** setelah pembayaran berhasil atau gagal |

## A3. Arti status HTTP

Cukup ingat kelompoknya:

| Status | Kelompok | Arti dalam aplikasi ini |
|---|---|---|
| `200` | ✅ Berhasil | Permintaan berhasil |
| `201` | ✅ Berhasil | Data baru berhasil **dibuat** (akun, booking, venue) |
| `204` | ✅ Berhasil | Berhasil, tetapi tidak ada isi yang dikembalikan |
| `400` | ❌ Kesalahan permintaan | Format data salah atau ada isian kosong |
| `401` | ❌ Kesalahan permintaan | **Belum login**, token salah, atau token kedaluwarsa |
| `403` | ❌ Kesalahan permintaan | Sudah login, tetapi **tidak punya hak** (misalnya USER mencoba fitur admin) |
| `404` | ❌ Kesalahan permintaan | Data tidak ditemukan, **atau** data itu milik orang lain |
| `409` | ❌ Kesalahan permintaan | Bentrok, misalnya jam sudah dipesan atau email sudah dipakai |
| `422` | ❌ Kesalahan permintaan | Data lengkap, tetapi **melanggar aturan**, misalnya booking 5 jam (maksimal 4) |
| `500` | 🐞 Kesalahan server | Ada bug di aplikasi. Penyebabnya hanya terlihat di **log aplikasi** |

Kesalahan 4xx berarti **permintaannya** yang salah, dan aplikasi menolaknya dengan benar. Di Bagian D kita akan sengaja memicu kesalahan-kesalahan ini.

### Bentuk pesan error

Hampir semua error berbentuk seperti ini:

```json
{
  "status": 409,
  "title": "Conflict",
  "code": "SLOT_TAKEN",
  "detail": "Slot 11:00-13:00 sudah dipesan",
  "instance": "/api/bookings",
  "timestamp": "2026-11-18T02:41:00.123Z"
}
```

Yang paling penting dibaca adalah **`code`** (jenis kesalahan, untuk program) dan **`detail`** (penjelasan untuk manusia).

Pengecualian: error **401 dan 403 dari pemeriksaan login** tidak punya isi sama sekali (kosong). Ini normal.

---

# Bagian B: Persiapan (sekali saja)

## B1. Cek aplikasi dan database

🎯 Memastikan semua yang dibutuhkan sudah siap.

▶️ Jalankan satu per satu:

```bash
cd ~/ProjectsDev/booking                     # masuk ke folder proyek
curl -s localhost:8080/actuator/health; echo  # aplikasi hidup?
grep -c '^MIDTRANS_SERVER_KEY=.\+' .env       # key Midtrans sudah diisi?
jq --version                                  # alat pembaca JSON terpasang?
```

✅ Hasil yang benar:

```
{"groups":["liveness","readiness"],"status":"UP"}
1
jq-1.7.1
```

⚠️ Jika berbeda:

| Gejala | Solusi |
|---|---|
| `curl: (7) Failed to connect` | Aplikasi belum berjalan. Buka tab terminal lain: `./mvnw spring-boot:run`, tunggu sampai muncul `Started BookingApplication` |
| Angka `0` atau `No such file` | Buat `.env`: `cp .env.example .env`, lalu isi `MIDTRANS_SERVER_KEY=` dengan key Sandbox, lalu restart aplikasi |
| `jq: command not found` | `brew install jq` |

## B2. Siapkan terminal

🎯 Membuat beberapa **variabel** (tempat menyimpan nilai) dan **jalan pintas** supaya perintah berikutnya lebih pendek.

▶️ Salin **seluruh** blok ini sekaligus, tempel ke terminal, lalu tekan Enter:

```bash
# ── variabel ─────────────────────────────────────────────
BASE=localhost:8080                       # alamat aplikasi
J='Content-Type: application/json'        # header: "data yang saya kirim berformat JSON"
TS=$(date +%s)                            # angka unik, supaya email uji tidak bentrok
DATE=$(date -v+3d +%F)                    # tanggal booking: 3 hari dari hari ini
KEY=$(grep '^MIDTRANS_SERVER_KEY=' .env | cut -d= -f2-)   # dipakai di Langkah 9
export PGPASSWORD=booking                 # password database untuk perintah psql

# ── jalan pintas ──────────────────────────────────────────

# api <siapa> <sisa perintah curl>
#   siapa = anon (tanpa login) | user | user2 | admin
#   menempelkan token yang sesuai, lalu menampilkan status HTTP di baris terakhir
api() {
  local who=$1; shift
  local auth=()
  case $who in
    user)  auth=(-H "Authorization: Bearer $TOKEN") ;;
    user2) auth=(-H "Authorization: Bearer $TOKEN2") ;;
    admin) auth=(-H "Authorization: Bearer $ADMIN_TOKEN") ;;
  esac
  curl -s -w '%{stderr}\n-> HTTP %{http_code}\n' "${auth[@]}" "$@"
}

# cek <kode booking>  → melihat status booking & pembayaran langsung di database
cek() {
  psql -h localhost -U booking -d booking -c \
    "select b.code, b.status as booking, p.status as pembayaran
     from bookings b left join payments p on p.booking_id = b.id where b.code = '$1'"
}

# kabar_midtrans <kode booking> <status_code> <nominal> <transaction_status> [fraud_status]
#   meniru kabar dari Midtrans, lengkap dengan "segel" (signature) yang benar. Dipakai di Langkah 9.
kabar_midtrans() {
  local sig fraud=""
  sig=$(printf '%s' "$1$2$3$KEY" | shasum -a 512 | cut -d' ' -f1)
  [ -n "$5" ] && fraud=",\"fraud_status\":\"$5\""
  api anon -X POST $BASE/api/payments/callback -H "$J" \
    -d "{\"order_id\":\"$1\",\"status_code\":\"$2\",\"gross_amount\":\"$3\",\"signature_key\":\"$sig\",\"transaction_status\":\"$4\",\"transaction_id\":\"tx-$RANDOM\"$fraud}"
}

echo "Siap. DATE=$DATE, panjang KEY=${#KEY}"
```

✅ Hasil yang benar: `Siap. DATE=2026-11-21, panjang KEY=38` (tanggal mengikuti hari ini, dan panjang KEY harus **lebih dari 0**).

💡 Fungsi `api` sebenarnya hanya **curl biasa** ditambah dua hal: header token dan baris `-> HTTP <status>` di akhir.
Bandingkan dua perintah di bawah. **Cukup dibaca**, tidak perlu dijalankan sekarang; keduanya akan 401 karena Anda belum login.

```text
# versi panjang (curl biasa)
curl -s -w '\n-> HTTP %{http_code}\n' localhost:8080/api/me -H "Authorization: Bearer $TOKEN"
# versi pendek (jalan pintas)
api user $BASE/api/me
```

⚠️ Variabel dan jalan pintas **hilang jika tab terminal ditutup**. Jika itu terjadi, ulangi B2, lalu login lagi (Langkah 2).

---

# Bagian C: Alur utama, dari daftar sampai lunas

Bagian ini mengikuti perjalanan **satu pelanggan** dari awal sampai lunas. Contoh output diambil dari pengujian nyata dengan user **Andi Pemula** (18 Nov 2026).

## Langkah 1. Daftar akun

🎯 Membuat akun pelanggan baru.

▶️

```bash
api anon -X POST $BASE/api/auth/register -H "$J" \
  -d "{\"fullName\":\"Andi Pemula\",\"email\":\"andi$TS@test.local\",\"password\":\"andi12345\"}"
```

✅ Status **201**:

```json
{"accessToken":"eyJhbGciOiJIUzI1NiJ9.eyJpc3Mi...","tokenType":"Bearer","expiresIn":7200}
-> HTTP 201
```

💡
- `$TS` membuat email unik (misalnya `andi1794969660@test.local`), sehingga langkah ini bisa diulang tanpa bentrok.
- Pendaftaran langsung memberikan token. `expiresIn: 7200` artinya token berlaku 7200 detik = 2 jam.
- Akun baru **selalu** berperan USER. Tidak ada cara mendaftar sebagai ADMIN dari API.

## Langkah 2. Login dan simpan token

🎯 Mendapatkan token lalu **menyimpannya** di variabel `TOKEN`, supaya tidak perlu disalin manual.

▶️

```bash
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H "$J" \
  -d "{\"email\":\"andi$TS@test.local\",\"password\":\"andi12345\"}" | jq -r .accessToken)
echo "panjang token: ${#TOKEN}"
```

✅ `panjang token: 255` (sekitar 250–270 karakter).

💡 `| jq -r .accessToken` mengambil **hanya** nilai `accessToken` dari JSON. `TOKEN=$( ... )` menyimpan hasilnya.
Mulai sekarang, `api user ...` otomatis memakai token ini.

⚠️ Jika panjangnya `4`, isi `TOKEN` adalah `null`, artinya login gagal. Jalankan perintah login tanpa `TOKEN=$(` ... `| jq ...)` untuk melihat pesan error-nya.

## Langkah 3. Cek profil sendiri

🎯 Membuktikan token bekerja: aplikasi tahu **siapa** yang sedang meminta.

▶️

```bash
api user $BASE/api/me
```

✅ Status **200**:

```json
{"id":"01a1ec87-6a6a-72c0-aafe-5694b870cce2","fullName":"Andi Pemula","email":"andi1794969660@test.local","role":"USER"}
-> HTTP 200
```

💡 Kita tidak mengirim email atau id sama sekali. Aplikasi membacanya **dari dalam token**.

## Langkah 4. Cari lapangan

🎯 Mendapatkan `id` lapangan yang akan dipesan. Endpoint ini **publik**, bisa diakses tanpa login.

▶️

```bash
api anon "$BASE/api/courts?sportType=FUTSAL&size=1"
COURT=$(curl -s "$BASE/api/courts?sportType=FUTSAL&size=1" | jq -r '.content[0].id')
echo "COURT=$COURT"
```

✅ Status **200**:

```json
{"content":[{"id":"01a1e2e8-a766-79f5-a8b4-2cd7051c0864","venueId":"...","venueName":"Arena Futsal Senayan",
  "name":"Lapangan A","sportType":"FUTSAL","pricePerHour":150000.00,"active":true}],
 "page":0,"size":1,"totalElements":2,"totalPages":2}
-> HTTP 200
COURT=01a1e2e8-a766-79f5-a8b4-2cd7051c0864
```

💡 Hasilnya **berhalaman** (*paging*): `size=1` meminta 1 data per halaman, `totalElements: 2` berarti ada 2 lapangan futsal.
Lapangan A, Rp150.000/jam, buka 08:00–23:00, berasal dari data awal (*seed*).

## Langkah 5. Lihat jam yang masih kosong

🎯 Memeriksa jadwal sebelum memesan.

▶️

```bash
api anon "$BASE/api/courts/$COURT/availability?date=$DATE" | jq -c '.[0:4][]'
```

✅ Status **200**, satu baris per jam:

```
{"start":"08:00:00","end":"09:00:00","available":true}
{"start":"09:00:00","end":"10:00:00","available":true}
{"start":"10:00:00","end":"11:00:00","available":true}
{"start":"11:00:00","end":"12:00:00","available":true}
```

💡 Jam buka venue dipecah menjadi potongan **1 jam**. `available: false` berarti jam itu sudah dipesan orang lain, sudah lewat, atau lapangannya ditutup.
`jq -c '.[0:4][]'` hanya menampilkan 4 baris pertama supaya rapi. Hapus bagian itu untuk melihat semuanya.

## Langkah 6. Pesan lapangan

🎯 Membuat booking jam **10:00–12:00** (2 jam).

▶️

```bash
api user -X POST $BASE/api/bookings -H "$J" \
  -d "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"10:00\",\"endTime\":\"12:00\"}" | tee /tmp/booking.json
BOOKING_ID=$(jq -r .id /tmp/booking.json)
KODE=$(jq -r .code /tmp/booking.json)
NOMINAL=$(printf '%.2f' "$(jq -r .totalPrice /tmp/booking.json)")
echo; echo "BOOKING_ID=$BOOKING_ID KODE=$KODE NOMINAL=$NOMINAL"
```

✅ Status **201**:

```json
{"id":"01a1ec87-6b85-794b-bfff-104fc296fa21","code":"BK-20261121-7DBB57",
 "courtId":"01a1e2e8-a766-79f5-a8b4-2cd7051c0864","courtName":"Lapangan A","venueName":"Arena Futsal Senayan",
 "date":"2026-11-21","startTime":"10:00:00","endTime":"12:00:00",
 "totalPrice":300000.00,"status":"PENDING_PAYMENT","expiresAt":"2026-11-18T02:56:00.291642Z"}
-> HTTP 201
BOOKING_ID=01a1ec87-6b85-... KODE=BK-20261121-7DBB57 NOMINAL=300000.00
```

💡
- `code` adalah **kode booking** yang dilihat pelanggan, dengan format `BK-<tanggal main>-<6 karakter acak>`. Kode ini juga menjadi nomor order di Midtrans.
- `totalPrice` = Rp150.000 × 2 jam = **300000.00**.
- `status: PENDING_PAYMENT` berarti jam sudah **ditahan** untuk Anda, tetapi belum dibayar.
- `expiresAt` adalah batas bayar, **15 menit** dari sekarang. Waktunya dalam UTC (`Z`), jadi tambah 7 jam untuk WIB.
- `tee /tmp/booking.json` menampilkan response **sekaligus** menyimpannya ke file, supaya `id`, `code`, dan `totalPrice` bisa diambil.

⚠️ `409 SLOT_TAKEN` berarti jam 10–12 di tanggal itu sudah dipesan, misalnya dari percobaan sebelumnya. Ganti jamnya (misalnya `13:00`–`15:00`) atau lakukan Bagian H.

> ⏰ **Langkah 8 dan 9 harus selesai dalam 15 menit**. Jika lewat, booking menjadi `EXPIRED`; ulangi dari Langkah 6.

## Langkah 7. Lihat daftar booking saya

▶️

```bash
api user "$BASE/api/bookings/me" | jq -c '.content[] | {code, date, startTime, status, totalPrice}'
```

✅

```
{"code":"BK-20261121-7DBB57","date":"2026-11-21","startTime":"10:00:00","status":"PENDING_PAYMENT","totalPrice":300000.00}
```

💡 Yang muncul hanya booking milik **pemilik token**. Booking pelanggan lain tidak pernah terlihat.

## Langkah 8. Buat tagihan pembayaran

🎯 Meminta aplikasi membuat transaksi di Midtrans.

▶️

```bash
api user -X POST $BASE/api/bookings/$BOOKING_ID/payment | jq
```

✅ Status **200**:

```json
{
  "bookingCode": "BK-20261121-7DBB57",
  "amount": 300000.00,
  "status": "PENDING",
  "snapToken": "bcdfe773-7064-4186-a3cc-9d30718e4bba",
  "redirectUrl": "https://app.sandbox.midtrans.com/snap/v4/redirection/bcdfe773-7064-4186-a3cc-9d30718e4bba"
}
```

💡
- Di balik layar, aplikasi **benar-benar menghubungi Midtrans Sandbox** memakai `MIDTRANS_SERVER_KEY`.
- `redirectUrl` adalah halaman pembayaran. Buka di browser untuk melihatnya. Di aplikasi sungguhan, pelanggan diarahkan ke halaman ini.
- Menjalankan perintah ini dua kali **tidak** membuat dua tagihan. Hasil kedua sama persis dengan yang pertama.

⚠️ Jika hasilnya `500`, kemungkinan besar key Midtrans salah atau kosong. Lihat Bagian I.

## Langkah 9. Simulasi: Midtrans mengabarkan "sudah lunas"

🎯 Meniru kabar yang dikirim Midtrans setelah pelanggan membayar.

💡 **Kenapa disimulasikan?** Midtrans mengirim kabar ke alamat internet aplikasi kita, sedangkan `localhost` hanya ada di laptop Anda dan tidak bisa dijangkau Midtrans. Jadi kita yang mengirim kabarnya, persis seperti format Midtrans.

💡 **Apa itu signature (segel)?** Siapa pun bisa mengirim permintaan ke `/api/payments/callback`, karena alamat ini memang terbuka untuk Midtrans. Supaya tidak sembarang orang bisa berkata "sudah lunas", setiap kabar harus membawa **segel**:

```
signature_key = SHA512( kode booking + status_code + nominal + SERVER_KEY )
```

Hanya Midtrans dan aplikasi kita yang tahu `SERVER_KEY`, jadi hanya mereka yang bisa membuat segel yang benar. Jalan pintas `kabar_midtrans` menghitung segel ini otomatis.

▶️

```bash
kabar_midtrans $KODE 200 $NOMINAL settlement
```

✅ Status **200**, tanpa isi:

```
-> HTTP 200
```

💡 `settlement` adalah istilah Midtrans untuk "uang sudah diterima". `200` di tengah perintah adalah kode status dari Midtrans, yang hanya ikut dihitung dalam segel.

## Langkah 10. Cek hasil akhir

▶️

```bash
cek $KODE
api user "$BASE/api/bookings/me" | jq -c '.content[0] | {code, status}'
```

✅

```
        code        |  booking  | pembayaran
--------------------+-----------+------------
 BK-20261121-7DBB57 | CONFIRMED | PAID
(1 row)

{"code":"BK-20261121-7DBB57","status":"CONFIRMED"}
```

🎉 **Selesai!** Booking sudah **CONFIRMED** (pasti main) dan pembayaran **PAID** (lunas). Anda baru saja menguji alur utama aplikasi dari awal sampai akhir.

---

# Bagian D: Coba sengaja salah

Aplikasi yang baik harus **menolak** permintaan yang salah dengan pesan yang jelas. Di bagian ini kita sengaja membuat kesalahan dan memeriksa penolakannya.

Format setiap tabel: **perintah → status yang benar → `code` / pesan → artinya**.

### Persiapan Bagian D

Butuh **user kedua** (untuk menguji akses ke data orang lain) dan **token admin**:

```bash
api anon -X POST $BASE/api/auth/register -H "$J" \
  -d "{\"fullName\":\"Budi Lain\",\"email\":\"budi$TS@test.local\",\"password\":\"budi12345\"}" > /dev/null
TOKEN2=$(curl -s -X POST $BASE/api/auth/login -H "$J" \
  -d "{\"email\":\"budi$TS@test.local\",\"password\":\"budi12345\"}" | jq -r .accessToken)
ADMIN_TOKEN=$(curl -s -X POST $BASE/api/auth/login -H "$J" \
  -d '{"email":"admin@booking.local","password":"password"}' | jq -r .accessToken)
echo "TOKEN2=${#TOKEN2} ADMIN_TOKEN=${#ADMIN_TOKEN}"     # keduanya harus > 200
```

Mulai sekarang ada tiga "orang": `user` (Andi), `user2` (Budi), dan `admin`.

## D1. Akun dan login

```bash
# a) daftar dengan email yang sudah dipakai
api anon -X POST $BASE/api/auth/register -H "$J" \
  -d "{\"fullName\":\"Andi\",\"email\":\"andi$TS@test.local\",\"password\":\"andi12345\"}"

# b) isian tidak valid
api anon -X POST $BASE/api/auth/register -H "$J" -d '{"fullName":"","email":"bukan-email","password":"123"}'

# c) password salah
api anon -X POST $BASE/api/auth/login -H "$J" -d "{\"email\":\"andi$TS@test.local\",\"password\":\"salah12345\"}"

# d) email tidak terdaftar
api anon -X POST $BASE/api/auth/login -H "$J" -d '{"email":"tidakada@test.local","password":"andi12345"}'

# e) lihat profil tanpa login
api anon $BASE/api/me

# f) token palsu
curl -s -w '\n-> HTTP %{http_code}\n' $BASE/api/me -H "Authorization: Bearer token.palsu.sekali"
```

| | Status | `code` / pesan | Artinya |
|---|---|---|---|
| a | 409 | `EMAIL_TAKEN` "Email already exists" | Satu email hanya untuk satu akun |
| b | 400 | `VALIDATION_ERROR` + daftar `errors` per isian | Nama wajib diisi, email harus valid, password 8–72 karakter |
| c | 401 | `UNAUTHORIZED` "email or password is incorrect" | |
| d | 401 | **sama persis dengan c** | Pesan sengaja disamakan, supaya orang lain tidak bisa menebak email mana yang terdaftar |
| e | 401 | (kosong) | Ditolak sebelum masuk aplikasi karena tidak ada token |
| f | 401 | (kosong) | Token palsu ketahuan karena tanda tangannya tidak cocok |

Contoh output (b):

```json
{"detail":"Input tidak valid","status":400,"title":"Bad Request","code":"VALIDATION_ERROR",
 "errors":{"email":"must be a well-formed email address","fullName":"must not be blank","password":"size must be between 8 and 72"}}
```

## D2. Booking

Semua contoh di bawah memakai lapangan `$COURT` (Lapangan A, buka 08:00–23:00).

```bash
# a) jam yang sudah dipesan (Andi sudah memesan 10–12 di Langkah 6)
api user2 -X POST $BASE/api/bookings -H "$J" -d "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"11:00\",\"endTime\":\"13:00\"}"

# b) tepat bersebelahan: 12:00–13:00 (boleh!)
api user2 -X POST $BASE/api/bookings -H "$J" -d "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"12:00\",\"endTime\":\"13:00\"}"

# c) jam tidak bulat
api user -X POST $BASE/api/bookings -H "$J" -d "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"14:30\",\"endTime\":\"16:00\"}"

# d) terlalu lama (5 jam)
api user -X POST $BASE/api/bookings -H "$J" -d "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"14:00\",\"endTime\":\"19:00\"}"

# e) sebelum lapangan buka
api user -X POST $BASE/api/bookings -H "$J" -d "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"06:00\",\"endTime\":\"08:00\"}"

# f) tanggal yang sudah lewat
api user -X POST $BASE/api/bookings -H "$J" -d "{\"courtId\":\"$COURT\",\"date\":\"2026-01-01\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}"

# g) lebih dari 30 hari ke depan
api user -X POST $BASE/api/bookings -H "$J" -d "{\"courtId\":\"$COURT\",\"date\":\"$(date -v+40d +%F)\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}"

# h) lapangan tidak ada
api user -X POST $BASE/api/bookings -H "$J" -d "{\"courtId\":\"00000000-0000-7000-8000-000000000000\",\"date\":\"$DATE\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}"

# i) isian kurang
api user -X POST $BASE/api/bookings -H "$J" -d "{\"courtId\":\"$COURT\"}"

# j) lupa tanda kutip pada id lapangan (kesalahan yang sangat sering terjadi)
api user -X POST $BASE/api/bookings -H "$J" -d "{\"courtId\":$COURT,\"date\":\"$DATE\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}"

# k) tanpa login
api anon -X POST $BASE/api/bookings -H "$J" -d "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"15:00\",\"endTime\":\"16:00\"}"

# l) admin juga boleh booking
api admin -X POST $BASE/api/bookings -H "$J" -d "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"22:00\",\"endTime\":\"23:00\"}"
```

| | Status | `code` | Pesan / artinya |
|---|---|---|---|
| a | 409 | `SLOT_TAKEN` | "Slot 11:00-13:00 sudah dipesan" (11–12 bertabrakan dengan booking Andi) |
| b | **201** | — | Booking berakhir jam 12:00 dan yang berikutnya **mulai** jam 12:00 tidak dianggap bentrok |
| c | 422 | `INVALID_SLOT` | "Jam harus bulat, contoh 19:00" |
| d | 422 | `INVALID_DURATION` | "Durasi booking 1 sampai 4 jam" |
| e | 422 | `OUTSIDE_OPERATING_HOURS` | "Di luar jam operasional venue" |
| f | 422 | `INVALID_DATE` | "Tanggal harus di masa depan, maksimal 30 hari lagi" |
| g | 422 | `INVALID_DATE` | sama dengan f |
| h | 404 | `NOT_FOUND` | "Lapangan tidak ditemukan" |
| i | 400 | `VALIDATION_ERROR` | `date`, `startTime`, `endTime` "must not be null" |
| j | 400 | — | "Failed to read request": JSON-nya rusak. Id harus ditulis `"courtId":"01a1..."` (pakai kutip) |
| k | 401 | — | (kosong) Booking wajib login |
| l | **201** | — | Admin juga pelanggan. Booking tidak dibatasi peran |

💡 **422 vs 400**: `400` berarti **format**-nya salah (JSON rusak, isian kosong). `422` berarti formatnya benar, tetapi **isinya melanggar aturan** bisnis.

## D3. Pembatalan

Aturan pembatalan:

| Status booking | Bisa dibatalkan? |
|---|---|
| `PENDING_PAYMENT` (belum bayar) | ✅ Ya |
| `CONFIRMED` (sudah bayar), jadwal main **lebih dari 24 jam** lagi | ✅ Ya |
| `CONFIRMED`, jadwal main **24 jam atau kurang** | ❌ `CANCEL_TOO_LATE` (dicoba di Bagian F2) |
| `CANCELLED` / `EXPIRED` | ❌ `INVALID_STATUS` |

▶️ Buat booking baru khusus untuk dibatalkan:

```bash
api user -X POST $BASE/api/bookings -H "$J" \
  -d "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"16:00\",\"endTime\":\"17:00\"}" > /tmp/batal.json
BATAL_ID=$(jq -r .id /tmp/batal.json); echo "BATAL_ID=$BATAL_ID"

api user2 -X PATCH $BASE/api/bookings/$BATAL_ID/cancel                    # a) Budi membatalkan booking Andi
api user  -X PATCH $BASE/api/bookings/$BATAL_ID/cancel | jq -c '{code,status}'   # b) Andi membatalkan
api user  -X PATCH $BASE/api/bookings/$BATAL_ID/cancel                    # c) dibatalkan lagi
api user  -X PATCH $BASE/api/bookings/abc/cancel                          # d) id asal-asalan
api user2 -X POST $BASE/api/bookings -H "$J" \
  -d "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"16:00\",\"endTime\":\"17:00\"}"   # e) jam bekas batal
```

| | Status | Hasil | Artinya |
|---|---|---|---|
| a | 404 | "Booking tidak ditemukan" | Booking orang lain diperlakukan seolah **tidak ada** (bukan 403), supaya keberadaannya tidak bocor |
| b | 200 | `"status":"CANCELLED"` | |
| c | 422 | `INVALID_STATUS` "Booking berstatus CANCELLED tidak bisa dibatalkan" | |
| d | 400 | "Failed to convert 'id' with value: 'abc'" | Id harus berbentuk UUID |
| e | 201 | — | Jam yang dibatalkan **langsung bisa dipesan** orang lain |

## D4. Pembayaran

```bash
api user2 -X POST $BASE/api/bookings/$BOOKING_ID/payment       # a) Budi membayar booking Andi
api anon  -X POST $BASE/api/bookings/$BOOKING_ID/payment       # b) tanpa login
api user  -X POST $BASE/api/bookings/$BATAL_ID/payment         # c) booking yang sudah dibatalkan
api user  -X POST $BASE/api/bookings/$BOOKING_ID/payment       # d) booking yang sudah lunas (Langkah 9)
```

| | Status | `code` | Artinya |
|---|---|---|---|
| a | 404 | `NOT_FOUND` | Hanya pemilik booking yang bisa membayar |
| b | 401 | — | Wajib login |
| c | 422 | `NOT_PAYABLE` "Booking tidak dalam status menunggu pembayaran" | |
| d | 422 | `NOT_PAYABLE` | Booking yang sudah `CONFIRMED` tidak perlu dibayar lagi |

Untuk kasus **batas 15 menit sudah lewat** (`BOOKING_EXPIRED`), lihat Bagian F1.

## D5. Notifikasi Midtrans (callback)

### Bagaimana aplikasi memeriksa kabar dari Midtrans

Setiap kabar diperiksa **berurutan**. Jika satu pemeriksaan gagal, proses berhenti di situ:

```
1. Segelnya asli?                      tidak → 401 "Signature tidak valid"
2. Kode booking punya tagihan?         tidak → 404 "Order tidak dikenal"
3. Nominalnya sama dengan tagihan?     tidak → 422 "Nominal tidak sesuai"
4. Tagihan masih menunggu (PENDING)?   tidak → 200, kabar diabaikan (sudah diproses sebelumnya)
5. Apa isi kabarnya?
     settlement                 → lunas:  pembayaran PAID,   booking CONFIRMED
     capture + accept           → lunas (kartu kredit lolos pemeriksaan)
     capture + challenge        → 200, belum ada perubahan (kartu kredit masih diperiksa)
     pending                    → 200, belum ada perubahan (pelanggan belum transfer)
     expire / cancel / deny     → gagal:  pembayaran FAILED, booking EXPIRED (jam dilepas)
```

💡 Untuk kabar yang sah, aplikasi **selalu menjawab 200**, termasuk kabar yang diabaikan. Jika tidak mendapat 200, Midtrans akan **mengirim ulang** kabarnya berkali-kali.

### D5a. Kabar yang ditolak

```bash
# a) segel palsu
api anon -X POST $BASE/api/payments/callback -H "$J" \
  -d "{\"order_id\":\"$KODE\",\"status_code\":\"200\",\"gross_amount\":\"$NOMINAL\",\"signature_key\":\"palsu\",\"transaction_status\":\"settlement\"}"

# b) kabar kosong
api anon -X POST $BASE/api/payments/callback -H "$J" -d '{}'

# c) kode booking yang tidak ada
kabar_midtrans BK-TIDAK-ADA 200 $NOMINAL settlement

# d) nominal berbeda dari tagihan
kabar_midtrans $KODE 200 1000.00 settlement
```

| | Status | Pesan | Artinya |
|---|---|---|---|
| a | 401 | "Signature tidak valid" | Orang yang tidak tahu SERVER_KEY tidak bisa memalsukan pelunasan |
| b | 401 | "Signature tidak valid" | |
| c | 404 | "Order tidak dikenal" | Hal yang sama terjadi untuk booking yang **ada** tetapi belum pernah dibuatkan tagihan (Langkah 8) |
| d | 422 | `AMOUNT_MISMATCH` "Nominal tidak sesuai" | Segelnya asli, tetapi nominal tidak cocok dengan tagihan |

### D5b. Satu booking dari awal sampai gagal bayar

▶️ Buat booking baru, buat tagihannya, lalu kirim beberapa kabar:

```bash
api user -X POST $BASE/api/bookings -H "$J" \
  -d "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"18:00\",\"endTime\":\"19:00\"}" > /tmp/b2.json
B2_ID=$(jq -r .id /tmp/b2.json); B2_KODE=$(jq -r .code /tmp/b2.json)
B2_NOMINAL=$(printf '%.2f' "$(jq -r .totalPrice /tmp/b2.json)")
api user -X POST $BASE/api/bookings/$B2_ID/payment > /dev/null

kabar_midtrans $B2_KODE 201 $B2_NOMINAL pending;              cek $B2_KODE    # 1) belum transfer
kabar_midtrans $B2_KODE 200 $B2_NOMINAL capture challenge;    cek $B2_KODE    # 2) kartu masih diperiksa
kabar_midtrans $B2_KODE 407 $B2_NOMINAL expire;               cek $B2_KODE    # 3) waktu bayar habis
kabar_midtrans $B2_KODE 200 $B2_NOMINAL settlement;           cek $B2_KODE    # 4) "lunas" datang terlambat
```

✅ Semua mendapat **200**. Statusnya:

| Kabar | booking | pembayaran | Artinya |
|---|---|---|---|
| 1) `pending` | PENDING_PAYMENT | PENDING | Belum ada perubahan, tunggu kabar berikutnya |
| 2) `capture` + `challenge` | PENDING_PAYMENT | PENDING | Belum ada perubahan |
| 3) `expire` | **EXPIRED** | **FAILED** | Gagal bayar, jam 18–19 dilepas |
| 4) `settlement` | EXPIRED | FAILED | **Diabaikan**, karena tagihan sudah selesai diproses |

💡 Kabar `deny` (ditolak bank) dan `cancel` (dibatalkan dari Midtrans) berperilaku **sama** dengan `expire`.
`status_code` yang umum dipakai Midtrans: `200` berhasil, `201` pending, `202` ditolak/dibatalkan, `407` kedaluwarsa.

### D5c. Kabar ganda untuk booking yang sudah lunas

```bash
kabar_midtrans $KODE 200 $NOMINAL settlement;  cek $KODE     # kabar lunas yang sama dikirim lagi
kabar_midtrans $KODE 407 $NOMINAL expire;      cek $KODE     # kabar "kedaluwarsa" datang terlambat
```

✅ Keduanya **200**, dan status **tetap** `CONFIRMED / PAID`. Midtrans bisa mengirim kabar yang sama berulang kali; aplikasi hanya memproses kabar **pertama** yang sah.

### D5d. Lunas, tetapi pelanggan sudah membatalkan

```bash
api user -X POST $BASE/api/bookings -H "$J" \
  -d "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"13:00\",\"endTime\":\"14:00\"}" > /tmp/b3.json
B3_ID=$(jq -r .id /tmp/b3.json); B3_KODE=$(jq -r .code /tmp/b3.json)
B3_NOMINAL=$(printf '%.2f' "$(jq -r .totalPrice /tmp/b3.json)")
api user -X POST $BASE/api/bookings/$B3_ID/payment > /dev/null
api user -X PATCH $BASE/api/bookings/$B3_ID/cancel > /dev/null      # pelanggan membatalkan
kabar_midtrans $B3_KODE 200 $B3_NOMINAL settlement;  cek $B3_KODE   # ... tetapi ternyata tetap membayar
```

✅ **200**, dengan status `CANCELLED / PAID`. Di log aplikasi muncul:

```
ERROR Order BK-... paid but booking is CANCELLED. Manual refund needed
```

💡 Uangnya masuk, tetapi jamnya sudah dilepas (mungkin sudah dipesan orang lain). Aplikasi **tidak** mengembalikan booking secara diam-diam; kasus ini dicatat supaya admin melakukan **refund manual**.

---

# Bagian E: Fitur admin (venue dan lapangan)

Melihat venue dan lapangan bisa dilakukan siapa saja. Membuat, mengubah, atau menonaktifkan hanya boleh dilakukan **ADMIN**.

▶️ Coba buat venue sebagai tiga orang berbeda:

```bash
VENUE_JSON="{\"name\":\"Venue Uji $TS\",\"address\":\"Jl. Uji\",\"openTime\":\"08:00\",\"closeTime\":\"22:00\"}"
api anon  -X POST $BASE/api/venues -H "$J" -d "$VENUE_JSON"        # tanpa login
api user  -X POST $BASE/api/venues -H "$J" -d "$VENUE_JSON"        # pelanggan
api admin -X POST $BASE/api/venues -H "$J" -d "$VENUE_JSON" | tee /tmp/venue.json   # admin
VENUE=$(jq -r .id /tmp/venue.json)
```

✅ Berturut-turut: **401** (belum login), **403** (bukan admin), **201**:

```json
{"id":"01a1e3ac-...","name":"Venue Uji 1794969660","address":"Jl. Uji","openTime":"08:00:00","closeTime":"22:00:00"}
```

▶️ Lanjutkan sebagai admin:

```bash
# a) tambah lapangan
api admin -X POST $BASE/api/venues/$VENUE/courts -H "$J" \
  -d '{"name":"Lapangan Uji","sportType":"BADMINTON","pricePerHour":60000}' | tee /tmp/court.json
COURT_UJI=$(jq -r .id /tmp/court.json)

# b) isian lapangan tidak valid
api admin -X POST $BASE/api/venues/$VENUE/courts -H "$J" -d '{"name":"","sportType":"FUTSAL","pricePerHour":-1}'

# c) jenis olahraga tidak dikenal
api admin -X POST $BASE/api/venues/$VENUE/courts -H "$J" -d '{"name":"X","sportType":"GOLF","pricePerHour":1}'

# d) ubah alamat venue
api admin -X PUT $BASE/api/venues/$VENUE -H "$J" \
  -d "{\"name\":\"Venue Uji $TS\",\"address\":\"Jl. Uji Baru\",\"openTime\":\"08:00\",\"closeTime\":\"22:00\"}"

# e) hapus venue yang masih punya lapangan
api admin -X DELETE $BASE/api/venues/$VENUE

# f) nonaktifkan lapangan: pelanggan, lalu admin
api user  -X DELETE $BASE/api/courts/$COURT_UJI
api admin -X DELETE $BASE/api/courts/$COURT_UJI

# g) booking lapangan yang sudah nonaktif
api user -X POST $BASE/api/bookings -H "$J" \
  -d "{\"courtId\":\"$COURT_UJI\",\"date\":\"$DATE\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"}"

# h) venue yang tidak ada
api anon $BASE/api/venues/00000000-0000-7000-8000-000000000000

# i) nama lapangan kembar di venue yang sama
api admin -X POST $BASE/api/venues/$VENUE/courts -H "$J" \
  -d '{"name":"Lapangan Uji","sportType":"FUTSAL","pricePerHour":50000}'
```

| | Status | `code` / pesan | Artinya |
|---|---|---|---|
| a | 201 | `"active":true` | |
| b | 400 | `VALIDATION_ERROR`: `name` "must not be blank", `pricePerHour` "must be greater than 0" | |
| c | 400 | "Failed to read request" | Hanya `FUTSAL` dan `BADMINTON` yang dikenal. Kesalahan ini terdeteksi saat membaca JSON, jadi tidak ada daftar `errors` |
| d | 200 | alamat berubah | |
| e | 409 | `VENUE_HAS_COURTS` | Venue tidak boleh dihapus selama masih punya lapangan |
| f | 403, lalu **204** | (kosong) | 204 = berhasil tanpa isi. Lapangan **tidak dihapus**, hanya dinonaktifkan (`active=false`) |
| g | 422 | `COURT_INACTIVE` "Lapangan sedang tidak tersedia" | |
| h | 404 | `NOT_FOUND` "Venue ... tidak ditemukan" | |
| i | 409 | `COURT_NAME_TAKEN` "Nama lapangan 'Lapangan Uji' sudah dipakai di venue ini" | Nama lapangan harus unik dalam satu venue (huruf besar/kecil dianggap sama) |

> ⚠️ Satu perilaku yang **salah** (bug, lihat Lampiran): pelanggan **bisa** mengubah lapangan.
>
> ```bash
> api user -X PUT $BASE/api/courts/$COURT_UJI -H "$J" -d '{"name":"Lapangan Uji","sportType":"BADMINTON","pricePerHour":60000}'
> ```
>
> Hasilnya **200**, padahal seharusnya **403**.

---

# Bagian F: Lanjutan

## F1. Booking yang tidak dibayar menjadi EXPIRED

🎯 Membuktikan bahwa jam yang tidak dibayar dalam 15 menit akan dilepas otomatis.

💡 Di dalam aplikasi ada "petugas otomatis" (`BookingExpiryJob`) yang berkeliling **setiap 60 detik**. Ia mengubah booking `PENDING_PAYMENT` yang batas bayarnya sudah lewat menjadi `EXPIRED`.
Supaya tidak perlu menunggu 15 menit, kita **memundurkan** batas bayarnya langsung di database.

▶️

```bash
api user -X POST $BASE/api/bookings -H "$J" \
  -d "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"21:00\",\"endTime\":\"22:00\"}" > /tmp/b4.json
B4_ID=$(jq -r .id /tmp/b4.json); B4_KODE=$(jq -r .code /tmp/b4.json)

# mundurkan batas bayar ke 1 menit yang lalu
psql -h localhost -U booking -d booking -c "update bookings set expires_at = now() - interval '1 minute' where id = '$B4_ID'"

# 1) coba bayar: ditolak
api user -X POST $BASE/api/bookings/$B4_ID/payment

# 2) tunggu petugas otomatis (maksimal ±60 detik)
for i in $(seq 1 35); do
  s=$(psql -h localhost -U booking -d booking -At -c "select status from bookings where id='$B4_ID'")
  [ "$s" = EXPIRED ] && break; printf '.'; perl -e 'select(undef,undef,undef,2)'
done; echo " status: $s"

# 3) jam 21–22 sudah bebas lagi
api user2 -X POST $BASE/api/bookings -H "$J" \
  -d "{\"courtId\":\"$COURT\",\"date\":\"$DATE\",\"startTime\":\"21:00\",\"endTime\":\"22:00\"}" | jq -c '{status}'
```

✅
1. **422** `BOOKING_EXPIRED` "Batas waktu pembayaran sudah lewat"
2. Beberapa titik, lalu ` status: EXPIRED` (pada uji nyata ±50 detik). Log aplikasi: `INFO Expired 1 unpaid bookings`
3. **201** `{"status":"PENDING_PAYMENT"}`. Budi berhasil memesan jam yang tadinya dipegang Andi

⚠️ Jika langkah 1 menghasilkan `NOT_PAYABLE`, petugas otomatis sudah lebih dulu mengubahnya menjadi EXPIRED. Itu juga benar.

## F2. Batas pembatalan 24 jam

🎯 Booking yang **sudah dibayar** tidak boleh dibatalkan jika jadwal mainnya tinggal 24 jam atau kurang.

▶️ Buat booking **besok, pada jam yang sama dengan sekarang** (jadi jadwal mainnya kurang dari 24 jam lagi), bayar, lalu batalkan:

```bash
H=$(date +%H); H2=$(date -v+1H +%H); BESOK=$(date -v+1d +%F)
echo "booking besok $BESOK jam ${H}:00-${H2}:00"
api user -X POST $BASE/api/bookings -H "$J" \
  -d "{\"courtId\":\"$COURT\",\"date\":\"$BESOK\",\"startTime\":\"${H}:00\",\"endTime\":\"${H2}:00\"}" > /tmp/b5.json
B5_ID=$(jq -r .id /tmp/b5.json); B5_KODE=$(jq -r .code /tmp/b5.json)
B5_NOMINAL=$(printf '%.2f' "$(jq -r .totalPrice /tmp/b5.json)")
api user -X POST $BASE/api/bookings/$B5_ID/payment > /dev/null
kabar_midtrans $B5_KODE 200 $B5_NOMINAL settlement > /dev/null
cek $B5_KODE
api user -X PATCH $BASE/api/bookings/$B5_ID/cancel
```

✅ Status `CONFIRMED / PAID`, lalu pembatalan ditolak dengan **422**:

```json
{"code":"CANCEL_TOO_LATE","detail":"Pembatalan maksimal 24 jam sebelum jadwal main", ...}
```

⚠️
- Hanya bisa diuji **pukul 08:00–21:59**, karena Lapangan A buka 08:00–23:00. Di luar jam itu, booking pada langkah pertama ditolak `OUTSIDE_OPERATING_HOURS`.
- Booking yang sudah dibayar dan jadwalnya **masih lebih dari 24 jam** tetap **boleh** dibatalkan (200). Uangnya tidak otomatis dikembalikan; lihat Lampiran.

## F3. Dua orang memesan jam yang sama pada detik yang sama

🎯 Memastikan tidak mungkin ada dua booking di jam yang sama, walaupun permintaannya datang **bersamaan**.

💡 Aplikasi punya dua lapis penjaga:
1. **Pemeriksaan di aplikasi**: "apakah jam ini sudah dipesan?" Masalahnya, dua permintaan yang datang bersamaan bisa sama-sama mendapat jawaban "belum".
2. **Aturan di database**: menolak penyimpanan kedua jika jamnya beririsan. Lapis inilah yang tidak bisa dilewati.

▶️ Kirim 10 permintaan sekaligus untuk jam yang sama (tanda `&` membuat semuanya berjalan bersamaan):

```bash
BODY="{\"courtId\":\"$COURT\",\"date\":\"$(date -v+4d +%F)\",\"startTime\":\"15:00\",\"endTime\":\"16:00\"}"
for i in $(seq 1 10); do
  curl -s -w '  -> %{http_code}\n' -X POST $BASE/api/bookings -H "$J" \
    -H "Authorization: Bearer $TOKEN" -d "$BODY" &
done > /tmp/race.txt; wait
grep -o -- '-> [0-9]*' /tmp/race.txt | sort | uniq -c       # berapa yang berhasil / gagal
grep -o '"detail":"[^"]*"' /tmp/race.txt | sort | uniq -c   # pesan penolakan
```

✅ Tepat **satu** yang berhasil:

```
   1 -> 201
   9 -> 409
   3 "detail":"Slot 15:00-16:00 baru saja dipesan orang lain"
   6 "detail":"Slot 15:00-16:00 sudah dipesan"
```

Pembagian 3 dan 6 di atas bisa berbeda setiap kali dijalankan, bahkan bisa saja kesembilannya *"sudah dipesan"*. Yang penting: **1 × 201**.

💡 *"sudah dipesan"* artinya ditolak oleh lapis 1. *"baru saja dipesan orang lain"* artinya lolos lapis 1 karena datang bersamaan, lalu ditolak lapis 2.
❌ Yang **tidak boleh** terjadi: lebih dari satu `201`, atau ada `500`.

## F4. Pembayaran sungguhan lewat Midtrans Sandbox

🎯 Membayar **benar-benar** di Midtrans Sandbox (bukan simulasi seperti Langkah 9), lalu melihat aplikasi menerima kabar asli dari Midtrans.

### Apa bedanya dengan Langkah 9?

```
Langkah 9 (simulasi):   terminal ──kabar buatan sendiri──► aplikasi
                        Midtrans tidak tahu apa-apa. Di dashboard Midtrans tidak ada transaksi.

F4 (sungguhan):         Anda bayar di halaman Midtrans ──► Midtrans mencatat transaksi
                        Midtrans ──kabar asli + segel asli──► aplikasi
                        Transaksi terlihat di Dashboard Sandbox → Transactions.
```

Ada satu kendala: Midtrans mengirim kabar ke **alamat internet**, sedangkan `localhost` hanya ada di laptop Anda. Panduan ini memakai jalan keluar yang paling mudah: **terminal menanyakan status ke Midtrans, lalu meneruskan jawabannya ke aplikasi.** Jawaban Midtrans sudah berisi segel buatan Midtrans sendiri, jadi isinya persis sama dengan kabar asli. (Cara memakai alamat publik ada di akhir bagian ini.)

### Persiapan: dua jalan pintas baru

Jalankan sekali, di tab terminal yang sama dengan B2:

```bash
# status_midtrans <kode booking>  → menanyakan status transaksi langsung ke Midtrans Sandbox
#   (login memakai server key: -u "KEY:")
status_midtrans() {
  curl -s -u "$KEY:" -H 'Accept: application/json' \
    "https://api.sandbox.midtrans.com/v2/$1/status" > /tmp/midtrans-status.json
  jq -c '{status_code, status_message, transaction_status, payment_type, va_numbers}' /tmp/midtrans-status.json
}

# teruskan_kabar  → mengirim jawaban status_midtrans terakhir ke aplikasi, persis seperti Midtrans mengirimnya
teruskan_kabar() {
  api anon -X POST $BASE/api/payments/callback -H "$J" -d @/tmp/midtrans-status.json
}
```

### Langkah F4-1. Booking dan tagihan baru

```bash
api user -X POST $BASE/api/bookings -H "$J" \
  -d "{\"courtId\":\"$COURT\",\"date\":\"$(date -v+2d +%F)\",\"startTime\":\"19:00\",\"endTime\":\"20:00\"}" > /tmp/bayar.json
BAYAR_ID=$(jq -r .id /tmp/bayar.json); BAYAR_KODE=$(jq -r .code /tmp/bayar.json)
jq -c '{code, totalPrice, status, expiresAt}' /tmp/bayar.json
api user -X POST $BASE/api/bookings/$BAYAR_ID/payment | jq -r .redirectUrl
```

✅ Contoh nyata (24 Nov 2026):

```
{"code":"BK-20261126-E974D7","totalPrice":150000.00,"status":"PENDING_PAYMENT","expiresAt":"2026-11-24T10:56:01Z"}
-> HTTP 200
https://app.sandbox.midtrans.com/snap/v4/redirection/c7bf5211-9001-42e0-a5e6-2d5a91adae87
```

> ⏰ Mulai sekarang Anda punya **15 menit** (`expiresAt` + 7 jam = 17:56 WIB pada contoh ini) untuk menyelesaikan F4-3 dan F4-4.

### Langkah F4-2. Tanya Midtrans: sudah ada transaksinya?

```bash
status_midtrans $BAYAR_KODE
```

✅ Belum:

```
{"status_code":"404","status_message":"Transaction doesn't exist.","transaction_status":null,...}
```

💡 Ini **normal**. Meminta tagihan (Langkah 8) hanya membuka "sesi kasir". Transaksi baru tercatat di Midtrans setelah pelanggan **memilih metode bayar**.

### Langkah F4-3. Pilih metode bayar di browser

1. Buka `redirectUrl` dari F4-1 di browser (Firefox/Chrome).
2. Pilih **BCA Virtual Account**.
3. Halaman menampilkan **nomor Virtual Account**. Biarkan halaman tetap terbuka.

▶️ Tanya Midtrans lagi:

```bash
status_midtrans $BAYAR_KODE
```

✅ Sekarang transaksinya **ada**, dengan status `pending` (menunggu transfer):

```
{"status_code":"201","status_message":"Success, transaction is found","transaction_status":"pending",
 "payment_type":"bank_transfer","va_numbers":[{"bank":"bca","va_number":"77432031150752769234121"}]}
```

▶️ (Opsional) teruskan ke aplikasi untuk melihat bahwa kabar `pending` diterima tetapi tidak mengubah apa pun:

```bash
teruskan_kabar; cek $BAYAR_KODE        # → 200, tetap PENDING_PAYMENT / PENDING
```

### Langkah F4-4. Bayar memakai simulator

Di Sandbox tidak ada bank sungguhan, jadi "transfer" dilakukan lewat simulator Midtrans:

1. Buka https://simulator.sandbox.midtrans.com
2. Pilih **BCA** → **Virtual Account**.
3. Tempel nomor VA dari F4-3, klik **Inquire**. Nama tagihan dan nominal Rp150.000 akan tampil.
4. Klik **Pay**.

Label tombol di simulator bisa sedikit berbeda, tetapi alurnya sama: masukkan nomor VA → periksa → bayar.

▶️ Tanya Midtrans lagi:

```bash
status_midtrans $BAYAR_KODE
```

✅ `settlement`, artinya uang sudah diterima Midtrans:

```
{"status_code":"200","status_message":"Success, transaction is found","transaction_status":"settlement",
 "payment_type":"bank_transfer","va_numbers":[{"bank":"bca","va_number":"77432031150752769234121"}]}
```

Pada titik ini transaksinya juga terlihat di **Dashboard Midtrans (mode Sandbox) → Transactions** dengan status *Settlement*.

### Langkah F4-5. Teruskan kabar ke aplikasi dan cek hasilnya

```bash
teruskan_kabar
cek $BAYAR_KODE
api user "$BASE/api/bookings/me" | jq -c ".content[] | select(.code==\"$BAYAR_KODE\") | {code, status, expiresAt}"
```

✅ Hasil nyata:

```
-> HTTP 200
        code        |  booking  | pembayaran
--------------------+-----------+------------
 BK-20261126-E974D7 | CONFIRMED | PAID

{"code":"BK-20261126-E974D7","status":"CONFIRMED","expiresAt":null}
```

💡
- Isi kabar yang diteruskan **dibuat oleh Midtrans**, termasuk segelnya (`signature_key`). Diterimanya kabar ini (200, bukan 401) membuktikan bahwa cara aplikasi memeriksa segel **cocok dengan Midtrans asli**, bukan hanya dengan helper `kabar_midtrans` buatan kita.
- `expiresAt: null`: setelah lunas, batas bayar tidak berlaku lagi.
- Kolom `provider_ref` di tabel `payments` sekarang berisi `transaction_id` dari Midtrans (contoh: `7bbe9d3d-cd82-...`). Nomor ini dipakai jika perlu menelusuri transaksi di dashboard Midtrans.
- Menjalankan `teruskan_kabar` sekali lagi tetap menghasilkan **200** tanpa perubahan, karena kabar ganda diabaikan (lihat D5c).

### (Opsional) Pantau otomatis tanpa mengetik berulang

Jalankan sebelum F4-3. Perintah ini menanyakan Midtrans setiap 10 detik, mencetak setiap perubahan, dan meneruskan kabarnya ke aplikasi saat pembayaran selesai:

```bash
last=""
for i in $(seq 1 100); do
  st=$(status_midtrans $BAYAR_KODE | jq -r '.transaction_status // "belum ada"')
  [ "$st" != "$last" ] && echo "$(date +%H:%M:%S) Midtrans: $st" && last=$st
  case $st in settlement|capture|expire|cancel|deny) teruskan_kabar; cek $BAYAR_KODE; break ;; esac
  perl -e 'select(undef,undef,undef,10)'     # jeda 10 detik
done
```

✅ Contoh nyata (Anda memilih BCA VA pukul 17:45:15, lalu membayar 11 detik kemudian):

```
17:41:15 Midtrans: belum ada
17:45:15 Midtrans: pending
17:45:26 Midtrans: settlement
-> HTTP 200
 BK-20261126-E974D7 | CONFIRMED | PAID
```

### Jika berbeda

| Gejala | Penyebab | Solusi |
|---|---|---|
| F4-3 masih `404` setelah memilih metode bayar | Halaman Snap belum selesai, atau key di `.env` milik akun/mode lain | Pastikan nomor VA sudah tampil, dan key diambil dari dashboard **Sandbox** (awalan `SB-Mid-server-`) |
| Simulator: VA tidak ditemukan | Bank di simulator berbeda, atau VA sudah kedaluwarsa (15 menit) | Pilih bank yang sama (BCA), atau ulangi dari F4-1 |
| `teruskan_kabar` → `401` | Jawaban Midtrans berasal dari key yang berbeda dengan yang dipakai aplikasi | Restart aplikasi setelah mengubah `.env` |
| `teruskan_kabar` → `404 Order tidak dikenal` | Data payment terhapus (misalnya setelah Bagian H) | Ulangi dari F4-1 |
| Hasil `CANCELLED / PAID` atau `EXPIRED / PAID` | Dibayar setelah booking dibatalkan atau melewati 15 menit | Perilaku yang benar: log aplikasi mencatat "Manual refund needed" (lihat D5d) |
| Tidak terlihat di dashboard | Dashboard dalam mode **Production** | Pindahkan ke **Sandbox** |

### Cara lain: Midtrans mengirim kabar sendiri (alamat publik)

Agar Midtrans **sendiri** yang mengirim kabar ke laptop Anda, tanpa `teruskan_kabar`:

1. Buat terowongan publik ke aplikasi (pilih salah satu, keduanya sudah terpasang di laptop ini):
   ```bash
   cloudflared tunnel --url localhost:8080      # menampilkan alamat https://<acak>.trycloudflare.com
   # atau
   ngrok http 8080
   ```
2. Dashboard Midtrans **Sandbox** → *Settings* → *Payment* → *Notification URL*: isi `https://<alamat-terowongan>/api/payments/callback`.
3. Lakukan F4-1, F4-3, dan F4-4. Setelah membayar, cukup jalankan `cek $BAYAR_KODE`; statusnya sudah berubah sendiri.

> ⚠️ **Keamanan:** terowongan membuat **seluruh API** di laptop Anda bisa diakses siapa pun yang tahu alamatnya, termasuk login admin `admin@booking.local` / `password`.
> Hidupkan terowongan hanya selama pengujian, matikan dengan `Ctrl+C` setelah selesai, dan kosongkan lagi Notification URL di dashboard.

---

# Bagian G: Checklist

Centang setelah hasilnya sesuai:

**Alur utama**
- [ ] B1 aplikasi `UP`, key Midtrans terisi
- [ ] Langkah 1–3: daftar (201), login (token tersimpan), profil (200)
- [ ] Langkah 4–5: cari lapangan, lihat jam kosong
- [ ] Langkah 6–7: booking (201, `PENDING_PAYMENT`), muncul di daftar booking
- [ ] Langkah 8: tagihan dibuat (200, ada `redirectUrl`)
- [ ] Langkah 9–10: kabar lunas (200), booking menjadi `CONFIRMED / PAID`

**Kesalahan**
- [ ] D1 akun: 409, 400, 401 ×4
- [ ] D2 booking: 409, 201 (bersebelahan), 422 ×5, 404, 400 ×2, 401, 201 (admin)
- [ ] D3 pembatalan: 404, 200, 422, 400, 201
- [ ] D4 pembayaran: 404, 401, 422 ×2
- [ ] D5 callback: 401 ×2, 404, 422; alur gagal bayar; kabar ganda diabaikan; lunas setelah batal

**Admin dan lanjutan**
- [ ] E venue/lapangan: 401/403/201, 201, 400 ×2, 200, 409, 403/204, 422, 404, 409
- [ ] F1 booking kedaluwarsa otomatis
- [ ] F2 `CANCEL_TOO_LATE`
- [ ] F3 10 permintaan bersamaan → hanya 1 berhasil
- [ ] F4 bayar sungguhan di Sandbox: 404 → pending → settlement di Midtrans, lalu `CONFIRMED / PAID` di aplikasi

---

# Bagian H: Bersih-bersih

Semua data uji memakai angka `$TS` yang sama. Jalankan **di tab terminal yang sama** dengan pengujian:

```bash
echo "Menghapus data uji dengan penanda $TS"
psql -h localhost -U booking -d booking -v ON_ERROR_STOP=1 <<SQL
BEGIN;
-- 1. pembayaran & booking milik user uji, atau yang ada di venue uji
DELETE FROM payments WHERE booking_id IN (
  SELECT b.id FROM bookings b
  JOIN users u ON u.id = b.user_id
  JOIN courts c ON c.id = b.court_id JOIN venues v ON v.id = c.venue_id
  WHERE u.email LIKE '%$TS@test.local' OR v.name = 'Venue Uji $TS');
DELETE FROM bookings WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%$TS@test.local')
   OR court_id IN (SELECT c.id FROM courts c JOIN venues v ON v.id = c.venue_id WHERE v.name = 'Venue Uji $TS');
-- 2. lapangan & venue uji
DELETE FROM courts WHERE venue_id IN (SELECT id FROM venues WHERE name = 'Venue Uji $TS');
DELETE FROM venues WHERE name = 'Venue Uji $TS';
-- 3. user uji
DELETE FROM users WHERE email LIKE '%$TS@test.local';
COMMIT;
SQL
```

✅ Beberapa baris `DELETE <angka>` lalu `COMMIT`. Urutannya penting: data yang bergantung (pembayaran → booking) dihapus lebih dulu.

⚠️ Booking yang dibuat **admin** di D2 (l) tidak ikut terhapus, karena admin bukan user uji. Hapus khusus booking itu:

```bash
psql -h localhost -U booking -d booking -c "delete from bookings
  where user_id = (select id from users where email = 'admin@booking.local')
    and court_id = '$COURT' and booking_date = '$DATE' and start_time = '22:00'"
```

---

# Bagian I: Jika ada masalah

| Gejala | Kemungkinan penyebab | Solusi |
|---|---|---|
| Semua permintaan mendapat 401 | Token kedaluwarsa (2 jam), atau tab terminal baru | Ulangi B2 dan Langkah 2 (serta persiapan Bagian D) |
| Endpoint publik (lihat lapangan) ikut 401 | Mengirim token kosong/rusak | Pakai `api anon`, yang tidak mengirim token sama sekali |
| `panjang token: 4` | Login gagal (`null`) | Cek email/password, jalankan login tanpa `jq` untuk melihat error |
| Langkah 6: `409 SLOT_TAKEN` | Jam itu terpakai oleh percobaan sebelumnya | Ganti jam, atau lakukan Bagian H |
| Langkah 6: `422 INVALID_DATE` | `DATE` kosong atau berasal dari sesi lama | Jalankan lagi `DATE=$(date -v+3d +%F)` |
| Langkah 8: `500` | Key Midtrans kosong/salah, atau `.env` diubah tanpa restart | Periksa `.env`, restart aplikasi, lihat log aplikasi |
| Langkah 9: `401 Signature tidak valid` | `KEY` di terminal kosong, atau berbeda dengan yang dipakai aplikasi | `echo ${#KEY}` harus > 0. Restart aplikasi setelah mengubah `.env` |
| Langkah 9: `404 Order tidak dikenal` | Langkah 8 belum dijalankan untuk booking ini | Jalankan Langkah 8 |
| Langkah 9: `422 AMOUNT_MISMATCH` | `NOMINAL` tidak sama dengan `totalPrice` | Ulangi bagian akhir Langkah 6 yang mengisi `NOMINAL` |
| Langkah 10 masih `PENDING_PAYMENT / PENDING` | Lebih dari 15 menit berlalu, atau kabar ditolak | Periksa status HTTP di Langkah 9, atau mulai ulang dari Langkah 6 |
| Terminal menampilkan `quote>` | Ada tanda kutip "miring" (`’ ” “`) hasil copy dari Notes/Word | Tekan `Ctrl+C`, ketik ulang dengan kutip lurus `'` dan `"` |
| Hasil `500` | Bug di aplikasi | Lihat log di terminal tempat `./mvnw spring-boot:run` berjalan; cari baris `ERROR` |

Melihat header lengkap, berguna untuk 401/403 yang isinya kosong:

```bash
curl -s -i $BASE/api/me | head -5
```

---

# Lampiran: bug yang diketahui

Ditemukan saat menyusun panduan ini dan **belum diperbaiki**:

| # | Di mana | Yang terjadi | Seharusnya | Perbaikan |
|---|---|---|---|---|
| 1 | Bagian E | Pelanggan bisa mengubah lapangan (`PUT /api/courts/{id}` → 200) | 403 | Tambahkan aturan `PUT /api/courts/**` → `ADMIN` di `SecurityConfig` |
| 2 | Callback | Kabar dengan nominal bukan angka (dan segel valid) → 500 | 400/422 | Tangani `NumberFormatException` di `PaymentService.handleNotification` |
| 3 | D5d, F2 | Booking yang sudah dibayar lalu dibatalkan: uang tidak dikembalikan otomatis | Refund | Fitur refund (Midtrans Refund API) |

Dokumen terkait:
- [manual-api-testing.md](manual-api-testing.md): versi lama tanpa pembayaran
- [swagger-vs-terminal-token.md](swagger-vs-terminal-token.md): memakai token di Swagger
- [database-migration-flyway.md](database-migration-flyway.md): migration database dan aturan anti-bentrok
