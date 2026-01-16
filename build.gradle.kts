plugins {
    java
}

group = "com.friends"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/HytaleServer.jar"))
}

tasks.jar {
    archiveBaseName.set("HytaleFriends")
    archiveVersion.set("0.1.0")
    manifest {
        attributes("Main-Class" to "com.friends.FriendsPlugin")
    }
}
