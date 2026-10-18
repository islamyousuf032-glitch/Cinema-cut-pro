# CMakeLists.txt
cmake_minimum_required(VERSION 3.22.1)
project("videoeditor")

add_library(
        native_player
        SHARED
        native_player.cpp)

add_library(
        transform_engine
        SHARED
        native_transform_jni.cpp
        matrix_math.cpp
        transform_engine.cpp
        keyframe_interpolator.cpp
        crop_perspective.cpp
        motion_path.cpp
        blend_mode_processor.cpp
        native_motion_blur.cpp
        native_export_transform_engine.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        native_player
        ${log-lib})

find_library(
        jnigraphics-lib
        jnigraphics)

target_link_libraries(
        transform_engine
        ${log-lib}
        ${jnigraphics-lib})

add_library(
        export_core
        SHARED
        export_core.cpp
        render_frame.cpp
        transform_eval.cpp
        color_eval.cpp
        lut_processor.cpp
        frame_compositor.cpp
        blend_mode_processor.cpp
        audio_mixer.cpp
        native_export_jni.cpp)

target_link_libraries(
        export_core
        ${log-lib})

add_library(
        timeline_engine
        SHARED
        timeline_engine_jni.cpp
        timeline_math.cpp
)

target_link_libraries(
        timeline_engine
        ${log-lib}
)

add_library(
        native_timeline_core
        SHARED
        timeline_core.cpp
        timeline_layout.cpp
        timeline_hit_test.cpp
        timeline_snap.cpp
        timeline_edit.cpp
        native_timeline_jni.cpp
)

target_link_libraries(
        native_timeline_core
        ${log-lib}
)

add_library(
        native_advanced_timeline_core
        SHARED
        advanced_timeline_core.cpp
        timeline_layout_engine.cpp
        timeline_hit_test.cpp
        timeline_snap_engine.cpp
        timeline_edit_math.cpp
        native_advanced_timeline_jni.cpp
)
target_link_libraries(
        native_advanced_timeline_core
        ${log-lib}
)
