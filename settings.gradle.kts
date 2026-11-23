pluginManagement {
    repositories {
        // region mirrors: fast accelerators with fall-through on 404; official repos last
        maven { setUrl("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        maven { setUrl("https://maven.aliyun.com/repository/google") }
        maven { setUrl("https://maven.aliyun.com/repository/gradle-plugin") }
        // endregion
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // region mirrors: fall through to the official repos below on any miss
        maven { setUrl("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        maven { setUrl("https://maven.aliyun.com/repository/google") }
        maven { setUrl("https://maven.aliyun.com/repository/gradle-plugin") }
        // jitpack must precede mavenCentral: server errors (429/5xx) abort resolution
        // instead of falling through, so a throttled Central must not shadow jitpack
        maven { setUrl("https://www.jitpack.io") }
        // endregion
        google()
        mavenCentral()
        mavenLocal()
    }
}
include(":app")
