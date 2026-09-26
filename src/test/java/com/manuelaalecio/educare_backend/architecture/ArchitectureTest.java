package com.manuelaalecio.educare_backend.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

/**
 * Architecture rules from CLAUDE.md ("Regras de dependência"). Every first-level package below the root is a
 * business module, except {@code shared}. Layers are defined by package pattern ({@code ..educare_backend.*.api..}
 * etc.), so the same rules apply to every module without listing them. Rules between modules pass trivially while
 * {@code user} is the only module and start to bite as soon as a second one is added.
 */
class ArchitectureTest {

	private static final String BASE_PACKAGE = "com.manuelaalecio.educare_backend";
	private static final String SHARED_PACKAGE = BASE_PACKAGE + ".shared..";

	private static final String API = "..educare_backend.*.api..";
	private static final String APPLICATION = "..educare_backend.*.application..";
	private static final String DOMAIN = "..educare_backend.*.domain..";
	private static final String INFRASTRUCTURE = "..educare_backend.*.infrastructure..";

	/** Captures the module (group 1) and the layer below it (group 2, may be absent) of a business class. */
	private static final Pattern MODULE_PACKAGE = Pattern
		.compile("^" + Pattern.quote(BASE_PACKAGE) + "\\.([^.]+)(?:\\.([^.]+))?.*$");

	private static JavaClasses classes;

	@BeforeAll
	static void importProductionClasses() {
		classes = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages(BASE_PACKAGE);
	}

	@Test
	void shouldRespectLayerDependenciesWhenAccessingOtherLayers() {
		ArchRule rule = layeredArchitecture()
			.consideringOnlyDependenciesInLayers()
			.withOptionalLayers(true)
			.layer("Api").definedBy(API)
			.layer("Application").definedBy(APPLICATION)
			.layer("Domain").definedBy(resideInAPackage(DOMAIN).and(not(assignableTo(Repository.class)))
				.as("domain classes other than repositories"))
			.layer("Repository").definedBy(resideInAPackage(DOMAIN).and(assignableTo(Repository.class))
				.as("domain repositories"))
			.layer("Infrastructure").definedBy(INFRASTRUCTURE)
			.whereLayer("Api").mayNotBeAccessedByAnyLayer()
			.whereLayer("Application").mayOnlyBeAccessedByLayers("Api")
			.whereLayer("Domain").mayOnlyBeAccessedByLayers("Api", "Application", "Repository", "Infrastructure")
			.whereLayer("Repository").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
			.whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer();

		rule.check(classes);
	}

	@Test
	void shouldKeepDomainFreeOfWebAndOtherLayersWhenDependingOnClasses() {
		ArchRule rule = noClasses().that().resideInAPackage(DOMAIN)
			.should().dependOnClassesThat()
			.resideInAnyPackage("org.springframework.web..", "jakarta.servlet..", API, APPLICATION, INFRASTRUCTURE);

		rule.check(classes);
	}

	@Test
	void shouldKeepApplicationFreeOfHttpWhenDependingOnClasses() {
		ArchRule rule = noClasses().that().resideInAPackage(APPLICATION)
			.should().dependOnClassesThat(
				belongToAnyOf(ResponseEntity.class, HttpStatus.class, HttpStatusCode.class)
					.or(resideInAnyPackage("jakarta.servlet..", "org.springframework.web.servlet..")));

		rule.check(classes);
	}

	@Test
	void shouldKeepSharedIndependentWhenDependingOnBusinessModules() {
		ArchRule rule = noClasses().that().resideInAPackage(SHARED_PACKAGE)
			.should().dependOnClassesThat(inBusinessModule())
			.allowEmptyShould(true);

		rule.check(classes);
	}

	@Test
	void shouldHaveNoCyclesWhenModulesDependOnEachOther() {
		ArchRule rule = slices().matching(BASE_PACKAGE + ".(*)..")
			.namingSlices("module $1")
			.should().beFreeOfCycles()
			.ignoreDependency(resideInAPackage(SHARED_PACKAGE), DescribedPredicate.alwaysTrue())
			.ignoreDependency(DescribedPredicate.alwaysTrue(), resideInAPackage(SHARED_PACKAGE))
			.allowEmptyShould(true);

		rule.check(classes);
	}

	@Test
	void shouldOnlyUseApplicationLayerWhenAccessingAnotherModule() {
		ArchRule rule = classes().that(inBusinessModule())
			.should(notAccessApiDomainOrInfrastructureOfAnotherModule())
			.allowEmptyShould(true);

		rule.check(classes);
	}

	@Test
	void shouldUseConstructorInjectionWhenInjectingDependencies() {
		ArchRule rule = noFields().should().beAnnotatedWith(Autowired.class)
			.because("injection is constructor-only (CLAUDE.md)")
			.allowEmptyShould(true);

		rule.check(classes);
	}

	private static DescribedPredicate<JavaClass> inBusinessModule() {
		return DescribedPredicate.describe("reside in a business module",
			javaClass -> moduleOf(javaClass).isPresent());
	}

	private static ArchCondition<JavaClass> notAccessApiDomainOrInfrastructureOfAnotherModule() {
		return new ArchCondition<>("not access api, domain or infrastructure of another module") {
			@Override
			public void check(JavaClass origin, ConditionEvents events) {
				String originModule = moduleOf(origin).orElseThrow().module();
				for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
					moduleOf(dependency.getTargetClass())
						.filter(target -> !target.module().equals(originModule))
						.filter(target -> target.isInternalLayer())
						.ifPresent(target -> events.add(SimpleConditionEvent.violated(dependency,
							dependency.getDescription())));
				}
			}
		};
	}

	/** The business module (and layer) of a class, or empty for the root package, {@code shared} and outsiders. */
	private static Optional<ModuleLocation> moduleOf(JavaClass javaClass) {
		Matcher matcher = MODULE_PACKAGE.matcher(javaClass.getPackageName());
		if (!matcher.matches() || matcher.group(1).equals("shared")) {
			return Optional.empty();
		}
		return Optional.of(new ModuleLocation(matcher.group(1), matcher.group(2)));
	}

	private record ModuleLocation(String module, String layer) {

		boolean isInternalLayer() {
			return "api".equals(layer) || "domain".equals(layer) || "infrastructure".equals(layer);
		}
	}
}
