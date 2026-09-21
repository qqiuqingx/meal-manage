package me.zhengjie.modules.customer.profile.rest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.support.config.FastJsonConfig;
import com.alibaba.fastjson2.support.spring.http.converter.FastJsonHttpMessageConverter;
import me.zhengjie.annotation.rest.AnonymousAccess;
import me.zhengjie.exception.handler.GlobalExceptionHandler;
import me.zhengjie.modules.customer.profile.domain.dto.intake.CustomerSelfIntakeRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户自助资料登记测试接口测试。
 *
 * 使用 standalone MockMvc 验证真实 Controller 方法，不启动完整应用上下文，
 * 因此不访问数据库、Redis 或其他业务存储。
 *
 * @author qqx
 * @date 2026-09-21
 */
class CustomerSelfIntakeControllerTest {

    private static final String URL = "/api/public/customer-intake";
    private static final String SUCCESS_MESSAGE = "测试资料已接收，请联系您的客服确认";

    private MockMvc mockMvc;
    private Logger controllerLogger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

    /**
     * 初始化只包含被测 Controller 和全局异常处理器的 MockMvc，并挂载日志捕获器。
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerSelfIntakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(fastJsonConverter())
                .setValidator(validator)
                .build();

        controllerLogger = (Logger) LoggerFactory.getLogger(CustomerSelfIntakeController.class);
        originalLevel = controllerLogger.getLevel();
        controllerLogger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        controllerLogger.addAppender(appender);
    }

    /**
     * 移除日志捕获器并恢复测试前的日志级别。
     */
    @AfterEach
    void tearDown() {
        controllerLogger.detachAppender(appender);
        appender.stop();
        controllerLogger.setLevel(originalLevel);
    }

    /**
     * 验证合法请求返回追踪号、固定提示并产生对应日志。
     */
    @Test
    void shouldAcceptValidRequestAndReturnRequestId() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("测试客户", "13800000000", "测试地址北京市朝阳区", "测试提交")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId", matchesPattern("[0-9a-f]{32}")))
                .andExpect(jsonPath("$.message").value(SUCCESS_MESSAGE));

        assertThat(messages()).hasSize(1);
        assertThat(messages().get(0))
                .contains("客户自助资料测试提交")
                .contains("customerName=测试客户")
                .contains("phone=13800000000")
                .contains("address=测试地址北京市朝阳区")
                .contains("remark=测试提交");
    }

    /**
     * 验证接口方法带有匿名访问标记，调用方无需 JWT 即可进入 Controller 映射。
     */
    @Test
    void shouldBeMarkedAsAnonymousPostEndpoint() throws Exception {
        Method method = CustomerSelfIntakeController.class.getMethod("submit", CustomerSelfIntakeRequest.class);

        assertNotNull(AnnotationUtils.findAnnotation(method, AnonymousAccess.class));
        ConditionalOnProperty conditional = AnnotationUtils.findAnnotation(
                CustomerSelfIntakeController.class, ConditionalOnProperty.class);
        assertNotNull(conditional);
        assertTrue(conditional.havingValue().equalsIgnoreCase("true"));
    }

    /**
     * 验证客户姓名为空时由 Bean Validation 返回 400。
     */
    @Test
    void shouldRejectBlankCustomerName() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("  ", "13800000000", "测试地址北京市朝阳区", "测试提交")))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证手机号格式错误时返回 400。
     */
    @Test
    void shouldRejectInvalidPhone() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("测试客户", "12345678901", "测试地址北京市朝阳区", "测试提交")))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证地址过短或过长时均返回 400。
     */
    @Test
    void shouldRejectAddressOutsideLengthRange() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("测试客户", "13800000000", "短", "测试提交")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("测试客户", "13800000000", repeated('地', 501), "测试提交")))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证备注超过最大长度时返回 400。
     */
    @Test
    void shouldRejectRemarkLongerThanMaximum() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("测试客户", "13800000000", "测试地址北京市朝阳区", repeated('备', 1001))))
                .andExpect(status().isBadRequest());
    }

    /**
     * 验证写入日志的字段会将换行和控制字符规范化为单行文本。
     */
    @Test
    void shouldNormalizeControlCharactersBeforeLogging() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("测试\n客户", "13800000000", "测试地址\r\n北京市朝阳区", "第一行\r\n伪造行\t控制")))
                .andExpect(status().isOk());

        assertThat(messages()).hasSize(1);
        String formattedMessage = messages().get(0);
        assertThat(formattedMessage)
                .contains("customerName=测试 客户")
                .contains("address=测试地址  北京市朝阳区")
                .contains("remark=第一行  伪造行 控制")
                .doesNotContain("\r")
                .doesNotContain("\n")
                .doesNotContain("\t");
    }

    /**
     * 验证默认属性缺失时不会注册自助资料登记 Controller。
     */
    @Test
    void shouldNotRegisterControllerWhenFeatureIsDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(CustomerSelfIntakeController.class)
                .run(context -> assertThat(context).doesNotHaveBean(CustomerSelfIntakeController.class));
    }

    /**
     * 验证显式开启开关后可以注册自助资料登记 Controller。
     */
    @Test
    void shouldRegisterControllerWhenFeatureIsEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(CustomerSelfIntakeController.class)
                .withPropertyValues("customer.self-intake.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CustomerSelfIntakeController.class);
                });
    }

    /**
     * 验证 Controller 没有注入任何持久化组件，避免测试提交产生业务数据。
     */
    @Test
    void shouldNotDependOnPersistenceComponents() {
        boolean hasInstanceDependency = Arrays.stream(CustomerSelfIntakeController.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .anyMatch(type -> type.getSimpleName().endsWith("Mapper")
                        || type.getSimpleName().endsWith("Repository")
                        || type.getSimpleName().endsWith("Service"));

        assertThat(hasInstanceDependency).isFalse();
    }

    /**
     * 返回被测 Controller 使用的 FastJSON HTTP 消息转换器，保持测试与生产序列化方式一致。
     *
     * @return FastJSON HTTP 消息转换器
     */
    private FastJsonHttpMessageConverter fastJsonConverter() {
        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();
        FastJsonConfig config = new FastJsonConfig();
        config.setWriterFeatures(JSONWriter.Feature.WriteMapNullValue);
        converter.setFastJsonConfig(config);
        converter.setSupportedMediaTypes(Arrays.asList(MediaType.APPLICATION_JSON));
        return converter;
    }

    /**
     * 构造请求 JSON，避免测试依赖任何业务实体或持久化数据。
     *
     * @param customerName 客户姓名
     * @param phone 手机号
     * @param address 地址
     * @param remark 备注
     * @return 请求 JSON
     */
    private String payload(String customerName, String phone, String address, String remark) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("customerName", customerName);
        payload.put("phone", phone);
        payload.put("address", address);
        payload.put("remark", remark);
        return JSON.toJSONString(payload);
    }

    /**
     * 生成指定长度的重复字符文本，兼容项目 Java 8 编译目标。
     *
     * @param character 重复字符
     * @param length 文本长度
     * @return 重复字符文本
     */
    private String repeated(char character, int length) {
        char[] value = new char[length];
        Arrays.fill(value, character);
        return new String(value);
    }

    /**
     * 提取当前测试捕获的日志格式化消息。
     *
     * @return 日志消息列表
     */
    private List<String> messages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(java.util.stream.Collectors.toList());
    }
}
