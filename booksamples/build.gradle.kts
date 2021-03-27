plugins {
    java
}

gradle.buildFinished {
    delete(project.buildDir)
}

val os = org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem()
var natives = "natives-windows"
if ( os.isMacOsX()) {
    natives = "natives-macos"
} else if ( os.isLinux()) {
    natives = "natives-linux"
}

subprojects {

    apply(plugin = "java")

    repositories {
	    mavenCentral()
		maven(url="https://oss.sonatype.org/content/repositories/snapshots/")
    }

    val jomlVersion = "1.10.0"
    val log4jVersion = "2.14.0"
    val lwjglVersion = "3.3.0-SNAPSHOT"
	var imguiVersion = "1.81.2"

    dependencies {
        implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
        implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
        implementation("org.lwjgl:lwjgl:$lwjglVersion")
        implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion")
        implementation("org.lwjgl:lwjgl-vulkan:$lwjglVersion")
        implementation("org.lwjgl:lwjgl-shaderc:$lwjglVersion")
        implementation("org.lwjgl:lwjgl-stb:$lwjglVersion")
        implementation("org.lwjgl:lwjgl-assimp:$lwjglVersion")
        implementation("org.lwjgl:lwjgl-vma:$lwjglVersion")
        implementation("org.joml:joml:$jomlVersion")
        implementation("io.github.spair:imgui-java-binding:$imguiVersion")

        runtimeOnly("org.lwjgl:lwjgl::$natives")
        runtimeOnly("org.lwjgl:lwjgl-glfw::$natives")
        runtimeOnly("org.lwjgl:lwjgl-shaderc::$natives")
        runtimeOnly("org.lwjgl:lwjgl-stb::$natives")
        runtimeOnly("org.lwjgl:lwjgl-assimp::$natives")
        runtimeOnly("org.lwjgl:lwjgl-vma::$natives")
        runtimeOnly("io.github.spair:imgui-java-$natives:$imguiVersion")
    }

    tasks {
        register<Copy>("copyDependencies") {
            val destDir = "build/dist"

			// Copy resources
			from("./resources")
			into(destDir + "/resources")

            // Copy generated JAR
            from(jar)
            into(destDir)

            // Copy dependencies
            from(configurations.runtimeClasspath)
            into(destDir)			
        }
    }

    tasks.build {
        dependsOn("copyDependencies")
    }

    tasks.withType<JavaCompile> {
	    options.compilerArgs.add("--enable-preview")
	}
	
    tasks.jar {
        manifest {
            attributes(
                "Main-class" to "org.vulkanb.Main",
                "Class-Path" to configurations.runtimeClasspath.files.joinToString(separator = " "){"${it.getName()}"}
            )
        }
    }
}
