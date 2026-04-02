plugins {
    java
    id("org.springframework.boot") version "4.0.4"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.ownpic"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven-central.storage-download.googleapis.com/maven2")
    }
}

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // OpenAPI / Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // Database
    runtimeOnly("org.postgresql:postgresql")

    // Flyway (Spring Boot 4는 starter 필수)
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // ONNX Runtime (ML models)
    implementation("com.microsoft.onnxruntime:onnxruntime:1.22.0")

    // HTML parsing
    implementation("org.jsoup:jsoup:1.18.3")

    // Google Cloud Vision API (WebDetection — 리버스 이미지 검색)
    implementation("com.google.cloud:google-cloud-vision:3.86.0")

    // WebP/TIFF 등 추가 이미지 포맷 지원 (ImageIO 플러그인)
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")

    // PDF 생성 (HTML → PDF, 한글 폰트 지원)
    implementation("io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.37")
    // DOCX 생성
    implementation("org.apache.poi:poi-ooxml:5.4.0")

    // Selenium (Google Lens 크롤링 + 판매 페이지 동적 렌더링)
    implementation("org.seleniumhq.selenium:selenium-java:4.27.0")

    // Dev tools
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("com.h2database:h2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
