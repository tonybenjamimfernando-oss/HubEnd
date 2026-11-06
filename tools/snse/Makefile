# Reproducible llama.cpp Android build. Requires Android NDK r28+, CMake 3.22+.
NDK_PATH ?= $(ANDROID_NDK_HOME)
LLAMA_REPO ?= https://github.com/ggerganov/llama.cpp
LLAMA_VERSION ?= b2678
BUILD_ROOT ?= build/snse
JNI_OUT ?= android/hubops/src/main/jniLibs
HOST_BUILD ?= $(BUILD_ROOT)/host
ABIS ?= arm64-v8a
ANDROID_API ?= 26

ifeq ($(strip $(NDK_PATH)),)
$(error Define ANDROID_NDK_HOME or NDK_PATH)
endif

.PHONY: llama-src android-arm64 android-armv7 android-all host-tools quantize sign-models install-to-apk package-models clean
llama-src:
	@mkdir -p $(BUILD_ROOT)
	@if [ ! -d $(BUILD_ROOT)/llama.cpp/.git ]; then \
	  git clone --filter=blob:none --branch $(LLAMA_VERSION) --depth 1 $(LLAMA_REPO) $(BUILD_ROOT)/llama.cpp; \
	fi
	@cd $(BUILD_ROOT)/llama.cpp && git rev-parse HEAD > ../llama-commit.txt

android-arm64: llama-src
	$(MAKE) android-build ABI=arm64-v8a
android-armv7: llama-src
	$(MAKE) android-build ABI=armeabi-v7a ANDROID_API=26
android-all: android-arm64

.PHONY: android-build
android-build:
	@set -eu; ABI='$(ABI)'; OUT='$(BUILD_ROOT)/android-'$$ABI; \
	HOST_TAG=$$(find '$(NDK_PATH)/toolchains/llvm/prebuilt' -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | head -1); \
	test -n "$$HOST_TAG"; \
	cmake -S $(BUILD_ROOT)/llama.cpp -B "$$OUT" \
	  -DCMAKE_TOOLCHAIN_FILE=$(NDK_PATH)/build/cmake/android.toolchain.cmake \
	  -DANDROID_ABI=$$ABI -DANDROID_PLATFORM=android-$(ANDROID_API) \
	  -DANDROID_STL=c++_shared -DBUILD_SHARED_LIBS=ON \
	  -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=ON -DLLAMA_BUILD_SERVER=OFF \
	  -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -DGGML_VULKAN=OFF -DLLAMA_CURL=OFF \
	  -DCMAKE_BUILD_TYPE=Release \
	  -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384" \
	  -DCMAKE_EXE_LINKER_FLAGS="-Wl,-z,max-page-size=16384"; \
	cmake --build "$$OUT" --config Release --parallel --target llama-cli; \
	mkdir -p $(JNI_OUT)/$$ABI android/hubops/src/main/assets/native/$$ABI; \
	TRIPLE=$$(case $$ABI in arm64-v8a) echo aarch64-linux-android;; armeabi-v7a) echo arm-linux-androideabi;; esac); \
	cp "$(NDK_PATH)/toolchains/llvm/prebuilt/$$HOST_TAG/sysroot/usr/lib/$$TRIPLE/libc++_shared.so" $(JNI_OUT)/$$ABI/; \
	test -s "$$OUT/bin/llama-cli"; cp "$$OUT/bin/llama-cli" android/hubops/src/main/assets/native/$$ABI/llama-cli; \
	"$(NDK_PATH)/toolchains/llvm/prebuilt/$$HOST_TAG/bin/llvm-strip" android/hubops/src/main/assets/native/$$ABI/llama-cli; \
	chmod 755 android/hubops/src/main/assets/native/$$ABI/llama-cli; \
	find "$$OUT" -type f \( -name 'libllama.so' -o -name 'libggml*.so' \) -exec cp -f {} $(JNI_OUT)/$$ABI/ \;; \
	STRIP='$(NDK_PATH)/toolchains/llvm/prebuilt/'$$HOST_TAG'/bin/llvm-strip'; \
	for f in $(JNI_OUT)/$$ABI/*.so; do "$$STRIP" "$$f"; done; \
	echo "Android shared libraries staged for $$ABI"; ls -lh $(JNI_OUT)/$$ABI

host-tools: llama-src
	cmake -S $(BUILD_ROOT)/llama.cpp -B $(HOST_BUILD) -DBUILD_SHARED_LIBS=OFF \
	  -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=ON -DLLAMA_BUILD_SERVER=OFF \
	  -DLLAMA_CURL=OFF -DCMAKE_BUILD_TYPE=Release
	cmake --build $(HOST_BUILD) --parallel --target llama-quantize llama-cli

quantize: host-tools
	@test -d models/finetuned || { echo 'models/finetuned missing'; exit 2; }
	@mkdir -p models/shards
	@set -eu; for shard in sqli crypto auth network storage logging deserialization; do \
	  src="models/f16/$$shard-f16.gguf"; \
	  test -s "$$src" || { echo "Missing $$src"; exit 3; }; \
	  $(HOST_BUILD)/bin/llama-quantize "$$src" "models/shards/$$shard.gguf" Q4_K_M; \
	done
	@python3 tools/snse/write_model_manifest.py models/shards

# Model integrity uses public-key signatures at release time. Never embed HMAC secrets in APK.
sign-models:
	@test -n "$${SNSE_SIGNING_KEY:-}" || { echo 'SNSE_SIGNING_KEY (Ed25519 PEM) required'; exit 2; }
	@python3 tools/snse/sign_models.py models/shards "$${SNSE_SIGNING_KEY}"

install-to-apk: android-arm64 sign-models
	@test -f models/shards/manifest.json || { echo 'Generate and verify model manifest first'; exit 2; }
	@mkdir -p android/hubops/src/main/assets/models/shards
	@cp models/shards/manifest.json models/shards/manifest.json.sig android/hubops/src/main/assets/models/shards/

package-models: sign-models
	@mkdir -p dist
	@cd models/shards && zip -q -9 ../../dist/snse-models-arm64.zip *.gguf manifest.json manifest.json.sig

clean:
	rm -rf $(BUILD_ROOT)
