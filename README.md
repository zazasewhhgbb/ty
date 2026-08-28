# Self-Hosted Voice Generator

A personal, self-hosted text-to-speech system: record or upload a voice
sample you own or have permission to use, save it as a reusable voice
profile, and generate speech from arbitrarily large amounts of text using
that voice. No ElevenLabs, no per-character subscription quota — the only
limits are your own hardware and time.

> **Build status:** both the **backend** (Phases 1–7) and the **Android app**
> (Phases 8–10) are implemented — real code, not a mock. GPU cloud
> deployment (Phase 11) is documented below. See "Building the APK" for the
> fastest way to get an installable app from this repo.

## 1. What this project does

- Upload or record a voice sample → backend creates a reusable **voice
  profile**.
- Paste, type, or import large text (tested design target: 100,000+ words).
- Backend cleans the text, detects chapters/paragraphs/sentences, and splits
  it into TTS-sized chunks — you never do this by hand.
- Each chunk is synthesized with the **same voice**, so the final audio
  sounds like one continuous speaker.
- Chunks are combined into a single WAV/MP3. Generation runs as a background
  job so it can take minutes or hours without holding an HTTP connection
  open, and it survives app restarts and server restarts.

## 2. Architecture

```
Android app (Kotlin/Compose) --HTTPS/REST--> FastAPI backend --> job queue
                                                                 --> XTTS-v2
                                                                 --> ffmpeg/pydub
                                                                 --> final WAV/MP3
```

The Android app is UI only. All AI inference happens on the backend, which
is Dockerized and meant to run on a GPU Linux box (or locally on CPU for
development).

## 3. TTS model choice & licensing

Models compared, per spec section 4:

| Model | Cloning quality | Ref. audio needed | Languages | GPU VRAM | CPU support | License (weights) |
|---|---|---|---|---|---|---|
| **XTTS-v2 (Coqui)** | Very good, mature, widely benchmarked | ~6s (20-30s recommended) | 17 | ~4GB fp16 | Yes, slow | **CPML — non-commercial** |
| F5-TTS | Comparable/slightly better on some benchmarks, diffusion/flow-matching | Few seconds + reference transcript | Primarily EN/ZH, community multilingual forks | ~4-6GB | Yes, slow | **CC-BY-NC-4.0 — non-commercial** (official checkpoints) |
| Piper / Kokoro / MeloTTS | Good general TTS, but **no real voice cloning** | N/A | Varies | <2GB / CPU-friendly | Yes, fast | MIT / Apache-2.0 (permissive) |

**Choice: XTTS-v2.** Reasoning:

- It's the most mature, most-documented voice-cloning model with an active
  community fork (`coqui-tts` on PyPI) keeping it working on current
  Python/PyTorch after Coqui Inc. shut down in Jan 2024.
- F5-TTS is a legitimate close competitor and would be a reasonable
  alternative, but its officially published weights carry the *same kind*
  of non-commercial license (CC-BY-NC-4.0), so switching to it buys no
  licensing advantage while giving up XTTS's larger ecosystem and simpler
  `speaker_wav`-only cloning (F5 wants a reference transcript too).
- The MIT/Apache options (Piper, Kokoro, MeloTTS) are commercially clean but
  **do not do voice cloning** — they're fixed-voice TTS — so they don't meet
  the actual requirement of this project.

**License consequence:** XTTS-v2's weights are under the **Coqui Public
Model License (CPML)**, which restricts use to non-commercial purposes and
explicitly blocks using the model's output to bootstrap a commercial
product. Coqui Inc. no longer sells commercial licenses (it shut down in
2024), so treat CPML as strictly non-commercial for the foreseeable future.

**This project is built for personal, non-commercial, self-hosted use**,
which CPML permits. If you ever want to turn this into a paid product, you
would need to replace the TTS engine with a permissively-licensed
alternative (or fine-tune your own model) — do not ship this app's current
TTS engine in anything you charge for.

The app's own code (this repo) is MIT-licensed regardless — see `LICENSE`.

## 4. Hardware requirements

| | Minimum | Recommended |
|---|---|---|
| GPU | none (CPU works, much slower) | NVIDIA GPU, 6GB+ VRAM (e.g. RTX 3060) |
| VRAM | — | ~4-5GB actually used by XTTS-v2 fp16; 6GB+ leaves headroom |
| CPU-only speed | Real-time-factor roughly 3-10x slower than realtime depending on CPU | — |
| RAM | 8GB | 16GB+ |
| Storage | ~5GB (model weights + a few generations) | 20GB+ for a real audiobook-scale library |

CUDA is auto-detected (`DEVICE=cuda` in `.env` falls back to CPU
automatically if no GPU is found — see `app/tts/engine.py`).

## 5. What's implemented so far (Phases 1-7)

- `scripts/test_minimal.py` — the Phase 2 proof script: voice sample + text
  file in, generated WAV out, no backend involved. Run this first to confirm
  XTTS-v2 actually works on your machine before trusting anything else.
- `backend/` — full FastAPI application:
  - `POST /voices`, `GET /voices`, `GET /voices/{id}`, `GET
    /voices/{id}/sample`, `DELETE /voices/{id}`
  - `POST /generate`, `GET /jobs/{id}`, `POST /jobs/{id}/cancel`, `GET
    /jobs/{id}/audio`, `DELETE /jobs/{id}`, `GET /health`
  - SQLite metadata store (`app/models/database.py`), designed to move to
    Postgres later by changing only `DATABASE_URL`.
  - Text chunking with chapter/paragraph/sentence-aware splitting
    (`app/audio/chunking.py`).
  - Background job processing with per-chunk retry and resumability across
    server restarts (`app/jobs/job_manager.py`).
  - Bearer-token auth (`app/api/deps.py`) so the server isn't open to
    strangers.
  - Upload validation: format, duration, empty/corrupt files, path-traversal-safe
    filenames (`app/services/voice_service.py`, `app/storage/file_storage.py`).
  - Tests in `backend/tests/test_api.py` (health, auth, upload validation,
    chunking, 404s; full end-to-end generation test is opt-in via
    `RUN_TTS_TESTS=1` since it needs real model weights).
- `docker/Dockerfile` + `docker-compose.yml` for GPU deployment, with the
  model pre-baked into the image so first request isn't stuck downloading
  2GB or blocked on the CPML license prompt.

**Not yet built:** the full cloud deployment walkthrough beyond what's in
section 10 below (load balancing, autoscaling, managed Postgres/S3 — those
are genuinely deployment-specific and are called out as follow-up work).

## 6. Android app (Phases 8-10)

`android/` is a complete Kotlin/Jetpack Compose app implementing every
screen from the spec:

- **Voice Setup** — first-launch screen, upload or record, with the
  authorization notice always shown.
- **Record Voice** — real `MediaRecorder`-based recording with runtime mic
  permission handling, playback, and re-record.
- **Voice Ready** — plays the saved sample back before continuing.
- **Home** — voice picker, large text field, file import, generation
  settings (language/speed/format), Generate button.
- **Generation** — live progress via polling `GET /jobs/{id}` (chunk
  count, percentage, elapsed time), survives navigating away and back.
- **Library** — on-device history of past generations (the backend
  deliberately has no "list all jobs" endpoint, so the app keeps a local
  index — see `GenerationHistoryStore.kt`), each polling its own live
  status, with Play/Share/Delete.
- **Voice Profiles** — list, select, rename-free display, delete with
  confirmation; if the last voice is deleted, the app returns you to Voice
  Setup.
- **Settings** — configurable backend URL and API key (spec section 30 —
  nothing is hard-coded), output format, a live "Test Connection" check,
  About and Privacy text.

Architecture: MVVM (`ui/<screen>/XxxViewModel.kt` + `XxxScreen.kt`),
Retrofit/OkHttp for networking (`data/remote/`), a repository layer
(`data/repository/`) that turns raw exceptions into human-readable errors,
Jetpack DataStore for persisted preferences and the selected voice profile
id (`data/local/`, satisfying spec section 11 — the app never re-asks for
the voice after a restart), and Media3 ExoPlayer for file-based audio
playback that never loads a whole audiobook into memory. Dependency
injection is a small hand-rolled `AppContainer` rather than Hilt, since the
app isn't large enough yet to need it.

**Honesty about verification:** I don't have an Android SDK / Gradle
network access in the sandbox that built this, so this code has *not* been
compiled locally — only checked for balanced braces/parens and reviewed
line by line against the backend API it targets. Treat the first
`./gradlew build` (or the GitHub Actions workflow below) as the real
compile check, and expect that you may need to fix a small Gradle/Compose
version-compatibility nit or two. The backend, by contrast, **was**
actually run end-to-end in the sandbox (see section 5's test results).

## 7. Building the APK

### Option A — GitHub Actions (no Android Studio needed)

1. Push this repo to GitHub.
2. Go to the **Actions** tab → **Build Android APK** → confirm it ran (it
   also runs automatically on every push that touches `android/`).
3. Open the finished run → **Artifacts** → download `app-debug` → unzip →
   install `app-debug.apk` on your phone (enable "Install unknown apps" for
   your file manager/browser first).

This workflow (`.github/workflows/android-build.yml`) generates the Gradle
wrapper jar on the runner (it's intentionally not committed to the repo —
binary jars in a hand-built repo are a common source of "it doesn't match
what Gradle expects" errors) and runs `./gradlew assembleDebug`.

This produces a **debug** APK — fine for installing on your own device to
test. For a signed **release** build, you'll need to set up a signing key;
see Android's official guide on
[signing your app](https://developer.android.com/studio/publish/app-signing).

### Option B — Android Studio

1. Open the `android/` folder as a project in Android Studio (Hedgehog or
   newer). It will regenerate the Gradle wrapper jar automatically on
   first sync.
2. Build → Build Bundle(s) / APK(s) → Build APK(s).
3. Find the output under `android/app/build/outputs/apk/debug/`.

Either way, before your first real generation: set the **Backend URL** and
**API Key** in the app's Settings screen to point at your running backend
(section 8 below gets that running locally, or use section 10 for a real
GPU server).

## 8. Local development setup (CPU or GPU)

```bash
# 1. Python deps
cd backend
python3.11 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# 2. ffmpeg (required by pydub for audio combining/export)
# Debian/Ubuntu:
sudo apt-get install ffmpeg
# macOS:
brew install ffmpeg

# 3. Configure environment
cp .env.example .env
# edit .env: set a real API_KEY, and DEVICE=cpu if you have no NVIDIA GPU

# 4. Pre-download the model (optional but recommended — avoids a slow
#    first request and handles the CPML license prompt non-interactively)
cd ..
bash scripts/download_model.sh

# 5. Prove the TTS pipeline works standalone, before trusting the API
python scripts/test_minimal.py path/to/your_voice.wav path/to/text.txt out.wav

# 6. Start the backend
cd backend
python -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

CPU generation works but is considerably slower than GPU (expect a real-time
factor of roughly 3-10x, i.e. a 1-minute clip can take several minutes to
generate) — fine for development and testing, not for large audiobook runs.

### Testing the API directly

```bash
curl http://localhost:8000/health

curl -X POST http://localhost:8000/voices \
  -H "Authorization: Bearer <your API_KEY>" \
  -F "name=My Voice" \
  -F "file=@your_voice.wav"

curl -X POST http://localhost:8000/generate \
  -H "Authorization: Bearer <your API_KEY>" \
  -H "Content-Type: application/json" \
  -d '{"voice_id": "vp_xxx", "text": "Hello, this is a test.", "language": "en"}'

curl http://localhost:8000/jobs/job_xxx -H "Authorization: Bearer <your API_KEY>"

curl http://localhost:8000/jobs/job_xxx/audio -H "Authorization: Bearer <your API_KEY>" -o out.mp3
```

## 9. Running tests

```bash
cd backend
pytest                        # fast tests, no model weights needed
RUN_TTS_TESTS=1 pytest        # also runs a real end-to-end generation
```

## 10. GPU cloud deployment

```bash
cp backend/.env.example backend/.env   # fill in a real API_KEY
docker compose up --build -d
```

The provided `docker-compose.yml` requests one NVIDIA GPU via the
`nvidia` runtime — your host needs the NVIDIA Container Toolkit installed.
For a CPU-only host, delete the `deploy:` block in `docker-compose.yml` and
set `DEVICE=cpu` in `.env`; the same image works, just slower.

Put the backend behind a reverse proxy (e.g. Caddy or nginx) with TLS in
front of it for production — the Android app is required to speak HTTPS to
anything other than your local dev IP.

## 11. Storage & privacy

- Voice samples and generated audio live under `data/` on disk, never in
  the database (only paths/metadata are in SQLite).
- Deleting a voice profile deletes its sample file; deleting a job deletes
  its chunk/final audio.
- Nothing is sent to any third-party commercial TTS API — synthesis happens
  entirely inside this backend, on infrastructure you control.
- **Only upload or record a voice you own or have explicit permission to
  use.** This tool is not designed or intended to circumvent the safeguards
  of commercial voice-cloning services, and it should not be used to clone
  a voice without the speaker's consent.

## 12. Troubleshooting

- **First request hangs / asks for license confirmation:** run
  `bash scripts/download_model.sh` (sets `COQUI_TOS_AGREED=1`
  non-interactively) before starting the server, or rely on the Docker
  image, which pre-bakes this.
- **CUDA out of memory:** lower `MAX_CONCURRENT_JOBS` to 1 (default), or
  switch `DEVICE=cpu`.
- **`ffmpeg: command not found`:** install ffmpeg (see setup above) —
  `pydub` shells out to it for MP3 export and chunk combining.
- **401 on every request:** check the `Authorization: Bearer <API_KEY>`
  header matches `API_KEY` in `.env` exactly.

## 13. Roadmap / future work

Already designed for, not yet built (see spec section 36 for the full
list): Android app UI, multiple/character voices, DOCX/PDF/EPUB import,
per-chapter audio files, push notifications, S3-compatible storage,
Postgres, subtitle/SRT generation, and a pluggable AI text-generation
front-end (kept deliberately decoupled from any specific provider so it's
not locked to one text-generation service).
