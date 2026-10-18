package xyz.erupt.sms.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.springframework.stereotype.Service;
import xyz.erupt.core.config.GsonFactory;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.core.i18n.I18nTranslate;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.linq.lambda.LambdaSee;
import xyz.erupt.linq.lambda.SFunction;
import xyz.erupt.sms.model.SmsChannel;
import xyz.erupt.sms.model.SmsCode;
import xyz.erupt.sms.model.SmsMessage;
import xyz.erupt.sms.model.SmsTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The API a host application sends through. A template is named by its code, a parameter by a
 * record field, and the channel is the default one unless {@link #via} picks another:
 * <pre>
 * &#64;Resource
 * private EruptSms sms;
 *
 * record OrderShipped(String name, String order) {}
 *
 * sms.template(Templates.ORDER_SHIPPED).to("13800000000").params(new OrderShipped("Ann", "A-1")).send();
 * sms.template("ORDER_SHIPPED").to(phones).param(OrderShipped::name, "Ann").param(OrderShipped::order, "A-1").send();
 * sms.via("Marketing").template(Templates.SALE).to(phones).send();
 *
 * sms.code("13800000000").send();                       // the template's ${code} and ${minutes} are filled in
 * sms.code("13800000000").param("product", "erupt").send();
 * sms.code("13800000000").verify("482913");
 * </pre>
 * Every send goes the way the admin's own does: blacklist, the template's parameter order, and a
 * row in Messages with the outcome. A failed send throws and leaves a FAILED row.
 *
 * @author YuePeng
 */
@Service
public class EruptSms {

    private final EruptDao eruptDao;

    private final SmsSender sender;

    private final SmsCodeService codeService;

    public EruptSms(EruptDao eruptDao, SmsSender sender, SmsCodeService codeService) {
        this.eruptDao = eruptDao;
        this.sender = sender;
        this.codeService = codeService;
    }

    /** A channel other than the default, by the name shown in the admin. */
    public Channel via(String channel) {
        return new Channel(this.channel(channel));
    }

    public Message template(String code) {
        return new Channel(this.defaultChannel()).template(code);
    }

    public Message template(Enum<?> code) {
        return this.template(code.name());
    }

    public Code code(String phone) {
        return new Channel(this.defaultChannel()).code(phone);
    }

    public final class Channel {

        private final SmsChannel channel;

        private Channel(SmsChannel channel) {
            this.channel = channel;
        }

        public Message template(String code) {
            SmsTemplate template = eruptDao.lambdaQuery(SmsTemplate.class)
                    .with(SmsTemplate::getChannel).eq(SmsChannel::getId, channel.getId()).with()
                    .eq(SmsTemplate::getCode, code).eq(SmsTemplate::getEnabled, true).list().stream().findFirst()
                    .orElseThrow(() -> new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.template_unknown") + " → " + code));
            return new Message(channel, template);
        }

        public Message template(Enum<?> code) {
            return this.template(code.name());
        }

        public Code code(String phone) {
            return new Code(channel, phone);
        }
    }

    public final class Message {

        private final SmsChannel channel;

        private final SmsTemplate template;

        private final List<String> phones = new ArrayList<>();

        private final LinkedHashMap<String, String> params = new LinkedHashMap<>();

        private Message(SmsChannel channel, SmsTemplate template) {
            this.channel = channel;
            this.template = template;
        }

        public Message to(String... phones) {
            this.phones.addAll(List.of(phones));
            return this;
        }

        public Message to(List<String> phones) {
            this.phones.addAll(phones);
            return this;
        }

        /** One parameter by the field that names it: {@code param(OrderShipped::order, "A-1")}. */
        public <T, R> Message param(SFunction<T, R> field, Object value) {
            return this.param(LambdaSee.field(field), value);
        }

        public Message param(String name, Object value) {
            params.put(name, null == value ? "" : String.valueOf(value));
            return this;
        }

        /** Every parameter at once, from a record or bean whose fields carry the names, or from a map. */
        public Message params(Object source) {
            params.putAll(EruptSms.params(source));
            return this;
        }

        public SmsMessage send() {
            return sender.send(channel, template, phones, params);
        }
    }

    public final class Code {

        private final SmsChannel channel;

        private final String phone;

        private final LinkedHashMap<String, String> params = new LinkedHashMap<>();

        private Code(SmsChannel channel, String phone) {
            this.channel = channel;
            this.phone = phone;
        }

        /** A parameter of the code template besides {@code code} and {@code minutes}, which are filled in. */
        public <T, R> Code param(SFunction<T, R> field, Object value) {
            return this.param(LambdaSee.field(field), value);
        }

        public Code param(String name, Object value) {
            params.put(name, null == value ? "" : String.valueOf(value));
            return this;
        }

        public Code params(Object source) {
            params.putAll(EruptSms.params(source));
            return this;
        }

        public SmsCode send() {
            return codeService.send(channel, phone, params);
        }

        public boolean verify(String code) {
            return codeService.verify(channel, phone, code);
        }
    }

    // A record, a bean or a map, flattened to name → text
    static LinkedHashMap<String, String> params(Object source) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (null == source) return values;
        if (source instanceof Map<?, ?> map) {
            map.forEach((key, value) -> values.put(String.valueOf(key), null == value ? "" : String.valueOf(value)));
            return values;
        }
        JsonElement json = GsonFactory.getGson().toJsonTree(source);
        if (!json.isJsonObject()) throw new EruptWebApiRuntimeException("Parameters need a record, a bean or a map, not " + source.getClass().getSimpleName());
        for (Map.Entry<String, JsonElement> entry : ((JsonObject) json).entrySet()) {
            values.put(entry.getKey(), entry.getValue().isJsonPrimitive() ? entry.getValue().getAsString() : entry.getValue().toString());
        }
        return values;
    }

    public SmsChannel channel(String name) {
        return eruptDao.lambdaQuery(SmsChannel.class).eq(SmsChannel::getName, name).eq(SmsChannel::getEnabled, true).list().stream().findFirst()
                .orElseThrow(() -> new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.channel_unknown") + " → " + name));
    }

    public SmsChannel defaultChannel() {
        return eruptDao.lambdaQuery(SmsChannel.class).eq(SmsChannel::getIsDefault, true).eq(SmsChannel::getEnabled, true).list().stream().findFirst()
                .orElseThrow(() -> new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.default_channel_missing")));
    }

}
