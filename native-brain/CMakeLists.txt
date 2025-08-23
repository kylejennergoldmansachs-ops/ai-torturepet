cmake_minimum_required(VERSION 3.10)
project(native_brain)

set(CMAKE_CXX_STANDARD 17)

add_library(reservoir SHARED
    src/reservoir.cpp
    src/native_bridge.cpp
)

target_include_directories(reservoir PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)

if (CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(reservoir PRIVATE -O3 -march=native)
else()
    target_compile_options(reservoir PRIVATE -O2)
endif()
