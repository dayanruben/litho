JDK_COMPILER_ARGS = [
    "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.source.util=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
]

LITHO_ROOT = "//"

LITHO_VISIBILITY = [
    "PUBLIC",
]

LITHO_TESTING_UTIL_VISIBILITY = [
    "PUBLIC",
]

LITHO_COMPONENTS_TARGET_VISIBILITY = [
    "PUBLIC",
]

LITHO_CONTACT_NAME = ["litho"]

LITHO_IS_OSS_BUILD = True

def make_dep_path(pth):
    return LITHO_ROOT + pth

LITHO_OSS_ROOT_TARGET = make_dep_path(":components")

# Java source
LITHO_JAVA_TARGET = make_dep_path("litho-core/src/main/java/com/facebook/litho:litho")

LITHO_ANNOTATIONS_TARGET = make_dep_path("litho-annotations/src/main/java/com/facebook/litho/annotations:annotations")

LITHO_WIDGET_ACCESSIBILITIES_TARGET = make_dep_path("litho-widget/src/main/java/com/facebook/litho/widget/accessibility:accessibility")

LITHO_WIDGET_TARGET = make_dep_path("litho-widget/src/main/java/com/facebook/litho/widget:widget")

LITHO_WIDGET_MATERIAL_TARGET = make_dep_path("litho-widget-material/src/main/java/com/facebook/litho/widget:widget")

LITHO_WIDGET_RES_TARGET = make_dep_path("litho-widget:res")

LITHO_LITHO_FRESCO_TARGET = make_dep_path("litho-fresco/src/main/java/com/facebook/litho/fresco:fresco")

LITHO_EDITOR_CORE_TARGET = make_dep_path("litho-editor-core/src/main/java/com/facebook/litho/editor:editor")

LITHO_EDITOR_FLIPPER_TARGET = make_dep_path("litho-editor-flipper/src/main/java/com/facebook/litho/editor/flipper:editor")

# Kotlin targets
LITHO_WIDGET_KOTLIN_TARGET = make_dep_path("litho-widget-kotlin/src/main/kotlin/com/facebook/litho/kotlin/widget:widget")

LITHO_SECTIONS_WIDGET_KOTLIN_TARGET = make_dep_path("litho-widget-kotlin/src/main/kotlin/com/facebook/litho/sections/widget:widget")

LITHO_PERF_LOGGER_TARGET = make_dep_path("litho-perf-logger/src/main/kotlin/com/facebook/litho/performance:litho-perf-logger")

LITHO_FRESCO_KOTLIN_TARGET = make_dep_path("litho-fresco-kotlin/src/main/kotlin/com/facebook/litho/fresco:fresco-kotlin")

# Testing targets
LITHO_TESTING_CORE_TARGET = make_dep_path("litho-testing/src/main/java/com/facebook/litho:litho")

LITHO_TESTING_TARGET = make_dep_path("litho-testing/src/main/java/com/facebook/litho/testing:testing")

LITHO_TESTING_WHITEBOX_TARGET = make_dep_path("litho-testing/src/main/java/com/facebook/litho/testing:whitebox")

LITHO_TESTING_ASSERTJ_TARGET = make_dep_path("litho-testing/src/main/java/com/facebook/litho/testing/assertj:assertj")

LITHO_TESTING_HELPER_TARGET = make_dep_path("litho-testing/src/main/java/com/facebook/litho/testing/helper:helper")

LITHO_TESTING_SUBCOMPONENTS_TARGET = make_dep_path("litho-testing/src/main/java/com/facebook/litho/testing/subcomponents:subcomponents")

LITHO_TESTING_WIDGET_TARGET = make_dep_path("litho-testing/src/main/java/com/facebook/litho/widget:widget")

LITHO_TESTING_ESPRESSO_TARGET = make_dep_path("litho-espresso/src/main/java/com/facebook/litho/testing/espresso:espresso")

LITHO_TEST_RES = make_dep_path("litho-it/src/main:res")

LITHO_TEST_MANIFEST = make_dep_path("litho-it/src/main:manifest")

LITHO_SECTIONS_TARGET = make_dep_path("litho-sections-core/src/main/java/com/facebook/litho/sections:sections")

LITHO_SECTIONS_COMMON_TARGET = make_dep_path("litho-sections-core/src/main/java/com/facebook/litho/sections/common:common")

LITHO_SECTIONS_WIDGET_TARGET = make_dep_path("litho-sections-widget/src/main/java/com/facebook/litho/sections/widget:widget")

LITHO_SECTIONS_ANNOTATIONS_TARGET = make_dep_path("litho-sections-annotations/src/main/java/com/facebook/litho/sections/annotations:annotations")

LITHO_SECTIONS_PROCESSOR_TARGET = make_dep_path("litho-sections-processor/src/main/java/com/facebook/litho/sections/specmodels/processor:processor")

LITHO_SECTIONS_CONFIG_TARGET = make_dep_path("litho-sections-core/src/main/java/com/facebook/litho/sections/config:config")

LITHO_SECTIONS_DEBUG_TARGET = make_dep_path("litho-sections-debug/src/main/java/com/facebook/litho/sections/debug:debug")

LITHO_SECTIONS_DEBUG_WIDGET_TARGET = make_dep_path("litho-sections-debug/src/main/java/com/facebook/litho/sections/debug/widget:widget")

# Test source
LITHO_TEST_TARGET = make_dep_path("litho-it/src/test/com/facebook/litho:litho")

LITHO_TEST_WIDGET_TARGET = make_dep_path("litho-it/src/main/java/com/facebook/litho/widget:widget")

# Java source with local upstream
LITHO_PROGUARDANNOTATIONS_TARGET = make_dep_path("litho-annotations/src/main/java/com/facebook/proguard/annotations:annotations")

# Resources
LITHO_RES_TARGET = make_dep_path("litho-core:res")

# Libraries
LITHO_INFERANNOTATIONS_TARGET = make_dep_path("lib/infer-annotations:infer-annotations")

LITHO_JSR_TARGET = make_dep_path("lib/jsr-305:jsr-305")

LITHO_ANDROIDSUPPORT_TARGET = make_dep_path("lib/androidx:androidx")

LITHO_ANDROIDSUPPORT_ANNOTATION_TARGET = make_dep_path("lib/androidx:annotation")

LITHO_ANDROIDSUPPORT_RECYCLERVIEW_TARGET = make_dep_path("lib/androidx:androidx-recyclerview")

LITHO_ANDROIDSUPPORT_APPCOMPAT_TARGET = make_dep_path("lib/androidx:androidx-appcompat")

LITHO_ANDROIDSUPPORT_MATERIAL_TARGET = make_dep_path("lib/androidx:androidx-material")

LITHO_ANDROIDSUPPORT_SWIPEREFRESHLAYOUT_TARGET = make_dep_path("lib/androidx:androidx-swiperefreshlayout")

LITHO_ANDROIDSUPPORT_TRANSITION_TARGET = make_dep_path("lib/androidx:androidx-transition")

LITHO_ANDROIDSUPPORT_TESTING_TARGET = make_dep_path("lib/androidx:androidx-testing")

LITHO_ANDROIDSUPPORT_TESTING_CORE_TARGET = make_dep_path("lib/androidx:androidx-testing-core")

LITHO_BUILD_CONFIG_TARGET = make_dep_path(":build_config")

LITHO_YOGA_TARGET = make_dep_path("lib/yoga:yoga")

LITHO_YOGAJNI_TARGET = make_dep_path("lib/yogajni:jni")

LITHO_PROGUARD_ANNOTATIONS_TARGET = make_dep_path("lib/yoga:proguard-annotations")

LITHO_COMMONS_CLI_TARGET = make_dep_path("lib/commons-cli:commons-cli")

LITHO_TEXTLAYOUTBUILDER_TARGET = make_dep_path("lib/textlayoutbuilder:textlayoutbuilder")

LITHO_JAVAPOET_TARGET = make_dep_path("lib/javapoet:javapoet")

LITHO_FBCORE_TARGET = make_dep_path("lib/fbcore:fbcore")

LITHO_SOLOADER_TARGET = make_dep_path("lib/soloader:soloader")

LITHO_KOTLIN_STDLIB_TARGET = make_dep_path("lib/kotlin:kotlin-stdlib")

LITHO_KOTLIN_REFLECT_TARGET = make_dep_path("lib/kotlin:kotlin-reflect")

LITHO_ASSERTJ_TARGET = make_dep_path("lib/assertj:assertj")

LITHO_COMPILE_TESTING_TARGET = make_dep_path("lib/compile-testing:compile-testing")

LITHO_TRUTH_TARGET = make_dep_path("lib/truth:truth")

LITHO_MOCKITO_V2_TARGET = make_dep_path("lib/mockito2:mockito2")

LITHO_JNI_TARGET = make_dep_path("lib/jni-hack:jni-hack")

LITHO_GUAVA_TARGET = make_dep_path("lib/guava:guava")

ANDROID_STUDIO_PLUGIN_SDK = make_dep_path("lib/intellij-plugin-sdk:intellij-plugin-sdk")

ANDROID_STUDIO_PLUGIN_SDK_FOR_TESTS = make_dep_path("lib/intellij-plugin-sdk:intellij-plugin-sdk")

LITHO_DIFFUTILS_TARGET = make_dep_path("lib/diff-utils:diff-utils")

LITHO_ESPRESSO_TARGET = make_dep_path("lib/espresso:espresso")

LITHO_SCREENSHOT_TARGET = make_dep_path("lib/screenshot:screenshot")

LITHO_RENDERCORE_TARGET = make_dep_path("litho-rendercore:rendercore-stub")

LITHO_RENDERCORE_TESTING_TARGET = make_dep_path("litho-rendercore-testing:litho-rendercore-testing")

LITHO_RENDERCORE_TEXT_TARGET = make_dep_path("litho-rendercore-text:rendercore-text")

LITHO_RENDERCORE_VISIBILITY_TARGET = make_dep_path("litho-rendercore-visibility:rendercore-visibility-stub")

LITHO_RENDERCORE_TRANSITIONS_TARGET = make_dep_path("litho-rendercore-transitions:rendercore-transitions-stub")

LITHO_RENDERCORE_PRIMITIVE_COMPONENTS_CANVAS_TARGET = make_dep_path("litho-rendercore-primitive-components/canvas:canvas")

LITHO_RENDERCORE_PRIMITIVE_COMPONENTS_UTILS_TARGET = make_dep_path("litho-rendercore-primitive-components/utils:utils")

LITHO_RENDERCORE_PRIMITIVES_TARGET = make_dep_path("litho-rendercore-primitives:rendercore-primitives")

# Previews dummy
CARIBOU_PREVIEW = make_dep_path("sample:activity")

DEBUG_KEYSTORE = make_dep_path("sample:debug_keystore")

# Fresco
LITHO_FRESCO_TARGET = make_dep_path("lib/fresco:fresco")

LITHO_ROBOLECTRIC_V4_TARGET = make_dep_path("lib/robolectric4:robolectric4")

LITHO_JUNIT_TARGET = make_dep_path("lib/junit:junit")

LITHO_HAMCREST_TARGET = make_dep_path("lib/hamcrest:hamcrest")

# Annotation processors
LITHO_ANNOTATIONPROCESSORS_COMMON_TARGET = make_dep_path("litho-processor/src/main/java/com/facebook/annotationprocessors/common:common")

LITHO_PROCESSOR_TARGET = make_dep_path("litho-processor/src/main/java/com/facebook/litho/specmodels/processor:processor")

LITHO_PROCESSOR_LIB_TARGET = make_dep_path("litho-processor/src/main/java/com/facebook/litho/specmodels/processor:processor-lib")

LITHO_SECTIONS_PROCESSOR_LIB_TARGET = make_dep_path("litho-sections-processor/src/main/java/com/facebook/litho/sections/specmodels/processor:processor-lib")

# Sample app
LITHO_SAMPLE = make_dep_path("sample/src/main/java/com/facebook/samples/litho:litho")

LITHO_SAMPLE_BAREBONES_JAVA = make_dep_path("sample-barebones/src/main/java/com/facebook/samples/lithobarebones:lithobarebones")

LITHO_SAMPLE_BAREBONES_RES = make_dep_path("sample-barebones:res")

LITHO_SAMPLE_CODELAB_JAVA = make_dep_path("sample-codelab/src/main/java/com/facebook/samples/lithocodelab:lithocodelab")

LITHO_SAMPLE_CODELAB_RES = make_dep_path("sample-codelab:res")

LITHO_SAMPLE_RES = make_dep_path("sample:res")

# Other targets
LITHO_OSS_TARGET = make_dep_path(":components")

# Targets that sometimes exist and sometimes don't
LITHO_TEXTLAYOUTBUILDER_UTILS_TARGET = []

LITHO_FRESCO_TARGETS = [
    make_dep_path("lib/fbcore:fbcore"),
    make_dep_path("lib/fresco:fresco-drawee"),
    make_dep_path("lib/fresco:fresco"),
]

LITHO_FRESCO_EXPORTED_TARGETS = []

LITHO_FLIPPER_TARGETS = [
    make_dep_path("lib/flipper:flipper"),
]

LITHO_FRESCO_PIPELINE_TARGET = [make_dep_path("lib/fresco:imagepipeline")]

LITHO_FRESCO_VITO_INIT = [make_dep_path("lib/fresco:vito")]

def litho_robolectric4_test(
        name,
        language = "KOTLIN",
        pure_kotlin = False,
        *args,
        **kwargs):
    """Tests that can successfully run from the library root folder."""
    extra_vm_args = [
        "-Drobolectric.dependency.dir=lib/fb/android-all",
        "-Dcom.facebook.litho.is_oss=true",
        "-Dlitho.animation.disabled=true",
    ]
    kwargs["vm_args"] = extra_vm_args
    kwargs["use_cxx_libraries"] = True
    kwargs["cxx_library_allowlist"] = [
        "//lib/yogajni:jni",
    ]
    kwargs["manifest"] = kwargs.pop("manifest", LITHO_TEST_MANIFEST)

    kwargs.pop("autoglob", False)

    annotation_processor_params = kwargs.pop("annotation_processor_params", [])
    found = False
    for param in annotation_processor_params:
        if (param.find("com.facebook.litho.testing=") == 0):
            found = True

    if (not found):
        annotation_processor_params.append("com.facebook.litho.testing=true")

    native.robolectric_test(
        name = name,
        annotation_processor_params = annotation_processor_params,
        language = language,
        pure_kotlin = pure_kotlin,
        *args,
        **kwargs
    )

def fb_java_test(*args, **kwargs):
    """Uses native java_test for OSS project."""

    kwargs.pop("autoglob", False)
    native.java_test(*args, **kwargs)

def litho_android_library(name, srcs = None, *args, **kwargs):
    srcs = srcs or []

    kwargs.pop("autoglob", False)
    native.android_library(name = name, srcs = srcs, *args, **kwargs)

def litho_android_test_library(**kwargs):
    annotation_processor_params = kwargs.pop("annotation_processor_params", [])
    found = False
    for param in annotation_processor_params:
        if (param.find("com.facebook.litho.testing=") == 0):
            found = True

    if (not found):
        annotation_processor_params.append("com.facebook.litho.testing=true")

    litho_android_library(
        annotation_processor_params = annotation_processor_params,
        **kwargs
    )

def fb_android_cxx_library(*args, **kwargs):
    """Delegates to cxx_library for OSS project."""

    native.cxx_library(*args, **kwargs)

def fb_android_resource(**kwargs):
    """Delegates to native android_resource rule."""

    native.android_resource(**kwargs)

def fb_java_binary(**kwargs):
    """Delegates to native java_binary rule."""
    native.java_binary(**kwargs)

def fb_java_library(**kwargs):
    """Delegates to native java_library rule."""

    kwargs.pop("autoglob", False)
    native.java_library(**kwargs)

def fb_android_library(**kwargs):
    """Delegates to native android_library rule."""

    kwargs.pop("autoglob", False)
    native.android_library(**kwargs)

def fb_prebuilt_cxx_library(**kwargs):
    """Delegates to native prebuilt_cxx_library."""
    native.prebuilt_cxx_library(**kwargs)

def instrumentation_test(**kwargs):
    """
    We don't support this in the OSS build for now.
    Please use Gradle instead.
    """

    kwargs.pop("autoglob", False)
    _ignore = kwargs
    pass

def fb_core_android_library(**kwargs):
    kwargs.pop("autoglob", False)
    native.android_library(**kwargs)

# This target is only used in open source and will break the monobuild
# because we cannot define `soname` multiple times.
def define_yogajni_targets():
    fb_prebuilt_cxx_library(
        name = "ndklog",
        exported_platform_linker_flags = [
            (
                "^android.*",
                ["-llog"],
            ),
        ],
        header_only = True,
        visibility = LITHO_VISIBILITY,
    )

    fb_android_cxx_library(
        name = "jni",
        srcs = native.glob(["src/main/cpp/jni/*.cpp"]),
        header_namespace = "",
        compiler_flags = [
            "-fno-omit-frame-pointer",
            "-fexceptions",
            "-Wall",
            "-O3",
            "-std=c++20",  # FIXME
        ],
        soname = "libyoga.$(ext)",
        visibility = LITHO_VISIBILITY,
        deps = [
            ":ndklog",
            LITHO_JNI_TARGET,
            make_dep_path("lib/yoga/src/main/cpp:yoga"),
        ],
    )

# This target is only used in open source and will break the monobuild
# because we cannot define `soname` multiple times.
def define_cpp_yoga_targets():
    fb_prebuilt_cxx_library(
        name = "ndklog",
        exported_platform_linker_flags = [
            (
                "^android.*",
                ["-llog"],
            ),
        ],
        header_only = True,
        visibility = LITHO_VISIBILITY,
    )
    fb_android_cxx_library(
        name = "yoga",
        srcs = native.glob(["yoga/**/*.cpp"]),
        header_namespace = "",
        exported_headers = native.glob(["yoga/**/*.h"]),
        compiler_flags = [
            "-fno-omit-frame-pointer",
            "-fexceptions",
            "-Wall",
            "-std=c++20",  # FIXME
            "-O3",
        ],
        force_static = True,
        visibility = LITHO_VISIBILITY,
        deps = [
            ":ndklog",
        ],
    )
