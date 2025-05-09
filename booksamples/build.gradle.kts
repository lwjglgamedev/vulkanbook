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
    }

    val jomlVersion = "1.10.2"
    val tinyLogVersion = "2.4.1"
    val lwjglVersion = "3.3.0"
	var imguiVersion = "1.85.1"

    dependencies {
        implementation("org.tinylog:tinylog-api:$tinyLogVersion")
        implementation("org.tinylog:tinylog-impl:$tinyLogVersion")
        implementation("org.lwjgl:lwjgl:$lwjglVersion")
        implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion")
        implementation("org.lwjgl:lwjgl-openal:$lwjglVersion")
        implementation("org.lwjgl:lwjgl-vulkan:$lwjglVersion")
        implementation("org.lwjgl:lwjgl-shaderc:$lwjglVersion")
        implementation("org.lwjgl:lwjgl-stb:$lwjglVersion")
        implementation("org.lwjgl:lwjgl-assimp:$lwjglVersion")
        implementation("org.lwjgl:lwjgl-vma:$lwjglVersion")
        implementation("org.joml:joml:$jomlVersion")
        implementation("io.github.spair:imgui-java-binding:$imguiVersion")

        runtimeOnly("org.lwjgl:lwjgl::$natives")
        runtimeOnly("org.lwjgl:lwjgl-glfw::$natives")
        runtimeOnly("org.lwjgl:lwjgl-openal::$natives")
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
        manifest.attributes["Main-Class"] = "org.boxes.Main"
        manifest.attributes["Class-Path"] = configurations
            .runtimeClasspath
            .get()
            .joinToString(separator = " ") { file ->
                "${file.name}"
            }
    }    
}
