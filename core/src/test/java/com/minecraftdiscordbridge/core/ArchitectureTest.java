package com.minecraftdiscordbridge.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
    private static final String CORE = "com.minecraftdiscordbridge.core";
    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(CORE);

    @Test
    void onlyTheStoragePackageUsesJackson() {
        noClasses()
                .that()
                .resideOutsideOfPackage(CORE + ".storage..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.fasterxml.jackson..")
                .check(PRODUCTION);
    }

    @Test
    void coreNeverTouchesGameOrLoaderApis() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "net.minecraft..",
                        "org.bukkit..",
                        "io.papermc..",
                        "com.velocitypowered..",
                        "net.fabricmc..",
                        "net.neoforged..",
                        "net.minecraftforge..")
                .check(PRODUCTION);
    }

    @Test
    void featurePackagesHaveNoCycles() {
        slices().matching(CORE + ".(*)..").should().beFreeOfCycles().check(PRODUCTION);
    }
}
