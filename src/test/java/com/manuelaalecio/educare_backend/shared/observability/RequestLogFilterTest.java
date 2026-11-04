package com.manuelaalecio.educare_backend.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestLogFilterTest {

	private final RequestLogFilter filter = new RequestLogFilter();

	private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);

	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	private Level originalLevel;

	@BeforeEach
	void setUp() {
		originalLevel = logger.getLevel();
		logger.setLevel(Level.INFO);
		appender.start();
		logger.addAppender(appender);
	}

	@AfterEach
	void tearDown() {
		logger.detachAppender(appender);
		logger.setLevel(originalLevel);
	}

	@Test
	void shouldLogRoutePatternWhenRouteHasVariable() throws Exception {
		// given
		MockHttpServletRequest request = request("GET", "/api/v1/children/3f1c2a9e-8b7d-4c6e-9a1b-2d3e4f5a6b7c");
		MockFilterChain chain = chainSetting("/api/v1/children/{id}", 200);

		// when
		filter.doFilter(request, new MockHttpServletResponse(), chain);

		// then
		assertThat(lines()).singleElement().satisfies(line -> {
			assertThat(line).matches("http method=GET route=/api/v1/children/\\{id} status=200 durationMs=\\d+");
			assertThat(line).doesNotContain("3f1c2a9e");
		});
	}

	@Test
	void shouldLeaveQueryStringOutWhenRequestHasOne() throws Exception {
		// given
		MockHttpServletRequest request = request("GET", "/api/v1/children");
		request.setQueryString("name=Maria&page=0");
		request.setParameter("name", "Maria");

		// when
		filter.doFilter(request, new MockHttpServletResponse(), chainSetting("/api/v1/children", 200));

		// then
		assertThat(lines()).singleElement().satisfies(line -> {
			assertThat(line).startsWith("http method=GET route=/api/v1/children status=200 durationMs=");
			assertThat(line).doesNotContain("Maria").doesNotContain("page");
		});
	}

	@Test
	void shouldLogUnmatchedWhenNoRouteServedTheRequest() throws Exception {
		// given: refused by the security filter chain, before any handler mapping
		MockHttpServletRequest request = request("GET", "/api/v1/users");

		// when
		filter.doFilter(request, new MockHttpServletResponse(), chainSetting(null, 401));

		// then
		assertThat(lines()).singleElement().asString()
			.matches("http method=GET route=unmatched status=401 durationMs=\\d+");
	}

	@Test
	void shouldLogUnmatchedWhenStaticResourceHandlerServedTheRequest() throws Exception {
		// given
		MockHttpServletRequest request = request("GET", "/api/v1/nao-existe/123");

		// when
		filter.doFilter(request, new MockHttpServletResponse(), chainSetting("/**", 404));

		// then
		assertThat(lines()).singleElement().satisfies(line -> {
			assertThat(line).matches("http method=GET route=unmatched status=404 durationMs=\\d+");
			assertThat(line).doesNotContain("nao-existe");
		});
	}

	@Test
	void shouldLogInternalServerErrorWhenChainThrows() {
		// given
		MockHttpServletRequest request = request("POST", "/api/v1/children");
		MockFilterChain chain = new MockFilterChain(new HttpServlet() {
			@Override
			protected void service(HttpServletRequest req,
					HttpServletResponse res) throws ServletException {
				req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/children");
				throw new ServletException("falha");
			}
		});

		// when / then
		assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), chain))
			.isInstanceOf(ServletException.class);
		assertThat(lines()).singleElement().asString()
			.matches("http method=POST route=/api/v1/children status=500 durationMs=\\d+");
	}

	@Test
	void shouldNotLogWhenRequestIsToActuator() throws Exception {
		// given
		MockFilterChain health = chainSetting(null, 200);
		MockFilterChain readiness = chainSetting(null, 200);

		// when
		filter.doFilter(request("GET", "/actuator/health"), new MockHttpServletResponse(), health);
		filter.doFilter(request("GET", "/actuator/health/readiness"), new MockHttpServletResponse(), readiness);
		filter.doFilter(request("GET", "/actuator"), new MockHttpServletResponse(), chainSetting(null, 200));

		// then
		assertThat(lines()).isEmpty();
		assertThat(health.getRequest()).isNotNull();
		assertThat(readiness.getRequest()).isNotNull();
	}

	@Test
	void shouldLogWhenPathOnlyStartsLikeActuator() throws Exception {
		// when
		filter.doFilter(request("GET", "/actuatorx"), new MockHttpServletResponse(), chainSetting(null, 401));

		// then
		assertThat(lines()).hasSize(1);
	}

	@Test
	void shouldNotLogButPassRequestOnWhenLoggerIsAboveInfo() throws Exception {
		// given
		logger.setLevel(Level.WARN);
		MockFilterChain chain = chainSetting("/api/v1/auth/me", 200);
		MockHttpServletResponse response = new MockHttpServletResponse();

		// when
		filter.doFilter(request("GET", "/api/v1/auth/me"), response, chain);

		// then
		assertThat(lines()).isEmpty();
		assertThat(chain.getRequest()).isNotNull();
		assertThat(response.getStatus()).isEqualTo(200);
	}

	private List<String> lines() {
		return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
	}

	private static MockHttpServletRequest request(String method, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRequestURI(path);
		return request;
	}

	/** A chain that, like the dispatcher, sets the matched pattern (when not null) and the response status. */
	private static MockFilterChain chainSetting(String pattern, int status) {
		return new MockFilterChain(new HttpServlet() {
			@Override
			protected void service(HttpServletRequest req,
					HttpServletResponse res) {
				if (pattern != null) {
					req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
				}
				res.setStatus(status);
			}
		});
	}

}
