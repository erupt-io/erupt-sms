package xyz.erupt.sms.core;

import org.dromara.sms4j.api.entity.SmsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.core.i18n.I18nTranslate;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.sms.model.SmsBlacklist;
import xyz.erupt.sms.model.SmsChannel;
import xyz.erupt.sms.model.SmsCode;
import xyz.erupt.sms.model.SmsTemplate;
import xyz.erupt.sms.prop.EruptSmsProperties;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verification codes: issued through the channel's code template, rate limited per phone, and
 * good for one check. Reached through {@link EruptSms#code}.
 *
 * @author YuePeng
 */
@Service
public class SmsCodeService {

    private final SecureRandom random = new SecureRandom();

    private final EruptDao eruptDao;

    private final SmsChannels channels;

    private final EruptSmsProperties properties;

    public SmsCodeService(EruptDao eruptDao, SmsChannels channels, EruptSmsProperties properties) {
        this.eruptDao = eruptDao;
        this.channels = channels;
        this.properties = properties;
    }

    /**
     * @param params the template's parameters besides {@code code} and {@code minutes}, which are filled in here
     */
    @Transactional
    public SmsCode send(SmsChannel channel, String phone, Map<String, String> params) {
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
            SmsResponse response = channels.get(channelId).sendMessage(phone, template.getTemplateId(), SmsSender.ordered(template, values));
            code.setSent(response.isSuccess());
        } catch (RuntimeException e) {
            code.setSent(false);
        }
        eruptDao.persist(code);
        if (!code.getSent()) throw new EruptWebApiRuntimeException(I18nTranslate.$translate("sms.send_failed"));
        return code;
    }

    @Transactional
    public boolean verify(SmsChannel channel, String phone, String value) {
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

    private String generate() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < properties.getCodeLength(); i++) code.append(random.nextInt(10));
        return code.toString();
    }

}
