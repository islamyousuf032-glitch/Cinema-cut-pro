#!/usr/bin/env bash
set -euo pipefail

readonly FFMPEG_REPOSITORY="https://github.com/FFmpeg/FFmpeg.git"
readonly FFMPEG_TAG="n8.1.3"
readonly FFMPEG_COMMIT="1041abdc962f4cc4f394aa8de9dc5236c0c3b9e7"
readonly NDK_VERSION="27.2.12479018"
readonly DEFAULT_API_LEVEL=24

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${1:-${ROOT_DIR}/app/build/ffmpeg-android}"
mkdir -p "${WORK_DIR}"
WORK_DIR="$(cd "${WORK_DIR}" && pwd)"
API_LEVEL="${ANDROID_API_LEVEL:-${DEFAULT_API_LEVEL}}"
SOURCE_DIR="${FFMPEG_SOURCE_DIR:-${WORK_DIR}/source}"
BUILD_DIR="${WORK_DIR}/build"
PREFIX="${WORK_DIR}/prefix"

if [[ "${API_LEVEL}" != "${DEFAULT_API_LEVEL}" ]]; then
  echo "ANDROID_API_LEVEL is pinned to ${DEFAULT_API_LEVEL} to match the app minSdk; got '${API_LEVEL}'." >&2
  exit 2
fi

NDK_ROOT="${FFMPEG_NDK_ROOT:-}"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "${NDK_ROOT}" && -n "${SDK_ROOT}" && -d "${SDK_ROOT}/ndk/${NDK_VERSION}" ]]; then
  # Prefer the exact pinned SDK installation over setup-android's generic NDK aliases,
  # which may point at a different side-by-side version.
  NDK_ROOT="${SDK_ROOT}/ndk/${NDK_VERSION}"
fi
if [[ -z "${NDK_ROOT}" ]]; then
  NDK_ROOT="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-}}"
fi
if [[ -z "${NDK_ROOT}" || ! -d "${NDK_ROOT}/toolchains/llvm/prebuilt" ]]; then
  echo "Set FFMPEG_NDK_ROOT or install NDK ${NDK_VERSION} under ANDROID_SDK_ROOT." >&2
  exit 2
fi
actual_ndk_version="$(sed -n 's/^Pkg.Revision = //p' "${NDK_ROOT}/source.properties" 2>/dev/null | head -n 1)"
if [[ "${actual_ndk_version}" != "${NDK_VERSION}" ]]; then
  echo "Expected Android NDK ${NDK_VERSION}; found '${actual_ndk_version:-unknown}' at ${NDK_ROOT}." >&2
  exit 2
fi

case "$(uname -s)" in
  Linux) NDK_HOST_TAG="linux-x86_64" ;;
  Darwin)
    if [[ "$(uname -m)" == "arm64" && -d "${NDK_ROOT}/toolchains/llvm/prebuilt/darwin-arm64" ]]; then
      NDK_HOST_TAG="darwin-arm64"
    else
      NDK_HOST_TAG="darwin-x86_64"
    fi
    ;;
  *)
    echo "This source-build script currently supports Linux and macOS hosts; got $(uname -s)." >&2
    exit 2
    ;;
esac

TOOLCHAIN="${NDK_ROOT}/toolchains/llvm/prebuilt/${NDK_HOST_TAG}"
if [[ ! -d "${TOOLCHAIN}" ]]; then
  echo "NDK host toolchain not found: ${TOOLCHAIN}" >&2
  exit 2
fi

if [[ ! -d "${SOURCE_DIR}/.git" ]]; then
  mkdir -p "$(dirname "${SOURCE_DIR}")"
  git clone --quiet --depth 1 --branch "${FFMPEG_TAG}" "${FFMPEG_REPOSITORY}" "${SOURCE_DIR}"
fi
remote_url="$(git -C "${SOURCE_DIR}" remote get-url origin 2>/dev/null || true)"
if [[ "${remote_url}" != "${FFMPEG_REPOSITORY}" ]]; then
  echo "Unexpected FFmpeg source remote at ${SOURCE_DIR}: '${remote_url}'." >&2
  exit 2
fi
if [[ -n "$(git -C "${SOURCE_DIR}" status --porcelain)" ]]; then
  echo "FFmpeg source checkout is modified; refusing to overwrite it: ${SOURCE_DIR}." >&2
  exit 2
fi
actual_commit="$(git -C "${SOURCE_DIR}" rev-parse HEAD)"
if [[ "${actual_commit}" != "${FFMPEG_COMMIT}" ]]; then
  git -C "${SOURCE_DIR}" fetch --quiet --depth 1 origin "${FFMPEG_COMMIT}"
  git -C "${SOURCE_DIR}" checkout --quiet --detach "${FFMPEG_COMMIT}"
fi
actual_commit="$(git -C "${SOURCE_DIR}" rev-parse HEAD)"
if [[ "${actual_commit}" != "${FFMPEG_COMMIT}" ]]; then
  echo "FFmpeg source pin mismatch: expected ${FFMPEG_COMMIT}, got ${actual_commit}." >&2
  exit 2
fi

CC="${TOOLCHAIN}/bin/aarch64-linux-android${API_LEVEL}-clang"
CXX="${TOOLCHAIN}/bin/aarch64-linux-android${API_LEVEL}-clang++"
if [[ ! -x "${CC}" || ! -x "${CXX}" ]]; then
  echo "NDK clang target is unavailable for arm64-v8a, API ${API_LEVEL}." >&2
  exit 2
fi

# Only directories carrying our marker are cleaned on rebuild; never remove an unrelated
# directory if the caller supplied an unexpected output path.
readonly GENERATED_MARKER=".cinema-cut-pro-ffmpeg-generated"
for generated_dir in "${BUILD_DIR}" "${PREFIX}"; do
  if [[ -e "${generated_dir}" && ! -f "${generated_dir}/${GENERATED_MARKER}" ]]; then
    echo "Refusing to remove unmarked output directory: ${generated_dir}." >&2
    exit 2
  fi
done
rm -rf "${BUILD_DIR}" "${PREFIX}"
mkdir -p "${BUILD_DIR}" "${PREFIX}"
touch "${BUILD_DIR}/${GENERATED_MARKER}" "${PREFIX}/${GENERATED_MARKER}"
cd "${BUILD_DIR}"

"${SOURCE_DIR}/configure" \
  --prefix="${PREFIX}" \
  --target-os=android \
  --arch=aarch64 \
  --cpu=armv8-a \
  --enable-cross-compile \
  --sysroot="${TOOLCHAIN}/sysroot" \
  --cc="${CC}" \
  --cxx="${CXX}" \
  --ar="${TOOLCHAIN}/bin/llvm-ar" \
  --ranlib="${TOOLCHAIN}/bin/llvm-ranlib" \
  --nm="${TOOLCHAIN}/bin/llvm-nm" \
  --strip="${TOOLCHAIN}/bin/llvm-strip" \
  --enable-pic \
  --enable-shared \
  --disable-static \
  --disable-programs \
  --disable-doc \
  --disable-debug \
  --disable-network \
  --disable-autodetect \
  --enable-jni \
  --enable-mediacodec \
  --enable-decoder=h264_mediacodec \
  --enable-decoder=hevc_mediacodec

for component in JNI MEDIACODEC; do
  if ! grep -Eq "^#define CONFIG_${component} 1$" config.h; then
    echo "Expected FFmpeg Android component was not enabled: ${component}." >&2
    exit 2
  fi
done

for component in H264_MEDIACODEC_DECODER HEVC_MEDIACODEC_DECODER; do
  if ! grep -Eq "^#define CONFIG_${component} 1$" config_components.h; then
    echo "Expected FFmpeg Android decoder was not enabled: ${component}." >&2
    exit 2
  fi
done

if grep -Eq '^CONFIG_(GPL|VERSION3|NONFREE)=yes$' config.mak; then
  echo "Refusing to build: selected FFmpeg configuration enables GPL, version 3, or nonfree code." >&2
  exit 2
fi

JOBS="${FFMPEG_BUILD_JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 2)}"
make -j"${JOBS}"
make install

for library in avutil avcodec avformat swscale swresample; do
  if [[ ! -f "${PREFIX}/lib/lib${library}.so" ]]; then
    echo "Expected FFmpeg shared library was not installed: ${PREFIX}/lib/lib${library}.so" >&2
    exit 1
  fi
done

cat > "${PREFIX}/FFMPEG_BUILD_INFO.txt" <<EOF
FFmpeg tag: ${FFMPEG_TAG}
FFmpeg commit: ${FFMPEG_COMMIT}
Android ABI: arm64-v8a
Android API: ${API_LEVEL}
NDK version expected: ${NDK_VERSION}
License configuration: default LGPL-only (GPL, version 3, and nonfree options not enabled)
MediaCodec: enabled
Libraries: shared, position-independent
EOF

echo "FFmpeg ${FFMPEG_TAG} (${FFMPEG_COMMIT}) built for arm64-v8a/API ${API_LEVEL}."
echo "Shared libraries and headers: ${PREFIX}"
