package xyz.erupt.sms.core;

import org.dromara.sms4j.api.entity.SmsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.erupt.core.exception.EruptWebApiRuntimeException;
import xyz.erupt.core.i18n.I18nTranslate;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.sms.model.SmsBlacklist;
import xyz.erupt.sms.model.SmsChannel;
import xyz.erupt.sms.model.SmsMessage;
import xyz.erupt.sms.model.SmsTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends through a channel: the blacklist is checked, the template's parameters are passed in its
 * order, and the outcome lands on the message row.
 *
 * @author YuePeng
 */
@Service
public class SmsSender {

    private final SmsChannels channels;

    private final EruptDao eruptDao;

    public SmsSender(SmsChannels channels, EruptDao eruptDao) {
        this.channels = channels;
        this.eruptDao = eruptDao;
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

}
