// (c) Meta Platforms, Inc. and affiliates. Confidential and proprietary.

/*
This header exists so that targets that need to be built against both 1) Android
(where we can and should use the Android NDK jni headers) and 2) the host
platform(generally for local unit tests) can depend on a single target and get
the right jni header for whatever platform they're building against
automatically.
*/
#ifdef __ANDROID__
#include <jni.h>
#else
#include "jni-shims.h"
#endif
