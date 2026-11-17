package com.manuelaalecio.educare_backend.child;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * One test per Scenario of the {@code child} spec, end to end through the API with a real database. Users,
 * children and classes are recreated before every test, with every child in "Turma 1" unless the scenario says
 * otherwise; unless a scenario says otherwise, requests are made by a {@code COORDENADOR}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class, TestUsers.class})
class ChildScenarioTest {

	private static final String CHILDREN = "/api/v1/children";
	private static final String GUARDIANS = "/api/v1/guardians";
	private static final String PROBLEM_JSON = "application/problem+json";
	private static final String MISSING_ID = "0190f4a2-0000-7000-8000-000000000000";
	private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
	private static final String ADMIN_LOGIN = "admin@educare.org";
	private static final String ANA_LOGIN = "ana.souza@educare.org";
	private static final String BRUNO_LOGIN = "bruno.lima@educare.org";
	private static final String PASSWORD = "segredo123";
	private static final String M = "10453901552013100012021000012321";
	private static final String M_WITH_SPACES = "104539 01 55 2013 1 00012 021 0000123 21";
	private static final String M_WITH_DOTS = "104539.01.55.2013.1.00012.021.0000123-21";
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
		turma1 = createClassroom("Turma 1");
	}

	// ---------- Dados da criança ----------

	@Test
	@DisplayName("Dados da criança — Scenario: Criança criada só com os dados obrigatórios")
	void shouldCreateChildWithRequiredDataOnly() throws Exception {
		// when
		MvcResult result = postChild(body()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Maria Clara Souza"))
			.andExpect(jsonPath("$.birthDate").value("2018-05-10"))
			.andExpect(jsonPath("$.cep").value("01310100"))
			.andExpect(jsonPath("$.address").value("Avenida Paulista"))
			.andExpect(jsonPath("$.neighborhood").value("Bela Vista"))
			.andExpect(jsonPath("$.addressNumber").value("1000"))
			.andExpect(jsonPath("$.city").value("São Paulo"))
			.andExpect(jsonPath("$.state").value("SP"))
			.andExpect(jsonPath("$.createdAt").value("2026-03-01T10:00:00Z"))
			.andExpect(jsonPath("$.updatedAt").value("2026-03-01T10:00:00Z"))
			.andReturn();

		// then
		UUID id = idOf(result);
		assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(CHILDREN + "/" + id);
		for (String optional : new String[] {"addressComplement", "birthplace", "race", "gender", "cpf", "rg",
				"nationality", "maritalStatus", "religion", "church", "healthReport", "medication",
				"medicalObservation", "birthCertificateNumber"}) {
			assertThat(JsonPath.<Object>read(result.getResponse().getContentAsString(), "$." + optional))
				.as(optional)
				.isNull();
		}
	}

	@Test
	@DisplayName("Dados da criança — Scenario: Criança criada com todos os dados")
	void shouldCreateChildWithEveryField() throws Exception {
		// given
		String body = body("addressComplement", "Apto 12", "birthplace", "São Paulo - SP", "race", "BROWN",
				"gender", "FEMALE", "cpf", "529.982.247-25", "rg", "12.345.678-9", "nationality", "Brasileira",
				"maritalStatus", "SINGLE", "religion", "Católica", "church", "Paróquia São José", "healthReport",
				"Asma leve", "medication", "Salbutamol em crises", "medicalObservation", "Evitar poeira");

		// when
		UUID id = idOf(postChild(body).andExpect(status().isCreated()).andReturn());

		// then
		getChild(id).andExpect(status().isOk())
			.andExpect(jsonPath("$.addressComplement").value("Apto 12"))
			.andExpect(jsonPath("$.birthplace").value("São Paulo - SP"))
			.andExpect(jsonPath("$.race").value("BROWN"))
			.andExpect(jsonPath("$.gender").value("FEMALE"))
			.andExpect(jsonPath("$.cpf").value("52998224725"))
			.andExpect(jsonPath("$.rg").value("123456789"))
			.andExpect(jsonPath("$.nationality").value("Brasileira"))
			.andExpect(jsonPath("$.maritalStatus").value("SINGLE"))
			.andExpect(jsonPath("$.religion").value("Católica"))
			.andExpect(jsonPath("$.church").value("Paróquia São José"))
			.andExpect(jsonPath("$.healthReport").value("Asma leve"))
			.andExpect(jsonPath("$.medication").value("Salbutamol em crises"))
			.andExpect(jsonPath("$.medicalObservation").value("Evitar poeira"));
	}

	@Test
	@DisplayName("Dados da criança — Scenario: Textos com espaços nas pontas e opcional em branco")
	void shouldStripTextsAndClearBlankOptional() throws Exception {
		postChild(body("name", "  Maria Clara Souza  ", "religion", "   ")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Maria Clara Souza"))
			.andExpect(jsonPath("$.religion").isEmpty());
	}

	@Test
	@DisplayName("Dados da criança — Scenario: Campos obrigatórios ausentes")
	void shouldListEveryMissingRequiredField() throws Exception {
		postChild("{}").andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "birthDate", "cep", "address",
					"neighborhood", "addressNumber", "city", "state", "classroomId", "guardians")));
		assertThat(countChildren()).isZero();
	}

	@Test
	@DisplayName("Dados da criança — Scenario: Criança sem complemento")
	void shouldCreateChildWithoutComplement() throws Exception {
		postChild(body("addressComplement", "   ")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.addressComplement").isEmpty());
	}

	@Test
	@DisplayName("Dados da criança — Scenario: Textos longos demais")
	void shouldRejectTextsLongerThanMax() throws Exception {
		postChild(body("name", "a".repeat(151), "medicalObservation", "a".repeat(2001)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "medicalObservation")));
		assertThat(countChildren()).isZero();
	}

	@Test
	@DisplayName("Dados da criança — Scenario: Texto no limite do tamanho")
	void shouldAcceptTextWithMaxLength() throws Exception {
		postChild(body("name", "a".repeat(150))).andExpect(status().isCreated());
	}

	// ---------- Data de nascimento ----------

	@Test
	@DisplayName("Data de nascimento — Scenario: Nascimento hoje")
	void shouldAcceptBirthDateToday() throws Exception {
		postChild(body("birthDate", "2026-03-01")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.birthDate").value("2026-03-01"));
	}

	@Test
	@DisplayName("Data de nascimento — Scenario: Nascimento no futuro")
	void shouldRejectBirthDateInFuture() throws Exception {
		expectFieldError(postChild(body("birthDate", "2026-03-02")), "birthDate");
		assertThat(countChildren()).isZero();
	}

	@Test
	@DisplayName("Data de nascimento — Scenario: Data de hoje no Brasil, já amanhã em UTC")
	void shouldUseSaoPauloDateWhenUtcIsAlreadyTomorrow() throws Exception {
		// given
		moveClockTo(Instant.parse("2026-03-02T01:00:00Z"));

		// when / then
		expectFieldError(postChild(body("birthDate", "2026-03-02")), "birthDate");
	}

	@Test
	@DisplayName("Data de nascimento — Scenario: Data inexistente")
	void shouldRejectNonexistentBirthDate() throws Exception {
		expectFieldError(postChild(body("birthDate", "2018-02-30")), "birthDate");
		assertThat(countChildren()).isZero();
	}

	@Test
	@DisplayName("Data de nascimento — Scenario: Data em outro formato")
	void shouldRejectBirthDateInAnotherFormat() throws Exception {
		expectFieldError(postChild(body("birthDate", "10/05/2018")), "birthDate");
		assertThat(countChildren()).isZero();
	}

	// ---------- CEP ----------

	@Test
	@DisplayName("CEP — Scenario: CEP sem hífen")
	void shouldAcceptCepWithoutHyphen() throws Exception {
		postChild(body("cep", "01310100")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.cep").value("01310100"));
	}

	@Test
	@DisplayName("CEP — Scenario: CEP com dígitos a menos")
	void shouldRejectCepWithFewerDigits() throws Exception {
		expectFieldError(postChild(body("cep", "01310-10")), "cep");
		assertThat(countChildren()).isZero();
	}

	@Test
	@DisplayName("CEP — Scenario: CEP com letras")
	void shouldRejectCepWithLetters() throws Exception {
		expectFieldError(postChild(body("cep", "0131A-100")), "cep");
	}

	// ---------- Estado (UF) ----------

	@Test
	@DisplayName("Estado (UF) — Scenario: UF em minúsculas é gravada em maiúsculas")
	void shouldUpperCaseState() throws Exception {
		postChild(body("state", "sp")).andExpect(status().isCreated()).andExpect(jsonPath("$.state").value("SP"));
	}

	@Test
	@DisplayName("Estado (UF) — Scenario: Sigla inexistente")
	void shouldRejectUnknownState() throws Exception {
		expectFieldError(postChild(body("state", "XX")), "state");
		assertThat(countChildren()).isZero();
	}

	@Test
	@DisplayName("Estado (UF) — Scenario: Estado por extenso")
	void shouldRejectStateFullName() throws Exception {
		expectFieldError(postChild(body("state", "São Paulo")), "state");
		assertThat(countChildren()).isZero();
	}

	// ---------- Listas fixas de raça, gênero e estado civil ----------

	@Test
	@DisplayName("Listas fixas de raça, gênero e estado civil — Scenario: Valores válidos")
	void shouldAcceptFixedListValues() throws Exception {
		postChild(body("race", "INDIGENOUS", "gender", "OTHER", "maritalStatus", "STABLE_UNION"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.race").value("INDIGENOUS"))
			.andExpect(jsonPath("$.gender").value("OTHER"))
			.andExpect(jsonPath("$.maritalStatus").value("STABLE_UNION"));
	}

	@Test
	@DisplayName("Listas fixas de raça, gênero e estado civil — Scenario: Valores fora das listas")
	void shouldRejectValuesOutsideFixedLists() throws Exception {
		postChild(body("race", "brown", "gender", "F", "maritalStatus", "DATING")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("race", "gender", "maritalStatus")));
		assertThat(countChildren()).isZero();
	}

	// ---------- CPF válido e único ----------

	@Test
	@DisplayName("CPF válido e único — Scenario: CPF com máscara é gravado só com dígitos")
	void shouldStoreCpfWithDigitsOnly() throws Exception {
		postChild(body("cpf", "529.982.247-25")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.cpf").value("52998224725"));
	}

	@Test
	@DisplayName("CPF válido e único — Scenario: Dígito verificador errado")
	void shouldRejectCpfWithWrongCheckDigit() throws Exception {
		expectFieldError(postChild(body("cpf", "529.982.247-24")), "cpf");
		assertThat(countChildren()).isZero();
	}

	@Test
	@DisplayName("CPF válido e único — Scenario: Dígitos repetidos")
	void shouldRejectCpfWithRepeatedDigits() throws Exception {
		expectFieldError(postChild(body("cpf", "11111111111")), "cpf");
	}

	@Test
	@DisplayName("CPF válido e único — Scenario: Criação com CPF já usado")
	void shouldRejectCreationWithCpfInUse() throws Exception {
		// given
		createChild("cpf", "52998224725");

		// when / then
		postChild(body("name", "Outra", "cpf", "529.982.247-25")).andExpect(status().isConflict())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(content().string(not(containsString("52998224725"))))
			.andExpect(content().string(not(containsString("529.982.247-25"))));
		assertThat(countWhere("cpf = '52998224725'")).isEqualTo(1);
	}

	@Test
	@DisplayName("CPF válido e único — Scenario: Alteração para CPF de outra criança")
	void shouldRejectUpdateToCpfOfAnotherChild() throws Exception {
		// given
		createChild("cpf", "52998224725");
		UUID second = createChild("name", "Segunda", "cpf", "11144477735");

		// when / then
		putChild(second, body("name", "Segunda", "cpf", "52998224725")).andExpect(status().isConflict());
		getChild(second).andExpect(jsonPath("$.cpf").value("11144477735"));
	}

	@Test
	@DisplayName("CPF válido e único — Scenario: Várias crianças sem CPF")
	void shouldAcceptManyChildrenWithoutCpf() throws Exception {
		createChild();
		postChild(body("name", "Outra")).andExpect(status().isCreated());
	}

	// ---------- RG único ----------

	@Test
	@DisplayName("RG único — Scenario: RG gravado só com letras e dígitos")
	void shouldStoreRgWithLettersAndDigitsOnly() throws Exception {
		postChild(body("rg", "12.345.678-x")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.rg").value("12345678X"));
	}

	@Test
	@DisplayName("RG único — Scenario: Criação com RG já usado em outra pontuação")
	void shouldRejectCreationWithRgInUse() throws Exception {
		// given
		createChild("rg", "12345678X");

		// when / then
		postChild(body("name", "Outra", "rg", "12345678-X")).andExpect(status().isConflict())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(content().string(not(containsString("12345678X"))))
			.andExpect(content().string(not(containsString("12345678-X"))));
		assertThat(countWhere("rg = '12345678X'")).isEqualTo(1);
	}

	@Test
	@DisplayName("RG único — Scenario: Alteração para RG de outra criança")
	void shouldRejectUpdateToRgOfAnotherChild() throws Exception {
		// given
		createChild("rg", "12345678X");
		UUID second = createChild("name", "Segunda", "rg", "987654321");

		// when / then
		putChild(second, body("name", "Segunda", "rg", "12.345.678-X")).andExpect(status().isConflict());
		getChild(second).andExpect(jsonPath("$.rg").value("987654321"));
	}

	@Test
	@DisplayName("RG único — Scenario: Várias crianças sem RG")
	void shouldAcceptManyChildrenWithoutRg() throws Exception {
		createChild();
		postChild(body("name", "Outra")).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("RG único — Scenario: RG sem letras nem dígitos")
	void shouldRejectRgWithoutLettersNorDigits() throws Exception {
		expectFieldError(postChild(body("rg", "...")), "rg");
		assertThat(countChildren()).isZero();
	}

	// ---------- Certidão de nascimento ----------

	@Test
	@DisplayName("Certidão de nascimento — Scenario: Matrícula com separadores é gravada só com dígitos")
	void shouldStoreBirthCertificateNumberWithDigitsOnly() throws Exception {
		UUID id = idOf(postChild(body("birthCertificateNumber", M_WITH_DOTS)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.birthCertificateNumber").value(M))
			.andReturn());
		getChild(id).andExpect(jsonPath("$.birthCertificateNumber").value(M));
	}

	@Test
	@DisplayName("Certidão de nascimento — Scenario: Matrícula com espaços entre os grupos")
	void shouldAcceptBirthCertificateNumberWithSpaces() throws Exception {
		postChild(body("birthCertificateNumber", M_WITH_SPACES)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.birthCertificateNumber").value(M));
	}

	@Test
	@DisplayName("Certidão de nascimento — Scenario: Matrícula com dígitos a menos ou a mais")
	void shouldRejectBirthCertificateNumberWithWrongLength() throws Exception {
		expectFieldError(postChild(body("birthCertificateNumber", M.substring(0, 31))), "birthCertificateNumber");
		expectFieldError(postChild(body("birthCertificateNumber", M + "0")), "birthCertificateNumber");
		assertThat(countChildren()).isZero();
	}

	@Test
	@DisplayName("Certidão de nascimento — Scenario: Matrícula com letras")
	void shouldRejectBirthCertificateNumberWithLetters() throws Exception {
		expectFieldError(postChild(body("birthCertificateNumber", M.substring(0, 31) + "X")),
				"birthCertificateNumber");
		assertThat(countChildren()).isZero();
	}

	@Test
	@DisplayName("Certidão de nascimento — Scenario: Matrícula em branco")
	void shouldClearBlankBirthCertificateNumber() throws Exception {
		postChild(body("birthCertificateNumber", "   ")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.birthCertificateNumber").isEmpty());
	}

	@Test
	@DisplayName("Certidão de nascimento — Scenario: Matrícula de outra criança")
	void shouldRejectBirthCertificateNumberOfAnotherChild() throws Exception {
		// given
		createChild("birthCertificateNumber", M);
		UUID third = createChild("name", "Terceira");

		// when
		String created = postChild(body("name", "Outra", "birthCertificateNumber", M_WITH_SPACES))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andReturn().getResponse().getContentAsString();
		String updated = putChild(third, body("name", "Terceira", "birthCertificateNumber", M_WITH_SPACES))
			.andExpect(status().isConflict())
			.andReturn().getResponse().getContentAsString();

		// then
		for (int i = 0; i + 5 <= M.length(); i++) {
			assertThat(created).doesNotContain(M.substring(i, i + 5));
			assertThat(updated).doesNotContain(M.substring(i, i + 5));
		}
		assertThat(countWhere("birth_certificate_number = '" + M + "'")).isEqualTo(1);
	}

	@Test
	@DisplayName("Certidão de nascimento — Scenario: Várias crianças sem matrícula")
	void shouldAcceptManyChildrenWithoutBirthCertificateNumber() throws Exception {
		createChild();
		postChild(body("name", "Outra")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.birthCertificateNumber").isEmpty());
	}

	@Test
	@DisplayName("Certidão de nascimento — Scenario: Alteração mantendo a própria matrícula")
	void shouldKeepOwnBirthCertificateNumberWhenUpdating() throws Exception {
		// given
		UUID id = createChild("birthCertificateNumber", M);

		// when / then
		putChild(id, body("birthCertificateNumber", M_WITH_DOTS)).andExpect(status().isOk())
			.andExpect(jsonPath("$.birthCertificateNumber").value(M));
	}

	@Test
	@DisplayName("Certidão de nascimento — Scenario: Alteração sem matrícula apaga a matrícula")
	void shouldClearBirthCertificateNumberWhenUpdateOmitsIt() throws Exception {
		// given
		UUID id = createChild("birthCertificateNumber", M);

		// when
		putChild(id, body()).andExpect(status().isOk()).andExpect(jsonPath("$.birthCertificateNumber").isEmpty());

		// then
		postChild(body("name", "Outra", "birthCertificateNumber", M)).andExpect(status().isCreated());
	}

	// ---------- Consultar criança por id ----------

	@Test
	@DisplayName("Consultar criança por id — Scenario: Criança existente")
	void shouldReturnExistingChild() throws Exception {
		// given
		UUID id = createChild("cpf", "52998224725");

		// when / then
		getChild(id).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id.toString()))
			.andExpect(jsonPath("$.name").value("Maria Clara Souza"))
			.andExpect(jsonPath("$.cpf").value("52998224725"))
			.andExpect(jsonPath("$.createdAt").value("2026-03-01T10:00:00Z"))
			.andExpect(jsonPath("$.updatedAt").value("2026-03-01T10:00:00Z"));
	}

	@Test
	@DisplayName("Consultar criança por id — Scenario: Criança inexistente")
	void shouldRespondNotFoundForMissingChild() throws Exception {
		perform(get(CHILDREN + "/" + MISSING_ID)).andExpect(status().isNotFound())
			.andExpect(content().contentType(PROBLEM_JSON));
	}

	@Test
	@DisplayName("Consultar criança por id — Scenario: Id malformado")
	void shouldRespondBadRequestForMalformedId() throws Exception {
		perform(get(CHILDREN + "/abc")).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON));
	}

	// ---------- Listar crianças com paginação e busca por nome ----------

	@Test
	@DisplayName("Listar crianças com paginação e busca por nome — Scenario: Primeira página ordenada por nome")
	void shouldListFirstPageSortedByName() throws Exception {
		// given
		createChildren("Carla", "Ana", "Bruno", "Davi");

		// when / then
		perform(get(CHILDREN).param("size", "2")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].name").value(contains("Ana", "Bruno")))
			.andExpect(jsonPath("$.page.size").value(2))
			.andExpect(jsonPath("$.page.number").value(0))
			.andExpect(jsonPath("$.page.totalElements").value(4))
			.andExpect(jsonPath("$.page.totalPages").value(2));
	}

	@Test
	@DisplayName("Listar crianças com paginação e busca por nome — Scenario: Itens da listagem sem dados sensíveis")
	void shouldListOnlySummaryFields() throws Exception {
		// given
		createChild("cpf", "52998224725", "rg", "123456789", "birthCertificateNumber", M, "healthReport", "Asma");

		// when / then
		perform(get(CHILDREN)).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].length()").value(6))
			.andExpect(jsonPath("$.content[0].classroom.length()").value(2))
			.andExpect(jsonPath("$.content[0].classroom.id").value(turma1.toString()))
			.andExpect(jsonPath("$.content[0].classroom.name").value("Turma 1"))
			.andExpect(jsonPath("$.content[0].id").exists())
			.andExpect(jsonPath("$.content[0].name").value("Maria Clara Souza"))
			.andExpect(jsonPath("$.content[0].birthDate").value("2018-05-10"))
			.andExpect(jsonPath("$.content[0].createdAt").exists())
			.andExpect(jsonPath("$.content[0].updatedAt").exists());
	}

	@Test
	@DisplayName("Listar crianças com paginação e busca por nome — Scenario: Ordenação decrescente por data de nascimento")
	void shouldSortByBirthDateDescending() throws Exception {
		// given
		createChild("name", "Ana", "birthDate", "2019-01-01");
		createChild("name", "Bruno", "birthDate", "2017-06-15");
		createChild("name", "Carla", "birthDate", "2020-03-20");

		// when / then
		perform(get(CHILDREN).param("sort", "birthDate,desc")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].name").value(contains("Carla", "Ana", "Bruno")));
	}

	@Test
	@DisplayName("Listar crianças com paginação e busca por nome — Scenario: Busca sem acento e sem maiúsculas")
	void shouldSearchIgnoringAccentsAndCase() throws Exception {
		// given
		createChildren("João Pedro", "Joana Lima", "Maria João");

		// when / then
		perform(get(CHILDREN).param("name", "joao")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].name").value(contains("João Pedro", "Maria João")))
			.andExpect(jsonPath("$.page.totalElements").value(2));
	}

	@Test
	@DisplayName("Listar crianças com paginação e busca por nome — Scenario: Busca sem resultado")
	void shouldReturnEmptyPageWhenSearchMatchesNothing() throws Exception {
		// given
		createChildren("Ana", "Bruno");

		// when / then
		perform(get(CHILDREN).param("name", "zz")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content").value(empty()))
			.andExpect(jsonPath("$.page.totalElements").value(0));
	}

	@Test
	@DisplayName("Listar crianças com paginação e busca por nome — Scenario: Caracteres curinga na busca")
	void shouldTreatWildcardsAsLiterals() throws Exception {
		// given
		createChildren("Ana", "Bruno");

		// when / then
		perform(get(CHILDREN).param("name", "%")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content").value(empty()));
	}

	@Test
	@DisplayName("Listar crianças com paginação e busca por nome — Scenario: Busca em branco é ignorada")
	void shouldIgnoreBlankSearch() throws Exception {
		// given
		createChildren("Ana", "Bruno");

		// when / then
		perform(get(CHILDREN).param("name", "  ")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].name").value(contains("Ana", "Bruno")));
	}

	@Test
	@DisplayName("Listar crianças com paginação e busca por nome — Scenario: Tamanho de página acima do máximo")
	void shouldCapPageSize() throws Exception {
		// given
		createChildren("Ana", "Bruno", "Carla");

		// when / then
		perform(get(CHILDREN).param("size", "500")).andExpect(status().isOk())
			.andExpect(jsonPath("$.page.size").value(100))
			.andExpect(jsonPath("$.content.length()").value(3));
	}

	@Test
	@DisplayName("Listar crianças com paginação e busca por nome — Scenario: Ordenação por propriedade não permitida")
	void shouldRejectSortByNotAllowedProperty() throws Exception {
		perform(get(CHILDREN).param("sort", "cpf")).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON));
	}

	// ---------- Alterar criança ----------

	@Test
	@DisplayName("Alterar criança — Scenario: Alteração com dados válidos")
	void shouldUpdateChildWithValidData() throws Exception {
		// given
		UUID id = createChild();
		moveClockTo(Instant.parse("2026-03-02T09:00:00Z"));

		// when / then
		putChild(id, body("neighborhood", "Consolação", "addressNumber", "250")).andExpect(status().isOk())
			.andExpect(jsonPath("$.neighborhood").value("Consolação"))
			.andExpect(jsonPath("$.addressNumber").value("250"))
			.andExpect(jsonPath("$.createdAt").value("2026-03-01T10:00:00Z"))
			.andExpect(jsonPath("$.updatedAt").value("2026-03-02T09:00:00Z"));
	}

	@Test
	@DisplayName("Alterar criança — Scenario: Opcional omitido é apagado")
	void shouldClearOmittedOptional() throws Exception {
		// given
		UUID id = createChild("medication", "Salbutamol em crises");

		// when / then
		putChild(id, body()).andExpect(status().isOk()).andExpect(jsonPath("$.medication").isEmpty());
		getChild(id).andExpect(jsonPath("$.medication").isEmpty());
	}

	@Test
	@DisplayName("Alterar criança — Scenario: Manter o próprio CPF")
	void shouldKeepOwnCpf() throws Exception {
		// given
		UUID id = createChild("cpf", "52998224725");

		// when / then
		putChild(id, body("cpf", "529.982.247-25")).andExpect(status().isOk())
			.andExpect(jsonPath("$.cpf").value("52998224725"));
	}

	@Test
	@DisplayName("Alterar criança — Scenario: Alteração com dados inválidos")
	void shouldRejectInvalidUpdate() throws Exception {
		// given
		UUID id = createChild();

		// when / then
		putChild(id, body("name", "", "cep", "123")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "cep")));
		getChild(id).andExpect(jsonPath("$.name").value("Maria Clara Souza"))
			.andExpect(jsonPath("$.cep").value("01310100"));
	}

	@Test
	@DisplayName("Alterar criança — Scenario: Alteração de criança inexistente")
	void shouldRespondNotFoundWhenUpdatingMissingChild() throws Exception {
		putChild(UUID.fromString(MISSING_ID), body()).andExpect(status().isNotFound())
			.andExpect(content().contentType(PROBLEM_JSON));
		assertThat(countChildren()).isZero();
	}

	// ---------- Excluir criança ----------

	@Test
	@DisplayName("Excluir criança — Scenario: Criança excluída")
	void shouldDeleteChild() throws Exception {
		// given
		UUID id = createChild();

		// when
		perform(delete(CHILDREN + "/" + id)).andExpect(status().isNoContent());

		// then
		getChild(id).andExpect(status().isNotFound());
		perform(get(CHILDREN)).andExpect(jsonPath("$.content[*].id").value(not(hasItem(id.toString()))));
	}

	@Test
	@DisplayName("Excluir criança — Scenario: CPF e matrícula liberados após exclusão")
	void shouldFreeDocumentsAfterDeletion() throws Exception {
		// given
		UUID id = createChild("cpf", "52998224725", "birthCertificateNumber", M);
		perform(delete(CHILDREN + "/" + id)).andExpect(status().isNoContent());

		// when / then
		postChild(body("cpf", "52998224725", "birthCertificateNumber", M)).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("Excluir criança — Scenario: Exclusão de criança inexistente")
	void shouldRespondNotFoundWhenDeletingMissingChild() throws Exception {
		perform(delete(CHILDREN + "/" + MISSING_ID)).andExpect(status().isNotFound())
			.andExpect(content().contentType(PROBLEM_JSON));
	}

	// ---------- Corpo de requisição ilegível ----------

	@Test
	@DisplayName("Corpo de requisição ilegível — Scenario: JSON inválido na criação")
	void shouldRejectBrokenJson() throws Exception {
		postChild("{\"name\": \"Maria\"").andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON));
		assertThat(countChildren()).isZero();
	}

	@Test
	@DisplayName("Corpo de requisição ilegível — Scenario: JSON válido é aceito")
	void shouldAcceptWellFormedJson() throws Exception {
		postChild(body()).andExpect(status().isCreated());
	}

	// ---------- Autoria da criança ----------

	@Test
	@DisplayName("Autoria da criança — Scenario: Autoria na criação e na alteração")
	void shouldRecordAuthorsOnCreationAndUpdate() throws Exception {
		// given
		UUID id = idOf(postChild(anaBearer, body()).andExpect(status().isCreated()).andReturn());

		// when
		perform(brunoBearer, putJson(id, body("neighborhood", "Consolação"))).andExpect(status().isOk());

		// then
		assertThat(author("created_by", id)).isEqualTo(anaId);
		assertThat(author("updated_by", id)).isEqualTo(brunoId);
	}

	@Test
	@DisplayName("Autoria da criança — Scenario: Alteração recusada não muda a autoria")
	void shouldKeepAuthorsWhenUpdateIsRejected() throws Exception {
		// given
		UUID id = idOf(postChild(anaBearer, body()).andExpect(status().isCreated()).andReturn());

		// when
		perform(brunoBearer, putJson(id, body("cep", "123"))).andExpect(status().isBadRequest());

		// then
		assertThat(author("updated_by", id)).isEqualTo(anaId);
	}

	@Test
	@DisplayName("Autoria da criança — Scenario: Respostas sem autoria")
	void shouldNotExposeAuthors() throws Exception {
		// when
		MvcResult created = postChild(body()).andExpect(status().isCreated()).andReturn();
		UUID id = idOf(created);

		// then
		for (String response : new String[] {created.getResponse().getContentAsString(),
				getChild(id).andReturn().getResponse().getContentAsString(),
				perform(get(CHILDREN)).andReturn().getResponse().getContentAsString()}) {
			assertThat(response).doesNotContain("createdBy", "updatedBy", anaId.toString(), brunoId.toString());
		}
	}

	// ---------- Acesso ao cadastro de crianças ----------

	@Test
	@DisplayName("Acesso ao cadastro de crianças — Scenario: EDUCADOR cria e altera criança")
	void shouldLetEducatorCreateAndUpdateChild() throws Exception {
		UUID id = idOf(postChild(anaBearer, body()).andExpect(status().isCreated()).andReturn());
		perform(anaBearer, putJson(id, body("neighborhood", "Consolação"))).andExpect(status().isOk());
	}

	@Test
	@DisplayName("Acesso ao cadastro de crianças — Scenario: EDUCADOR não exclui criança")
	void shouldForbidEducatorToDeleteChild() throws Exception {
		// given
		UUID id = createChild();

		// when / then
		perform(anaBearer, delete(CHILDREN + "/" + id)).andExpect(status().isForbidden())
			.andExpect(content().contentType(PROBLEM_JSON));
		getChild(id).andExpect(status().isOk());
	}

	@Test
	@DisplayName("Acesso ao cadastro de crianças — Scenario: Listagem sem token")
	void shouldRejectListingWithoutToken() throws Exception {
		mockMvc.perform(get(CHILDREN)).andExpect(status().isUnauthorized())
			.andExpect(content().contentType(PROBLEM_JSON));
	}

	@Test
	@DisplayName("Acesso ao cadastro de crianças — Scenario: Criação sem token")
	void shouldRejectCreationWithoutToken() throws Exception {
		mockMvc.perform(post(CHILDREN).contentType(MediaType.APPLICATION_JSON).content(body()))
			.andExpect(status().isUnauthorized());
		assertThat(countChildren()).isZero();
	}

	// ---------- Turma obrigatória da criança ----------

	@Test
	@DisplayName("Turma obrigatória da criança — Scenario: Criança criada com turma")
	void shouldCreateChildInClassroom() throws Exception {
		// given
		moveClockTo(Instant.parse("2026-10-01T13:00:00Z"));

		// when
		UUID id = idOf(postChild(body()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.classroom.id").value(turma1.toString()))
			.andExpect(jsonPath("$.classroom.name").value("Turma 1"))
			.andReturn());

		// then
		history(id).andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].classroomId").value(turma1.toString()))
			.andExpect(jsonPath("$[0].classroomName").value("Turma 1"))
			.andExpect(jsonPath("$[0].startDate").value("2026-10-01"))
			.andExpect(jsonPath("$[0].endDate").isEmpty());
	}

	@Test
	@DisplayName("Turma obrigatória da criança — Scenario: Criação sem turma")
	void shouldRejectChildWithoutClassroom() throws Exception {
		expectFieldError(postChild(body("classroomId", ABSENT)), "classroomId");
		assertThat(countChildren()).isZero();
	}

	@Test
	@DisplayName("Turma obrigatória da criança — Scenario: Criação sem turma e com outro campo inválido")
	void shouldListClassroomWithOtherInvalidFields() throws Exception {
		postChild(body("classroomId", ABSENT, "cep", "123")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("classroomId", "cep")));
	}

	@Test
	@DisplayName("Turma obrigatória da criança — Scenario: Criação com turma inexistente")
	void shouldRejectChildInMissingClassroom() throws Exception {
		postChild(body("classroomId", MISSING_ID)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("classroomId"))
			.andExpect(jsonPath("$.errors[0].message").value("Turma não encontrada"));
		assertThat(countChildren()).isZero();
		assertThat(countLinks()).isZero();
	}

	@Test
	@DisplayName("Turma obrigatória da criança — Scenario: Criação com classroomId malformado")
	void shouldRejectMalformedClassroomId() throws Exception {
		expectFieldError(postChild(body("classroomId", "turma-1")), "classroomId");
		assertThat(countChildren()).isZero();
	}

	@Test
	@DisplayName("Turma obrigatória da criança — Scenario: Alteração sem turma")
	void shouldRejectUpdateWithoutClassroom() throws Exception {
		// given
		UUID id = createChild();

		// when / then
		expectFieldError(putChild(id, body("name", "Maria Clara Lima", "classroomId", ABSENT)), "classroomId");
		getChild(id).andExpect(jsonPath("$.name").value("Maria Clara Souza"))
			.andExpect(jsonPath("$.classroom.name").value("Turma 1"));
		history(id).andExpect(jsonPath("$.length()").value(1));
	}

	// ---------- Troca de turma com histórico ----------

	@Test
	@DisplayName("Troca de turma com histórico — Scenario: Troca para outra turma")
	void shouldMoveChildToAnotherClassroom() throws Exception {
		// given
		UUID turma2 = createClassroom("Turma 2");
		moveClockTo(Instant.parse("2026-10-01T13:00:00Z"));
		UUID id = createChild();
		moveClockTo(Instant.parse("2026-11-01T13:00:00Z"));

		// when / then
		putChild(id, body("classroomId", turma2.toString())).andExpect(status().isOk())
			.andExpect(jsonPath("$.classroom.id").value(turma2.toString()))
			.andExpect(jsonPath("$.classroom.name").value("Turma 2"));
		history(id).andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].classroomName").value("Turma 2"))
			.andExpect(jsonPath("$[0].startDate").value("2026-11-01"))
			.andExpect(jsonPath("$[0].endDate").isEmpty())
			.andExpect(jsonPath("$[1].classroomName").value("Turma 1"))
			.andExpect(jsonPath("$[1].startDate").value("2026-10-01"))
			.andExpect(jsonPath("$[1].endDate").value("2026-11-01"));
	}

	@Test
	@DisplayName("Troca de turma com histórico — Scenario: Alteração mantendo a turma")
	void shouldKeepLinkWhenClassroomIsTheSame() throws Exception {
		// given
		moveClockTo(Instant.parse("2026-10-01T13:00:00Z"));
		UUID id = createChild();
		moveClockTo(Instant.parse("2026-11-01T13:00:00Z"));

		// when / then
		putChild(id, body("name", "Maria Clara Lima")).andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Maria Clara Lima"))
			.andExpect(jsonPath("$.classroom.name").value("Turma 1"));
		history(id).andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].startDate").value("2026-10-01"))
			.andExpect(jsonPath("$[0].endDate").isEmpty());
	}

	@Test
	@DisplayName("Troca de turma com histórico — Scenario: Duas trocas no mesmo dia")
	void shouldEndSameDayLinkWhenMovingTwiceOnSameDay() throws Exception {
		// given
		UUID turma2 = createClassroom("Turma 2");
		UUID turma3 = createClassroom("Turma 3");
		moveClockTo(Instant.parse("2026-10-01T13:00:00Z"));
		UUID id = createChild();
		moveClockTo(Instant.parse("2026-11-01T13:00:00Z"));

		// when
		putChild(id, body("classroomId", turma2.toString())).andExpect(status().isOk());
		moveClockTo(Instant.parse("2026-11-01T18:00:00Z"));
		putChild(id, body("classroomId", turma3.toString())).andExpect(status().isOk());

		// then
		getChild(id).andExpect(jsonPath("$.classroom.name").value("Turma 3"));
		history(id).andExpect(jsonPath("$[*].classroomName").value(contains("Turma 3", "Turma 2", "Turma 1")))
			.andExpect(jsonPath("$[0].startDate").value("2026-11-01"))
			.andExpect(jsonPath("$[0].endDate").isEmpty())
			.andExpect(jsonPath("$[1].startDate").value("2026-11-01"))
			.andExpect(jsonPath("$[1].endDate").value("2026-11-01"))
			.andExpect(jsonPath("$[2].startDate").value("2026-10-01"))
			.andExpect(jsonPath("$[2].endDate").value("2026-11-01"));
	}

	@Test
	@DisplayName("Troca de turma com histórico — Scenario: Troca para turma inexistente")
	void shouldKeepClassroomWhenMovingToMissingClassroom() throws Exception {
		// given
		UUID id = createChild();

		// when / then
		putChild(id, body("classroomId", MISSING_ID)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("classroomId"))
			.andExpect(jsonPath("$.errors[0].message").value("Turma não encontrada"));
		getChild(id).andExpect(jsonPath("$.classroom.name").value("Turma 1"));
		history(id).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	@DisplayName("Troca de turma com histórico — Scenario: Troca recusada por conflito")
	void shouldKeepClassroomWhenMoveIsRejectedByConflict() throws Exception {
		// given
		UUID turma2 = createClassroom("Turma 2");
		UUID id = createChild();
		createChild("name", "Outra", "cpf", "52998224725");

		// when / then
		putChild(id, body("classroomId", turma2.toString(), "cpf", "52998224725")).andExpect(status().isConflict());
		getChild(id).andExpect(jsonPath("$.classroom.name").value("Turma 1"));
		history(id).andExpect(jsonPath("$.length()").value(1));
	}

	// ---------- Turma atual nas respostas de criança ----------

	@Test
	@DisplayName("Turma atual nas respostas de criança — Scenario: Consulta por id com a turma")
	void shouldReturnCurrentClassroomWhenReadingChild() throws Exception {
		// given
		UUID id = createChild();

		// when / then
		getChild(id).andExpect(status().isOk())
			.andExpect(jsonPath("$.classroom.id").value(turma1.toString()))
			.andExpect(jsonPath("$.classroom.name").value("Turma 1"))
			.andExpect(jsonPath("$.name").value("Maria Clara Souza"));
	}

	@Test
	@DisplayName("Turma atual nas respostas de criança — Scenario: Turma renomeada")
	void shouldReturnNewClassroomNameAfterRename() throws Exception {
		// given
		UUID id = createChild();
		perform(put("/api/v1/classrooms/" + turma1).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"Maternal A\"}")).andExpect(status().isOk());

		// when / then
		getChild(id).andExpect(jsonPath("$.classroom.id").value(turma1.toString()))
			.andExpect(jsonPath("$.classroom.name").value("Maternal A"));
	}

	// ---------- Histórico de turmas da criança ----------

	@Test
	@DisplayName("Histórico de turmas da criança — Scenario: Histórico de uma criança que nunca trocou de turma")
	void shouldReturnSingleLinkWhenChildNeverMoved() throws Exception {
		// given
		moveClockTo(Instant.parse("2026-10-01T13:00:00Z"));
		UUID id = createChild();
		anaBearer = testUsers.bearer(ANA_LOGIN, PASSWORD);

		// when / then
		perform(anaBearer, get(CHILDREN + "/" + id + "/classrooms")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].classroomId").value(turma1.toString()))
			.andExpect(jsonPath("$[0].classroomName").value("Turma 1"))
			.andExpect(jsonPath("$[0].startDate").value("2026-10-01"))
			.andExpect(jsonPath("$[0].endDate").isEmpty());
	}

	@Test
	@DisplayName("Histórico de turmas da criança — Scenario: Histórico de criança inexistente")
	void shouldRespondNotFoundForHistoryOfMissingChild() throws Exception {
		perform(get(CHILDREN + "/" + MISSING_ID + "/classrooms")).andExpect(status().isNotFound())
			.andExpect(content().contentType(PROBLEM_JSON));
	}

	@Test
	@DisplayName("Histórico de turmas da criança — Scenario: Histórico com id malformado")
	void shouldRespondBadRequestForHistoryWithMalformedId() throws Exception {
		perform(get(CHILDREN + "/abc/classrooms")).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON));
	}

	@Test
	@DisplayName("Histórico de turmas da criança — Scenario: Histórico sem token")
	void shouldRejectHistoryWithoutToken() throws Exception {
		// given
		UUID id = createChild();

		// when / then
		mockMvc.perform(get(CHILDREN + "/" + id + "/classrooms")).andExpect(status().isUnauthorized())
			.andExpect(content().contentType(PROBLEM_JSON));
	}

	// ---------- Exclusão da criança apaga o histórico de turmas ----------

	@Test
	@DisplayName("Exclusão da criança apaga o histórico de turmas — Scenario: Criança com dois vínculos excluída")
	void shouldDeleteLinksWithChild() throws Exception {
		// given
		UUID turma2 = createClassroom("Turma 2");
		UUID id = createChild();
		putChild(id, body("classroomId", turma2.toString())).andExpect(status().isOk());

		// when
		perform(delete(CHILDREN + "/" + id)).andExpect(status().isNoContent());

		// then
		history(id).andExpect(status().isNotFound());
		perform(get("/api/v1/classrooms/" + turma2)).andExpect(jsonPath("$.studentCount").value(0));
		perform(delete("/api/v1/classrooms/" + turma1)).andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("Exclusão da criança apaga o histórico de turmas — Scenario: EDUCADOR não exclui criança com turma")
	void shouldKeepChildAndLinksWhenEducatorTriesToDelete() throws Exception {
		// given
		UUID id = createChild();

		// when / then
		perform(anaBearer, delete(CHILDREN + "/" + id)).andExpect(status().isForbidden());
		getChild(id).andExpect(jsonPath("$.classroom.name").value("Turma 1"));
		history(id).andExpect(jsonPath("$.length()").value(1));
	}

	// ---------- Listar crianças: filtro por turma ----------

	@Test
	@DisplayName("Listar crianças com paginação e busca por nome — Scenario: Filtro por turma com busca por nome")
	void shouldFilterByCurrentClassroomAndName() throws Exception {
		// given
		UUID turma2 = createClassroom("Turma 2");
		createChild("name", "João Pedro");
		createChild("name", "Ana Lima");
		createChild("name", "Maria João", "classroomId", turma2.toString());
		UUID joaoPaulo = createChild("name", "João Paulo");
		putChild(joaoPaulo, body("name", "João Paulo", "classroomId", turma2.toString())).andExpect(status().isOk());

		// when / then
		perform(get(CHILDREN).param("classroomId", turma1.toString()).param("name", "joao")).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].name").value(contains("João Pedro")))
			.andExpect(jsonPath("$.content[0].classroom.name").value("Turma 1"))
			.andExpect(jsonPath("$.page.totalElements").value(1));
	}

	@Test
	@DisplayName("Listar crianças com paginação e busca por nome — Scenario: Filtro por turma sem crianças ou inexistente")
	void shouldReturnEmptyPageWhenClassroomHasNoChildrenOrDoesNotExist() throws Exception {
		// given
		UUID turma3 = createClassroom("Turma 3");
		createChild();

		// when / then
		perform(get(CHILDREN).param("classroomId", turma3.toString())).andExpect(status().isOk())
			.andExpect(jsonPath("$.content").value(empty()))
			.andExpect(jsonPath("$.page.totalElements").value(0));
		perform(get(CHILDREN).param("classroomId", MISSING_ID)).andExpect(status().isOk())
			.andExpect(jsonPath("$.content").value(empty()))
			.andExpect(jsonPath("$.page.totalElements").value(0));
	}

	@Test
	@DisplayName("Listar crianças com paginação e busca por nome — Scenario: Filtro por turma malformado")
	void shouldRejectMalformedClassroomFilter() throws Exception {
		perform(get(CHILDREN).param("classroomId", "turma-1")).andExpect(status().isBadRequest())
			.andExpect(content().contentType(PROBLEM_JSON));
	}

	// ---------- helpers ----------

	// ---------- Responsáveis obrigatórios da criança ----------

	@Test
	@DisplayName("Responsáveis obrigatórios da criança — Scenario: Criança criada com responsável novo")
	void shouldCreateChildWithNewGuardian() throws Exception {
		// when
		MvcResult result = postChild(body()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.guardians.length()").value(1))
			.andExpect(jsonPath("$.guardians[0].name").value("Maria Souza"))
			.andExpect(jsonPath("$.guardians[0].relationship").value("MOTHER"))
			.andExpect(jsonPath("$.guardians[0].phone").value("11912345678"))
			.andExpect(jsonPath("$.guardians[0].notWorking").value(false))
			.andExpect(jsonPath("$.guardians[0].workplace").value("Padaria Central"))
			.andExpect(jsonPath("$.guardians[0].workPhone").value("1133334444"))
			.andReturn();

		// then
		UUID maria = guardianIdOf(result, "Maria Souza");
		perform(get(GUARDIANS)).andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(maria.toString()))
			.andExpect(jsonPath("$.content[0].children[0].id").value(idOf(result).toString()))
			.andExpect(jsonPath("$.content[0].children[0].relationship").value("MOTHER"));
	}

	@Test
	@DisplayName("Responsáveis obrigatórios da criança — Scenario: Irmão vinculado à responsável já cadastrada")
	void shouldLinkSiblingToExistingGuardian() throws Exception {
		// given
		UUID maria = guardianIdOf(postChild(body("name", "Ana Souza")).andExpect(status().isCreated()).andReturn(),
				"Maria Souza");

		// when
		postChild(body("name", "Pedro Souza", "guardians", items(existingItem(maria, "MOTHER"))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.guardians[0].id").value(maria.toString()));

		// then
		assertThat(countGuardians()).isOne();
		perform(get(GUARDIANS + "/" + maria)).andExpect(status().isOk())
			.andExpect(jsonPath("$.children[*].name").value(contains("Ana Souza", "Pedro Souza")));
	}

	@Test
	@DisplayName("Responsáveis obrigatórios da criança — Scenario: Criança sem responsável")
	void shouldRejectChildWithoutGuardians() throws Exception {
		for (String body : new String[] {body("guardians", ABSENT), body("guardians", List.of())}) {
			postChild(body).andExpect(status().isBadRequest())
				.andExpect(content().contentType(PROBLEM_JSON))
				.andExpect(jsonPath("$.errors.length()").value(1))
				.andExpect(jsonPath("$.errors[0].field").value("guardians"))
				.andExpect(jsonPath("$.errors[0].message").value("Informe pelo menos um responsável."));
		}
		assertThat(countChildren()).isZero();
	}

	@Test
	@DisplayName("Responsáveis obrigatórios da criança — Scenario: Sem responsável e com outro campo inválido")
	void shouldListMissingGuardiansTogetherWithOtherErrors() throws Exception {
		postChild(body("cep", "123", "guardians", ABSENT)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("guardians", "cep")));
	}

	@Test
	@DisplayName("Responsáveis obrigatórios da criança — Scenario: Mais de cinco responsáveis")
	void shouldRejectMoreThanFiveGuardians() throws Exception {
		// given
		List<Map<String, Object>> six = items();
		for (int i = 1; i <= 6; i++) {
			six.add(newItem(guardianMap("name", "Responsável " + i), "OTHER"));
		}

		// when / then
		postChild(body("guardians", six)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("guardians"))
			.andExpect(jsonPath("$.errors[0].message").value("Informe no máximo 5 responsáveis."));
		postChild(body("guardians", six.subList(0, 5))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.guardians.length()").value(5));
	}

	@Test
	@DisplayName("Responsáveis obrigatórios da criança — Scenario: Mesmo responsável duas vezes")
	void shouldRejectSameGuardianTwice() throws Exception {
		// given
		UUID maria = createMaria();

		// when / then
		postChild(body("name", "Pedro Souza", "guardians",
				items(existingItem(maria, "MOTHER"), existingItem(maria, "OTHER"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("guardians"))
			.andExpect(jsonPath("$.errors[0].message")
				.value("O mesmo responsável não pode ser informado mais de uma vez."));
		assertThat(countChildren()).isOne();
	}

	@Test
	@DisplayName("Responsáveis obrigatórios da criança — Scenario: Item sem responsável ou com os dois")
	void shouldRejectItemWithoutSourceOrWithBoth() throws Exception {
		// given
		UUID maria = createMaria();
		Map<String, Object> both = newItem(guardianMap(), "FATHER");
		both.put("guardianId", maria.toString());

		// when / then
		postChild(body("name", "Pedro Souza", "guardians", items(Map.of("relationship", "MOTHER"), both)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("guardians[0]", "guardians[1]")));
		assertThat(countChildren()).isOne();
		assertThat(countGuardians()).isOne();
	}

	@Test
	@DisplayName("Responsáveis obrigatórios da criança — Scenario: Parentesco ausente e fora da lista")
	void shouldRejectMissingAndUnknownRelationship() throws Exception {
		// given
		Map<String, Object> withoutRelationship = new LinkedHashMap<>();
		withoutRelationship.put("guardian", guardianMap());

		// when / then
		postChild(body("guardians", items(withoutRelationship,
				newItem(guardianMap("name", "João Souza"), "COUSIN"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("guardians[0].relationship",
					"guardians[1].relationship")));
		assertThat(countChildren()).isZero();
		assertThat(countGuardians()).isZero();
	}

	@Test
	@DisplayName("Responsáveis obrigatórios da criança — Scenario: Responsável cadastrado inexistente")
	void shouldRejectMissingExistingGuardian() throws Exception {
		postChild(body("guardians", items(existingItem(UUID.fromString(MISSING_ID), "MOTHER"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("guardians[0].guardianId"))
			.andExpect(jsonPath("$.errors[0].message").value("Responsável não encontrado"));
		assertThat(countChildren()).isZero();
	}

	@Test
	@DisplayName("Responsáveis obrigatórios da criança — Scenario: guardianId malformado")
	void shouldRejectMalformedGuardianId() throws Exception {
		postChild(body("guardians", items(Map.of("guardianId", "maria", "relationship", "MOTHER"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(contains("guardians[0].guardianId")));
	}

	@Test
	@DisplayName("Responsáveis obrigatórios da criança — Scenario: Responsável novo com dados inválidos")
	void shouldRejectInvalidNewGuardian() throws Exception {
		// given
		UUID maria = createMaria();
		Map<String, Object> invalid = new LinkedHashMap<>();
		invalid.put("name", "");
		invalid.put("phone", "111234");
		invalid.put("notWorking", true);
		invalid.put("cpf", "123.456.789-00");

		// when / then
		postChild(body("name", "Pedro Souza", "guardians",
				items(existingItem(maria, "FATHER"), newItem(invalid, "MOTHER"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("guardians[1].guardian.name",
					"guardians[1].guardian.phone", "guardians[1].guardian.cpf")));
		assertThat(countChildren()).isOne();
		assertThat(countGuardians()).isOne();
	}

	@Test
	@DisplayName("Responsáveis obrigatórios da criança — Scenario: Responsável novo nascido no futuro")
	void shouldRejectNewGuardianBornInTheFuture() throws Exception {
		postChild(body("guardians", items(newItem(guardianMap("birthDate", "2026-03-02"), "MOTHER"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[*].field").value(contains("guardians[0].guardian.birthDate")));
		assertThat(countChildren()).isZero();
		assertThat(countGuardians()).isZero();
	}

	@Test
	@DisplayName("Responsáveis obrigatórios da criança — Scenario: Responsável novo com CPF já cadastrado")
	void shouldRejectNewGuardianWithCpfInUse() throws Exception {
		// given
		postChild(body("name", "Pedro Lima", "guardians",
				items(newItem(guardianMap("name", "João Lima", "cpf", "52998224725"), "FATHER"))))
			.andExpect(status().isCreated());

		// when / then
		postChild(body("guardians", items(newItem(guardianMap(), "MOTHER"),
				newItem(guardianMap("name", "Joana Lima", "cpf", "529.982.247-25"), "AUNT"))))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Já existe um responsável com este CPF."))
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("guardians[1].guardian.cpf"))
			.andExpect(content().string(not(containsString("52998224725"))))
			.andExpect(content().string(not(containsString("529.982.247-25"))));
		assertThat(countChildren()).isOne();
		assertThat(countGuardians()).isOne();
	}

	@Test
	@DisplayName("Responsáveis obrigatórios da criança — Scenario: Dois responsáveis novos com o mesmo CPF")
	void shouldRejectTwoNewGuardiansWithSameCpf() throws Exception {
		postChild(body("guardians", items(newItem(guardianMap("cpf", "52998224725"), "MOTHER"),
				newItem(guardianMap("name", "Joana Lima", "cpf", "52998224725"), "AUNT"))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errors[*].field").value(contains("guardians[1].guardian.cpf")));
		assertThat(countChildren()).isZero();
		assertThat(countGuardians()).isZero();
	}

	@Test
	@DisplayName("Responsáveis obrigatórios da criança — Scenario: Responsável novo com CPF igual ao da própria "
			+ "criança")
	void shouldAcceptNewGuardianWithCpfOfTheChild() throws Exception {
		postChild(body("cpf", "52998224725", "guardians",
				items(newItem(guardianMap("cpf", "52998224725"), "MOTHER"))))
			.andExpect(status().isCreated());
	}

	// ---------- Substituição dos responsáveis na alteração ----------

	@Test
	@DisplayName("Substituição dos responsáveis na alteração — Scenario: Acrescentar o pai")
	void shouldAddFatherOnUpdate() throws Exception {
		// given
		MvcResult ana = postChild(body("name", "Ana Souza")).andExpect(status().isCreated()).andReturn();
		UUID maria = guardianIdOf(ana, "Maria Souza");

		// when / then
		putChild(idOf(ana), body("name", "Ana Souza", "guardians", items(existingItem(maria, "MOTHER"),
				newItem(guardianMap("name", "João Souza"), "FATHER"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.guardians[*].name").value(contains("João Souza", "Maria Souza")))
			.andExpect(jsonPath("$.guardians[*].relationship").value(contains("FATHER", "MOTHER")));
	}

	@Test
	@DisplayName("Substituição dos responsáveis na alteração — Scenario: Remover a mãe")
	void shouldRemoveMotherOnUpdate() throws Exception {
		// given
		MvcResult ana = postChild(body("name", "Ana Souza", "guardians", items(newItem(guardianMap(), "MOTHER"),
				newItem(guardianMap("name", "João Souza"), "FATHER"))))
			.andExpect(status().isCreated())
			.andReturn();
		UUID maria = guardianIdOf(ana, "Maria Souza");
		UUID joao = guardianIdOf(ana, "João Souza");

		// when / then
		putChild(idOf(ana), body("name", "Ana Souza", "guardians", items(existingItem(joao, "FATHER"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.guardians[*].name").value(contains("João Souza")));
		perform(get(GUARDIANS + "/" + maria)).andExpect(status().isOk())
			.andExpect(jsonPath("$.children").value(empty()));
	}

	@Test
	@DisplayName("Substituição dos responsáveis na alteração — Scenario: Trocar o parentesco")
	void shouldChangeRelationshipOnUpdate() throws Exception {
		// given
		UUID maria = createMaria();
		UUID pedro = idOf(postChild(body("name", "Pedro Lima", "guardians", items(existingItem(maria, "OTHER"))))
			.andExpect(status().isCreated())
			.andReturn());

		// when / then
		putChild(pedro, body("name", "Pedro Lima", "guardians", items(existingItem(maria, "STEPMOTHER"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.guardians[0].id").value(maria.toString()))
			.andExpect(jsonPath("$.guardians[0].relationship").value("STEPMOTHER"));
		perform(get(GUARDIANS + "/" + maria)).andExpect(status().isOk())
			.andExpect(jsonPath("$.children[*].name").value(contains("Ana Souza", "Pedro Lima")))
			.andExpect(jsonPath("$.children[*].relationship").value(contains("MOTHER", "STEPMOTHER")));
	}

	@Test
	@DisplayName("Substituição dos responsáveis na alteração — Scenario: Remover todos os responsáveis")
	void shouldRejectRemovingEveryGuardian() throws Exception {
		// given
		MvcResult ana = postChild(body("name", "Ana Souza")).andExpect(status().isCreated()).andReturn();

		// when / then
		putChild(idOf(ana), body("name", "Ana Souza", "guardians", List.of())).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.length()").value(1))
			.andExpect(jsonPath("$.errors[0].field").value("guardians"))
			.andExpect(jsonPath("$.errors[0].message").value("Informe pelo menos um responsável."));
		assertThat(countGuardianLinks(idOf(ana))).isOne();
	}

	@Test
	@DisplayName("Substituição dos responsáveis na alteração — Scenario: Criança antiga sem responsável")
	void shouldReadOldChildWithoutGuardiansAndRequireThemOnUpdate() throws Exception {
		// given: saved before the guardians were required
		UUID bia = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO children (id, name, birth_date, cep, address, neighborhood, address_number, city, state,
				                      created_at, updated_at, created_by, updated_by)
				VALUES (?, 'Bia Lima', DATE '2018-05-10', '01310100', 'Avenida Paulista', 'Bela Vista', '1000',
				        'São Paulo', 'SP', now(), now(), ?, ?)
				""", bia, anaId, anaId);
		jdbcTemplate.update("""
				INSERT INTO child_classrooms (id, child_id, classroom_id, start_date, created_at, updated_at,
				                              created_by, updated_by)
				VALUES (?, ?, ?, DATE '2026-02-01', now(), now(), ?, ?)
				""", UUID.randomUUID(), bia, turma1, anaId, anaId);

		// when / then
		getChild(bia).andExpect(status().isOk())
			.andExpect(jsonPath("$.guardians").isArray())
			.andExpect(jsonPath("$.guardians").value(empty()));
		expectFieldError(putChild(bia, body("name", "Bia Lima Souza", "guardians", ABSENT)), "guardians");
		getChild(bia).andExpect(jsonPath("$.name").value("Bia Lima"));
	}

	@Test
	@DisplayName("Substituição dos responsáveis na alteração — Scenario: Alteração recusada não muda os vínculos")
	void shouldNotChangeLinksWhenUpdateIsRefused() throws Exception {
		// given
		MvcResult ana = postChild(body("name", "Ana Souza")).andExpect(status().isCreated()).andReturn();
		UUID maria = guardianIdOf(ana, "Maria Souza");
		createChild("name", "Outra Criança", "cpf", "11144477735", "guardians", items(existingItem(maria, "AUNT")));

		// when / then
		putChild(idOf(ana), body("name", "Ana Souza", "cpf", "11144477735", "guardians",
				items(newItem(guardianMap("name", "João Souza"), "FATHER"))))
			.andExpect(status().isConflict());
		getChild(idOf(ana)).andExpect(jsonPath("$.guardians[*].name").value(contains("Maria Souza")));
		assertThat(countGuardians()).isOne();
	}

	// ---------- Responsáveis nas respostas da criança ----------

	@Test
	@DisplayName("Responsáveis nas respostas da criança — Scenario: Consulta com dois responsáveis")
	void shouldReturnTwoGuardiansByName() throws Exception {
		// given
		Map<String, Object> carlos = guardianMap("name", "Carlos Souza", "notWorking", true);
		UUID ana = createChild("name", "Ana Souza", "guardians", items(newItem(guardianMap("cpf", "52998224725",
				"rg", "12345678X", "birthDate", "1990-05-10", "maritalStatus", "MARRIED"), "MOTHER"),
				newItem(carlos, "GRANDFATHER")));

		// when / then
		getChild(ana).andExpect(status().isOk())
			.andExpect(jsonPath("$.guardians.length()").value(2))
			.andExpect(jsonPath("$.guardians[0].name").value("Carlos Souza"))
			.andExpect(jsonPath("$.guardians[0].relationship").value("GRANDFATHER"))
			.andExpect(jsonPath("$.guardians[0].notWorking").value(true))
			.andExpect(jsonPath("$.guardians[0].workplace").isEmpty())
			.andExpect(jsonPath("$.guardians[0].workPhone").isEmpty())
			.andExpect(jsonPath("$.guardians[1].name").value("Maria Souza"))
			.andExpect(jsonPath("$.guardians[1].relationship").value("MOTHER"))
			.andExpect(jsonPath("$.guardians[1].workplace").value("Padaria Central"))
			.andExpect(jsonPath("$.guardians[1].workPhone").value("1133334444"))
			.andExpect(jsonPath("$.guardians[*].cpf").value(empty()))
			.andExpect(jsonPath("$.guardians[*].rg").value(empty()))
			.andExpect(jsonPath("$.guardians[*].birthDate").value(empty()))
			.andExpect(jsonPath("$.guardians[*].maritalStatus").value(empty()))
			.andExpect(jsonPath("$.guardians[*].workAddress").value(empty()));
	}

	@Test
	@DisplayName("Responsáveis nas respostas da criança — Scenario: Listagem de crianças sem responsáveis")
	void shouldListChildrenWithoutGuardians() throws Exception {
		// given
		createChild("name", "Ana Souza");

		// when / then
		perform(get(CHILDREN)).andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].guardians").doesNotExist());
	}

	// ---------- Exclusão da criança apaga os vínculos com responsáveis ----------

	@Test
	@DisplayName("Exclusão da criança apaga os vínculos com responsáveis — Scenario: Criança excluída, responsável "
			+ "mantido")
	void shouldDeleteLinksAndKeepGuardianWhenChildIsDeleted() throws Exception {
		// given
		MvcResult ana = postChild(body("name", "Ana Souza")).andExpect(status().isCreated()).andReturn();
		UUID maria = guardianIdOf(ana, "Maria Souza");
		createChild("name", "Pedro Souza", "guardians", items(existingItem(maria, "MOTHER")));

		// when
		perform(delete(CHILDREN + "/" + idOf(ana))).andExpect(status().isNoContent());

		// then
		perform(get(GUARDIANS + "/" + maria)).andExpect(status().isOk())
			.andExpect(jsonPath("$.children[*].name").value(contains("Pedro Souza")));
	}

	@Test
	@DisplayName("Exclusão da criança apaga os vínculos com responsáveis — Scenario: EDUCADOR não exclui criança com "
			+ "responsável")
	void shouldNotLetEducatorDeleteChildWithGuardian() throws Exception {
		// given
		MvcResult ana = postChild(body("name", "Ana Souza")).andExpect(status().isCreated()).andReturn();

		// when / then
		perform(anaBearer, delete(CHILDREN + "/" + idOf(ana))).andExpect(status().isForbidden());
		assertThat(countGuardianLinks(idOf(ana))).isOne();
	}

	/**
	 * "A responsável Maria Souza": created with "os dados da Maria" in the body of {@code Ana Souza}, as
	 * {@code MOTHER}.
	 */
	private UUID createMaria() throws Exception {
		return guardianIdOf(postChild(body("name", "Ana Souza")).andExpect(status().isCreated()).andReturn(),
				"Maria Souza");
	}

	/**
	 * The body of a valid child with the required data only, in "Turma 1", changed by the given pairs of field and
	 * value ({@link #ABSENT} leaves the field out).
	 */
	private String body(Object... fieldsAndValues) {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("name", "Maria Clara Souza");
		fields.put("birthDate", "2018-05-10");
		fields.put("cep", "01310-100");
		fields.put("address", "Avenida Paulista");
		fields.put("neighborhood", "Bela Vista");
		fields.put("addressNumber", "1000");
		fields.put("city", "São Paulo");
		fields.put("state", "SP");
		fields.put("classroomId", turma1.toString());
		fields.put("guardians", List.of(newItem(guardianMap(), "MOTHER")));
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
	 * "Os dados da Maria", with the given fields replaced or added.
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
			fields.put((String) fieldsAndValues[i], fieldsAndValues[i + 1]);
		}
		return fields;
	}

	@SafeVarargs
	private static List<Map<String, Object>> items(Map<String, Object>... items) {
		return new ArrayList<>(List.of(items));
	}

	private static UUID guardianIdOf(MvcResult childResult, String guardianName) throws Exception {
		List<String> ids = JsonPath.read(childResult.getResponse().getContentAsString(),
				"$.guardians[?(@.name == '" + guardianName + "')].id");
		assertThat(ids).hasSize(1);
		return UUID.fromString(ids.get(0));
	}

	private int countGuardians() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM guardians", Integer.class);
	}

	private int countGuardianLinks(UUID childId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM child_guardians WHERE child_id = ?", Integer.class,
				childId);
	}

	/**
	 * Moves the application clock and logs in again: tokens last 8 hours, so the one issued in {@link #setUp()}
	 * would be expired after a longer jump.
	 */
	private void moveClockTo(Instant instant) throws Exception {
		clock.setInstant(instant);
		adminBearer = testUsers.bearer(ADMIN_LOGIN, PASSWORD);
	}

	private UUID createClassroom(String name) throws Exception {
		MvcResult result = perform(post("/api/v1/classrooms").contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"" + name + "\"}")).andExpect(status().isCreated()).andReturn();
		return idOf(result);
	}

	private ResultActions history(UUID id) throws Exception {
		return perform(get(CHILDREN + "/" + id + "/classrooms"));
	}

	private int countLinks() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM child_classrooms", Integer.class);
	}

	private UUID createChild(Object... fieldsAndValues) throws Exception {
		return idOf(postChild(body(fieldsAndValues)).andExpect(status().isCreated()).andReturn());
	}

	private void createChildren(String... names) throws Exception {
		for (String name : names) {
			createChild("name", name);
		}
	}

	private ResultActions postChild(String body) throws Exception {
		return postChild(adminBearer, body);
	}

	private ResultActions postChild(String bearer, String body) throws Exception {
		return perform(bearer, post(CHILDREN).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions putChild(UUID id, String body) throws Exception {
		return perform(adminBearer, putJson(id, body));
	}

	private static MockHttpServletRequestBuilder putJson(UUID id, String body) {
		return put(CHILDREN + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private ResultActions getChild(UUID id) throws Exception {
		return perform(get(CHILDREN + "/" + id));
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

	private int countChildren() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM children", Integer.class);
	}

	private int countWhere(String condition) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM children WHERE " + condition, Integer.class);
	}

	private UUID author(String column, UUID id) {
		return jdbcTemplate.queryForObject("SELECT " + column + " FROM children WHERE id = ?", UUID.class, id);
	}

}
