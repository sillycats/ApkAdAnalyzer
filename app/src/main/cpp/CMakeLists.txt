cmake_minimum_required(VERSION 3.22.1)
project("apkadcrypto")

# C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(
        native_crypto
        SHARED
        native_crypto.cpp
)

# 目标链接标准 C++ 库
target_link_libraries(native_crypto
        android
        log
)