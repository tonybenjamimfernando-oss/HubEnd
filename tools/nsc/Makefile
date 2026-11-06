# Build PRoot as an executable (NOT a fake lib*.so); stage for app-private extraction.
NDK_PATH ?= $(ANDROID_NDK_HOME)
API ?= 26
ABI ?= arm64-v8a
TRIPLE_arm64-v8a := aarch64-linux-android
TRIPLE_armeabi-v7a := armv7a-linux-androideabi
TRIPLE := $(TRIPLE_$(ABI))
HOST_TAG ?= linux-x86_64
TOOLCHAIN := $(NDK_PATH)/toolchains/llvm/prebuilt/$(HOST_TAG)
CC := $(TOOLCHAIN)/bin/$(TRIPLE)$(API)-clang
STRIP := $(TOOLCHAIN)/bin/llvm-strip
ROOT := build/nsc
PROOT_TAG ?= v5.4.0
TALLOC_VERSION ?= 2.4.2

.PHONY: sources talloc proot install-proot rootfs-alpine manifest sign-manifest all clean
sources:
	@test -n "$(NDK_PATH)" || { echo 'ANDROID_NDK_HOME required'; exit 2; }
	@mkdir -p $(ROOT)
	@if [ ! -d $(ROOT)/proot/.git ]; then git clone --depth 1 --branch $(PROOT_TAG) https://github.com/proot-me/proot.git $(ROOT)/proot; fi
	@if [ ! -d $(ROOT)/talloc ]; then curl -fL https://www.samba.org/ftp/talloc/talloc-$(TALLOC_VERSION).tar.gz -o $(ROOT)/talloc.tar.gz; tar xzf $(ROOT)/talloc.tar.gz -C $(ROOT); mv $(ROOT)/talloc-$(TALLOC_VERSION) $(ROOT)/talloc; fi

talloc: sources
	cd $(ROOT)/talloc && CC='$(CC)' LDFLAGS='-Wl,-z,max-page-size=16384' ./configure --host=$(TRIPLE) --prefix='$(abspath $(ROOT)/install)' --disable-python
	$(MAKE) -C $(ROOT)/talloc -j$$(nproc)
	$(MAKE) -C $(ROOT)/talloc install

proot: talloc
	@echo 'Building PRoot; upstream build flags can vary by pinned release.'
	$(MAKE) -C $(ROOT)/proot/src clean
	$(MAKE) -C $(ROOT)/proot/src CC='$(CC)' CFLAGS='-O2 -fPIE -I$(abspath $(ROOT)/install/include)' LDFLAGS='-pie -Wl,-z,max-page-size=16384 -L$(abspath $(ROOT)/install/lib)' proot
	@test -x $(ROOT)/proot/src/proot
	@file $(ROOT)/proot/src/proot | grep -q 'ELF'

install-proot: proot
	@mkdir -p android/hubops/src/main/assets/native/$(ABI)
	@cp $(ROOT)/proot/src/proot android/hubops/src/main/assets/native/$(ABI)/proot
	@mkdir -p $(JNI_OUT)/$(ABI)
	@cp $(ROOT)/install/lib/libtalloc.so* $(JNI_OUT)/$(ABI)/
	@$(STRIP) android/hubops/src/main/assets/native/$(ABI)/proot
	@for f in $(JNI_OUT)/$(ABI)/libtalloc.so*; do $(STRIP) --strip-unneeded "$$f" 2>/dev/null || true; done
	@chmod 755 android/hubops/src/main/assets/native/$(ABI)/proot
	@sha256sum android/hubops/src/main/assets/native/$(ABI)/proot > android/hubops/src/main/assets/native/$(ABI)/proot.sha256

rootfs-alpine:
	@mkdir -p build/alpine android/hubops/src/main/assets/oci
	@curl -fL --retry 3 https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/alpine-minirootfs-3.24.4-aarch64.tar.gz -o build/alpine/alpine.tar.gz
	@curl -fL --retry 3 https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/alpine-minirootfs-3.24.4-aarch64.tar.gz.sha256 -o build/alpine/alpine.tar.gz.sha256
	@EXPECTED=$$(awk '{print $$1}' build/alpine/alpine.tar.gz.sha256); ACTUAL=$$(sha256sum build/alpine/alpine.tar.gz | awk '{print $$1}'); test "$$EXPECTED" = "$$ACTUAL" || { echo 'Alpine upstream SHA-256 mismatch'; exit 3; }
	@tar xzf build/alpine/alpine.tar.gz -C build/alpine
	@rm -rf build/alpine/usr/share/man build/alpine/usr/share/doc build/alpine/var/cache/apk/*
	@tar czf android/hubops/src/main/assets/oci/alpine_3.24.4.tar.gz -C build/alpine .
	@sha256sum android/hubops/src/main/assets/oci/alpine_3.24.4.tar.gz > android/hubops/src/main/assets/oci/alpine_3.24.4.tar.gz.sha256
	@stat -c '%s bytes' android/hubops/src/main/assets/oci/alpine_3.24.4.tar.gz

manifest: install-proot rootfs-alpine
	python3 tools/nsc/write_manifest.py
sign-manifest: manifest
	@test -n "$${NSC_SIGNING_KEY:-}" || { echo 'NSC_SIGNING_KEY (Ed25519 PEM) required'; exit 2; }
	openssl pkeyutl -sign -rawin -inkey "$${NSC_SIGNING_KEY}" -in android/hubops/src/main/assets/oci/release-manifest.json -out android/hubops/src/main/assets/oci/release-manifest.json.sig
all: sign-manifest
clean:
	rm -rf $(ROOT) build/alpine
