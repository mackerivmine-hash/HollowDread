# Hollow Dread (Fabric 1.21.1)

## Compilar
1. Instale o JDK 21.
2. Gere o wrapper do Gradle (uma vez): `gradle wrapper --gradle-version 8.10`
   (ou copie a pasta `gradle/` + `gradlew` do fabric-example-mod).
3. `./gradlew build` -> o .jar fica em `build/libs/`.
Teste direto: `./gradlew runClient`.

Se a versão do Fabric API não existir, troque `fabric_version` em gradle.properties pela mais recente para 1.21.1.

## Comandos (op)
/dread fear <0-1000> | /dread event | /dread spawn
