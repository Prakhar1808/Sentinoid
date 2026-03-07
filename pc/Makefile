CXX = g++
CXXFLAGS = -std=c++20 -Wall -Wextra -O2 -Iinclude
LDFLAGS = -lusb-1.0 -lcurl -ljsoncpp -lpthread

SRC_DIR = src
INCLUDE_DIR = include
BUILD_DIR = build
BIN_DIR = bin

SOURCES = $(wildcard $(SRC_DIR)/*.cpp)
OBJECTS = $(patsubst $(SRC_DIR)/%.cpp,$(BUILD_DIR)/%.o,$(SOURCES))
TARGET = $(BIN_DIR)/sentinoid-pc

.PHONY: all clean run install-deps

all: $(BUILD_DIR) $(TARGET)

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

$(TARGET): $(OBJECTS)
	@mkdir -p $(BIN_DIR)
	$(CXX) $(CXXFLAGS) -o $@ $^ $(LDFLAGS)

$(BUILD_DIR)/%.o: $(SRC_DIR)/%.cpp
	$(CXX) $(CXXFLAGS) -c -o $@ $<

clean:
	rm -rf $(BUILD_DIR) $(BIN_DIR)

install-deps:
	@echo "Installing dependencies..."
	@echo "Arch Linux: sudo pacman -S libusb curl jsoncpp"
	@echo "Debian/Ubuntu: sudo apt install libusb-1.0-dev libcurl4-openssl-dev libjsoncpp-dev"
	@echo "macOS: brew install libusb curl jsoncpp"

run: $(TARGET)
	./$(TARGET)

help:
	@echo "Sentinoid PC Security Engine"
	@echo ""
	@echo "Targets:"
	@echo "  all          - Build the project"
	@echo "  clean        - Remove build artifacts"
	@echo "  install-deps - Show dependency installation commands"
	@echo "  run          - Build and run"
	@echo ""
	@echo "Usage:"
	@echo "  1. Install dependencies: make install-deps"
	@echo "  2. Build: make"
	@echo "  3. Run with Android device connected via USB"
