cmake_minimum_required(VERSION 3.22.1)
project(om15_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_CPP_DIR "C:/Users/FadheelK/llama.cpp")

# Configure llama.cpp build options before add_subdirectory
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)

# CPU-only for Android. The tablet's cores support dotprod + fp16, which ggml only uses when told to.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)

# Use llama.cpp's own build system
add_subdirectory(${LLAMA_CPP_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp)

# Build our JNI shared library
add_library(om15_llama SHARED om15_jni.cpp)

target_include_directories(om15_llama PRIVATE
    "${LLAMA_CPP_DIR}/include"
    "${LLAMA_CPP_DIR}/ggml/include"
    "${LLAMA_CPP_DIR}/common"
    "${LLAMA_CPP_DIR}/tools/mtmd"
)

target_link_libraries(om15_llama
    llama
    mtmd
    llama-common
    android
    log
)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(om15_llama PRIVATE -march=armv8.2-a+dotprod+fp16)
endif()

target_compile_options(om15_llama PRIVATE -O2 -DNDEBUG)
