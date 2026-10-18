#!/usr/bin/env python3
"""Compile and run the host-testable C++ export kernels without an Android SDK/NDK."""

from __future__ import annotations

import argparse
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CPP = ROOT / "app/src/main/cpp"
TEST_SOURCE = ROOT / "app/src/test/cpp/native_export_engine_test.cpp"
SOURCES = [
    CPP / "frame_compositor.cpp",
    CPP / "blend_mode_processor.cpp",
    CPP / "color_eval.cpp",
    CPP / "lut_processor.cpp",
    CPP / "transform_eval.cpp",
    CPP / "audio_mixer.cpp",
    TEST_SOURCE,
]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compiler", default="g++", help="C++17 compiler executable (default: g++)")
    args = parser.parse_args()

    with tempfile.TemporaryDirectory(prefix="cinema-cut-native-tests-") as temp_dir:
        executable = Path(temp_dir) / "native_export_engine_test"
        command = [
            args.compiler,
            "-std=c++17",
            "-O2",
            "-Wall",
            "-Wextra",
            "-pedantic",
            "-I",
            str(CPP),
            *(str(source) for source in SOURCES),
            "-o",
            str(executable),
        ]
        subprocess.run(command, cwd=ROOT, check=True)
        subprocess.run([str(executable)], cwd=ROOT, check=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
