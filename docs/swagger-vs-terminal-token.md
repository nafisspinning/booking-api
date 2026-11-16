# Token di Swagger vs Terminal

Pendamping [manual-api-testing.md](manual-api-testing.md).
Pertanyaan yang dijawab di sini:

> Jika saya login sebagai user/admin di terminal, apakah token di Swagger ikut berubah?

**Jawaban singkat: tidak.** Terminal dan Swagger tidak saling berbagi token. Begitu juga sebaliknya.
Setiap request membawa token miliknya sendiri, dan server tidak menyimpan informasi siapa yang sedang login.

---

## 1. Di mana token disimpan?

```
┌──────────────── Terminal (zsh) ────────────────┐      ┌──────────── Browser (Firefox) ────────────┐
│ USER_TOKEN=eyJ...   (variabel shell)            │      │ Swagger UI  →  fetch() ke localhost:8080   │
│ ADMIN_TOKEN=eyJ...  (hilang saat terminal ditutup)│     │ Header Authorization ditambahkan oleh      │
│                                                 │      │ ekstensi ModHeader (nilai yang Anda isi)   │
│ curl -H "Authorization: Bearer $ADMIN_TOKEN"    │      │                                            │
└───────────────────────┬─────────────────────────┘      └─────────────────────┬──────────────────────┘
                        │  request + header sendiri                            │  request + header sendiri
                        ▼                                                      ▼
                ┌──────────────────────────────────────────────────────────────────┐
                │ Spring Boot API (STATELESS)                                       │
                │ - tidak ada session / cookie                                      │
                │ - setiap request: baca header → verifikasi signature JWT → role   │
                │ - tidak tahu & tidak peduli "token terakhir" yang dibuat          │
                └──────────────────────────────────────────────────────────────────┘
```

| Tempat | Penyimpanan token | Kapan hilang |
|---|---|---|
| Terminal | Variabel shell `USER_TOKEN` / `ADMIN_TOKEN` di proses zsh tersebut | Saat tab terminal ditutup, atau di tab terminal lain (variabel tidak dibagikan antar tab) |
| Swagger (dengan ModHeader) | Pengaturan ekstensi ModHeader di browser | Saat Anda mengubah/mematikan profil ModHeader |
| Server | **Tidak disimpan sama sekali** | — |

Konsekuensinya:

- Menjalankan ulang `ADMIN_TOKEN=$(curl ... login ...)` di terminal **hanya mengubah variabel shell**. Browser tidak tahu apa pun tentang hal itu.
- Login lewat Swagger (`POST /api/auth/login` → *Try it out*) memang mengembalikan `accessToken` di response, tetapi Swagger **tidak memakainya secara otomatis**. Token itu hanya teks di layar.

---

## 2. Kenapa server tidak "ingat" siapa yang login?

Konfigurasi di `SecurityConfig`:

```java
.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

`STATELESS` berarti Spring Security **tidak membuat HTTP session**. Login (`AuthService.login`) hanya:

1. mencocokkan email dan password,
2. membuat JWT berisi `sub` (UUID user), `role`, dan `exp` (2 jam), lalu menandatanganinya dengan `app.jwt.secret`,
3. mengembalikan JWT tersebut. **Tidak ada yang disimpan di server atau database.**

Saat request berikutnya datang, server hanya memeriksa:
"apakah signature token ini valid dan belum kedaluwarsa?" Jika ya, identitas dan role diambil **dari isi token itu sendiri**.

Akibatnya:

| Situasi | Hasil |
|---|---|
| Login admin 3 kali | Ada 3 token admin yang **semuanya valid** sampai masing-masing kedaluwarsa |
| Login sebagai user setelah login admin | Token admin yang lama **tetap valid**, tidak "tertimpa" |
| Token disalin dari terminal ke browser | Bekerja normal, karena server tidak peduli token dibuat dari mana |
| Restart aplikasi | Token lama **tetap valid** selama `JWT_SECRET` sama dan belum lewat 2 jam |
| `JWT_SECRET` diganti | Semua token lama langsung ditolak (401) |

> Tidak ada logout di sisi server. Untuk "logout" cukup hapus token di klien (unset variabel / matikan ModHeader).
> Token yang sudah bocor tetap berlaku sampai `exp`. Karena itu TTL dibuat pendek (2 jam).

---

## 3. Alur kerja: memakai token dari terminal di Swagger

Swagger di proyek ini **belum memiliki tombol Authorize**, karena belum ada `SecurityScheme` di konfigurasi OpenAPI.
Karena itu header disisipkan dengan ekstensi **ModHeader** (lihat juga [manual-api-testing.md §10.4](manual-api-testing.md#104-mengganti-token-di-browser-swagger)).

### 3.1 Ambil token di terminal lalu salin ke clipboard

Jalankan langkah 1.4 di dokumen utama. Setelah itu:

```bash
# salin token ADMIN ke clipboard (macOS)
printf '%s' "$ADMIN_TOKEN" | pbcopy

# atau token USER
printf '%s' "$USER_TOKEN" | pbcopy
```

`printf '%s'` dipakai, bukan `echo`, agar tidak ada karakter newline yang ikut tersalin.

### 3.2 Tempel di ModHeader

1. Buka ikon ModHeader di toolbar Firefox.
2. Request header:
   - Name: `Authorization`
   - Value: `Bearer ` + tempel token (perhatikan **spasi** setelah `Bearer`)
3. Filter URL: `localhost:8080` (agar header tidak terkirim ke situs lain).
4. Buka http://localhost:8080/swagger-ui/index.html, lalu jalankan *Try it out*.

### 3.3 Berpindah role

Ada dua cara:

**Cara A: ganti nilai.** Salin token lain dari terminal (`printf '%s' "$USER_TOKEN" | pbcopy`), lalu tempel ulang di ModHeader.

**Cara B (lebih nyaman): dua header, aktifkan salah satu.**
Buat dua baris `Authorization` di ModHeader, satu berisi token USER dan satu berisi token ADMIN.
Centang hanya satu baris. Untuk menguji anonim, matikan keduanya.

```
☑ Authorization   Bearer eyJ...(admin)
☐ Authorization   Bearer eyJ...(user)
```

> Jangan mencentang keduanya sekaligus. Header ganda membuat hasil tidak bisa diprediksi.

### 3.4 Cek token yang sedang aktif di browser

Jalankan `GET /api/me` di Swagger:

- Response menampilkan email dan role **pemilik token yang sedang dikirim**.
- Jika hasilnya 401, berarti tidak ada token, token kedaluwarsa, atau token rusak (misalnya terpotong saat disalin).

Pembanding di terminal:

```bash
api admin $BASE/api/me | jq '{email, role}'
api user  $BASE/api/me | jq '{email, role}'
```

---

## 4. Alur kebalikan: login di Swagger, dipakai di terminal

1. Swagger → `POST /api/auth/login` → *Try it out* → body:
   ```json
   { "email": "admin@booking.local", "password": "password" }
   ```
2. Salin nilai `accessToken` dari response (tanpa tanda kutip).
3. Di terminal:
   ```bash
   ADMIN_TOKEN='eyJ...tempel di sini...'
   api admin $BASE/api/me
   ```

> Endpoint `/api/auth/**` bersifat `permitAll`. Login di Swagger tetap berhasil walaupun ModHeader sedang mengirim token lama,
> **asalkan token lama itu masih valid.** Jika token di ModHeader sudah kedaluwarsa atau rusak, **login pun mendapat 401**.
> Matikan ModHeader dulu, login, lalu tempel token baru. Lihat §5 poin pertama.

---

## 5. Jebakan yang sering terjadi

| Gejala | Penyebab | Solusi |
|---|---|---|
| Di Swagger, **semua** endpoint 401, termasuk `GET /api/venues` yang publik | ModHeader masih mengirim token yang sudah **kedaluwarsa** (lebih dari 2 jam). Token yang dikirim tetap diverifikasi; token tidak valid menghasilkan 401 walaupun endpoint-nya publik | Ambil token baru di terminal lalu tempel ulang, atau matikan ModHeader |
| Login ulang di terminal, tapi Swagger masih bertindak sebagai role lama | Swagger memakai nilai di ModHeader, bukan variabel terminal | Salin token baru ke ModHeader (§3.1–3.2) |
| Terminal tab baru: `api admin ...` → 401 | Variabel `ADMIN_TOKEN` tidak ada di tab baru, sehingga header berisi `Bearer ` kosong | Jalankan ulang langkah 1.4 di tab tersebut |
| Token dari Swagger ditempel di terminal → 401 | Tanda kutip `"` ikut tersalin, atau ada spasi/enter di tengah token | Salin hanya isi di antara kutip. Cek dengan `echo ${#ADMIN_TOKEN}`; panjang token biasanya ratusan karakter tanpa spasi |
| USER mendapat 403 di Swagger padahal di terminal (admin) 200 | Memang berbeda token, sehingga berbeda role | Periksa `GET /api/me` di kedua sisi |
| Data booking di Swagger berbeda dengan di terminal | `GET /api/bookings/me` memfilter berdasarkan `sub` token. Token berbeda berarti user berbeda | Pastikan kedua sisi memakai token dari akun yang sama |

---

## 6. Melihat isi token (tanpa library)

JWT terdiri dari `header.payload.signature`. Bagian payload hanya di-encode **base64url, tidak dienkripsi**:

```bash
# tampilkan payload token admin
printf '%s' "$ADMIN_TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -D 2>/dev/null; echo
```

Contoh hasil:

```json
{"iss":"booking-api","sub":"0193...","email":"admin@booking.local","role":["ADMIN"],"iat":1763...,"exp":1763...}
```

- `role` adalah claim yang dibaca `JwtAuthenticationConverter` dan diubah menjadi `ROLE_ADMIN`.
- `exp` adalah waktu kedaluwarsa dalam detik epoch. Ubah ke tanggal dengan `date -r <exp>` (macOS).

Karena isinya bisa dibaca siapa saja, **jangan menaruh data rahasia di claim JWT**.
Keamanannya ada pada *signature*: isi yang diubah membuat signature tidak cocok, sehingga server menolak dengan 401.

> Kadang `base64 -D` gagal karena panjang payload bukan kelipatan 4 (padding `=` dihapus pada base64url).
> Alternatif yang selalu berhasil: `printf '%s' "$ADMIN_TOKEN" | cut -d. -f2 | jq -R '@base64d | fromjson'`.

---

## 7. Ringkasan

- Token **tidak pernah berpindah otomatis** antara terminal dan Swagger.
- Server **stateless**: yang menentukan identitas dan role adalah token yang dikirim pada **request itu**.
- Login baru **tidak membatalkan** token lama. Semua token valid sampai `exp` (2 jam).
- Untuk memakai token terminal di Swagger: `printf '%s' "$ADMIN_TOKEN" | pbcopy`, lalu tempel di ModHeader sebagai `Bearer <token>`.
- Selalu cek dengan `GET /api/me` untuk mengetahui token siapa yang sedang aktif.
