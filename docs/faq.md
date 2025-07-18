---
id: faq
title: FAQ
---

## Frequently Asked Questions

### Using Litho with React Native

React Native ships with its own version of Yoga which can cause conflicts when merging the
dex files. In order to avoid this, you can instruct Gradle to exclude one of the Yoga modules.

To do this, add a section like this to your Gradle file after the dependency declaration:

```gradle
configurations.all {
  exclude group: 'com.facebook.yoga', module: 'yoga'
  exclude group: 'com.facebook.infer.annotation', module: 'infer-annotation'
  exclude group: 'com.google.code.findbugs', module: 'jsr305'
}
```

For more information, check out [issue #224](https://github.com/facebook/litho/issues/224).

### Forcing newer versions of the Support Library

If you want to override the version of the support library Litho requires, you can set
the overrides in your `build.gradle`:

```gradle
configurations.all {
  resolutionStrategy {
    force 'com.android.support:appcompat-v7:26.+'
    force 'com.android.support:support-compat:26.+'
    force 'com.android.support:support-core-ui:26.+'
    force 'com.android.support:support-annotations:26.+'
    force 'com.android.support:recyclerview-v7:26.+'
  }
}
```

### Could not initialize class com.facebook.yoga.YogaNode

If you are getting this error when running a Litho unit test, go through these steps:

- Ensure Java 8 is correctly set up. If you are on a Mac, make sure that `which java`
  points to something like `/Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/bin/java`
  and *not* `/usr/bin/java`. Otherwise, update your `$PATH` accordingly.

**For Buck**

- Make sure your tests use the `litho_robolectric4_test` which sets up the necessary dependencies on the native libraries.
- Try `buck kill` and `buck clean`.
- If everything else fails, reboot.

**For Gradle**

- Follow the instructions under [Unit Testing - Caveats](/docs/testing/unit-testing#caveats) for your setup.
- Relaunch the gradle daemon with `./gradlew --stop`.
