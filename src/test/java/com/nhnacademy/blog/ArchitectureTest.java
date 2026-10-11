package com.nhnacademy.blog;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.nhnacademy.blog.auth.application.CredentialService;
import com.nhnacademy.blog.auth.presentation.LoginSessions;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 패키지 경계와 계층 방향 (스텝 19b, T115). 컴파일러가 막지 않는 약속을 테스트로 지킨다. DB가 필요 없어 빠르다.
 * <ul>
 *   <li>인증의 속(토큰, 쿠키, 필터, 시도 횟수 제한, 인증 설정)은 auth와 보안 설정(global.config) 밖에서 쓰지 않는다.
 *       바깥은 global.auth의 입구(LoginMember, LoginMembers, BlogOwnerGuard, 정지 안내)와
 *       auth의 입구(CredentialService, LoginSessions)만 쓴다.</li>
 *   <li>비밀번호 해시 도구(PasswordEncoder)는 auth와 보안 설정 밖에서 쓰지 않는다.</li>
 *   <li>기능 패키지 안은 presentation → application → domain 한 방향이다(CLAUDE.md 기술 규칙).</li>
 * </ul>
 * 테스트 코드는 검사하지 않는다(테스트는 쿠키를 직접 만들어 로그인하는 등 속을 들여다봐도 된다).
 */
@AnalyzeClasses(packages = "com.nhnacademy.blog", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String AUTH = "com.nhnacademy.blog.auth..";
    private static final String SECURITY_CONFIG = "com.nhnacademy.blog.global.config..";

    @ArchTest
    static final ArchRule authInternalsStayInsideAuth = noClasses()
            .that().resideOutsideOfPackages(AUTH, SECURITY_CONFIG)
            .should().dependOnClassesThat(resideInAPackage(AUTH)
                    .and(not(belongToAnyOf(CredentialService.class, LoginSessions.class))))
            .because("인증의 속은 auth 안에만 둔다. 바깥은 global.auth의 입구와 CredentialService·LoginSessions만 쓴다 (스텝 19b)");

    @ArchTest
    static final ArchRule passwordHashingStaysInsideAuth = noClasses()
            .that().resideOutsideOfPackages(AUTH, SECURITY_CONFIG)
            .should().dependOnClassesThat().areAssignableTo(PasswordEncoder.class)
            .because("비밀번호 확인·바꾸기는 auth의 CredentialService가 한다 (스텝 19b)");

    @ArchTest
    static final ArchRule domainDoesNotKnowUpperLayers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..presentation..")
            .because("의존은 presentation → application → domain 한 방향이다");

    @ArchTest
    static final ArchRule applicationDoesNotKnowPresentation = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..presentation..")
            .because("의존은 presentation → application → domain 한 방향이다");

}
