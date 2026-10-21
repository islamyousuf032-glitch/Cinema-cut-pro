#!/usr/bin/env python3
"""Compile and run host-testable C++ export and MotionCam RAW tests."""

from __future__ import annotations

import argparse
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CPP = ROOT / "app/src/main/cpp"
TEST_CPP = ROOT / "app/src/test/cpp"
MOTIONCAM = CPP / "third_party/motioncam_decoder"
EXPORT_TEST = TEST_CPP / "native_export_engine_test.cpp"
EXPORT_SOURCES = [
    CPP / "frame_compositor.cpp",
    CPP / "blend_mode_processor.cpp",
    CPP / "color_eval.cpp",
    CPP / "lut_processor.cpp",
    CPP / "transform_eval.cpp",
    CPP / "audio_mixer.cpp",
    EXPORT_TEST,
]
MOTIONCAM_CORE = [
    MOTIONCAM / "lib/Decoder.cpp",
    MOTIONCAM / "lib/RawData.cpp",
    MOTIONCAM / "lib/RawData_Legacy.cpp",
]
MOTIONCAM_TESTS = [
    MOTIONCAM / "tests/DecoderTest.cpp",
    MOTIONCAM / "tests/DecoderGyroTest.cpp",
    MOTIONCAM / "tests/DecoderAccelerometerTest.cpp",
]


def compile_and_run(compiler: str, executable: Path, sources: list[Path], include_dirs: list[Path], *, quiet: bool = False) -> None:
    command = [compiler, "-std=c++17", "-O2"]
    if quiet:
        # The upstream decoder has warnings under GCC's strict warning set; do not
        # promote third-party warnings to failures while keeping the tests executable.
        command.append("-w")
    else:
        command.extend(["-Wall", "-Wextra", "-pedantic"])
    for include_dir in include_dirs:
        command.extend(["-I", str(include_dir)])
    command.extend(str(source) for source in sources)
    command.extend(["-o", str(executable)])
    subprocess.run(command, cwd=ROOT, check=True)
    subprocess.run([str(executable)], cwd=ROOT, check=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compiler", default="g++", help="C++17 compiler executable (default: g++)")
    args = parser.parse_args()

    with tempfile.TemporaryDirectory(prefix="cinema-cut-native-tests-") as temp_dir:
        temp = Path(temp_dir)
        compile_and_run(args.compiler, temp / "native_export_engine_test", EXPORT_SOURCES, [CPP])

        compile_and_run(
            args.compiler,
            temp / "mcraw_preview_renderer_test",
            [CPP / "mcraw_preview_renderer.cpp", TEST_CPP / "mcraw_preview_renderer_test.cpp"],
            [CPP],
        )

        motioncam_includes = [MOTIONCAM / "lib/include", MOTIONCAM / "thirdparty"]
        for index, test_source in enumerate(MOTIONCAM_TESTS):
            compile_and_run(
                args.compiler,
                temp / f"motioncam_decoder_test_{index}",
                [*MOTIONCAM_CORE, test_source],
                motioncam_includes,
                quiet=True,
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
