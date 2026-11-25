# Git di Proyek Booking API

Dokumen ini menjelaskan:

1. kenapa proyek ini memakai Git,
2. cara kerja Git,
3. alur yang **sudah Anda jalankan** di repo ini (dibaca dari `git log` dan `git reflog`),
4. langkah selanjutnya untuk branch `fix/double-booking`,
5. kaitan Git dengan Flyway, CI/CD, dan deploy.

Remote: `origin` → https://github.com/nafisspinning/booking-api.git

---

## 1. Kenapa Git diperlukan di proyek ini?

| Kebutuhan | Peran Git |
|---|---|
| Mencatat **kenapa** kode berubah | Setiap commit menyimpan pesan, penulis, waktu, dan perubahannya. `git log -p -- path/file` menjelaskan sejarah sebuah file |
| Mencoba fitur tanpa merusak versi stabil | **Branch**: `main` tetap stabil, sedangkan pekerjaan dilakukan di `feat/...` atau `fix/...` |
| Kembali jika ada yang rusak | `git restore`, `git revert`, atau checkout commit lama |
| Backup dan kolaborasi | `git push` ke GitHub |
| Otomasi | GitHub Actions (`.github/workflows/`) berjalan otomatis saat ada push atau pull request |
| Deploy ke server | Server mengambil kode dari GitHub, bukan dari laptop. Inilah alasan kunci SSH (`~/.ssh/booking_deploy`) dibuat |
| Migration database | Urutan file Flyway dijaga bersama riwayat kode. Lihat §6 |

---

## 2. Cara kerja Git

### 2.1 Empat tempat yang perlu dipahami

```
 Working directory        Staging area            Repository lokal           Remote (GitHub)
 (file yang Anda edit)    (yang akan di-commit)   (.git: semua commit)       (origin)
        │                        │                        │                        │
        │──── git add ──────────►│                        │                        │
        │                        │──── git commit ───────►│                        │
        │                        │                        │──── git push ─────────►│
        │◄────────────────────── git switch / restore ───│◄─── git fetch/pull ────│
```

- **Working directory**: folder proyek apa adanya.
- **Staging area (index)**: daftar perubahan yang **dipilih** untuk masuk commit berikutnya. Tempat ini memungkinkan satu file yang diubah untuk dua hal berbeda dipecah menjadi dua commit.
- **Repository lokal**: folder `.git`. Commit di sini **belum** ada di GitHub sampai Anda `push`.
- **Remote**: salinan di GitHub.

### 2.2 Commit dan branch

- **Commit** adalah snapshot seluruh proyek, ditambah pointer ke commit sebelumnya (*parent*). ID-nya berupa hash SHA, misalnya `c0d3d11`.
- **Branch** hanyalah **label yang menunjuk ke satu commit**. Membuat branch tidak menyalin file, sehingga prosesnya instan.
- **HEAD** adalah penanda branch yang sedang aktif.

```
948d381 ◄── 660ad71 ◄── ff98c38 ◄── 4d0894b ◄── 1b8b74d ◄── c0d3d11
                                       ▲            ▲            ▲
                          feat/venue-court    main         fix/double-booking
                                          feat/auth-jwt     ▲
                                          origin/main      HEAD
```

### 2.3 Fast-forward merge

Jika `main` tidak punya commit baru sejak branch fitur dibuat, `git merge` cukup **menggeser label `main` maju** ke commit terakhir branch fitur. Tidak ada merge commit, dan riwayat tetap lurus. Itulah yang terjadi di repo ini (lihat §3.2).

---

## 3. Riwayat repo ini

### 3.1 Daftar commit

| Commit | Tanggal | Pesan | Isi utama |
|---|---|---|---|
| `948d381` | 11 Nov | `chore: intial project setup` | Proyek Spring Boot awal |
| `660ad71` | 11 Nov | `feat: initial database schema with flyway` | `V1__init_schema.sql`, konfigurasi datasource dan Flyway |
| `ff98c38` | 11 Nov | `chore: add global handling and common utilities` | `GlobalExceptionHandler`, exception custom, `PageResponse`, `TimeConfig` |
| `4d0894b` | 12 Nov | `feat: CRUD Venue and Court` | Paket `venue/` (entity, repository, service, controller, DTO) |
| `1b8b74d` | 15 Nov | `feat: auth-jwt` | Paket `auth/`, `SecurityConfig`, seed admin `V1001` |
| `c0d3d11` | 16 Nov | `fix: double-booking(race-condition)` | Paket `booking/`, `V2__prevent_overlapping_bookings.sql`, `docs/`, `.github/workflows/` |

Lihat sendiri:

```bash
git log --oneline --graph --all --decorate
git show --stat 1b8b74d          # file apa saja yang berubah di commit itu
git show 1b8b74d -- src/main/java/com/example/booking/auth/SecurityConfig.java
```

### 3.2 Alur yang terjadi (dari `git reflog`)

```
11 Nov  commit (initial)                      948d381  di branch master
11 Nov  rename master → main
11 Nov  commit 660ad71, ff98c38               langsung di main
11 Nov  git switch -c feat/venue-court        ← mulai bekerja di branch
12 Nov  commit 4d0894b                        di feat/venue-court
14 Nov  git switch -c feat/auth-jwt           ← dibuat DARI feat/venue-court (bukan dari main)
15 Nov  commit 1b8b74d                        di feat/auth-jwt
15 Nov  git switch main
15 Nov  git merge feat/auth-jwt  → Fast-forward   main: ff98c38 → 1b8b74d
16 Nov  git switch -c fix/double-booking      dari main
16 Nov  commit c0d3d11
```

Dua hal penting dari alur ini:

1. **`feat/auth-jwt` dibuat di atas `feat/venue-court`** (*stacked branch*). Karena itu, ketika `feat/auth-jwt` di-merge ke `main`, commit venue-court **ikut masuk**. `feat/venue-court` tidak pernah di-merge sendiri, tetapi isinya sudah ada di `main`. Ini tidak salah, hanya perlu disadari: branch kedua bergantung pada branch pertama.
2. **Merge dilakukan secara lokal** (`git merge` di laptop), lalu `main` di-push. Alternatifnya adalah Pull Request di GitHub (§4.2).

### 3.3 Format pesan commit (Conventional Commits)

Anda sudah memakai pola `tipe: deskripsi`:

| Tipe | Kapan | Contoh di repo |
|---|---|---|
| `feat` | Fitur baru | `feat: auth-jwt` |
| `fix` | Perbaikan bug | `fix: double-booking(race-condition)` |
| `chore` | Setup atau perawatan, tanpa perubahan perilaku | `chore: add global handling...` |
| `docs` | Hanya dokumentasi | (belum ada; seharusnya dipakai untuk `docs/*.md`) |
| `refactor` / `test` / `ci` | Rapikan kode / tes / workflow CI | — |

Tips: tulis deskripsi dalam bentuk perintah dan cukup spesifik, misalnya `fix: return 409 when booking slot overlaps under concurrency`.

---

## 4. Langkah selanjutnya: branch `fix/double-booking`

Status saat dokumen ini ditulis:

```
* fix/double-booking  c0d3d11  ← belum punya upstream (belum di-push)
  main                1b8b74d  [origin/main]
```

### 4.1 Push branch ke GitHub

```bash
git push -u origin fix/double-booking
```

`-u` (*set upstream*) menghubungkan branch lokal dengan `origin/fix/double-booking`. Setelah itu cukup ketik `git push` atau `git pull`.

### 4.2 Gabungkan ke `main`: pilih salah satu

**Cara A: seperti sebelumnya (merge lokal).**

```bash
git switch main
git pull                          # pastikan main lokal = GitHub
git merge fix/double-booking      # akan Fast-forward
git push
```

**Cara B: Pull Request di GitHub (disarankan setelah CI aktif).**

1. Buka repo di GitHub, lalu klik *Compare & pull request* untuk `fix/double-booking`.
2. Tulis deskripsi: apa yang diperbaiki dan bagaimana mengujinya (misalnya uji 10 request paralel → 1× 201, 9× 409).
3. Tunggu workflow CI hijau, lalu klik *Merge*.
4. Di laptop:
   ```bash
   git switch main && git pull
   ```

PR memberi tempat untuk review dan menjalankan CI **sebelum** kode masuk `main`.

### 4.3 Bersihkan branch yang sudah di-merge

```bash
git branch --merged main          # daftar branch yang isinya sudah ada di main
git branch -d feat/venue-court feat/auth-jwt      # -d menolak jika belum di-merge (aman)
git push origin --delete feat/venue-court feat/auth-jwt
```

### 4.4 Memulai pekerjaan berikutnya

Selalu mulai dari `main` yang terbaru:

```bash
git switch main
git pull
git switch -c feat/payment
```

---

## 5. File yang tidak boleh masuk Git

### 5.1 `.gitignore` saat ini

Sudah mengabaikan: `target/` (hasil build Maven), `HELP.md`, file IDE (`.idea`, `.vscode/`, `*.iml`, ...).

Aturan untuk rahasia (sudah ditambahkan):

```gitignore
### Rahasia ###
.env
.env.*
!.env.example
```

- `.env` berisi nilai asli (`MIDTRANS_SERVER_KEY`, `JWT_SECRET`) dan **tidak di-commit**. Spring membacanya lewat
  `spring.config.import: optional:file:.env[.properties]` di `application.yaml`.
- `.env.example` hanya berisi nama variabel tanpa nilai, dan **di-commit** sebagai panduan. Pola `!` membatalkan aturan `.env.*` khusus untuk file ini.

Cara mengecek apakah sebuah file diabaikan:

```bash
git check-ignore -v .env          # menampilkan aturan .gitignore yang cocok
git status --ignored              # menampilkan file yang diabaikan
```

### 5.2 Aturan penting untuk rahasia

- **Kunci SSH** disimpan di `~/.ssh/`, **di luar** repo, sehingga tidak mungkin ter-commit.
- **`JWT_SECRET` di production** diberikan lewat environment variable atau GitHub Secrets, bukan ditulis di `application.yaml`. Nilai default `dev-only-secret-...` di yaml hanya untuk development.
- Akun admin di `V1001__seed.admin.sql` dengan password `password` **hanya untuk development**.
- Jika rahasia **terlanjur di-commit dan di-push**, anggap rahasia itu sudah bocor. **Ganti rahasianya** (rotate). Menghapus file di commit berikutnya tidak cukup, karena isinya tetap ada di riwayat Git.

### 5.3 Folder `.github/modernize/`

Folder ini tidak muncul di `git status` karena memiliki `.gitignore` sendiri di dalamnya. Isinya adalah alat bantu dari ekstensi VS Code (Java upgrade), bukan bagian dari aplikasi.

---

## 6. Git dan Flyway

Flyway membaca file migration **dari kode**, jadi Git ikut menentukan apa yang terjadi pada database.

1. **Migration yang sudah di-commit dan di-push dianggap final.** Mengedit `V1__init_schema.sql` membuat checksum di `flyway_schema_history` tidak cocok, sehingga aplikasi gagal start di semua mesin yang sudah menjalankannya. Untuk perubahan baru, buat file baru.
2. **Bentrok nomor versi antar branch.** Jika `feat/payment` dan `feat/report` sama-sama membuat `V3__...sql`, keduanya lolos di branch masing-masing, tetapi `main` akan memiliki dua V3 dan aplikasi gagal start. Sebelum merge, `git pull` lalu cek `ls src/main/resources/db/migration`, dan ganti nomor jika bentrok **sebelum** di-merge.
3. **Mengganti branch berarti mengganti daftar migration.** Misalnya database lokal sudah menjalankan V2 dari `fix/double-booking`, lalu Anda `git switch` ke branch lama yang tidak punya file V2. Flyway akan mengeluh ada migration di database yang tidak ditemukan di kode. Kembali ke branch yang benar, atau reset database development.

Detail Flyway: [database-migration-flyway.md](database-migration-flyway.md).

---

## 7. Git dan CI/CD (`.github/workflows/`)

GitHub Actions menjalankan file YAML di `.github/workflows/` setiap kali terjadi event yang didefinisikan di dalamnya (misalnya `push` atau `pull_request`).

| File | Biasanya berisi |
|---|---|
| `ci.yml` | Saat push/PR: `./mvnw verify` (compile + test). PR yang gagal CI tidak di-merge |
| `migration.yml` | Validasi atau jalankan Flyway terhadap database |
| `cd.yml` | Saat push ke `main`: build lalu deploy ke server lewat SSH (memakai private key yang disimpan di **GitHub Secrets**, bukan di repo) |

> Ketiga file ini saat ini **masih kosong (0 byte)**. GitHub akan menandai file workflow kosong sebagai tidak valid.
> Isi file-file itu, atau hapus dulu sampai siap dipakai.

---

## 8. Memperbaiki kesalahan

| Situasi | Perintah | Catatan |
|---|---|---|
| Batalkan perubahan file yang **belum** di-`add` | `git restore path/file` | Perubahan **hilang permanen** |
| Keluarkan file dari staging | `git restore --staged path/file` | File tetap berubah di working directory |
| Ubah pesan/isi commit terakhir yang **belum di-push** | `git commit --amend` | Jangan dipakai untuk commit yang sudah di-push |
| Batalkan commit terakhir yang **belum di-push**, perubahan tetap ada | `git reset --soft HEAD~1` | Aman; perubahan kembali ke staging |
| Batalkan commit yang **sudah di-push** | `git revert <hash>` | Membuat commit baru yang membalik perubahan; riwayat tidak ditulis ulang |
| Simpan pekerjaan sementara untuk pindah branch | `git stash` lalu `git stash pop` | |
| "Commit saya hilang" | `git reflog` | Hampir semua posisi HEAD tercatat di sini selama ±90 hari |

Hindari `git reset --hard` dan `git push --force` kecuali Anda benar-benar paham akibatnya. Keduanya bisa menghapus pekerjaan.

---

## 9. Pelajaran dari commit `c0d3d11`

Commit ini berisi 19 file dengan beberapa topik sekaligus:

- fitur booking (seluruh paket `booking/`),
- perbaikan race condition (V2 + `isOverlapViolation`),
- perubahan `SecurityConfig` dan `GlobalExceptionHandler`,
- tiga dokumen di `docs/`,
- tiga file workflow kosong.

Tidak ada yang rusak, tetapi riwayat jadi sulit dibaca. Jika suatu hari perbaikan race condition perlu di-`revert`, dokumen dan fitur booking akan ikut ter-revert.

Lebih baik dipecah menjadi beberapa commit:

```bash
git add src/main/java/com/example/booking/booking/ src/main/java/com/example/booking/auth/SecurityConfig.java
git commit -m "feat: booking endpoints and availability"

git add src/main/resources/db/migration/V2__prevent_overlapping_bookings.sql \
        src/main/resources/application.yaml \
        src/main/java/com/example/booking/common/exception/GlobalExceptionHandler.java
git commit -m "fix: prevent double booking with exclusion constraint"

git add docs/
git commit -m "docs: manual API testing and flyway guides"
```

Untuk satu file yang berisi dua perubahan berbeda, pakai `git add -p path/file`. Git menanyakan setiap potongan perubahan (*hunk*): `y` untuk memasukkan, `n` untuk melewati.

Selalu periksa sebelum commit:

```bash
git status
git diff --staged        # apa yang BENAR-BENAR akan di-commit
```

> Kode debug seperti `Thread.sleep(300)` sebaiknya tidak ikut di-commit, walaupun sudah dijadikan komentar.
> `git diff --staged` adalah tempat yang tepat untuk menangkapnya.

---

## 10. Ringkasan perintah harian

```bash
# mulai pekerjaan
git switch main && git pull
git switch -c feat/nama-fitur

# selama bekerja
git status
git diff
git add -p                       # pilih perubahan
git commit -m "feat: ..."

# kirim ke GitHub
git push -u origin feat/nama-fitur   # pertama kali
git push                             # selanjutnya

# selesai: merge (lokal atau lewat PR), lalu
git switch main && git pull
git branch -d feat/nama-fitur
```
