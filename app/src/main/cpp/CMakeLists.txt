cmake_minimum_required(VERSION 3.22.1)
project("pulsemix_audio")

find_package(oboe REQUIRED CONFIG)

add_library(
    pulsemix_audio
    SHARED
    RealTimeBpmDetector.cpp
    DspEffects.cpp
    TimeStretcher.cpp
    DeckProcessor.cpp
    AudioEngine.cpp
    jni_bridge.cpp
)

target_compile_options(pulsemix_audio PRIVATE -O3 -ffast-math -Wall)

target_link_libraries(
    pulsemix_audio
    oboe::oboe
    log
    android
)
