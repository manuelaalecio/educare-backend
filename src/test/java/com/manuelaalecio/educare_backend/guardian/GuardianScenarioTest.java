package com.manuelaalecio.educare_backend.guardian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.manuelaalecio.educare_backend.shared.testsupport.MutableClock;
import com.manuelaalecio.educare_backend.shared.testsupport.MutableClockConfiguration;
import com.manuelaalecio.educare_backend.shared.testsupport.TestUsers;
import com.manuelaalecio.educare_backend.shared.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * One test per Scenario of the {@code guardian} spec, end to end through the API with a real database. Guardians are
 * created as the spec says, in the body of the creation of a child; "a responsável Maria Souza" is created with
 * "os dados válidos" as the {@code MOTHER} of {@code Ana Souza}. Unless a scenario says otherwise, requests are made by
 * a {@code COORDENADOR}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class, TestUsers.class})
class GuardianScenarioTest {

	private static final String GUARDIANS = "/api/v1/guardians";
	private static final String CHILDREN = "/api/v1/children";
	private static final String PROBLEM_JSON = "application/problem+json";
	private static final String MISSING_ID = "0190f4a2-0000-7000-8000-000000000000";
	private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
	private static final String ADMIN_LOGIN = "admin@educare.org";
	private static final String ANA_LOGIN = "ana.souza@educare.org";
	private static final String BRUNO_LOGIN = "bruno.lima@educare.org";
	private static final String PASSWORD = "segredo123";
	/** Marks a field to leave out of the body. */
	private static final Object ABSENT = new Object();
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MutableClock clock;

	@Autowired
	private TestUsers testUsers;

	private UUID turma1;
	private UUID anaId;
	private UUID brunoId;
	private String adminBearer;
	private String anaBearer;
	private String brunoBearer;

	@BeforeEach
	void setUp() throws Exception {
		jdbcTemplate.update("DELETE FROM child_guardians");
		jdbcTemplate.update("DELETE FROM guardians");
		jdbcTemplate.update("DELETE FROM child_classrooms");
		jdbcTemplate.update("DELETE FROM children");
		jdbcTemplate.update("DELETE FROM classrooms");
		jdbcTemplate.update("DELETE FROM users");
		clock.setInstant(NOW);
		testUsers.create("Administrador", ADMIN_LOGIN, PASSWORD, "COORDENADOR");
		anaId = testUsers.create("Ana Souza", ANA_LOGIN, PASSWORD, "EDUCADOR");
		brunoId = testUsers.create("Bruno Lima", BRUNO_LOGIN, PASSWORD, "EDUCADOR");
		adminBearer = testUsers.bearer(ADMIN_LOGIN, PASSWORD);
		anaBearer = testUsers.bearer(ANA_LOGIN, PASSWORD);
		brunoBearer = testUsers.bearer(BRUNO_LOGIN, PASSWORD);
		turma1 = idOf(perform(post("/api/v1/classrooms").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"Turma 1\"}")).andExpect(status().isCreated()).andReturn());
	}

	// ---------- Dados do responsável ----------

	@Test
	@DisplayName("Dados do responsável — Scenario: Alteração só com os dados obrigatórios")
	void shouldUpdateWithRequiredDataOnly() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		putGuardian(maria, guardian("name", "Maria Souza Lima")).andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Maria Souza Lima"))
			.andExpect(jsonPath("$.phone").value("11912345678"))
			.andExpect(jsonPath("$.notWorking").value(false))
			.andExpect(jsonPath("$.workplace").value("Padaria Central"))
			.andExpect(jsonPath("$.workAddress").value("Rua das Flores, 100"))
			.andExpect(jsonPath("$.workPhone").value("1133334444"))
			.andExpect(jsonPath("$.maritalStatus").isEmpty())
			.andExpect(jsonPath("$.birthDate").isEmpty())
			.andExpect(jsonPath("$.cpf").isEmpty())
			.andExpect(jsonPath("$.rg").isEmpty());
	}

	@Test
	@DisplayName("Dados do responsável — Scenario: Não trabalha dispensa e descarta os campos de trabalho")
	void shouldDiscardWorkFieldsWhenNotWorking() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		putGuardian(maria, "{\"name\": \"Maria Souza\", \"phone\": \"11912345678\", \"notWorking\": true, "
				+ "\"workplace\": \"Padaria Central\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notWorking").value(true))
			.andExpect(jsonPath("$.workplace").isEmpty())
			.andExpect(jsonPath("$.workAddress").isEmpty())
			.andExpect(jsonPath("$.workPhone").isEmpty());
	}

	@Test
	@DisplayName("Dados do responsável — Scenario: Trabalha sem os dados do trabalho")
	void shouldRequireWorkFieldsWhenWorking() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		putGuardian(maria, "{\"name\": \"Maria Souza\", \"phone\": \"11912345678\", \"notWorking\": false, "
				+ "\"workAddress\": \"   \"}")
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("workplace", "workAddress",
					"workPhone")));
		getGuardian(maria).andExpect(jsonPath("$.workplace").value("Padaria Central"))
			.andExpect(jsonPath("$.workAddress").value("Rua das Flores, 100"))
			.andExpect(jsonPath("$.workPhone").value("1133334444"));
	}

	@Test
	@DisplayName("Dados do responsável — Scenario: notWorking ausente vale falso")
	void shouldTreatMissingNotWorkingAsFalse() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		putGuardian(maria, "{\"name\": \"Maria Souza\", \"phone\": \"11912345678\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("workplace", "workAddress",
					"workPhone")));
	}

	@Test
	@DisplayName("Dados do responsável — Scenario: Obrigatórios ausentes")
	void shouldListEveryMissingRequiredField() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		putGuardian(maria, "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "phone", "workplace",
					"workAddress", "workPhone")));
	}

	@Test
	@DisplayName("Dados do responsável — Scenario: Textos com espaços nas pontas e opcional em branco")
	void shouldStripTextsAndClearBlankOptional() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		putGuardian(maria, guardian("name", "  Maria Souza  ", "rg", "   ")).andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Maria Souza"))
			.andExpect(jsonPath("$.rg").isEmpty());
	}

	@Test
	@DisplayName("Dados do responsável — Scenario: Textos longos demais e no limite")
	void shouldRejectTextsLongerThanMaxAndAcceptTheLimit() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		putGuardian(maria, guardian("name", "a".repeat(151), "workAddress", "b".repeat(201)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "workAddress")));
		putGuardian(maria, guardian("name", "a".repeat(150), "workAddress", "b".repeat(200)))
			.andExpect(status().isOk());
	}

	// ---------- Telefones do responsável ----------

	@Test
	@DisplayName("Telefones do responsável — Scenario: Telefones com 10 e 11 dígitos")
	void shouldAcceptPhonesWithTenAndElevenDigits() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		putGuardian(maria, guardian("phone", "1133334444", "workPhone", "11987654321")).andExpect(status().isOk())
			.andExpect(jsonPath("$.phone").value("1133334444"))
			.andExpect(jsonPath("$.workPhone").value("11987654321"));
	}

	@Test
	@DisplayName("Telefones do responsável — Scenario: Telefone curto e telefone com máscara")
	void shouldRejectShortAndMaskedPhones() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		putGuardian(maria, guardian("phone", "111234", "workPhone", "(11) 3333-4444"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("phone", "workPhone")));
		getGuardian(maria).andExpect(jsonPath("$.phone").value("11912345678"))
			.andExpect(jsonPath("$.workPhone").value("1133334444"));
	}

	// ---------- Data de nascimento do responsável ----------

	@Test
	@DisplayName("Data de nascimento do responsável — Scenario: Nascimento hoje no Brasil")
	void shouldAcceptBirthDateOfTodayInSaoPaulo() throws Exception {
		// given
		UUID maria = createMaria();
		moveClockTo(Instant.parse("2026-03-02T01:00:00Z"));

		// when / then
		putGuardian(maria, guardian("birthDate", "2026-03-01")).andExpect(status().isOk())
			.andExpect(jsonPath("$.birthDate").value("2026-03-01"));
	}

	@Test
	@DisplayName("Data de nascimento do responsável — Scenario: Nascimento no futuro")
	void shouldRejectBirthDateInTheFuture() throws Exception {
		// given
		UUID maria = createMaria();
		moveClockTo(Instant.parse("2026-03-02T01:00:00Z"));

		// when / then
		expectFieldError(putGuardian(maria, guardian("birthDate", "2026-03-02")), "birthDate");
		getGuardian(maria).andExpect(jsonPath("$.birthDate").isEmpty());
	}

	@Test
	@DisplayName("Data de nascimento do responsável — Scenario: Data em outro formato")
	void shouldRejectBirthDateInAnotherFormat() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		expectFieldError(putGuardian(maria, guardian("birthDate", "10/05/1990")), "birthDate");
	}

	// ---------- Estado civil do responsável ----------

	@Test
	@DisplayName("Estado civil do responsável — Scenario: Estado civil válido")
	void shouldAcceptValidMaritalStatus() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		putGuardian(maria, guardian("maritalStatus", "DIVORCED")).andExpect(status().isOk())
			.andExpect(jsonPath("$.maritalStatus").value("DIVORCED"));
	}

	@Test
	@DisplayName("Estado civil do responsável — Scenario: Estado civil fora da lista")
	void shouldRejectMaritalStatusOutOfList() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		expectFieldError(putGuardian(maria, guardian("maritalStatus", "married")), "maritalStatus");
	}

	// ---------- CPF do responsável válido e único ----------

	@Test
	@DisplayName("CPF do responsável válido e único — Scenario: CPF com máscara gravado só com dígitos")
	void shouldStoreMaskedCpfWithDigitsOnly() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		putGuardian(maria, guardian("cpf", "529.982.247-25")).andExpect(status().isOk())
			.andExpect(jsonPath("$.cpf").value("52998224725"));
	}

	@Test
	@DisplayName("CPF do responsável válido e único — Scenario: CPF inválido")
	void shouldRejectInvalidCpf() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		expectFieldError(putGuardian(maria, guardian("cpf", "123.456.789-00")), "cpf");
	}

	@Test
	@DisplayName("CPF do responsável válido e único — Scenario: Alteração para o CPF de outro responsável")
	void shouldRejectCpfOfAnotherGuardian() throws Exception {
		// given
		UUID maria = createMaria();
		createChild("Pedro Lima", newItem(guardianMap("name", "João Lima", "cpf", "52998224725"), "FATHER"));

		// when / then
		putGuardian(maria, guardian("cpf", "52998224725")).andExpect(status().isConflict())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Já existe um responsável com este CPF."))
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("cpf"))
			.andExpect(content().string(not(containsString("52998224725"))));
		getGuardian(maria).andExpect(jsonPath("$.cpf").isEmpty());
	}

	@Test
	@DisplayName("CPF do responsável válido e único — Scenario: Manter o próprio CPF")
	void shouldKeepOwnCpf() throws Exception {
		// given
		UUID maria = createMaria("cpf", "52998224725");

		// when / then
		putGuardian(maria, guardian("cpf", "529.982.247-25")).andExpect(status().isOk())
			.andExpect(jsonPath("$.cpf").value("52998224725"));
	}

	@Test
	@DisplayName("CPF do responsável válido e único — Scenario: Vários responsáveis sem CPF e CPF igual ao de uma "
			+ "criança")
	void shouldAcceptGuardiansWithoutCpfAndWithCpfOfAChild() throws Exception {
		// given
		createChildWith("Ana Souza", Map.of("cpf", "11144477735"), newItem(guardianMap("name", "Maria Souza"), "MOTHER"));

		// when / then
		postChild(adminBearer, childBody("Pedro Souza", Map.of(), newItem(guardianMap("name", "Joana Souza"), "MOTHER"),
				newItem(guardianMap("name", "Carlos Souza"), "FATHER")))
			.andExpect(status().isCreated());
		postChild(adminBearer, childBody("Bia Souza", Map.of(),
				newItem(guardianMap("name", "Lia Souza", "cpf", "11144477735"), "AUNT")))
			.andExpect(status().isCreated());
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM guardians", Integer.class)).isEqualTo(4);
	}

	// ---------- RG do responsável ----------

	@Test
	@DisplayName("RG do responsável — Scenario: RG normalizado")
	void shouldNormalizeRg() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		putGuardian(maria, guardian("rg", "12.345.678-x")).andExpect(status().isOk())
			.andExpect(jsonPath("$.rg").value("12345678X"));
	}

	@Test
	@DisplayName("RG do responsável — Scenario: RG repetido entre responsáveis")
	void shouldAcceptRgRepeatedAmongGuardians() throws Exception {
		// given
		UUID maria = createMaria();
		createChild("Pedro Lima", newItem(guardianMap("name", "João Lima", "rg", "12345678X"), "FATHER"));

		// when / then
		putGuardian(maria, guardian("rg", "12345678-X")).andExpect(status().isOk())
			.andExpect(jsonPath("$.rg").value("12345678X"));
	}

	@Test
	@DisplayName("RG do responsável — Scenario: RG sem letras nem dígitos")
	void shouldRejectRgWithoutLettersOrDigits() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		expectFieldError(putGuardian(maria, guardian("rg", "...")), "rg");
	}

	// ---------- Listar responsáveis com paginação e busca ----------

	@Test
	@DisplayName("Listar responsáveis com paginação e busca — Scenario: Primeira página em ordem alfabética")
	void shouldListFirstPageInAlphabeticalOrder() throws Exception {
		// given
		createGuardians("Carla", "Ana", "Bruno", "Davi");

		// when / then
		perform(get(GUARDIANS).param("size", "2")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].name").value(contains("Ana", "Bruno")))
			.andExpect(jsonPath("$.page.size").value(2))
			.andExpect(jsonPath("$.page.number").value(0))
			.andExpect(jsonPath("$.page.totalElements").value(4))
			.andExpect(jsonPath("$.page.totalPages").value(2));
	}

	@Test
	@DisplayName("Listar responsáveis com paginação e busca — Scenario: Itens só com o resumo e as crianças")
	void shouldListOnlySummaryAndChildren() throws Exception {
		// given
		MvcResult pedro = createChild("Pedro Souza", newItem(guardianMap("cpf", "52998224725", "rg", "12345678X",
				"birthDate", "1990-05-10"), "MOTHER"));
		UUID maria = guardianIdOf(pedro, "Maria Souza");
		UUID ana = idOf(createChild("Ana Souza", existingItem(maria, "MOTHER")));

		// when / then
		perform(get(GUARDIANS)).andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].length()").value(4))
			.andExpect(jsonPath("$.content[0].id").value(maria.toString()))
			.andExpect(jsonPath("$.content[0].name").value("Maria Souza"))
			.andExpect(jsonPath("$.content[0].phone").value("11912345678"))
			.andExpect(jsonPath("$.content[0].children.length()").value(2))
			.andExpect(jsonPath("$.content[0].children[0].id").value(ana.toString()))
			.andExpect(jsonPath("$.content[0].children[0].name").value("Ana Souza"))
			.andExpect(jsonPath("$.content[0].children[0].relationship").value("MOTHER"))
			.andExpect(jsonPath("$.content[0].children[0].length()").value(3))
			.andExpect(jsonPath("$.content[0].children[1].id").value(idOf(pedro).toString()))
			.andExpect(jsonPath("$.content[0].children[1].name").value("Pedro Souza"))
			.andExpect(jsonPath("$.content[0].children[1].relationship").value("MOTHER"));
	}

	@Test
	@DisplayName("Listar responsáveis com paginação e busca — Scenario: Busca por nome sem acento e sem maiúsculas")
	void shouldSearchNameIgnoringAccentsAndCase() throws Exception {
		// given
		createGuardians("João Pedro", "Joana Lima", "Maria João");

		// when / then
		perform(get(GUARDIANS).param("q", "joao")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].name").value(contains("João Pedro", "Maria João")))
			.andExpect(jsonPath("$.page.totalElements").value(2));
	}

	@Test
	@DisplayName("Listar responsáveis com paginação e busca — Scenario: Busca pelo CPF completo")
	void shouldSearchByFullCpf() throws Exception {
		// given
		createChild("Pedro Lima", newItem(guardianMap("name", "João Lima", "cpf", "52998224725"), "FATHER"),
				newItem(guardianMap("name", "Maria Souza"), "MOTHER"));

		// when / then
		perform(get(GUARDIANS).param("q", "52998224725")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].name").value(contains("João Lima")));
	}

	@Test
	@DisplayName("Listar responsáveis com paginação e busca — Scenario: Trecho do CPF não encontra ninguém")
	void shouldNotFindAnyoneByPartOfCpf() throws Exception {
		// given
		createChild("Pedro Lima", newItem(guardianMap("name", "João Lima", "cpf", "52998224725"), "FATHER"));

		// when / then
		perform(get(GUARDIANS).param("q", "529982")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content").value(empty()));
	}

	@Test
	@DisplayName("Listar responsáveis com paginação e busca — Scenario: Busca sem resultado e curinga")
	void shouldFindNothingForUnknownTextAndWildcard() throws Exception {
		// given
		createGuardians("Ana", "Bruno");

		// when / then
		perform(get(GUARDIANS).param("q", "zz")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content").value(empty()))
			.andExpect(jsonPath("$.page.totalElements").value(0));
		perform(get(GUARDIANS).param("q", "%")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content").value(empty()))
			.andExpect(jsonPath("$.page.totalElements").value(0));
	}

	@Test
	@DisplayName("Listar responsáveis com paginação e busca — Scenario: Busca em branco é ignorada")
	void shouldIgnoreBlankSearch() throws Exception {
		// given
		createGuardians("Ana", "Bruno");

		// when / then
		perform(get(GUARDIANS).param("q", "  ")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].name").value(contains("Ana", "Bruno")));
	}

	@Test
	@DisplayName("Listar responsáveis com paginação e busca — Scenario: Responsável sem crianças continua na lista")
	void shouldKeepGuardianWithoutChildrenInList() throws Exception {
		// given
		MvcResult ana = createChild("Ana Souza", newItem(guardianMap(), "MOTHER"));
		perform(delete(CHILDREN + "/" + idOf(ana))).andExpect(status().isNoContent());

		// when / then
		perform(get(GUARDIANS)).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].name").value(contains("Maria Souza")))
			.andExpect(jsonPath("$.content[0].children").value(empty()));
	}

	@Test
	@DisplayName("Listar responsáveis com paginação e busca — Scenario: Tamanho de página acima do máximo")
	void shouldCapPageSize() throws Exception {
		// given
		createGuardians("Ana", "Bruno", "Carla");

		// when / then
		perform(get(GUARDIANS).param("size", "500")).andExpect(status().isOk())
			.andExpect(jsonPath("$.page.size").value(100))
			.andExpect(jsonPath("$.content.length()").value(3));
	}

	@Test
	@DisplayName("Listar responsáveis com paginação e busca — Scenario: Ordenação por propriedade não permitida")
	void shouldRejectSortByPropertyNotAllowed() throws Exception {
		perform(get(GUARDIANS).param("sort", "cpf")).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON));
	}

	// ---------- Consultar responsável por id ----------

	@Test
	@DisplayName("Consultar responsável por id — Scenario: Responsável de dois irmãos")
	void shouldReturnGuardianOfTwoSiblings() throws Exception {
		// given
		MvcResult ana = createChild("Ana Souza", newItem(guardianMap("cpf", "52998224725"), "MOTHER"));
		UUID maria = guardianIdOf(ana, "Maria Souza");
		createChild("Pedro Lima", existingItem(maria, "STEPMOTHER"));

		// when / then
		getGuardian(maria).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(maria.toString()))
			.andExpect(jsonPath("$.name").value("Maria Souza"))
			.andExpect(jsonPath("$.cpf").value("52998224725"))
			.andExpect(jsonPath("$.phone").value("11912345678"))
			.andExpect(jsonPath("$.notWorking").value(false))
			.andExpect(jsonPath("$.workplace").value("Padaria Central"))
			.andExpect(jsonPath("$.workAddress").value("Rua das Flores, 100"))
			.andExpect(jsonPath("$.workPhone").value("1133334444"))
			.andExpect(jsonPath("$.maritalStatus").isEmpty())
			.andExpect(jsonPath("$.birthDate").isEmpty())
			.andExpect(jsonPath("$.rg").isEmpty())
			.andExpect(jsonPath("$.createdAt").value("2026-03-01T10:00:00Z"))
			.andExpect(jsonPath("$.updatedAt").value("2026-03-01T10:00:00Z"))
			.andExpect(jsonPath("$.children[*].name").value(contains("Ana Souza", "Pedro Lima")))
			.andExpect(jsonPath("$.children[*].relationship").value(contains("MOTHER", "STEPMOTHER")));
	}

	@Test
	@DisplayName("Consultar responsável por id — Scenario: Responsável inexistente")
	void shouldRespondNotFoundWhenGuardianDoesNotExist() throws Exception {
		perform(get(GUARDIANS + "/" + MISSING_ID)).andExpect(status().isNotFound())
			.andExpect(content().contentType(PROBLEM_JSON));
	}

	@Test
	@DisplayName("Consultar responsável por id — Scenario: Id malformado")
	void shouldRespondBadRequestWhenIdIsMalformed() throws Exception {
		perform(get(GUARDIANS + "/abc")).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON));
	}

	// ---------- Alterar responsável ----------

	@Test
	@DisplayName("Alterar responsável — Scenario: Novo telefone aparece nas crianças vinculadas")
	void shouldShowNewPhoneInLinkedChildren() throws Exception {
		// given
		MvcResult ana = createChild("Ana Souza", newItem(guardianMap(), "MOTHER"));
		UUID maria = guardianIdOf(ana, "Maria Souza");
		UUID pedro = idOf(createChild("Pedro Souza", existingItem(maria, "MOTHER")));
		moveClockTo(Instant.parse("2026-03-02T09:00:00Z"));

		// when / then
		putGuardian(maria, guardian("phone", "11955554444")).andExpect(status().isOk())
			.andExpect(jsonPath("$.phone").value("11955554444"))
			.andExpect(jsonPath("$.createdAt").value("2026-03-01T10:00:00Z"))
			.andExpect(jsonPath("$.updatedAt").value("2026-03-02T09:00:00Z"))
			.andExpect(jsonPath("$.children[*].name").value(contains("Ana Souza", "Pedro Souza")));
		for (UUID child : new UUID[] {idOf(ana), pedro}) {
			perform(get(CHILDREN + "/" + child)).andExpect(status().isOk())
				.andExpect(jsonPath("$.guardians[0].id").value(maria.toString()))
				.andExpect(jsonPath("$.guardians[0].phone").value("11955554444"));
		}
	}

	@Test
	@DisplayName("Alterar responsável — Scenario: Opcional omitido é apagado")
	void shouldClearOmittedOptional() throws Exception {
		// given
		UUID maria = createMaria("maritalStatus", "MARRIED");

		// when / then
		putGuardian(maria, guardian()).andExpect(status().isOk()).andExpect(jsonPath("$.maritalStatus").isEmpty());
	}

	@Test
	@DisplayName("Alterar responsável — Scenario: Alteração de responsável inexistente")
	void shouldRespondNotFoundWithoutCreatingWhenUpdatingMissingGuardian() throws Exception {
		putGuardian(UUID.fromString(MISSING_ID), guardian()).andExpect(status().isNotFound())
			.andExpect(content().contentType(PROBLEM_JSON));
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM guardians", Integer.class)).isZero();
	}

	@Test
	@DisplayName("Alterar responsável — Scenario: JSON inválido")
	void shouldRejectBrokenJson() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		putGuardian(maria, "{\"name\": \"Maria\"").andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON));
		getGuardian(maria).andExpect(jsonPath("$.name").value("Maria Souza"));
	}

	// ---------- Autoria do responsável ----------

	@Test
	@DisplayName("Autoria do responsável — Scenario: Autoria na criação e na alteração")
	void shouldRecordAuthorsOfCreationAndUpdate() throws Exception {
		// given
		MvcResult child = postChild(anaBearer, childBody("Ana Souza", Map.of(), newItem(guardianMap(), "MOTHER")))
			.andExpect(status().isCreated())
			.andReturn();
		UUID maria = guardianIdOf(child, "Maria Souza");

		// when
		perform(brunoBearer, putJson(maria, guardian("phone", "11955554444"))).andExpect(status().isOk());

		// then
		assertThat(author("guardians", "created_by", maria)).isEqualTo(anaId);
		assertThat(author("guardians", "updated_by", maria)).isEqualTo(brunoId);
		assertThat(jdbcTemplate.queryForObject("SELECT created_by FROM child_guardians WHERE guardian_id = ?",
				UUID.class, maria)).isEqualTo(anaId);
	}

	@Test
	@DisplayName("Autoria do responsável — Scenario: Respostas sem autoria")
	void shouldNotExposeAuthorsInResponses() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		getGuardian(maria).andExpect(jsonPath("$.createdBy").doesNotExist())
			.andExpect(jsonPath("$.updatedBy").doesNotExist());
		perform(get(GUARDIANS)).andExpect(jsonPath("$.content[0].createdBy").doesNotExist())
			.andExpect(jsonPath("$.content[0].updatedBy").doesNotExist());
	}

	// ---------- Acesso ao cadastro de responsáveis ----------

	@Test
	@DisplayName("Acesso ao cadastro de responsáveis — Scenario: EDUCADOR lista, consulta e altera")
	void shouldLetEducatorListReadAndUpdate() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		perform(anaBearer, get(GUARDIANS)).andExpect(status().isOk());
		perform(anaBearer, get(GUARDIANS + "/" + maria)).andExpect(status().isOk());
		perform(anaBearer, putJson(maria, guardian("name", "Maria Souza Lima"))).andExpect(status().isOk());
	}

	@Test
	@DisplayName("Acesso ao cadastro de responsáveis — Scenario: Sem token")
	void shouldRespondUnauthorizedWithoutToken() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		mockMvc.perform(get(GUARDIANS)).andExpect(status().isUnauthorized())
			.andExpect(content().contentType(PROBLEM_JSON));
		mockMvc.perform(putJson(maria, guardian("name", "Outro Nome"))).andExpect(status().isUnauthorized())
			.andExpect(content().contentType(PROBLEM_JSON));
		getGuardian(maria).andExpect(jsonPath("$.name").value("Maria Souza"));
	}

	// ---------- helpers ----------

	/**
	 * "A responsável Maria Souza": created with the valid data (plus the given fields) in the body of the child
	 * {@code Ana Souza}, as {@code MOTHER}.
	 */
	private UUID createMaria(Object... fieldsAndValues) throws Exception {
		return guardianIdOf(createChild("Ana Souza", newItem(guardianMap(fieldsAndValues), "MOTHER")), "Maria Souza");
	}

	/**
	 * One guardian per name, all in the body of the same child (at most 5).
	 */
	private void createGuardians(String... names) throws Exception {
		List<Map<String, Object>> items = new ArrayList<>();
		for (String name : names) {
			items.add(newItem(guardianMap("name", name), "OTHER"));
		}
		@SuppressWarnings("unchecked")
		Map<String, Object>[] array = items.toArray(Map[]::new);
		createChild("Criança", array);
	}

	@SafeVarargs
	private MvcResult createChild(String name, Map<String, Object>... items) throws Exception {
		return createChildWith(name, Map.of(), items);
	}

	@SafeVarargs
	private MvcResult createChildWith(String name, Map<String, Object> childFields, Map<String, Object>... items)
			throws Exception {
		return postChild(adminBearer, childBody(name, childFields, items)).andExpect(status().isCreated())
			.andReturn();
	}

	@SafeVarargs
	private String childBody(String name, Map<String, Object> childFields, Map<String, Object>... items) {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("name", name);
		fields.put("birthDate", "2018-05-10");
		fields.put("cep", "01310-100");
		fields.put("address", "Avenida Paulista");
		fields.put("neighborhood", "Bela Vista");
		fields.put("addressNumber", "1000");
		fields.put("city", "São Paulo");
		fields.put("state", "SP");
		fields.put("classroomId", turma1.toString());
		fields.putAll(childFields);
		fields.put("guardians", List.of(items));
		return JSON.writeValueAsString(fields);
	}

	private static Map<String, Object> newItem(Map<String, Object> guardian, String relationship) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("guardian", guardian);
		item.put("relationship", relationship);
		return item;
	}

	private static Map<String, Object> existingItem(UUID guardianId, String relationship) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("guardianId", guardianId.toString());
		item.put("relationship", relationship);
		return item;
	}

	/**
	 * "Os dados válidos", with the given fields replaced or added ({@link #ABSENT} leaves a field out).
	 */
	private static Map<String, Object> guardianMap(Object... fieldsAndValues) {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("name", "Maria Souza");
		fields.put("phone", "11912345678");
		fields.put("notWorking", false);
		fields.put("workplace", "Padaria Central");
		fields.put("workAddress", "Rua das Flores, 100");
		fields.put("workPhone", "1133334444");
		for (int i = 0; i < fieldsAndValues.length; i += 2) {
			String field = (String) fieldsAndValues[i];
			Object value = fieldsAndValues[i + 1];
			if (value == ABSENT) {
				fields.remove(field);
			}
			else {
				fields.put(field, value);
			}
		}
		return fields;
	}

	private static String guardian(Object... fieldsAndValues) {
		return JSON.writeValueAsString(guardianMap(fieldsAndValues));
	}

	/**
	 * Moves the application clock and logs in again: tokens last 8 hours.
	 */
	private void moveClockTo(Instant instant) throws Exception {
		clock.setInstant(instant);
		adminBearer = testUsers.bearer(ADMIN_LOGIN, PASSWORD);
	}

	private ResultActions postChild(String bearer, String body) throws Exception {
		return perform(bearer, post(CHILDREN).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions putGuardian(UUID id, String body) throws Exception {
		return perform(putJson(id, body));
	}

	private static MockHttpServletRequestBuilder putJson(UUID id, String body) {
		return put(GUARDIANS + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private ResultActions getGuardian(UUID id) throws Exception {
		return perform(get(GUARDIANS + "/" + id));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return perform(adminBearer, request);
	}

	private ResultActions perform(String bearer, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer));
	}

	private static void expectFieldError(ResultActions result, String field) throws Exception {
		result.andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[*].field").value(hasItem(field)));
	}

	private static UUID idOf(MvcResult result) throws Exception {
		return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
	}

	private static UUID guardianIdOf(MvcResult childResult, String guardianName) throws Exception {
		List<String> ids = JsonPath.read(childResult.getResponse().getContentAsString(),
				"$.guardians[?(@.name == '" + guardianName + "')].id");
		assertThat(ids).hasSize(1);
		return UUID.fromString(ids.get(0));
	}

	private UUID author(String table, String column, UUID id) {
		return jdbcTemplate.queryForObject("SELECT " + column + " FROM " + table + " WHERE id = ?", UUID.class, id);
	}

}
