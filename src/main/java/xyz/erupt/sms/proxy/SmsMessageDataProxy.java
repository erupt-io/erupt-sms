package xyz.erupt.sms.proxy;

import org.springframework.stereotype.Component;
import xyz.erupt.annotation.fun.DataProxy;
import xyz.erupt.sms.core.SmsSender;
import xyz.erupt.sms.model.SmsMessage;

/**
 * Saving a message sends it.
 *
 * @author YuePeng
 */
@Component
public class SmsMessageDataProxy implements DataProxy<SmsMessage> {

    private final SmsSender sender;

    public SmsMessageDataProxy(SmsSender sender) {
        this.sender = sender;
    }

    @Override
    public void beforeAdd(SmsMessage message) {
        sender.send(message);
    }

}
