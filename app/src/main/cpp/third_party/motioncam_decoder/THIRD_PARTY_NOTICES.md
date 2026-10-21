# Third-party notices

The native MotionCam decoder sources are vendored from [`mirsadm/motioncam-decoder`](https://github.com/mirsadm/motioncam-decoder) commit `b931ec0e2466c8217c49f13df925531b78f76e33` and retain their Apache-2.0 headers and `LICENSE`.

The included dependencies are distributed under their own notices:

- **SIMDe 0.8.2** — MIT; copyright notices and SPDX identifiers are present in its headers.
- **nlohmann/json 3.11.3** — MIT; copyright and SPDX identifiers are present in its headers.
- **AudioFile** — MIT; its copyright and license text are present in `thirdparty/audiofile/AudioFile.h`.
- **TinyDNGWriter** — MIT; its copyright and license text are present in `thirdparty/tinydng/tiny_dng_writer.h`.

AudioFile and TinyDNGWriter are retained for the upstream extraction examples; the Android library does not compile them. The application-specific Bayer preview renderer is separate from the upstream decoder and is not a MotionCam or OCTOPUS RAW Player implementation.

This file is a summary only; the license headers and bundled license text remain authoritative.
