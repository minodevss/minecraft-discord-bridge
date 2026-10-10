package com.minecraftdiscordbridge.core.discord;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DiscordBoundaryTest {
    private static final String DISCORD = "com.minecraftdiscordbridge.core.discord";
    private static final String[] LIBRARIES = {"net.dv8tion..", "okhttp3..", "okio.."};
    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.minecraftdiscordbridge");

    @Test
    void onlyTheDiscordPackageUsesJdaOrOkHttp() {
        noClasses()
                .that()
                .resideOutsideOfPackage(DISCORD)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(LIBRARIES)
                .check(PRODUCTION);
    }

    @Test
    void publicDiscordTypesKeepJdaOutOfTheirSignatures() {
        classes()
                .that()
                .resideInAPackage(DISCORD)
                .and()
                .arePublic()
                .should(new ArchCondition<JavaClass>("expose only JDA-free types") {
                    @Override
                    public void check(JavaClass type, ConditionEvents events) {
                        type.getMembers().stream()
                                .filter(member -> member.getModifiers().contains(JavaModifier.PUBLIC))
                                .forEach(member -> exposedTypes(member)
                                        .filter(DiscordBoundaryTest::isLibraryType)
                                        .forEach(leak -> events.add(SimpleConditionEvent.violated(
                                                member, member.getFullName() + " exposes " + leak.getName()))));
                    }
                })
                .check(PRODUCTION);
    }

    private static Stream<JavaClass> exposedTypes(JavaMember member) {
        if (member instanceof com.tngtech.archunit.core.domain.JavaCodeUnit codeUnit) {
            return Stream.concat(Stream.of(codeUnit.getRawReturnType()), codeUnit.getRawParameterTypes().stream());
        }
        if (member instanceof com.tngtech.archunit.core.domain.JavaField field) {
            return Stream.of(field.getRawType());
        }
        return Stream.empty();
    }

    private static boolean isLibraryType(JavaClass type) {
        return List.of("net.dv8tion", "okhttp3", "okio").stream()
                .anyMatch(prefix -> type.getPackageName().startsWith(prefix));
    }
}
