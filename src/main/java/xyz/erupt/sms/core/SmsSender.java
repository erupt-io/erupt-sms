package xyz.erupt.sms.core;

import org.dromara.sms4j.api.entity.SmsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.core.i18n.I18nTranslate;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.sms.model.*;
import xyz.erupt.sms.prop.EruptSmsProperties;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends through a channel: the blacklist is checked, the template's parameters are passed in its
 * order, and the outcome lands on the message row. Verification codes are issued through the
 * channel's code template, rate limited per phone, and good for one check.
 *
 * @author YuePeng
 */
@Service
public class SmsSender {

    private final SecureRandom random = new SecureRandom();

    private final SmsChannels channels;

    private final EruptDao eruptDao;

    private final EruptSmsProperties properties;

    public SmsSender(SmsChannels channels, EruptDao eruptDao, EruptSmsProperties properties) {
        this.channels = channels;
        this.eruptDao = eruptDao;
        this.properties = properties;
    }

    /** A send from code: the message row is created, sent and kept; a failure throws after the row is kept. */
    @Transactional
    public SmsMessage send(SmsChannel channel, SmsTemplate template, List<String> phones, Map<String, String> params) {
        SmsMessage message = new SmsMessage();
        message.setChannel(channel);
        message.setTemplate(template);
        message.setPhones(String.join("\n", phones));
        message.setParams(new LinkedHashMap<>(params));
        this.send(message);
        eruptDao.persist(message);
        if (SmsMessage.FAILED.equals(message.getStatus())) {
            throw new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.send_failed") + " → " + message.getResponse());
        }
        return message;
    }

    public void send(SmsMessage message) {
        SmsChannel channel = eruptDao.find(SmsChannel.class, message.getChannel().getId());
        SmsTemplate template = eruptDao.find(SmsTemplate.class, message.getTemplate().getId());
        List<String> phones = SmsText.lines(message.getPhones());
        if (phones.isEmpty()) throw new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.phones_empty"));
        for (String phone : phones) {
            if (eruptDao.lambdaQuery(SmsBlacklist.class).eq(SmsBlacklist::getPhone, phone).count() > 0) {
                throw new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.blacklisted") + " → " + phone);
            }
        }
        LinkedHashMap<String, String> params = ordered(template, null == message.getParams() ? new LinkedHashMap<>() : new LinkedHashMap<>(message.getParams()));
        message.setContent(SmsText.render(template.getContent(), params));
        message.setSendTime(LocalDateTime.now());
        try {
            SmsResponse response = phones.size() == 1
                    ? channels.get(channel.getId()).sendMessage(phones.get(0), template.getTemplateId(), params)
                    : channels.get(channel.getId()).massTexting(phones, template.getTemplateId(), params);
            message.setStatus(response.isSuccess() ? SmsMessage.SENT : SmsMessage.FAILED);
            message.setResponse(null == response.getData() ? null : String.valueOf(response.getData()));
        } catch (RuntimeException e) {
            message.setStatus(SmsMessage.FAILED);
            message.setResponse(e.getMessage());
        }
    }

    /**
     * @param params the template's parameters besides {@code code} and {@code minutes}, which are filled in here
     */
    @Transactional
    public SmsCode sendCode(SmsChannel channel, String phone, Map<String, String> params) {
        Long channelId = channel.getId();
        if (null == phone || !phone.matches("\\+?\\d{6,20}")) throw new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.phone_invalid"));
        if (eruptDao.lambdaQuery(SmsBlacklist.class).eq(SmsBlacklist::getPhone, phone).count() > 0) {
            throw new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.blacklisted"));
        }
        List<SmsCode> today = eruptDao.lambdaQuery(SmsCode.class)
                .with(SmsCode::getChannel).eq(SmsChannel::getId, channelId).with()
                .eq(SmsCode::getPhone, phone).ge(SmsCode::getSendTime, LocalDate.now().atStartOfDay())
                .orderByDesc(SmsCode::getSendTime).list();
        if (today.size() >= channel.getDailyCodeLimit()) throw new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.daily_limit"));
        if (!today.isEmpty() && today.get(0).getSendTime().plusSeconds(properties.getResendSeconds()).isAfter(LocalDateTime.now())) {
            throw new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.resend_wait"));
        }
        SmsTemplate template = eruptDao.lambdaQuery(SmsTemplate.class)
                .with(SmsTemplate::getChannel).eq(SmsChannel::getId, channelId).with()
                .eq(SmsTemplate::getKind, SmsTemplate.CODE).eq(SmsTemplate::getEnabled, true).list().stream().findFirst()
                .orElseThrow(() -> new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.code_template_missing")));
        SmsCode code = new SmsCode();
        code.setChannel(channel);
        code.setPhone(phone);
        code.setCode(this.generate());
        code.setExpireAt(LocalDateTime.now().plusSeconds(properties.getCodeTtlSeconds()));
        code.setSendTime(LocalDateTime.now());
        LinkedHashMap<String, String> values = new LinkedHashMap<>(null == params ? Map.of() : params);
        values.put("code", code.getCode());
        values.putIfAbsent("minutes", String.valueOf(Math.max(1, properties.getCodeTtlSeconds() / 60)));
        try {
            SmsResponse response = channels.get(channelId).sendMessage(phone, template.getTemplateId(), ordered(template, values));
            code.setSent(response.isSuccess());
        } catch (RuntimeException e) {
            code.setSent(false);
        }
        eruptDao.persist(code);
        if (!code.getSent()) throw new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.send_failed"));
        return code;
    }

    @Transactional
    public boolean verifyCode(SmsChannel channel, String phone, String value) {
        Long channelId = channel.getId();
        if (null == value || value.isBlank()) return false;
        SmsCode code = eruptDao.lambdaQuery(SmsCode.class)
                .with(SmsCode::getChannel).eq(SmsChannel::getId, channelId).with()
                .eq(SmsCode::getPhone, phone).eq(SmsCode::getUsed, false).eq(SmsCode::getSent, true)
                .orderByDesc(SmsCode::getSendTime).list().stream().findFirst().orElse(null);
        if (null == code || code.getExpireAt().isBefore(LocalDateTime.now()) || !code.getCode().equals(value.trim())) return false;
        code.setUsed(true);
        eruptDao.merge(code);
        return true;
    }

    // The vendor reads parameters by position, so they go in the template's declared order
    public static LinkedHashMap<String, String> ordered(SmsTemplate template, Map<String, String> given) {
        List<String> names = SmsText.lines(null == template.getParams() ? "" : template.getParams().replace(',', '\n'));
        if (names.isEmpty()) return new LinkedHashMap<>(given);
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        for (String name : names) {
            if (!given.containsKey(name)) throw new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.param_missing") + " → " + name);
            ordered.put(name, given.get(name));
        }
        return ordered;
    }

    private String generate() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < properties.getCodeLength(); i++) code.append(random.nextInt(10));
        return code.toString();
    }

}
