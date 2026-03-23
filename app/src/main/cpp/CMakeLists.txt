cmake_minimum_required(VERSION 3.22.1)

project("sentinoid")

# Add your C++ files
add_library(sentinoid SHARED
            native-lib.cpp
            SecurityEngine.cpp)

# Link log and android libraries for debugging
find_library(log-lib log)
target_link_libraries(sentinoid ${log-lib})
