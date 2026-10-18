package xyz.erupt.sms.test;

import com.google.gson.JsonObject;
import jakarta.annotation.Resource;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import xyz.erupt.core.context.MetaContext;
import xyz.erupt.core.context.MetaErupt;
import xyz.erupt.core.context.MetaUser;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.core.module.MetaMenu;
import xyz.erupt.core.module.MetaUserinfo;
import xyz.erupt.core.scope.EruptScopeService;
import xyz.erupt.core.service.EruptCoreService;
import xyz.erupt.core.service.EruptModifyService;
import xyz.erupt.core.service.EruptService;
import xyz.erupt.core.util.Erupts;
import xyz.erupt.core.view.TableQuery;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.sms.core.EruptSms;
import xyz.erupt.sms.core.SmsChannels;
import xyz.erupt.sms.model.*;
import xyz.erupt.sms.core.SmsSender;
import xyz.erupt.upms.service.EruptTokenService;

import java.lang.reflect.Proxy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The module with a recording SmsBlend standing in for the vendor: the workspace picks the
 * channel, sending goes through the template in parameter order, and codes are issued and checked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SmsTest {

    private record Sent(String method, Object phones, String templateId, LinkedHashMap<String, String> params) {
    }

    private static final List<Sent> sent = new ArrayList<>();

    private static SmsChannel channel;

    private static SmsTemplate notice;

    @Resource
    private EruptDao eruptDao;

    @Resource
    private SmsChannels channels;

    @Resource
    private EruptService eruptService;

    @Resource
    private EruptModifyService eruptModifyService;

    @Resource
    private EruptTokenService eruptTokenService;

    @Resource
    private SmsSender sender;

    @Resource
    private EruptSms sms;

    @Resource
    private PlatformTransactionManager transactionManager;

    private String token;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        sent.clear();
        if (null == channel) {
            channel = new SmsChannel();
            channel.setName("Main");
            channel.setSupplier("alibaba");
            channel.setAccessKeyId("ak");
            channel.setAccessKeySecret("sk");
            channel.setSignature("erupt");
            channel.setDailyCodeLimit(3);
            channel.setIsDefault(true);
            this.persist(channel);
            notice = this.template("ORDER_SHIPPED", "Order shipped", "SMS_1", SmsTemplate.NOTICE, "Dear ${name}, order ${order} shipped", "name,order");
            this.template("LOGIN_CODE", "Login code", "SMS_2", SmsTemplate.CODE, "${product}: your code is ${code}, valid ${minutes} minutes", "product,code,minutes");
            // every vendor call is recorded and answered with success
            channels.put(channel.getId(), (SmsBlend) Proxy.newProxyInstance(SmsBlend.class.getClassLoader(), new Class[]{SmsBlend.class}, (proxy, method, args) -> {
                if (method.getName().equals("sendMessage") || method.getName().equals("massTexting")) {
                    sent.add(new Sent(method.getName(), args[0], (String) args[1], (LinkedHashMap<String, String>) args[2]));
                    SmsResponse response = new SmsResponse();
                    response.setSuccess(true);
                    response.setData("ok");
                    return response;
                }
                return method.getReturnType() == boolean.class ? false : null;
            }));
        }
        List<MetaMenu> menus = new ArrayList<>();
        for (Class<?> model : List.of(SmsTemplate.class, SmsMessage.class, SmsCode.class)) menus.add(MetaMenu.createEruptClassMenu(model, null, 1));
        MetaUserinfo user = new MetaUserinfo();
        user.setId(-50L);
        user.setAccount("sms-user");
        user.setUsername("sms-user");
        token = Erupts.generateCode(22);
        eruptTokenService.loginToken(user, menus, token, 5);
        this.workspace(channel.getId());
    }

    @AfterEach
    void tearDown() {
        MetaContext.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    private void persist(Object entity) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eruptDao.persist(entity));
    }

    private SmsTemplate template(String code, String name, String templateId, String kind, String content, String params) {
        SmsTemplate template = new SmsTemplate();
        template.setChannel(channel);
        template.setCode(code);
        template.setName(name);
        template.setTemplateId(templateId);
        template.setKind(kind);
        template.setContent(content);
        template.setParams(params);
        this.persist(template);
        return template;
    }

    private void workspace(Long channelId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (null != channelId) {
            request.addHeader(EruptScopeService.HEADER, URLEncoder.encode("{\"SmsScope\":{\"channel\":\"" + channelId + "\"}}", StandardCharsets.UTF_8));
        }
        request.addHeader("token", token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MetaContext.remove();
        MetaContext.register(new MetaUser(-50L, "sms-user", "sms-user"));
        MetaContext.registerToken(token);
        MetaContext.register(new MetaErupt(SmsTemplate.class.getSimpleName()));
    }

    private List<Map<String, Object>> list(Class<?> clazz) {
        MetaContext.register(new MetaErupt(clazz.getSimpleName()));
        TableQuery query = new TableQuery();
        query.setPageIndex(1);
        query.setPageSize(100);
        return new ArrayList<>(eruptService.getEruptData(EruptCoreService.getErupt(clazz.getSimpleName()), query, null).getList());
    }

    @Test
    void templatesFollowTheWorkspace() {
        assertEquals(2, list(SmsTemplate.class).size());
        this.workspace(null);
        assertThrows(EruptWebApiRuntimeException.class, () -> list(SmsTemplate.class), "no channel picked: nothing is guessed");
    }

    @Test
    void savingAMessageSendsItInTheTemplatesParameterOrder() {
        JsonObject message = new JsonObject();
        JsonObject template = new JsonObject();
        template.addProperty("id", notice.getId());
        message.add("template", template);
        message.addProperty("phones", "13800000001\n13800000002");
        JsonObject params = new JsonObject();
        params.addProperty("order", "A-1");
        params.addProperty("name", "Ann");
        message.add("params", params);
        MetaContext.register(new MetaErupt(SmsMessage.class.getSimpleName()));
        Object id = eruptModifyService.insertEruptData(EruptCoreService.getErupt(SmsMessage.class.getSimpleName()), message);

        assertEquals(1, sent.size());
        assertEquals("massTexting", sent.get(0).method());
        assertEquals("SMS_1", sent.get(0).templateId());
        assertEquals(List.of("name", "order"), new ArrayList<>(sent.get(0).params().keySet()), "the vendor reads parameters by position");
        SmsMessage saved = eruptDao.find(SmsMessage.class, id);
        assertEquals(SmsMessage.SENT, saved.getStatus());
        assertEquals("Dear Ann, order A-1 shipped", saved.getContent());
        assertEquals(channel.getId(), saved.getChannel().getId(), "the channel comes from the workspace, not the form");
    }

    @Test
    void aBlacklistedPhoneIsNeverSent() {
        SmsBlacklist blocked = new SmsBlacklist();
        blocked.setPhone("13800000009");
        this.persist(blocked);
        JsonObject message = new JsonObject();
        JsonObject template = new JsonObject();
        template.addProperty("id", notice.getId());
        message.add("template", template);
        message.addProperty("phones", "13800000009");
        JsonObject params = new JsonObject();
        params.addProperty("name", "Bob");
        params.addProperty("order", "B-2");
        message.add("params", params);
        MetaContext.register(new MetaErupt(SmsMessage.class.getSimpleName()));
        assertThrows(EruptWebApiRuntimeException.class, () -> eruptModifyService.insertEruptData(EruptCoreService.getErupt(SmsMessage.class.getSimpleName()), message));
        assertTrue(sent.isEmpty());
    }

    @Test
    void codesAreIssuedRateLimitedAndCheckedOnce() {
        sender.sendCode(channel, "13900000001", Map.of("product", "erupt"));
        assertEquals("SMS_2", sent.get(0).templateId());
        assertEquals(List.of("product", "code", "minutes"), new ArrayList<>(sent.get(0).params().keySet()), "the template's order, code and minutes filled in");
        assertEquals("5", sent.get(0).params().get("minutes"));
        String code = sent.get(0).params().get("code");
        assertEquals(6, code.length());

        assertThrows(EruptWebApiRuntimeException.class, () -> sender.sendCode(channel, "13900000001", null), "too soon for another");
        assertFalse(sender.verifyCode(channel, "13900000001", "000000"));
        assertTrue(sender.verifyCode(channel, "13900000001", code));
        assertFalse(sender.verifyCode(channel, "13900000001", code), "a code is good once");
        assertThrows(EruptWebApiRuntimeException.class, () -> sender.sendCode(channel, "not-a-phone", null));
    }

    private enum Templates {ORDER_SHIPPED, LOGIN_CODE, NO_SUCH}

    private record OrderShipped(String name, String order) {
    }

    @Test
    void theJavaApiSendsByTemplateCodeOnTheDefaultChannel() {
        SmsMessage message = sms.template(Templates.ORDER_SHIPPED).to("13800000005").params(new OrderShipped("Cid", "C-3")).send();
        assertEquals(SmsMessage.SENT, message.getStatus());
        assertNotNull(message.getId(), "the send is on record");
        assertEquals("Dear Cid, order C-3 shipped", message.getContent());
        assertEquals(List.of("name", "order"), new ArrayList<>(sent.get(0).params().keySet()));

        sms.via("Main").template("ORDER_SHIPPED").to("13800000006", "13800000007").param(OrderShipped::name, "Dee").param(OrderShipped::order, "D-4").send();
        assertEquals("massTexting", sent.get(1).method());
        assertEquals("D-4", sent.get(1).params().get("order"));

        assertThrows(EruptWebApiRuntimeException.class, () -> sms.template(Templates.NO_SUCH).to("13800000005").send());
        assertThrows(EruptWebApiRuntimeException.class, () -> sms.via("No such channel").template(Templates.ORDER_SHIPPED).to("13800000005").send());

        sms.code("13900000009").param("product", "erupt").send();
        String code = sent.get(sent.size() - 1).params().get("code");
        assertEquals("erupt", sent.get(sent.size() - 1).params().get("product"));
        assertTrue(sms.code("13900000009").verify(code));
    }

}
