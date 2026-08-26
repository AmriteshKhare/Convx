cmake_minimum_required(VERSION 3.22.1)
project(vivi_analysis LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(vivi_analysis SHARED
    audio_analysis.cpp
    tempo_analysis.cpp
    resampler.cpp
    mel_spectrogram.cpp
    vocal_spectrogram.cpp
    jni/analysis_jni.cpp
    jni/mel_jni.cpp
    jni/vocal_jni.cpp
)

target_include_directories(vivi_analysis PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# -ffast-math is deliberately NOT enabled: the analyzer's log/mel-adjacent
# arithmetic (see audio_analysis.cpp) is tuned against exact IEEE behaviour.
target_compile_options(vivi_analysis PRIVATE -O3 -fvisibility=hidden)

find_library(log-lib log)
target_link_libraries(vivi_analysis ${log-lib})
