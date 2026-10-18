package xyz.erupt.sms.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xyz.erupt.annotation.fun.OperationHandler;
import xyz.erupt.jpa.dao.EruptDao;
import xyz.erupt.sms.core.SmsSender;
import xyz.erupt.sms.model.SmsMessage;

import java.util.List;

/**
 * @author YuePeng
 */
@Component
public class SmsMessageHandler implements OperationHandler<SmsMessage, Void> {

    private final EruptDao eruptDao;

    private final SmsSender sender;

    public SmsMessageHandler(EruptDao eruptDao, SmsSender sender) {
        this.eruptDao = eruptDao;
        this.sender = sender;
    }

    @Override
    @Transactional
    public String exec(List<SmsMessage> data, Void form, String[] param) {
        SmsMessage message = eruptDao.find(SmsMessage.class, data.get(0).getId());
        sender.send(message);
        eruptDao.merge(message);
        return "msg.info('" + message.getStatus() + "')";
    }

}
