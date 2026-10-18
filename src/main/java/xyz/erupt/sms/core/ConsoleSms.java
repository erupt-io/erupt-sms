package xyz.erupt.sms.core;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.provider.config.BaseConfig;
import org.dromara.sms4j.provider.factory.BaseProviderFactory;
import org.dromara.sms4j.provider.service.AbstractSmsBlend;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * A vendor that sends nothing: every message is logged and kept in memory, and reported as sent.
 * Pick it on a channel to try templates, messages and verification codes without an account.
 *
 * @author YuePeng
 */
@Slf4j
public class ConsoleSms extends AbstractSmsBlend<ConsoleSms.Config> {

    public static final String SUPPLIER = "console";

    private static final int KEEP = 200;

    public record Message(LocalDateTime time, List<String> phones, String templateId, LinkedHashMap<String, String> params, String text) {
    }

    private static final Deque<Message> messages = new ArrayDeque<>();

    public ConsoleSms(Config config) {
        super(config);
    }

    // The most recent first
    public static List<Message> messages() {
        synchronized (messages) {
            return List.copyOf(messages);
        }
    }

    private SmsResponse keep(List<String> phones, String templateId, LinkedHashMap<String, String> params, String text) {
        Message message = new Message(LocalDateTime.now(), phones, templateId, null == params ? new LinkedHashMap<>() : params, text);
        synchronized (messages) {
            messages.addFirst(message);
            while (messages.size() > KEEP) messages.removeLast();
        }
        log.info("[erupt-sms console] to {} template {} params {} text {}", phones, templateId, message.params(), text);
        SmsResponse response = new SmsResponse();
        response.setSuccess(true);
        response.setConfigId(this.getConfigId());
        response.setData("console");
        return response;
    }

    @Override
    public String getConfigId() {
        return this.getConfig().getConfigId();
    }

    @Override
    public String getSupplier() {
        return SUPPLIER;
    }

    @Override
    public SmsResponse sendMessage(String phone, String message) {
        return this.keep(List.of(phone), null, null, message);
    }

    @Override
    public SmsResponse sendMessage(String phone, LinkedHashMap<String, String> params) {
        return this.keep(List.of(phone), this.getConfig().getTemplateId(), params, null);
    }

    @Override
    public SmsResponse sendMessage(String phone, String templateId, LinkedHashMap<String, String> params) {
        return this.keep(List.of(phone), templateId, params, null);
    }

    @Override
    public SmsResponse massTexting(List<String> phones, String message) {
        return this.keep(phones, null, null, message);
    }

    @Override
    public SmsResponse massTexting(List<String> phones, String templateId, LinkedHashMap<String, String> params) {
        return this.keep(phones, templateId, params, null);
    }

    @Getter
    @Setter
    public static class Config extends BaseConfig {

        @Override
        public String getSupplier() {
            return SUPPLIER;
        }
    }

    public static class Factory implements BaseProviderFactory<ConsoleSms, Config> {

        @Override
        public ConsoleSms createSms(Config config) {
            return new ConsoleSms(config);
        }

        @Override
        public Class<Config> getConfigClass() {
            return Config.class;
        }

        @Override
        public String getSupplier() {
            return SUPPLIER;
        }
    }

}
