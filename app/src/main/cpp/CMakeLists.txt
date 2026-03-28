cmake_minimum_required(VERSION 3.22.1)
project("hardware_abstraction")

add_library(
    hardware_abstraction
    SHARED
    hardware_abstraction.cpp)

find_library(
    log-lib
    log)

target_link_libraries(
    hardware_abstraction
    ${log-lib})
